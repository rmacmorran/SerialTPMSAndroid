package com.tpms.monitor;

import android.content.SharedPreferences;
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

import com.tpms.monitor.model.TPMSSensor;

/**
 * Settings activity for configuring TPMS parameters
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "TPMSPrefs";
    
    // UI Components
    private EditText targetPressureEdit;
    private EditText targetTemperatureEdit;
    private EditText pressureDeviationEdit;
    private EditText temperatureDeviationEdit;
    private Spinner baudRateSpinner;
    private Switch speechEnabledSwitch;
    private Button saveButton;
    private Button testSpeechButton;
    
    // Baud rate options
    private static final Integer[] BAUD_RATES = {9600, 19200, 38400, 115200};
    private static final int DEFAULT_BAUD_RATE = 9600;
    
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
        setupBaudRateSpinner();
        loadCurrentSettings();
    }
    
    private void initializeViews() {
        targetPressureEdit = findViewById(R.id.target_pressure_edit);
        targetTemperatureEdit = findViewById(R.id.target_temperature_edit);
        pressureDeviationEdit = findViewById(R.id.pressure_deviation_edit);
        temperatureDeviationEdit = findViewById(R.id.temperature_deviation_edit);
        baudRateSpinner = findViewById(R.id.baud_rate_spinner);
        speechEnabledSwitch = findViewById(R.id.speech_enabled_switch);
        saveButton = findViewById(R.id.save_button);
        testSpeechButton = findViewById(R.id.test_speech_button);
        
        saveButton.setOnClickListener(v -> saveSettings());
        testSpeechButton.setOnClickListener(v -> testSpeech());
    }
    
    private void setupBaudRateSpinner() {
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, BAUD_RATES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudRateSpinner.setAdapter(adapter);
    }
    
    private void loadCurrentSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load default values
        int targetPressure = prefs.getInt("target_pressure", 32);
        int targetTemperature = prefs.getInt("target_temperature", 75);
        float pressureDeviation = prefs.getFloat("pressure_deviation", 15.0f);
        float temperatureDeviation = prefs.getFloat("temperature_deviation", 20.0f);
        int baudRate = prefs.getInt("baud_rate", DEFAULT_BAUD_RATE);
        boolean speechEnabled = prefs.getBoolean("speech_enabled", true);
        
        // Set UI values
        targetPressureEdit.setText(String.valueOf(targetPressure));
        targetTemperatureEdit.setText(String.valueOf(targetTemperature));
        pressureDeviationEdit.setText(String.valueOf(pressureDeviation));
        temperatureDeviationEdit.setText(String.valueOf(temperatureDeviation));
        speechEnabledSwitch.setChecked(speechEnabled);
        
        // Set baud rate spinner selection
        for (int i = 0; i < BAUD_RATES.length; i++) {
            if (BAUD_RATES[i] == baudRate) {
                baudRateSpinner.setSelection(i);
                break;
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
            
            // Get current baud rate to check if it changed
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int previousBaudRate = prefs.getInt("baud_rate", DEFAULT_BAUD_RATE);
            boolean baudRateChanged = (previousBaudRate != baudRate);
            
            // Save to preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("target_pressure", targetPressure);
            editor.putInt("target_temperature", targetTemperature);
            editor.putFloat("pressure_deviation", pressureDeviation);
            editor.putFloat("temperature_deviation", temperatureDeviation);
            editor.putInt("baud_rate", baudRate);
            editor.putBoolean("speech_enabled", speechEnabled);
            editor.apply();
            
            // Apply to existing sensors (if MainActivity is available)
            applySettingsToSensors(targetPressure, targetTemperature, pressureDeviation, temperatureDeviation);
            
            String message = "Settings saved successfully";
            if (baudRateChanged) {
                message += ". Serial connection will be restarted with new baud rate.";
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
                // Alternative approach if getParent() doesn't work
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
        // Create a test announcement
        MainActivity mainActivity = (MainActivity) getParent();
        if (mainActivity != null) {
            // Test speech functionality
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
