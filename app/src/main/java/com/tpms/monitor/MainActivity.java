package com.tpms.monitor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.tpms.monitor.model.TPMSSensor;
import com.tpms.monitor.service.SerialService;
import com.tpms.monitor.ui.TireDisplayView;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main activity displaying vehicle and trailer with tire pressure/temperature monitoring
 */
public class MainActivity extends AppCompatActivity implements SerialService.ServiceCallback {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "TPMSPrefs";
    
    // UI Components
    private TextView statusText;
    private TireDisplayView[] tireViews = new TireDisplayView[8];
    
    // Service
    private SerialService serialService;
    private boolean serviceBound = false;
    
    // Sensors mapped to tire positions
    private final ConcurrentHashMap<TPMSSensor.TirePosition, TPMSSensor> assignedSensors = new ConcurrentHashMap<>();
    
    // Alarm system
    private ToneGenerator alarmTone;
    private TextToSpeech textToSpeech;
    private boolean speechEnabled = true;
    private Handler mainHandler;
    
    // UI update handling
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        initializeViews();
        initializeAlarmSystem();
        loadPreferences();
        
        // Start and bind to serial service
        startSerialService();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        
        // Initialize tire display views for all 8 positions
        tireViews[0] = findViewById(R.id.tire_vehicle_front_left);
        tireViews[1] = findViewById(R.id.tire_vehicle_front_right);
        tireViews[2] = findViewById(R.id.tire_vehicle_rear_left);
        tireViews[3] = findViewById(R.id.tire_vehicle_rear_right);
        tireViews[4] = findViewById(R.id.tire_trailer_front_left);
        tireViews[5] = findViewById(R.id.tire_trailer_front_right);
        tireViews[6] = findViewById(R.id.tire_trailer_rear_left);
        tireViews[7] = findViewById(R.id.tire_trailer_rear_right);
        
        // Set tire positions
        TPMSSensor.TirePosition[] positions = {
            TPMSSensor.TirePosition.VEHICLE_FRONT_LEFT,
            TPMSSensor.TirePosition.VEHICLE_FRONT_RIGHT,
            TPMSSensor.TirePosition.VEHICLE_REAR_LEFT,
            TPMSSensor.TirePosition.VEHICLE_REAR_RIGHT,
            TPMSSensor.TirePosition.TRAILER_FRONT_LEFT,
            TPMSSensor.TirePosition.TRAILER_FRONT_RIGHT,
            TPMSSensor.TirePosition.TRAILER_REAR_LEFT,
            TPMSSensor.TirePosition.TRAILER_REAR_RIGHT
        };
        
