package com.tpms.monitor.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth Serial Profile (SPP) connection handler for TPMS data
 * Implements the same interface as USB serial for seamless integration
 */
public class BluetoothSerialConnection {
    private static final String TAG = "BluetoothSerial";
    
    // Standard SPP UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private boolean isConnected = false;
    private DataListener dataListener;
    private Handler mainHandler;
    
    public interface DataListener {
        void onDataReceived(byte[] data);
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }
    
    public BluetoothSerialConnection() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }
    
    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    public Set<BluetoothDevice> getPairedDevices() {
        if (!isBluetoothAvailable()) {
            return null;
        }
        try {
            return bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting paired devices", e);
            return null;
        }
    }
    
    public void connect(BluetoothDevice device) {
        if (isConnected) {
            disconnect();
        }
        
        bluetoothDevice = device;
        
        // Run connection in background thread
        new Thread(() -> {
            try {
                // Create socket
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                
                // Cancel discovery to improve connection performance
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                
                // Connect to device
                bluetoothSocket.connect();
                
                // Get input/output streams
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
                
                isConnected = true;
                
                // Start read thread
                startReadThread();
                
                // Notify connected on main thread
                mainHandler.post(() -> {
                    if (dataListener != null) {
                        dataListener.onConnected();
                    }
                });
                
                Log.i(TAG, "Connected to Bluetooth device: " + device.getName());
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to Bluetooth device", e);
                isConnected = false;
                
                mainHandler.post(() -> {
                    if (dataListener != null) {
                        dataListener.onError("Failed to connect: " + e.getMessage());
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception during Bluetooth connection", e);
                isConnected = false;
                
                mainHandler.post(() -> {
                    if (dataListener != null) {
                        dataListener.onError("Permission denied for Bluetooth connection");
                    }
                });
            }
        }).start();
    }
    
    public void connect(String deviceAddress) {
        if (!isBluetoothAvailable()) {
            if (dataListener != null) {
                dataListener.onError("Bluetooth not available");
            }
            return;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connect(device);
        } catch (IllegalArgumentException e) {
            if (dataListener != null) {
                dataListener.onError("Invalid Bluetooth address: " + deviceAddress);
            }
        } catch (SecurityException e) {
            if (dataListener != null) {
                dataListener.onError("Permission denied for Bluetooth device access");
            }
        }
    }
    
    private void startReadThread() {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        
                        // Notify data received on main thread
                        mainHandler.post(() -> {
                            if (dataListener != null) {
                                dataListener.onDataReceived(data);
                            }
                        });
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        Log.e(TAG, "Error reading from Bluetooth", e);
                        
                        mainHandler.post(() -> {
                            if (dataListener != null) {
                                dataListener.onError("Read error: " + e.getMessage());
                            }
                        });
                        break;
                    }
                }
            }
            
            Log.d(TAG, "Read thread terminated");
        });
        
        readThread.start();
    }
    
    public void write(byte[] data) {
        if (!isConnected || outputStream == null) {
            return;
        }
        
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to Bluetooth", e);
            if (dataListener != null) {
                dataListener.onError("Write error: " + e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        isConnected = false;
        
        // Interrupt read thread
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        
        // Close streams
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream", e);
            }
            inputStream = null;
        }
        
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing output stream", e);
            }
            outputStream = null;
        }
        
        // Close socket
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket", e);
            }
            bluetoothSocket = null;
        }
        
        bluetoothDevice = null;
        
        // Notify disconnected on main thread
        mainHandler.post(() -> {
            if (dataListener != null) {
                dataListener.onDisconnected();
            }
        });
        
        Log.i(TAG, "Bluetooth connection closed");
    }
    
    public boolean isConnected() {
        return isConnected && bluetoothSocket != null && bluetoothSocket.isConnected();
    }
    
    public String getDeviceName() {
        if (bluetoothDevice != null) {
            try {
                return bluetoothDevice.getName();
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception getting device name", e);
                return bluetoothDevice.getAddress();
            }
        }
        return "Unknown";
    }
    
    public String getDeviceAddress() {
        return bluetoothDevice != null ? bluetoothDevice.getAddress() : "Unknown";
    }
}
