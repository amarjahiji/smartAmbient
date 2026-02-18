# SmartAmbient ESP32 Firmware

Arduino-based firmware for the ESP32 microcontroller that drives three individual LEDs (Red, Yellow, Green). Receives commands over MQTT and supports six music-genre-based lighting patterns.

## Hardware

### Components

- ESP32 development board
- 3x LEDs (Red, Yellow, Green)
- 3x 220-330 ohm resistors
- Breadboard and jumper wires

### Wiring

| LED | GPIO Pin | Resistor |
|-----|----------|----------|
| Red | 18 | 220-330 ohm |
| Yellow | 21 | 220-330 ohm |
| Green | 19 | 220-330 ohm |

Each LED connects from the GPIO pin through a resistor to GND.

## Prerequisites

- [PlatformIO](https://platformio.org/) (CLI or VS Code extension)
- USB cable for ESP32

## Configuration

Edit `include/config.h` before building:

```c
// WiFi
#define WIFI_SSID "YourNetwork"
#define WIFI_PASSWORD "YourPassword"

// MQTT broker (Raspberry Pi IP)
#define MQTT_BROKER_IP "192.168.1.100"
#define MQTT_BROKER_PORT 1883

// GPIO pins (change if wired differently)
#define LED_RED_PIN 18
#define LED_YELLOW_PIN 21
#define LED_GREEN_PIN 19
```

## Build and Upload

```bash
cd esp32

# Build
pio run

# Upload to ESP32
pio run --target upload

# Monitor serial output
pio device monitor
```

## MQTT Commands

The ESP32 subscribes to `smartambient/led/command` and publishes status to `smartambient/led/status`.

### Turn All On

```json
{"command": "on"}
```

### Turn All Off

```json
{"command": "off"}
```

### Set Individual LEDs

```json
{"command": "set", "red": true, "yellow": false, "green": true}
```

### Start Pattern

```json
{"command": "pattern", "patternId": 1}
```

## LED Patterns

Six genre-based patterns are built into the firmware:

| ID | Genre | Description |
|----|-------|-------------|
| 1 | Rock | Aggressive red flashing with yellow bursts (150ms/100ms) |
| 2 | Pop | Upbeat cycling through all color combinations (200-300ms) |
| 3 | Jazz | Slow warm red/yellow alternating (400-800ms) |
| 4 | Classical | Gentle sequential sweep across all LEDs (300-500ms) |
| 5 | Electronic | Fast strobing all colors (50-100ms) |
| 6 | Hip-Hop | Rhythmic beats with pauses (150-500ms) |

Patterns loop continuously until a new command is received. Any manual LED command (`on`, `off`, `set`) stops the active pattern.

## Device Registration

On each MQTT connection, the ESP32 publishes a registration message to `smartambient/device/register`:

```json
{
  "deviceName": "SmartAmbient-3LED",
  "deviceType": "ESP32",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "ipAddress": "192.168.1.50",
  "firmwareVersion": "2.0.0",
  "capabilities": "led_control,mqtt_client"
}
```

The Raspberry Pi hub receives this and forwards it to the cloud backend as a child device registration.

## Status Heartbeat

The ESP32 sends a status message every 60 seconds to `smartambient/led/status`:

```json
{
  "device": "SmartAmbient-3LED",
  "version": "2.0.0",
  "red": true,
  "yellow": false,
  "green": true,
  "uptime": 3600
}
```

## Behavior

- On startup: runs a test sequence (Red -> Yellow -> Green, 300ms each)
- WiFi timeout: restarts after 30 seconds if connection fails
- MQTT reconnect: retries every 5 seconds if broker connection is lost
- TX power: set to 8.5 dBm to prevent brownout on USB power

## Project Structure

```
esp32/
├── src/
│   ├── main.cpp              # Application entry point, WiFi setup
│   ├── led_controller.cpp    # LED control and pattern engine
│   └── mqtt_handler.cpp      # MQTT connection, message handling, registration
├── include/
│   ├── config.h              # WiFi, MQTT, GPIO, device configuration
│   ├── led_controller.h      # LedController class definition
│   └── mqtt_handler.h        # MqttHandler class definition
└── platformio.ini            # PlatformIO build configuration
```

## Dependencies

- `PubSubClient` 2.8 - MQTT client
- `ArduinoJson` 7.0.0 - JSON serialization/deserialization