        for (int i = 0; i < tireViews.length; i++) {
            if (tireViews[i] != null) {
                tireViews[i].setTirePosition(positions[i]);
                final int index = i;
                tireViews[i].setOnClickListener(v -> openSensorAssignment(positions[index]));
            }
        }
    }
    
    private void initializeAlarmSystem() {
        try {
            alarmTone = new ToneGenerator(AudioManager.STREAM_ALARM, 80);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to create alarm tone generator", e);
        }
        
        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                Log.i(TAG, "Text-to-Speech initialized successfully");
            } else {
                Log.e(TAG, "Text-to-Speech initialization failed");
            }
        });
    }
    
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        speechEnabled = prefs.getBoolean("speech_enabled", true);
        
        // Load sensor assignments
        for (TPMSSensor.TirePosition position : TPMSSensor.TirePosition.values()) {
            if (position != TPMSSensor.TirePosition.UNASSIGNED) {
                int sensorId = prefs.getInt("sensor_" + position.name(), -1);
                if (sensorId != -1) {
                    // Will be populated when sensor data is received
                }
            }
        }
    }
    
    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("speech_enabled", speechEnabled);
        
        // Save sensor assignments
        for (Map.Entry<TPMSSensor.TirePosition, TPMSSensor> entry : assignedSensors.entrySet()) {
            editor.putInt("sensor_" + entry.getKey().name(), entry.getValue().getSensorId());
        }
        
        editor.apply();
    }
    
    private void startSerialService() {
        Intent serviceIntent = new Intent(this, SerialService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SerialService.LocalBinder binder = (SerialService.LocalBinder) service;
            serialService = binder.getService();
            serialService.setCallback(MainActivity.this);
            serviceBound = true;
            
            Log.i(TAG, "Serial service connected");
            updateStatusText("Serial service connected");
            
            // Load existing sensors and assignments
            loadExistingSensors();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serialService = null;
            serviceBound = false;
            Log.i(TAG, "Serial service disconnected");
            updateStatusText("Serial service disconnected");
        }
    };
    
    private void loadExistingSensors() {
        if (serialService != null) {
            Map<Integer, TPMSSensor> sensors = serialService.getAllSensors();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            
            // Restore sensor assignments
            for (TPMSSensor.TirePosition position : TPMSSensor.TirePosition.values()) {
                if (position != TPMSSensor.TirePosition.UNASSIGNED) {
                    int sensorId = prefs.getInt("sensor_" + position.name(), -1);
                    TPMSSensor sensor = sensors.get(sensorId);
                    if (sensor != null) {
                        sensor.setAssignedPosition(position);
                        assignedSensors.put(position, sensor);
                        updateTireDisplay(sensor);
                    }
                }
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_assign_sensors) {
            openSensorAssignment(null);
            return true;
        } else if (id == R.id.action_reconnect) {
            reconnectSerial();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    private void openSensorAssignment(TPMSSensor.TirePosition position) {
        Intent intent = new Intent(this, SensorAssignmentActivity.class);
        if (position != null) {
            intent.putExtra("selected_position", position.name());
        }
        startActivity(intent);
    }
    
    private void reconnectSerial() {
        if (serialService != null) {
            serialService.reconnect();
            updateStatusText("Reconnecting...");
        }
    }
    
    // SerialService.ServiceCallback implementation
    @Override
    public void onSensorDataUpdated(TPMSSensor sensor) {
        uiHandler.post(() -> {
            // Check if sensor is assigned to a position
            TPMSSensor.TirePosition position = sensor.getAssignedPosition();
            if (position != TPMSSensor.TirePosition.UNASSIGNED) {
                assignedSensors.put(position, sensor);
                updateTireDisplay(sensor);
                
                // Check for alarm conditions
                if (sensor.isAlarmCondition()) {
                    triggerAlarm(sensor);
                }
            }
            
            // Update status
            updateStatusText(String.format("Last update: Sensor %d - %d PSI, %d°F", 
                sensor.getSensorId(), sensor.getPressurePsi(), sensor.getTemperatureF()));
        });
    }
    
    @Override
    public void onSerialConnected() {
        uiHandler.post(() -> updateStatusText("USB Serial Connected"));
    }
    
    @Override
    public void onSerialDisconnected() {
        uiHandler.post(() -> updateStatusText("USB Serial Disconnected"));
    }
    
    @Override
    public void onSerialError(String error) {
        uiHandler.post(() -> {
            updateStatusText("Serial Error: " + error);
            Toast.makeText(this, "Serial Error: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    private void updateStatusText(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }
    
    private void updateTireDisplay(TPMSSensor sensor) {
        TPMSSensor.TirePosition position = sensor.getAssignedPosition();
        if (position == TPMSSensor.TirePosition.UNASSIGNED) {
            return;
        }
        
        int index = position.getIndex();
        if (index >= 0 && index < tireViews.length && tireViews[index] != null) {
            tireViews[index].updateSensorData(sensor);
        }
    }
    
    private void triggerAlarm(TPMSSensor sensor) {
        // Play alarm sound
        if (alarmTone != null) {
            try {
                alarmTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000);
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to play alarm tone", e);
            }
        }
        
        // Speak alarm message
        if (speechEnabled && textToSpeech != null) {
            String message = sensor.getAnnouncementText();
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
        
        // Flash tire display
        TPMSSensor.TirePosition position = sensor.getAssignedPosition();
        int index = position.getIndex();
        if (index >= 0 && index < tireViews.length && tireViews[index] != null) {
            tireViews[index].startFlashing();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
        
        // Refresh tire displays
        for (Map.Entry<TPMSSensor.TirePosition, TPMSSensor> entry : assignedSensors.entrySet()) {
            updateTireDisplay(entry.getValue());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        
        if (alarmTone != null) {
            alarmTone.release();
        }
        
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
    
    // Getters for other activities
    public SerialService getSerialService() {
        return serialService;
    }
    
    public Map<TPMSSensor.TirePosition, TPMSSensor> getAssignedSensors() {
        return assignedSensors;
    }
    
    public void assignSensorToPosition(int sensorId, TPMSSensor.TirePosition position) {
        if (serialService != null) {
            TPMSSensor sensor = serialService.getSensor(sensorId);
            if (sensor != null) {
                // Remove previous assignment
                assignedSensors.entrySet().removeIf(entry -> entry.getValue().getSensorId() == sensorId);
                
                // Set new assignment
                sensor.setAssignedPosition(position);
                assignedSensors.put(position, sensor);
                
                // Update display
                updateTireDisplay(sensor);
                
                // Save preferences
                savePreferences();
                
                Toast.makeText(this, String.format("Sensor %d assigned to %s", 
                    sensorId, position.getDisplayName()), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
