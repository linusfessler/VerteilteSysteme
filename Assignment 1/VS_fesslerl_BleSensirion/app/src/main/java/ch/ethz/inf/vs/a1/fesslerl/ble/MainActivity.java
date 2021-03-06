package ch.ethz.inf.vs.a1.fesslerl.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback{

    private BluetoothAdapter btAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    private final static String LOG_LOC = "MainActivity";
    private final static int MAX_SCAN_TIME = 15000;
    private Handler handler;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> deviceNames;
    private List<BluetoothDevice> devices;
    private BluetoothLeScanner scanner;
    ScanCallback scanCallback;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // For testing
        //Intent startDeviceActivity = new Intent(MainActivity.this, ConnectedActivity.class);
        //startActivity(startDeviceActivity);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = bluetoothManager.getAdapter();
        handler = new Handler();

        devices = new ArrayList<>();

        deviceNames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                Log.d(LOG_LOC, "### found device: " + result.toString());
                BluetoothDevice device = result.getDevice();
                String deviceString = device.getName() + " (" + device.getAddress() +")";

                if(!deviceNames.contains(deviceString)) { //avoid multiple entries for the same gadget
                    devices.add(device);
                    deviceNames.add(deviceString);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                failed("scan failed");

            }
        };

        ListView lv_devices = (ListView) findViewById(R.id.bt_device_list);
        lv_devices.setAdapter(adapter);
        lv_devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent startDeviceActivity = new Intent(MainActivity.this, ConnectedActivity.class);
                Bundle bundle = new Bundle();
                stopScan();
                bundle.putParcelable(BleGlobalConsts.DEVICE_KEY, devices.get(position));
                startDeviceActivity.putExtras(bundle);
                startActivity(startDeviceActivity);
            }
        });

        Log.d(LOG_LOC, "### btAdapter created ###");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if Bluetooth is available (!=null) and enabled (isEnabled())
        if (btAdapter != null) {
            // Bluetooth is available
            Log.d(LOG_LOC, "### Bluetooth is available ###");
            if (!btAdapter.isEnabled()) {
                // Bluetooth is not enabled. Ask user for permission
                Log.d(LOG_LOC, "### Bluetooth is not enabled ###");
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
                // Bluetooth enabled and ready
                // Scan for Bluetooth devices
            } else {
                Log.d(LOG_LOC, "### Bluetooth already enabled ###");
                getLocationPermissionThenScan();
            }
        } else {
            failed("Bluetooth not available.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK)
            getLocationPermissionThenScan();
        else
            failed("Bluetooth activation was canceled.");
    }

    private void getLocationPermissionThenScan(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_LOC,"### Ask for ACCESS_FINE_LOCATION permission.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        } else{
            Log.d(LOG_LOC,"### Already has ACCESS_FINE_LOCATION permission.");
            activateLocationThenScan();
        }
    }

    private void activateLocationThenScan(){
        //TODO: Check if this method works in normal program flow
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean locationEnabled = false;

        // Check if location service is enabled.
        // TODO: Look at alternative Implementation using Settings.Secure.LOCATION_MODE
        try{
            // isProviderEnabled returns true if Location service is enabled, false if it is not. Can throw IllegalArgumentException
            locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }catch(IllegalArgumentException ex){
            // TODO: Implement proper exception handling
            Log.d(LOG_LOC, "IllegalArgumentException thrown when trying to check if location services are enabled.");
        }

        // Ask for enabling of Location services only if they are not enabled yet.
        if(!locationEnabled){
            // Create Dialog Box asking the user to enable Location Services in settings.
            // Pressing "YES" will open the settings app
            Log.d(LOG_LOC, "Location services not enabled.");
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage(R.string.dialog_request_location_sevices);
            dialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(settingsIntent);
                    Log.d(LOG_LOC, "Button " + (which) + " pressed. (YES)");
                }
            });
            dialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO: Implement negative action
                    Log.d(LOG_LOC, "Button " + (which) + " pressed. (NO)");

                }
            });

            // TODO: Check why dialog is not shown. (???)
            // Build and show dialog.
            dialog.show();

        }
        else{
            Log.d(LOG_LOC, "Location services already enabled");
        }


        scanForDevices();
    }

    //After the app asked for ACCESS_FINE_PERMISSION, this method will be called.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
            activateLocationThenScan();
        else
            failed("Permission was not granted.");
    }

    private void scanForDevices(){
        Log.d(LOG_LOC, "### Scanning for Bluetooth devices ###");


        scanner = btAdapter.getBluetoothLeScanner();


        List<ScanFilter> scanFilters= new ArrayList<>();

        //TODO: implement filters.
/*
        ScanFilter.Builder builder = new ScanFilter.Builder();

        builder.setServiceUuid(new ParcelUuid(SensirionSHT31UUIDS.UUID_TEMPERATURE_SERVICE));
        scanFilters.add(builder.build());
        builder.setServiceUuid(new ParcelUuid(SensirionSHT31UUIDS.UUID_HUMIDITY_SERVICE));
        scanFilters.add(builder.build());
        builder.setServiceUuid(new ParcelUuid(SensirionSHT31UUIDS.UUID_HUMIDITY_CHARACTERISTIC));
        scanFilters.add(builder.build());
        builder.setServiceUuid(new ParcelUuid(SensirionSHT31UUIDS.UUID_TEMPERATURE_CHARACTERISTIC));
        scanFilters.add(builder.build());
        //builder.setDeviceName("Smart Humigadget");

        //scanFilters.add(builder.build());*/




        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, MAX_SCAN_TIME);
        Log.d(LOG_LOC, "starting scan");
        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        scanner.startScan(scanFilters, settingsBuilder.build(), scanCallback);

    }

    protected void failed(String s){
        //TODO: hier möglicherweise eine Error Activity erstellen und aufrufen.. (z.B wenn kein Bluetooth vorhaben)
        Log.d(LOG_LOC, "ERROR: " + s);
    }

    private void stopScan(){
        if(scanner != null){
            Log.d(LOG_LOC, "stopping scan");
            scanner.stopScan(scanCallback);
            scanner = null;
        }
    }


}
