package com.tpms.monitor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.tpms.monitor.MainActivity;
import com.tpms.monitor.R;
import com.tpms.monitor.model.TPMSSensor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Background service to handle serial communication with USB TPMS receiver
 * Based on the Python script protocol: configurable baud rate, 8-N-1
 */
public class SerialService extends Service implements SerialInputOutputManager.Listener {
    private static final String TAG = "SerialService";
    private static final String CHANNEL_ID = "TPMS_SERVICE";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "TPMSPrefs";
    
    // Serial configuration based on Python script
    private static final int DEFAULT_BAUD_RATE = 9600;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = UsbSerialPort.STOPBITS_1;
    private static final int PARITY = UsbSerialPort.PARITY_NONE;
    
    // TPMS packet constants
    private static final int PACKET_LENGTH = 8;
    private static final byte HEADER_BYTE_1 = 0x55;
    private static final byte HEADER_BYTE_2 = (byte) 0xAA;
    private static final byte LENGTH_BYTE = 0x08;
    
    private UsbSerialPort serialPort;
    private SerialInputOutputManager serialIOManager;
    private final ConcurrentHashMap<Integer, TPMSSensor> sensors = new ConcurrentHashMap<>();
    private ServiceCallback callback;
    private int currentBaudRate = DEFAULT_BAUD_RATE;
    
    // Packet buffer for incomplete packets
    private byte[] packetBuffer = new byte[PACKET_LENGTH * 4]; // Buffer for multiple packets
    private int bufferPosition = 0;
    
    public interface ServiceCallback {
        void onSensorDataUpdated(TPMSSensor sensor);
        void onSerialConnected();
        void onSerialDisconnected();
        void onSerialError(String error);
    }
    
    public class LocalBinder extends Binder {
        public SerialService getService() {
            return SerialService.this;
        }
    }
    
    private final IBinder binder = new LocalBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        loadBaudRateFromSettings();
        Log.d(TAG, "SerialService created with baud rate: " + currentBaudRate);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Try to connect to USB serial device
        connectToUSBDevice();
        
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        Log.d(TAG, "SerialService destroyed");
    }
    
    private void loadBaudRateFromSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentBaudRate = prefs.getInt("baud_rate", DEFAULT_BAUD_RATE);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TPMS Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors tire pressure and temperature sensors");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TPMS Monitor")
            .setContentText("Monitoring tire sensors at " + currentBaudRate + " baud")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void connectToUSBDevice() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found");
            if (callback != null) {
                callback.onSerialError("No USB serial devices found");
            }
            return;
        }
        
        // Use the first available device
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        
        if (!manager.hasPermission(device)) {
            Log.e(TAG, "No permission for USB device");
            if (callback != null) {
                callback.onSerialError("No permission for USB device");
            }
            return;
        }
        
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device");
            if (callback != null) {
                callback.onSerialError("Failed to open USB device");
            }
            return;
        }
        
        serialPort = driver.getPorts().get(0); // Use first port
        
        try {
            serialPort.open(connection);
            serialPort.setParameters(currentBaudRate, DATA_BITS, STOP_BITS, PARITY);
            
            // Create and start the I/O manager
            serialIOManager = new SerialInputOutputManager(serialPort, this);
            Executors.newSingleThreadExecutor().submit(serialIOManager);
            
            Log.i(TAG, "Connected to USB serial device: " + device.getDeviceName() + " at " + currentBaudRate + " baud");
            if (callback != null) {
                callback.onSerialConnected();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to configure serial port", e);
            if (callback != null) {
                callback.onSerialError("Failed to configure serial port: " + e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        if (serialIOManager != null) {
            serialIOManager.stop();
            serialIOManager = null;
        }
        
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            serialPort = null;
        }
        
        if (callback != null) {
            callback.onSerialDisconnected();
        }
        
        Log.i(TAG, "Serial connection closed");
    }
    
    @Override
    public void onNewData(byte[] data) {
        // Add new data to buffer
        for (byte b : data) {
            packetBuffer[bufferPosition++] = b;
            
            // Prevent buffer overflow
            if (bufferPosition >= packetBuffer.length) {
                bufferPosition = 0;
                Log.w(TAG, "Packet buffer overflow, resetting");
            }
        }
        
        // Look for complete packets in buffer
        processPacketBuffer();
    }
    
    private void processPacketBuffer() {
        int searchStart = 0;
        
        while (searchStart <= bufferPosition - PACKET_LENGTH) {
            // Look for packet header
            int headerIndex = findPacketHeader(searchStart);
            
            if (headerIndex == -1 || headerIndex + PACKET_LENGTH > bufferPosition) {
                // No complete packet found
                break;
            }
            
            // Extract packet
            byte[] packet = new byte[PACKET_LENGTH];
            System.arraycopy(packetBuffer, headerIndex, packet, 0, PACKET_LENGTH);
            
            // Process the packet
            processTPMSPacket(packet);
            
            // Continue searching after this packet
            searchStart = headerIndex + PACKET_LENGTH;
        }
        
        // Shift remaining data to beginning of buffer
        if (searchStart > 0 && searchStart < bufferPosition) {
            int remainingBytes = bufferPosition - searchStart;
            System.arraycopy(packetBuffer, searchStart, packetBuffer, 0, remainingBytes);
            bufferPosition = remainingBytes;
        }
    }
    
    private int findPacketHeader(int startIndex) {
        for (int i = startIndex; i <= bufferPosition - 3; i++) {
            if (packetBuffer[i] == HEADER_BYTE_1 && 
                packetBuffer[i + 1] == HEADER_BYTE_2 && 
                packetBuffer[i + 2] == LENGTH_BYTE) {
                return i;
            }
        }
        return -1;
    }
    
    private void processTPMSPacket(byte[] packet) {
        Log.d(TAG, String.format("Processing TPMS packet: %02X %02X %02X %02X %02X %02X %02X %02X",
            packet[0] & 0xFF, packet[1] & 0xFF, packet[2] & 0xFF, packet[3] & 0xFF,
            packet[4] & 0xFF, packet[5] & 0xFF, packet[6] & 0xFF, packet[7] & 0xFF));
        
        // Extract sensor ID
        int sensorId = packet[3] & 0xFF;
        
        // Get or create sensor
        TPMSSensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            sensor = new TPMSSensor(sensorId);
            sensors.put(sensorId, sensor);
            Log.i(TAG, "New TPMS sensor discovered: ID " + sensorId);
        }
        
        // Update sensor with packet data
        sensor.updateFromPacket(packet);
        
        // Notify callback
        if (callback != null && sensor.isChecksumValid()) {
            callback.onSensorDataUpdated(sensor);
        }
    }
    
    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "Serial I/O error", e);
        if (callback != null) {
            callback.onSerialError("Serial I/O error: " + e.getMessage());
        }
        
        // Try to reconnect after a delay
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            Log.i(TAG, "Attempting to reconnect...");
            connectToUSBDevice();
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    // Public methods for MainActivity
    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }
    
    public Map<Integer, TPMSSensor> getAllSensors() {
        return new HashMap<>(sensors);
    }
    
    public TPMSSensor getSensor(int sensorId) {
        return sensors.get(sensorId);
    }
    
    public boolean isConnected() {
        return serialPort != null && serialPort.isOpen();
    }
    
    public void reconnect() {
        disconnect();
        loadBaudRateFromSettings(); // Reload baud rate in case it changed
        connectToUSBDevice();
    }
    
    public int getCurrentBaudRate() {
        return currentBaudRate;
    }
}
