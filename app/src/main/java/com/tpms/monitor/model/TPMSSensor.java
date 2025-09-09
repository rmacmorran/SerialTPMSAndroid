package com.tpms.monitor.model;

import java.util.Date;

/**
 * TPMS Sensor data model based on the decoded protocol:
 * - Byte 0-1: Header (0x55 0xAA)
 * - Byte 2: Length (0x08)
 * - Byte 3: Sensor ID
 * - Byte 4: Pressure in PSI
 * - Byte 5: Temperature in °F
 * - Byte 6: Reserved/unused
 * - Byte 7: XOR checksum
 */
public class TPMSSensor {
    
    public enum TirePosition {
        VEHICLE_FRONT_LEFT("Vehicle Front Left", "VFL", 0),
        VEHICLE_FRONT_RIGHT("Vehicle Front Right", "VFR", 1),
        VEHICLE_REAR_LEFT("Vehicle Rear Left", "VRL", 2),
        VEHICLE_REAR_RIGHT("Vehicle Rear Right", "VRR", 3),
        TRAILER_FRONT_LEFT("Trailer Front Left", "TFL", 4),
        TRAILER_FRONT_RIGHT("Trailer Front Right", "TFR", 5),
        TRAILER_REAR_LEFT("Trailer Rear Left", "TRL", 6),
        TRAILER_REAR_RIGHT("Trailer Rear Right", "TRR", 7),
        UNASSIGNED("Unassigned", "---", -1);
        
        private final String displayName;
        private final String shortName;
        private final int index;
        
        TirePosition(String displayName, String shortName, int index) {
            this.displayName = displayName;
            this.shortName = shortName;
            this.index = index;
        }
        
        public String getDisplayName() { return displayName; }
        public String getShortName() { return shortName; }
        public int getIndex() { return index; }
    }
    
    public enum SensorStatus {
        DORMANT,
        NORMAL,
        LOW_PRESSURE,
        HIGH_PRESSURE,
        VERY_HIGH_PRESSURE,
        COLD_TEMPERATURE,
        HOT_TEMPERATURE,
        OVERHEATING,
        NO_SIGNAL
    }
    
    private int sensorId;
    private int pressurePsi;
    private int temperatureF;
    private Date lastUpdate;
    private boolean checksumValid;
    private TirePosition assignedPosition;
    private SensorStatus status;
    private long lastSignalTime;
    
    // Configuration values
    private int targetPressure = 32; // Default target pressure in PSI
    private int targetTemperature = 75; // Default target temperature in °F
    private float pressureDeviationPercent = 15.0f; // Default ±15% deviation
    private float temperatureDeviationPercent = 20.0f; // Default ±20% deviation
    
    public TPMSSensor(int sensorId) {
        this.sensorId = sensorId;
        this.assignedPosition = TirePosition.UNASSIGNED;
        this.status = SensorStatus.NO_SIGNAL;
        this.lastSignalTime = 0;
    }
    
    /**
     * Update sensor data from TPMS packet
     * Protocol: [0x55, 0xAA, 0x08, sensorId, pressure, temperature, 0x00, checksum]
     */
    public void updateFromPacket(byte[] packet) {
        if (packet == null || packet.length != 8) {
            return;
        }
        
        // Verify header
        if ((packet[0] & 0xFF) != 0x55 || (packet[1] & 0xFF) != 0xAA || (packet[2] & 0xFF) != 0x08) {
            return;
        }
        
        // Verify checksum (XOR of first 7 bytes)
        int calculatedChecksum = 0;
        for (int i = 0; i < 7; i++) {
            calculatedChecksum ^= (packet[i] & 0xFF);
        }
        this.checksumValid = (calculatedChecksum == (packet[7] & 0xFF));
        
        if (!checksumValid) {
            return; // Skip invalid packets
        }
        
        // Extract data
        this.sensorId = packet[3] & 0xFF;
        this.pressurePsi = packet[4] & 0xFF;
        this.temperatureF = packet[5] & 0xFF;
        this.lastUpdate = new Date();
        this.lastSignalTime = System.currentTimeMillis();
        
        // Update status based on readings
        updateStatus();
    }
    
