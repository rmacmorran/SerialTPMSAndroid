package com.tpms.monitor;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.tpms.monitor.model.TPMSSensor;
import com.tpms.monitor.service.SerialService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity for assigning TPMS sensors to tire positions
 */
public class SensorAssignmentActivity extends AppCompatActivity {
    private static final String TAG = "SensorAssignmentActivity";
    
    private ListView sensorListView;
    private Spinner positionSpinner;
    private Button assignButton;
    private TextView instructionText;
    
    private SerialService serialService;
    private List<TPMSSensor> availableSensors = new ArrayList<>();
    private SensorListAdapter sensorAdapter;
    private TPMSSensor.TirePosition selectedPosition;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_assignment);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Assign Sensors");
        
        initializeViews();
        setupPositionSpinner();
        
        // Check if a specific position was selected
        String positionName = getIntent().getStringExtra("selected_position");
        if (positionName != null) {
            try {
                selectedPosition = TPMSSensor.TirePosition.valueOf(positionName);
                setSelectedPosition(selectedPosition);
            } catch (IllegalArgumentException e) {
                // Invalid position name, ignore
            }
        }
        
        // Get serial service from MainActivity
        MainActivity mainActivity = (MainActivity) getParent();
        if (mainActivity != null) {
            serialService = mainActivity.getSerialService();
        }
        
        refreshSensorList();
    }
    
    private void initializeViews() {
        sensorListView = findViewById(R.id.sensor_list);
        positionSpinner = findViewById(R.id.position_spinner);
        assignButton = findViewById(R.id.assign_button);
        instructionText = findViewById(R.id.instruction_text);
        
        assignButton.setOnClickListener(this::onAssignButtonClicked);
        
        // Set up sensor list
        sensorAdapter = new SensorListAdapter(this, availableSensors);
        sensorListView.setAdapter(sensorAdapter);
        sensorListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }
    
    private void setupPositionSpinner() {
        List<String> positionNames = new ArrayList<>();
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
        
        for (TPMSSensor.TirePosition position : positions) {
            positionNames.add(position.getDisplayName());
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, positionNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionSpinner.setAdapter(adapter);
    }
    
    private void setSelectedPosition(TPMSSensor.TirePosition position) {
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
        
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == position) {
                positionSpinner.setSelection(i);
                break;
            }
        }
    }
    
    private void refreshSensorList() {
        availableSensors.clear();
        
        if (serialService != null) {
            Map<Integer, TPMSSensor> allSensors = serialService.getAllSensors();
            
            for (TPMSSensor sensor : allSensors.values()) {
                // Only show sensors that have recent data
                if (sensor.hasRecentSignal(30000)) { // 30 seconds timeout
                    availableSensors.add(sensor);
                }
            }
        }
        
        sensorAdapter.notifyDataSetChanged();
        
        if (availableSensors.isEmpty()) {
            instructionText.setText("No active sensors detected. Make sure your TPMS receiver is connected and sensors are transmitting.");
        } else {
            instructionText.setText(String.format("Found %d active sensors. Select a sensor and tire position, then tap Assign.", 
                availableSensors.size()));
        }
    }
    
    private void onAssignButtonClicked(View view) {
        // Get selected sensor
        int selectedSensorIndex = sensorListView.getCheckedItemPosition();
        if (selectedSensorIndex == ListView.INVALID_POSITION) {
            Toast.makeText(this, "Please select a sensor", Toast.LENGTH_SHORT).show();
            return;
        }
        
        TPMSSensor selectedSensor = availableSensors.get(selectedSensorIndex);
        
        // Get selected position
        int selectedPositionIndex = positionSpinner.getSelectedItemPosition();
        if (selectedPositionIndex < 0) {
            Toast.makeText(this, "Please select a tire position", Toast.LENGTH_SHORT).show();
            return;
        }
        
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
        
        TPMSSensor.TirePosition selectedPosition = positions[selectedPositionIndex];
        
        // Assign sensor to position
        MainActivity mainActivity = (MainActivity) getParent();
        if (mainActivity == null) {
            // Try alternative method for getting MainActivity
            try {
                mainActivity = (MainActivity) this;
            } catch (ClassCastException e) {
                Toast.makeText(this, "Unable to assign sensor - connection error", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        if (mainActivity != null) {
            mainActivity.assignSensorToPosition(selectedSensor.getSensorId(), selectedPosition);
            
            Toast.makeText(this, String.format("Sensor %d assigned to %s", 
                selectedSensor.getSensorId(), selectedPosition.getDisplayName()), 
                Toast.LENGTH_LONG).show();
            
            // Close this activity
            finish();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        refreshSensorList();
    }
    
    // Custom adapter for sensor list
    private static class SensorListAdapter extends ArrayAdapter<TPMSSensor> {
        
        public SensorListAdapter(android.content.Context context, List<TPMSSensor> sensors) {
            super(context, android.R.layout.simple_list_item_2, sensors);
        }
        
        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            
            TPMSSensor sensor = getItem(position);
            if (sensor != null) {
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                
                text1.setText(String.format("Sensor ID: %d", sensor.getSensorId()));
                text2.setText(String.format("%d PSI, %d°F - %s", 
                    sensor.getPressurePsi(), sensor.getTemperatureF(), sensor.getStatusDescription()));
                
                // Show current assignment if any
                if (sensor.getAssignedPosition() != TPMSSensor.TirePosition.UNASSIGNED) {
                    text2.append(String.format(" (Currently: %s)", 
                        sensor.getAssignedPosition().getShortName()));
                }
            }
            
            return view;
        }
    }
}
