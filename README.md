# TPMS Monitor Android App

An Android application for monitoring Tire Pressure Monitoring System (TPMS) sensors via USB serial communication.

THIS IS A PROOF OF CONCEPT. It WORKS, but there may be incorrect assumptions in the code and documentation. One thing I still don't understand about these USB serial receivers is why they continue sending packets for sensors that are completely offline (battery removed). It's not just annoying but DANGEROUS to keep reporting the same temperature and pressure that was last reported by a sensor as though that's still the case. Your tire could be flat and on fire and you would think everything was fine! I haven't figured out how to identify this situation. Maybe the USB serial receiver can be queried over serial to get more details. Or maybe I'm missing some indicator in the data that would let me know a sensor is offline.  The only workaround I can think of for this is to throw the USB serial receiver in the trash, and replace it with a CC1101 module and implement custom decoding. (like RTL_433 does or the flipper zero code in my other repo -- also borrowed from the rtl_433 implementation)

## Features

- **Real-time TPMS monitoring** via USB serial connection
- **8-tire support** (4 vehicle + 4 trailer positions)
- **Visual tire display** with pressure/temperature bars
- **Configurable alarms** with audio and text-to-speech alerts
- **Sensor assignment** to specific tire positions
- **Configurable baud rates** (9600, 19200, 38400, 115200)
- **Deviation thresholds** for pressure and temperature monitoring

## TPMS Protocol

Based on analysis of TPMS receiver data, the app supports:
- **8-byte packet format**: `55 AA 08 [ID] [Pressure] [Temperature] [Reserved] [Checksum]`
- **Header**: `0x55 0xAA` with length `0x08`
- **Pressure**: PSI value in byte 4
- **Temperature**: Fahrenheit value in byte 5
- **XOR checksum** validation

## Requirements

- Android 7.0+ (API level 24)
- USB OTG support
- Compatible USB-to-serial TPMS receiver

## Setup

1. Connect TPMS receiver via USB OTG adapter
2. Launch app and grant USB permissions
3. Configure baud rate in Settings (try 19200 first, then 9600)
4. Use "Assign Sensors" to map sensor IDs to tire positions
5. Set target pressure/temperature and deviation thresholds

## Configuration

### Settings
- **Target Pressure**: Default 32 PSI
- **Target Temperature**: Default 75°F
- **Pressure Deviation**: Default 15%
- **Temperature Deviation**: Default 20%
- **Baud Rate**: 9600, 19200, 38400, or 115200
- **Text-to-Speech**: Configurable audio announcements

### Tire Positions
- **Vehicle**: Front Left, Front Right, Rear Left, Rear Right
- **Trailer**: Front Left, Front Right, Rear Left, Rear Right

## Visual Indicators

- **Green bars**: Within acceptable range
- **Yellow bars**: Approaching deviation limit
- **Red bars**: Exceeding acceptable range
- **Flashing tires**: Active alarm condition

## Alarms

When sensors exceed configured thresholds:
- **Audio tone**: Moderate intensity alarm
- **Visual flash**: Tire outline flashes red
- **Speech alert**: "Trailer right rear tire pressure 20 PSI"

## Development

Built with:
- Android Studio
- Java 8+
- USB Serial for Android library
- Material Design Components

## License

Open source - feel free to modify and distribute.

## Protocol Analysis

This app implements the TPMS protocol discovered through analysis of serial data from TPMS receivers. The protocol uses:
- Standard serial communication (8-N-1)
- Configurable baud rates
- Fixed 8-byte packet structure
- XOR checksum validation
- Real-time sensor data updates