    private void updateStatus() {
        // Check for dormant sensor
        if (pressurePsi == 0) {
            status = SensorStatus.DORMANT;
            return;
        }
        
        // Calculate pressure thresholds
        float pressureMin = targetPressure * (1 - pressureDeviationPercent / 100);
        float pressureMax = targetPressure * (1 + pressureDeviationPercent / 100);
        
        // Calculate temperature thresholds
        float tempMin = targetTemperature * (1 - temperatureDeviationPercent / 100);
        float tempMax = targetTemperature * (1 + temperatureDeviationPercent / 100);
        
        // Determine status
        if (pressurePsi < 15) {
            status = SensorStatus.LOW_PRESSURE;
        } else if (pressurePsi > 50) {
            status = SensorStatus.VERY_HIGH_PRESSURE;
        } else if (pressurePsi < pressureMin || pressurePsi > pressureMax) {
            status = pressurePsi < pressureMin ? SensorStatus.LOW_PRESSURE : SensorStatus.HIGH_PRESSURE;
        } else if (temperatureF < 32) {
            status = SensorStatus.COLD_TEMPERATURE;
        } else if (temperatureF > 120) {
            status = SensorStatus.OVERHEATING;
        } else if (temperatureF < tempMin || temperatureF > tempMax) {
            status = temperatureF > tempMax ? SensorStatus.HOT_TEMPERATURE : SensorStatus.COLD_TEMPERATURE;
        } else {
            status = SensorStatus.NORMAL;
        }
    }
    
    public boolean isAlarmCondition() {
        return status == SensorStatus.LOW_PRESSURE || 
               status == SensorStatus.VERY_HIGH_PRESSURE || 
               status == SensorStatus.OVERHEATING;
    }
    
    public boolean hasRecentSignal(long timeoutMs) {
        return (System.currentTimeMillis() - lastSignalTime) < timeoutMs;
    }
    
    public String getStatusDescription() {
        switch (status) {
            case DORMANT: return "Dormant (no pressure)";
            case NORMAL: return "Normal";
            case LOW_PRESSURE: return "Low pressure";
            case HIGH_PRESSURE: return "High pressure";
            case VERY_HIGH_PRESSURE: return "Very high pressure!";
            case COLD_TEMPERATURE: return "Cold";
            case HOT_TEMPERATURE: return "Hot";
            case OVERHEATING: return "Overheating!";
            case NO_SIGNAL: return "No signal";
            default: return "Unknown";
        }
    }
    
    public String getAnnouncementText() {
        if (assignedPosition == TirePosition.UNASSIGNED) {
            return String.format("Sensor %d pressure %d PSI temperature %d degrees", 
                sensorId, pressurePsi, temperatureF);
        } else {
            return String.format("%s pressure %d PSI temperature %d degrees", 
                assignedPosition.getDisplayName(), pressurePsi, temperatureF);
        }
    }
    
    // Getters and Setters
    public int getSensorId() { return sensorId; }
    public int getPressurePsi() { return pressurePsi; }
    public int getTemperatureF() { return temperatureF; }
    public Date getLastUpdate() { return lastUpdate; }
    public boolean isChecksumValid() { return checksumValid; }
    public TirePosition getAssignedPosition() { return assignedPosition; }
    public void setAssignedPosition(TirePosition position) { this.assignedPosition = position; }
    public SensorStatus getStatus() { return status; }
    
    public int getTargetPressure() { return targetPressure; }
    public void setTargetPressure(int targetPressure) { 
        this.targetPressure = targetPressure;
        updateStatus();
    }
    
    public int getTargetTemperature() { return targetTemperature; }
    public void setTargetTemperature(int targetTemperature) { 
        this.targetTemperature = targetTemperature;
        updateStatus();
    }
    
    public float getPressureDeviationPercent() { return pressureDeviationPercent; }
    public void setPressureDeviationPercent(float deviationPercent) { 
        this.pressureDeviationPercent = deviationPercent;
        updateStatus();
    }
    
    public float getTemperatureDeviationPercent() { return temperatureDeviationPercent; }
    public void setTemperatureDeviationPercent(float deviationPercent) { 
        this.temperatureDeviationPercent = deviationPercent;
        updateStatus();
    }
    
    public float getPressureMinThreshold() {
        return targetPressure * (1 - pressureDeviationPercent / 100);
    }
    
    public float getPressureMaxThreshold() {
        return targetPressure * (1 + pressureDeviationPercent / 100);
    }
    
    public float getTemperatureMinThreshold() {
        return targetTemperature * (1 - temperatureDeviationPercent / 100);
    }
    
    public float getTemperatureMaxThreshold() {
        return targetTemperature * (1 + temperatureDeviationPercent / 100);
    }
}
