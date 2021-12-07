package jp.co.techknowledge.ble_echo;

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
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private String LOG_TAG = "ble_echo";
    private BluetoothManager _btManager;
    private BluetoothAdapter _btAdapter;
    private BluetoothGattServer _gattServer;
    private BluetoothLeAdvertiser _advertiser;
    private BluetoothDevice _peerDevice;
    private AdvertisingSetCallback _advertisSetCallback;
    private AdvertiseCallback _advertiseCallBack;
    private AdvertisingSet _currentAdvertisingSet;
    private BluetoothGattCharacteristic _notifyCharacteristic;
    private Button _startAdvertiseButton;
    private Button _stopAdvertiseButton;
    private byte[] _lastValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _startAdvertiseButton = findViewById(R.id.advertise_start_btn);
        _startAdvertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startAdvertize();
            }
        });
        _stopAdvertiseButton = findViewById(R.id.advertise_stop_btn);
        _stopAdvertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopAdvertise();
            }
        });

        if( !BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported() ) {
            Toast.makeText( this, "Multiple advertisement not supported", Toast.LENGTH_SHORT ).show();
            _startAdvertiseButton.setEnabled( false );
            _stopAdvertiseButton.setEnabled( false );
        }

        Context ctx = getApplicationContext();
        _btManager = (BluetoothManager) getSystemService( ctx.BLUETOOTH_SERVICE);
        _btAdapter = BluetoothAdapter.getDefaultAdapter();
        _btAdapter.setName("ble_echo");
        _advertiser = _btAdapter.getBluetoothLeAdvertiser();
        if( _advertiser == null ){
            Toast.makeText(this, "BLE Peripheralモードが使用できません。", Toast.LENGTH_SHORT).show();
            return;
        }

        _gattServer = _btManager.openGattServer(this, _gattServerCallback);

        final String PRIMARY_SERVICE_UUID = getString(R.string.primary_service_uuid);
        final String NOTIFY_UUID = getString(R.string.indicate_uuid);
        final String WRITE_UUID = getString(R.string.write_uuid);
        final String READ_UUID = getString(R.string.read_uuid);

        BluetoothGattService previousService = _gattServer.getService( UUID.fromString(PRIMARY_SERVICE_UUID));

        if(null != previousService) _gattServer.removeService(previousService);

        _notifyCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString(NOTIFY_UUID),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString(WRITE_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattCharacteristic readCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString(READ_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService deviceInfoService = new BluetoothGattService(
                UUID.fromString(PRIMARY_SERVICE_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        deviceInfoService.addCharacteristic(readCharacteristic);
        deviceInfoService.addCharacteristic(writeCharacteristic);
        deviceInfoService.addCharacteristic(_notifyCharacteristic);
        _gattServer.addService(deviceInfoService);
    }

    private final BluetoothGattServerCallback _gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(LOG_TAG, "onConnectionStateChange :status:" + newState);
            if(newState == BluetoothProfile.STATE_CONNECTED){
                _peerDevice = device;
            }
            else{
                _peerDevice = null;
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            // super.onServiceAdded(status, service);
            Log.i(LOG_TAG, "onServiceAdded :status:" + status);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            // super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(LOG_TAG, "onCharacteristicReadRequest :status:" + device);
            _gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, _lastValue);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.i(LOG_TAG, "onCharacteristicWriteRequest :status:" + device);
            _gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            String msg = new String(value, Charset.forName("UTF8"));
            Log.i(LOG_TAG, msg);
            _notifyCharacteristic.setValue(value);
            _gattServer.notifyCharacteristicChanged(_peerDevice, _notifyCharacteristic, false);
            _lastValue = value;
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(LOG_TAG, "onDescriptorReadRequest :status:" + device);
            if(_lastValue!=null) {
                _gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, _lastValue);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(LOG_TAG, "onDescriptorWriteRequest :status:" + device);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(LOG_TAG, "onExecuteWrite :status:" + device);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(LOG_TAG, "onNotificationSent :status:" + status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(LOG_TAG, "onMtuChanged :mtu:" + mtu);
        }
    };

    private void stopAdvertise(){
        if(_advertiseCallBack!=null) {
            _advertiser.stopAdvertising(_advertiseCallBack);
        }
    }

    private void startAdvertize(){

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeTxPowerLevel(true);
        dataBuilder.addServiceUuid(ParcelUuid.fromString(getString( R.string.advatise_uuid )));

        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        settingsBuilder.setTimeout(0);
        settingsBuilder.setConnectable(true);

        AdvertiseData.Builder respBuilder = new AdvertiseData.Builder();
        respBuilder.setIncludeDeviceName(true);

        _advertiseCallBack = new AdvertiseCallback(){
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(LOG_TAG, "onStartSuccess");
            }
            @Override
            public void onStartFailure(int errorCode) {
                Log.i(LOG_TAG, "onStartFailure");
            }
        };

        _advertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), respBuilder.build(), _advertiseCallBack);
    }

    // andorid 8 or later.
    private void startAdvertizeAndroid8OrLater(){

        // java.lang.IllegalStateException: Legacy advertisement can't be connectable and non-scannable
        AdvertisingSetParameters settings = (new AdvertisingSetParameters.Builder())
                .setLegacyMode(false)       // => cant be connectable
                .setConnectable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                .build();

        ParcelUuid pUuid = new ParcelUuid( UUID.fromString( getString( R.string.advatise_uuid ) ) );

        byte[] name = "echo".getBytes(StandardCharsets.UTF_8);
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName( false )          // true => advertise data too big.
                .addServiceUuid( pUuid )
                .build();

        _advertisSetCallback = new AdvertisingSetCallback() {
            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                Log.i(LOG_TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
                        + status);
                _currentAdvertisingSet = advertisingSet;
            }

            @Override
            public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
                Log.i(LOG_TAG, "onAdvertisingDataSet() :status:" + status);
            }

            @Override
            public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
                Log.i(LOG_TAG, "onScanResponseDataSet(): status:" + status);
            }

            @Override
            public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                Log.i(LOG_TAG, "onAdvertisingSetStopped():");
            }
        };

        // https://stackoverflow.com/questions/28843123/how-can-android-broadcast-ble-local-name-like-in-ios
        AdvertiseData.Builder scanResponseBuilder = new AdvertiseData.Builder();
        scanResponseBuilder.addServiceData(pUuid,name);
        scanResponseBuilder.setIncludeDeviceName(true);
        // mBtAdapter.setName("echo");

        _advertiser.startAdvertisingSet( settings, data, scanResponseBuilder.build(), null, null, _advertisSetCallback);
    }
    private void stopAdvertiseAndroid8(){
        _advertiser.stopAdvertisingSet(_advertisSetCallback);
    }

}