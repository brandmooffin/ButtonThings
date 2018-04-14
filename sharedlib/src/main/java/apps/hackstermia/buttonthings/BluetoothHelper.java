package apps.hackstermia.buttonthings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";

    public static final String ANDROID_THINGS_DEVICE_NAME = "My Android Things device";
    public static final String MOBILE_DEVICE_NAME = "Pixel 2";

    public static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    public static final long SCAN_PERIOD = 10000;

    private static BluetoothManager mBluetoothManager;
    private static BluetoothGattServer mBluetoothGattServer;
    private static BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    public static BluetoothManager getBluetoothManager()
    {
        return mBluetoothManager;
    }

    public static BluetoothGattServer getBluetoothGattServer()
    {
        return mBluetoothGattServer;
    }

    public static void setBluetoothManager(BluetoothManager bluetoothManager)
    {
        mBluetoothManager = bluetoothManager;
    }

    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Remote LED Service.
     */
    public static void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = BluetoothHelper.getBluetoothManager().getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(RemoteLedProfile.REMOTE_LED_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    public static void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Remote LED Profile.
     */
    public static void startServer(Context context, BluetoothGattServerCallback mGattServerCallback) {
        mBluetoothGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(RemoteLedProfile.createRemoteLedService());
    }

    /**
     * Shut down the GATT server.
     */
    public static void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private static AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };
}