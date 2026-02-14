# SmartAmbient Raspberry Pi Hub

Python-based IoT hub for the SmartAmbient system.

## Features

- **MQTT Broker Integration**: Communicates with ESP32 devices via Mosquitto
- **HTTP API**: Receives commands from clients (curl, apps, etc.)
- **Cloud Sync**: Registers devices and logs commands to cloud backend
- **State Management**: Maintains LED state and configuration

## Setup

### 1. Install Dependencies

```bash
# Install system dependencies
sudo apt update
sudo apt install -y python3 python3-pip mosquitto mosquitto-clients

# Install Python packages
pip3 install -r requirements.txt
```

### 2. Configure MQTT Broker

```bash
chmod +x setup_mqtt.sh
./setup_mqtt.sh
```

### 3. Configure the Hub

Edit `config.json`:

```json
{
    "device_name": "SmartAmbient-Pi-Hub",
    "cloud_api_url": "http://YOUR_CLOUD_IP:8080",
    "mqtt": {
        "broker_host": "localhost",
        "broker_port": 1883
    },
    "http": {
        "host": "0.0.0.0",
        "port": 5000
    }
}
```

### 4. Run the Hub

```bash
python3 main.py
```

Or install as a systemd service:

```bash
chmod +x setup_service.sh
./setup_service.sh
```

## API Reference

### Health Check
```bash
curl http://localhost:5000/health
```

### LED Control

**Get State**
```bash
curl http://localhost:5000/led/state
```

**Turn On/Off**
```bash
curl -X POST http://localhost:5000/led/on
curl -X POST http://localhost:5000/led/off
```

**Set Color**
```bash
curl -X POST http://localhost:5000/led/color \
  -H "Content-Type: application/json" \
  -d '{"r": 255, "g": 0, "b": 128}'
```

**Set Brightness**
```bash
curl -X POST http://localhost:5000/led/brightness \
  -H "Content-Type: application/json" \
  -d '{"brightness": 75}'
```

**Set Pattern**
```bash
curl -X POST http://localhost:5000/led/pattern \
  -H "Content-Type: application/json" \
  -d '{"pattern": "rainbow", "speed": 50}'
```

**Set Multiple Parameters**
```bash
curl -X POST http://localhost:5000/led/set \
  -H "Content-Type: application/json" \
  -d '{
    "on": true,
    "r": 0, "g": 255, "b": 64,
    "brightness": 80,
    "pattern": "solid"
  }'
```

### Device Info
```bash
curl http://localhost:5000/devices
curl http://localhost:5000/patterns
```

## Service Management

```bash
# Start
sudo systemctl start smartambient

# Stop
sudo systemctl stop smartambient

# Restart
sudo systemctl restart smartambient

# View logs
journalctl -u smartambient -f
```

## Debugging

### Monitor MQTT Messages
```bash
mosquitto_sub -t 'smartambient/#' -v
```

### Test MQTT Publish
```bash
mosquitto_pub -t 'smartambient/esp32/command' -m '{"command":"on","request_id":"test"}'
```

## File Structure

```
raspberry-pi/
├── main.py              # Entry point
├── hub.py               # Main hub logic
├── config.json          # Configuration
├── requirements.txt     # Python dependencies
├── setup_mqtt.sh        # MQTT broker setup script
├── setup_service.sh     # Systemd service setup
└── README.md            # This file
```
