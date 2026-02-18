# SmartAmbient Raspberry Pi Hub

Python-based IoT hub that runs on a Raspberry Pi. Acts as the central coordinator between the ESP32 LED controller, the cloud backend, and user clients. Hosts a local MQTT broker, provides an HTTP REST API, and performs real-time audio processing for music-reactive lighting.

## Features

- **MQTT Broker** - Runs Mosquitto locally; relays commands to/from ESP32
- **HTTP REST API** - Flask server for LED control, song classification, and audio mode
- **Cloud Bridge** - Registers with backend, logs commands, proxies Ollama requests
- **Audio-Reactive Mode** - Captures microphone input, runs FFT, drives LEDs by frequency band
- **Device Registration** - Self-registers with backend; forwards ESP32 registrations as child devices
- **AI Integration** - Classifies songs into genres via Ollama (Llama 3.2) through backend proxy

## Prerequisites

- Raspberry Pi (3B+ or later recommended)
- Python 3.8+
- USB microphone (for audio-reactive mode)
- Network connectivity to ESP32 (WiFi) and cloud backend (internet)

## Setup

### 1. Install MQTT Broker

```bash
chmod +x setup_mqtt.sh
./setup_mqtt.sh
```

This installs and configures Mosquitto on port 1883 with anonymous access for the local network.

### 2. Install and Start the Hub

```bash
chmod +x setup_service.sh
./setup_service.sh
```

This creates a Python virtual environment, installs dependencies, and sets up a systemd service that starts on boot.

### 3. Configuration

Edit `config.json` to set your cloud backend IP and other settings:

```json
{
    "device_name": "SmartAmbient-Pi-Hub",
    "product_id": "SMART-AMBIENT-HUB-001",
    "cloud_api_url": "http://YOUR_CLOUD_IP:8080",
    "mqtt": {
        "broker": "localhost",
        "port": 1883,
        "topic_command": "smartambient/led/command",
        "topic_status": "smartambient/led/status",
        "topic_register": "smartambient/device/register"
    },
    "flask": {
        "host": "0.0.0.0",
        "port": 5000,
        "debug": false
    }
}
```

After the first successful registration with the backend, `device_id` and `device_api_key` are saved to this file automatically.

## API Endpoints

### Health and Status

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check (MQTT status, ESP32 connectivity) |
| `GET` | `/status` | Full system status (LED state, MQTT, ESP32) |
| `GET` | `/led/state` | Current LED on/off states |

### LED Control

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/led/on` | Turn all LEDs on |
| `POST` | `/led/off` | Turn all LEDs off |
| `POST` | `/led/set` | Set individual LED states |
| `POST` | `/led/toggle/<name>` | Toggle a specific LED (red, yellow, green) |

**Set LEDs** request body (all fields optional):
```json
{"red": true, "yellow": false, "green": true}
```

### Song Classification

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/song` | Classify a song's genre and play matching LED pattern |

**Request body:**
```json
{"song": "Bohemian Rhapsody by Queen"}
```

**Response:**
```json
{
  "success": true,
  "song": "Bohemian Rhapsody by Queen",
  "genre": "Rock",
  "genreId": 1,
  "message": "Playing Rock pattern for 'Bohemian Rhapsody by Queen'"
}
```

### Audio-Reactive Mode

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/music/start` | Start real-time audio-reactive LED mode |
| `POST` | `/music/stop` | Stop audio-reactive mode |
| `GET` | `/music/status` | Check if audio mode is active |

## Genre Mapping

| ID | Genre | ESP32 Pattern |
|----|-------|---------------|
| 1 | Rock | Aggressive red flashing |
| 2 | Pop | Upbeat color cycling |
| 3 | Jazz | Slow warm alternating |
| 4 | Classical | Gentle sequential sweep |
| 5 | Electronic | Fast strobe |
| 6 | Hip-Hop | Rhythmic beats |

## Service Management

```bash
sudo systemctl start smartambient      # Start
sudo systemctl stop smartambient       # Stop
sudo systemctl restart smartambient    # Restart
sudo systemctl status smartambient     # Status
journalctl -u smartambient -f          # Live logs
```

## Manual Run

```bash
source venv/bin/activate
python main.py
```

## Project Structure

```
raspberry-pi/
├── main.py              # Entry point, signal handling, startup sequence
├── hub.py               # Core logic: Flask routes, MQTT, audio, cloud integration
├── config.json          # Runtime configuration (auto-updated on registration)
├── requirements.txt     # Python dependencies
├── .env.example         # Environment variable template
├── setup_mqtt.sh        # Mosquitto installation script
├── setup_service.sh     # Systemd service setup
└── install_deps.sh      # Dependency installer (runs before each start)
```

## Dependencies

- `paho-mqtt` 2.0 - MQTT client
- `flask` 3.0 - HTTP server
- `flask-cors` 4.0 - Cross-origin support
- `requests` 2.31 - HTTP client for cloud API
- `python-dotenv` 1.0 - Environment variable loading
- `sounddevice` 0.4.6 - Audio input capture
- `numpy` 1.24 - FFT computation for audio analysis
- `libportaudio2` - System-level audio library (installed by setup scripts)
