package com.tpms.monitor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.tpms.monitor.model.TPMSSensor;
import com.tpms.monitor.service.SerialService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Enhanced settings activity with Bluetooth support
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "TPMSPrefs";
    private static final int REQUEST_BLUETOOTH_PERMISSION = 100;
    
    // UI Components
    private EditText targetPressureEdit;
    private EditText targetTemperatureEdit;
    private EditText pressureDeviationEdit;
    private EditText temperatureDeviationEdit;
    private Spinner baudRateSpinner;
    private Spinner connectionTypeSpinner;
    private Spinner bluetoothDeviceSpinner;
    private Switch speechEnabledSwitch;
    private Button saveButton;
    private Button testSpeechButton;
    
    // Configuration options
    private static final Integer[] BAUD_RATES = {9600, 19200, 38400, 115200};
    private static final String[] CONNECTION_TYPES = {SerialService.CONNECTION_USB, SerialService.CONNECTION_BLUETOOTH};
    private static final int DEFAULT_BAUD_RATE = 9600;
    
    private List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    private List<String> bluetoothDeviceNames = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Settings");
        
        initializeViews();
        setupSpinners();
        loadCurrentSettings();
        updateBluetoothDevices();
    }
    
    private void initializeViews() {
        targetPressureEdit = findViewById(R.id.target_pressure_edit);
        targetTemperatureEdit = findViewById(R.id.target_temperature_edit);
        pressureDeviationEdit = findViewById(R.id.pressure_deviation_edit);
        temperatureDeviationEdit = findViewById(R.id.temperature_deviation_edit);
        baudRateSpinner = findViewById(R.id.baud_rate_spinner);
        connectionTypeSpinner = findViewById(R.id.connection_type_spinner);
        bluetoothDeviceSpinner = findViewById(R.id.bluetooth_device_spinner);
        speechEnabledSwitch = findViewById(R.id.speech_enabled_switch);
        saveButton = findViewById(R.id.save_button);
        testSpeechButton = findViewById(R.id.test_speech_button);
        
        saveButton.setOnClickListener(v -> saveSettings());
        testSpeechButton.setOnClickListener(v -> testSpeech());
        
        // Update Bluetooth device list when connection type changes
        connectionTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                updateBluetoothDeviceSpinnerVisibility();
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }
    
    private void setupSpinners() {
        // Baud rate spinner
        ArrayAdapter<Integer> baudAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, BAUD_RATES);
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudRateSpinner.setAdapter(baudAdapter);
        
        // Connection type spinner
        ArrayAdapter<String> connectionAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, CONNECTION_TYPES);
        connectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        connectionTypeSpinner.setAdapter(connectionAdapter);
    }
    
    private void updateBluetoothDevices() {
        if (!checkBluetoothPermissions()) {
            return;
        }
        
        bluetoothDevices.clear();
        bluetoothDeviceNames.clear();
        
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            try {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (pairedDevices != null) {
                    for (BluetoothDevice device : pairedDevices) {
                        bluetoothDevices.add(device);
                        String deviceName = device.getName();
                        if (deviceName == null || deviceName.isEmpty()) {
                            deviceName = device.getAddress();
                        }
                        bluetoothDeviceNames.add(deviceName + " (" + device.getAddress() + ")");
                    }
                }
            } catch (SecurityException e) {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (bluetoothDeviceNames.isEmpty()) {
            bluetoothDeviceNames.add("No paired devices found");
        }
        
        // Update Bluetooth device spinner
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, bluetoothDeviceNames);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bluetoothDeviceSpinner.setAdapter(deviceAdapter);
        
        updateBluetoothDeviceSpinnerVisibility();
    }
    
    private void updateBluetoothDeviceSpinnerVisibility() {
        String selectedConnectionType = (String) connectionTypeSpinner.getSelectedItem();
        boolean isBluetoothSelected = SerialService.CONNECTION_BLUETOOTH.equals(selectedConnectionType);
        bluetoothDeviceSpinner.setVisibility(isBluetoothSelected ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.bluetooth_device_label).setVisibility(isBluetoothSelected ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    
    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_CONNECT permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 
                    REQUEST_BLUETOOTH_PERMISSION);
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateBluetoothDevices();
            } else {
                Toast.makeText(this, "Bluetooth permission required for device selection", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void loadCurrentSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load values
        int targetPressure = prefs.getInt("target_pressure", 32);
        int targetTemperature = prefs.getInt("target_temperature", 75);
        float pressureDeviation = prefs.getFloat("pressure_deviation", 15.0f);
        float temperatureDeviation = prefs.getFloat("temperature_deviation", 20.0f);
        int baudRate = prefs.getInt("baud_rate", DEFAULT_BAUD_RATE);
        String connectionType = prefs.getString("connection_type", SerialService.CONNECTION_USB);
        String bluetoothDeviceAddress = prefs.getString("bluetooth_device_address", "");
        boolean speechEnabled = prefs.getBoolean("speech_enabled", true);
        
        // Set UI values
        targetPressureEdit.setText(String.valueOf(targetPressure));
        targetTemperatureEdit.setText(String.valueOf(targetTemperature));
        pressureDeviationEdit.setText(String.valueOf(pressureDeviation));
        temperatureDeviationEdit.setText(String.valueOf(temperatureDeviation));
        speechEnabledSwitch.setChecked(speechEnabled);
        
        // Set baud rate spinner
        for (int i = 0; i < BAUD_RATES.length; i++) {
            if (BAUD_RATES[i] == baudRate) {
                baudRateSpinner.setSelection(i);
                break;
            }
        }
        
        // Set connection type spinner
        for (int i = 0; i < CONNECTION_TYPES.length; i++) {
            if (CONNECTION_TYPES[i].equals(connectionType)) {
                connectionTypeSpinner.setSelection(i);
                break;
            }
        }
        
        // Set Bluetooth device spinner
        if (!bluetoothDeviceAddress.isEmpty()) {
            for (int i = 0; i < bluetoothDevices.size(); i++) {
                if (bluetoothDevices.get(i).getAddress().equals(bluetoothDeviceAddress)) {
                    bluetoothDeviceSpinner.setSelection(i);
                    break;
                }
            }
        }
    }
    
    private void saveSettings() {
        try {
            // Parse values
            int targetPressure = Integer.parseInt(targetPressureEdit.getText().toString().trim());
            int targetTemperature = Integer.parseInt(targetTemperatureEdit.getText().toString().trim());
            float pressureDeviation = Float.parseFloat(pressureDeviationEdit.getText().toString().trim());
            float temperatureDeviation = Float.parseFloat(temperatureDeviationEdit.getText().toString().trim());
            int baudRate = (Integer) baudRateSpinner.getSelectedItem();
            String connectionType = (String) connectionTypeSpinner.getSelectedItem();
            boolean speechEnabled = speechEnabledSwitch.isChecked();
            
            // Validate ranges
            if (targetPressure < 10 || targetPressure > 100) {
                showError("Target pressure must be between 10 and 100 PSI");
                return;
            }
            
            if (targetTemperature < 0 || targetTemperature > 150) {
                showError("Target temperature must be between 0 and 150°F");
                return;
            }
            
            if (pressureDeviation < 1 || pressureDeviation > 50) {
                showError("Pressure deviation must be between 1% and 50%");
                return;
            }
            
            if (temperatureDeviation < 1 || temperatureDeviation > 50) {
                showError("Temperature deviation must be between 1% and 50%");
                return;
            }
            
            // Get Bluetooth device address if selected
            String bluetoothDeviceAddress = "";
            if (SerialService.CONNECTION_BLUETOOTH.equals(connectionType)) {
                int selectedDeviceIndex = bluetoothDeviceSpinner.getSelectedItemPosition();
                if (selectedDeviceIndex >= 0 && selectedDeviceIndex < bluetoothDevices.size()) {
                    bluetoothDeviceAddress = bluetoothDevices.get(selectedDeviceIndex).getAddress();
                } else {
                    showError("Please select a paired Bluetooth device");
                    return;
                }
            }
            
            // Check for changes
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int previousBaudRate = prefs.getInt("baud_rate", DEFAULT_BAUD_RATE);
            String previousConnectionType = prefs.getString("connection_type", SerialService.CONNECTION_USB);
            String previousBluetoothAddress = prefs.getString("bluetooth_device_address", "");
            
            boolean connectionChanged = (!previousConnectionType.equals(connectionType)) ||
                (previousBaudRate != baudRate) ||
                (!previousBluetoothAddress.equals(bluetoothDeviceAddress));
            
            // Save to preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("target_pressure", targetPressure);
            editor.putInt("target_temperature", targetTemperature);
            editor.putFloat("pressure_deviation", pressureDeviation);
            editor.putFloat("temperature_deviation", temperatureDeviation);
            editor.putInt("baud_rate", baudRate);
            editor.putString("connection_type", connectionType);
            editor.putString("bluetooth_device_address", bluetoothDeviceAddress);
            editor.putBoolean("speech_enabled", speechEnabled);
            editor.apply();
            
            // Apply to existing sensors
            applySettingsToSensors(targetPressure, targetTemperature, pressureDeviation, temperatureDeviation);
            
            String message = "Settings saved successfully";
            if (connectionChanged) {
                message += ". Connection will be restarted with new settings.";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            
            finish();
            
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for all fields");
        }
    }
    
    private void applySettingsToSensors(int targetPressure, int targetTemperature, 
                                      float pressureDeviation, float temperatureDeviation) {
        // Try to get MainActivity and update sensor settings
        try {
            MainActivity mainActivity = (MainActivity) getParent();
            if (mainActivity == null) {
                return;
            }
            
            // Update all assigned sensors with new settings
            var assignedSensors = mainActivity.getAssignedSensors();
            for (TPMSSensor sensor : assignedSensors.values()) {
                sensor.setTargetPressure(targetPressure);
                sensor.setTargetTemperature(targetTemperature);
                sensor.setPressureDeviationPercent(pressureDeviation);
                sensor.setTemperatureDeviationPercent(temperatureDeviation);
            }
            
        } catch (Exception e) {
            // Ignore errors - settings will be applied when sensors are next updated
        }
    }
    
    private void testSpeech() {
        MainActivity mainActivity = (MainActivity) getParent();
        if (mainActivity != null) {
            Toast.makeText(this, "Speech test not implemented yet", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot test speech - no connection to main activity", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
