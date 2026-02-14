# SmartAmbient ESP32 LED Controller

PlatformIO-based firmware for ESP32 controlling WS2812B LED strips.

## Features

- **WS2812B LED Control**: FastLED library for efficient LED control
- **MQTT Communication**: Receives commands from Raspberry Pi hub
- **13 LED Patterns**: Various effects including rainbow, fire, meteor, etc.
- **RGB Color Control**: Full color customization
- **Brightness Control**: Adjustable brightness levels
- **Auto-reconnect**: Automatic WiFi and MQTT reconnection

## Hardware Requirements

- ESP32 development board
- WS2812B LED strip (5m, 60 LEDs/m = 300 LEDs)
- 5V power supply (for LED strip)
- Connecting wires

## Wiring

| ESP32 | WS2812B |
|-------|---------|
| GPIO 5 | DI (Data In) |
| GND | GND |

**Power Supply to LED Strip:**
| Power Supply | WS2812B |
|--------------|---------|
| +5V | +5V (VCC) |
| GND | GND |

**Important:** Connect the GND of the power supply to ESP32's GND.

## Setup

### 1. Install PlatformIO

Install [PlatformIO IDE](https://platformio.org/install/ide) or CLI.

### 2. Configure Settings

Edit `include/config.h`:

```cpp
// WiFi Configuration
#define WIFI_SSID "YOUR_WIFI_SSID"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

// MQTT Configuration (Raspberry Pi IP)
#define MQTT_BROKER_IP "192.168.1.100"
#define MQTT_BROKER_PORT 1883

// LED Configuration
#define LED_PIN 5
#define LED_COUNT 300
```

### 3. Build and Upload

```bash
# Build
pio run

# Upload
pio run -t upload

# Monitor serial output
pio device monitor
```

## MQTT Commands

The ESP32 subscribes to `smartambient/esp32/command` and responds on `smartambient/esp32/response`.

### Command Format

```json
{
    "request_id": "unique-id",
    "command": "command_name",
    ...params
}
```

### Available Commands

**Turn On/Off**
```json
{"request_id": "1", "command": "on"}
{"request_id": "2", "command": "off"}
```

**Set Color**
```json
{"request_id": "3", "command": "color", "r": 255, "g": 0, "b": 128}
```

**Set Brightness**
```json
{"request_id": "4", "command": "brightness", "brightness": 75}
```

**Set Pattern**
```json
{"request_id": "5", "command": "pattern", "pattern": "rainbow", "speed": 50}
```

**Set All Parameters**
```json
{
    "request_id": "6",
    "command": "set",
    "on": true,
    "r": 255, "g": 128, "b": 0,
    "brightness": 80,
    "pattern": "solid",
    "speed": 50
}
```

### Response Format

```json
{
    "request_id": "1",
    "success": true,
    "mac_address": "AA:BB:CC:DD:EE:FF",
    "state": {
        "on": true,
        "red": 255,
        "green": 128,
        "blue": 0,
        "brightness": 80,
        "pattern": "solid",
        "speed": 50
    }
}
```

## Available Patterns

| Pattern | Description |
|---------|-------------|
| `solid` | Static single color |
| `rainbow` | Rainbow moving along strip |
| `rainbow_cycle` | Rainbow colors cycling across entire strip |
| `theater_chase` | Theater marquee style chasing lights |
| `color_wipe` | Color wiping along the strip |
| `scanner` | Knight Rider / Cylon scanner effect |
| `fade` | Fade color in and out |
| `twinkle` | Random twinkling stars |
| `fire` | Fire/flame simulation |
| `breathing` | Smooth brightness pulsing |
| `strobe` | Fast strobe flash |
| `meteor` | Meteor rain falling effect |
| `running_lights` | Smooth running sine wave |

## Project Structure

```
esp32/
├── platformio.ini         # PlatformIO configuration
├── include/
│   ├── config.h           # Configuration settings
│   ├── led_effects.h      # LED effects header
│   └── mqtt_handler.h     # MQTT handler header
├── src/
│   ├── main.cpp           # Main application
│   ├── led_effects.cpp    # LED effects implementation
│   └── mqtt_handler.cpp   # MQTT handler implementation
└── README.md              # This file
```

## Troubleshooting

### WiFi Connection Issues
- Double-check SSID and password in `config.h`
- Ensure ESP32 is within WiFi range
- Check serial monitor for connection status

### MQTT Connection Issues
- Verify Raspberry Pi IP address
- Ensure Mosquitto is running on Pi
- Check firewall settings

### LEDs Not Working
- Verify GPIO pin matches `LED_PIN` in config
- Check power supply voltage (5V)
- Ensure GND is shared between ESP32 and power supply
- Try reducing `LED_COUNT` to test with fewer LEDs

### Pattern Flickering
- Increase `MAX_POWER_MILLIAMPS` if using adequate power supply
- Reduce brightness to lower power consumption

## Power Considerations

Each WS2812B LED can draw up to 60mA at full white brightness.
- 300 LEDs × 60mA = 18A maximum at full brightness
- Use a 5V 10A+ power supply for 300 LEDs
- Configure `MAX_POWER_MILLIAMPS` in `config.h` to limit power

The firmware automatically limits power using FastLED's power management.
