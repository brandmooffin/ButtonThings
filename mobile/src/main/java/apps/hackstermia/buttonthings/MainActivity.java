package apps.hackstermia.buttonthings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    Boolean toggleLight = false;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private String mDeviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        BluetoothHelper.setBluetoothManager((BluetoothManager) getSystemService(BLUETOOTH_SERVICE));

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth enabled...starting services");
                BluetoothHelper.startAdvertising();
                BluetoothHelper.startServer(this, mGattServerCallback);
            }else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 0);
            }
        }

        Button toggleButton = findViewById(R.id.toggle_button);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLight = !toggleLight;
                notifyRegisteredDevices(toggleLight);
            }
        });
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    BluetoothHelper.startAdvertising();
                    BluetoothHelper.startServer(MainActivity.this, mGattServerCallback);
                    break;
                case BluetoothAdapter.STATE_OFF:
                    BluetoothHelper.stopServer();
                    BluetoothHelper.stopAdvertising();
                    break;
                default:
                    // Do nothing
            }
        }
    };

    private void initScan() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not available or not enabled.");
            return;
        }
        //setupBTProfiles();
        Log.d(TAG, "Set up Bluetooth Adapter name and profile");
        mBluetoothAdapter.setName(BluetoothHelper.MOBILE_DEVICE_NAME);
        scanLeDevice();
    }

    private void scanLeDevice() {
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }, BluetoothHelper.SCAN_PERIOD);

        mScanning = true;
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Boolean isDeviceFound = false;
                    if(device != null){
                        final String deviceName = device.getName();
                        if (deviceName != null && deviceName.length() > 0) {
                            if(deviceName.equals(BluetoothHelper.ANDROID_THINGS_DEVICE_NAME)){
                                isDeviceFound = true;
                                mDeviceAddress = device.getAddress();
                            }
                        }
                    }

                    if(isDeviceFound && !mConnected){
                        Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                        mConnected = true;
                    }
                }
            };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            Boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            mConnected = true;
            if(mScanning) {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * Send a remote led service notification to any devices that are subscribed
     * to the characteristic.
     */
    private void notifyRegisteredDevices(Boolean toggle) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic ledDataCharacteristic = BluetoothHelper.getBluetoothGattServer()
                    .getService(RemoteLedProfile.REMOTE_LED_SERVICE)
                    .getCharacteristic(RemoteLedProfile.REMOTE_LED_DATA);
            ledDataCharacteristic.setValue(toggle.toString());
            BluetoothHelper.getBluetoothGattServer().notifyCharacteristicChanged(device, ledDataCharacteristic, false);
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
                mRegisteredDevices.add(device);
                initScan();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            if (RemoteLedProfile.REMOTE_LED_DATA.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read data");
                BluetoothHelper.getBluetoothGattServer().sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                BluetoothHelper.getBluetoothGattServer().sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            BluetoothHelper.getBluetoothGattServer().sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (responseNeeded) {
                BluetoothHelper.getBluetoothGattServer().sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                List<BluetoothGattService> services = mBluetoothLeService.getSupportedGattServices();
                if(services != null){
                    for (BluetoothGattService gattService : services) {
                        if(gattService.getUuid().equals(RemoteLedProfile.REMOTE_LED_SERVICE)){
                            final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(RemoteLedProfile.REMOTE_LED_DATA);
                            if (characteristic != null) {
                                final int charaProp = characteristic.getProperties();
                                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                                    // If there is an active notification on a characteristic, clear
                                    // it first so it doesn't update the data field on the user interface.
                                    if (mNotifyCharacteristic != null) {
                                        mBluetoothLeService.setCharacteristicNotification(
                                                mNotifyCharacteristic, false);
                                        mNotifyCharacteristic = null;
                                    }
                                    mBluetoothLeService.readCharacteristic(characteristic);
                                }
                                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                    mNotifyCharacteristic = characteristic;
                                    mBluetoothLeService.setCharacteristicNotification(
                                            characteristic, true);
                                }
                            }
                        }
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Toast.makeText(context, "Raspberry Pi Toggled!", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mGattUpdateReceiver, BluetoothHelper.makeGattUpdateIntentFilter());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothHelper.REQUEST_ENABLE_BT) {
            Log.d(TAG, "Enable discoverable returned with result " + resultCode);

            // ResultCode, as described in BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, is either
            // RESULT_CANCELED or the number of milliseconds that the device will stay in
            // discoverable mode. In a regular Android device, the user will see a popup requesting
            // authorization, and if they cancel, RESULT_CANCELED is returned. In Android Things,
            // on the other hand, the authorization for pairing is always given without user
            // interference, so RESULT_CANCELED should never be returned.
            if (resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Enable discoverable has been cancelled by the user. " +
                        "This should never happen in an Android Things device.");
            }
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BluetoothHelper.getBluetoothManager().getAdapter().isEnabled()) {
            BluetoothHelper.stopServer();
            BluetoothHelper.stopAdvertising();
        }

        unregisterReceiver(mBluetoothReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
}
