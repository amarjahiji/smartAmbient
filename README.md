# SmartAmbient

An IoT system for music-reactive ambient LED lighting. SmartAmbient combines embedded hardware, edge computing, and cloud services to create LED light patterns that respond to music in real time.

## Architecture

The system is composed of three layers:

```
                          Cloud (GCP VM)
                     ┌─────────────────────┐
                     │  Spring Boot Backend │
                     │  MySQL Database      │
                     │  Port 8080           │
                     └────────┬────────────┘
                              │ REST/HTTP
                              │
                      Edge (Raspberry Pi)
                     ┌────────┴────────────┐
                     │  Python Flask Hub    │
                     │  Mosquitto MQTT      │
                     │  Audio Processing    │
                     │  Port 5000 (HTTP)    │
                     │  Port 1883 (MQTT)    │
                     └────────┬────────────┘
                              │ MQTT
                              │
                    Device (ESP32 Board)
                     ┌────────┴────────────┐
                     │  3-LED Controller    │
                     │  Red / Yellow / Green│
                     │  6 Genre Patterns    │
                     └─────────────────────┘
```

## Components

| Component | Directory | Technology | Role |
|-----------|-----------|------------|------|
| **Backend** | `backend/` | Java 25, Spring Boot 4, MySQL | Cloud API, user auth, device registry, command logging |
| **Raspberry Pi Hub** | `raspberry-pi/` | Python 3, Flask, Paho MQTT | Local MQTT broker, HTTP API, audio processing, cloud bridge |
| **ESP32 Firmware** | `esp32/` | C++, Arduino, PlatformIO | LED hardware control, MQTT client, pattern engine |

## Features

- **Manual LED Control** - Turn individual LEDs (red, yellow, green) on/off via REST API
- **Song-Based Patterns** - Send a song name; Ollama LLM classifies its genre and plays a matching LED pattern
- **Audio-Reactive Mode** - Real-time FFT analysis of microphone input drives LEDs based on bass/mid/treble energy
- **Device Management** - Self-registration, parent-child device hierarchy, ownership claiming
- **Command Audit Trail** - All LED commands are logged to the cloud backend
- **JWT Authentication** - User registration and login with RS256-signed tokens

## Quick Start

### Backend (GCP VM)

```bash
# Automated setup on a fresh Ubuntu VM
chmod +x setup.sh
./setup.sh
```

### Raspberry Pi

```bash
cd raspberry-pi
chmod +x setup_mqtt.sh setup_service.sh
./setup_mqtt.sh        # Install and configure Mosquitto
./setup_service.sh     # Install Python deps and create systemd service
```

### ESP32

```bash
cd esp32
# Edit include/config.h with your WiFi and MQTT broker settings
pio run --target upload
pio device monitor
```

## API Overview

### Raspberry Pi Hub (port 5000)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check |
| `GET` | `/status` | Full system status |
| `GET` | `/led/state` | Current LED states |
| `POST` | `/led/on` | All LEDs on |
| `POST` | `/led/off` | All LEDs off |
| `POST` | `/led/set` | Set individual LEDs |
| `POST` | `/led/toggle/<name>` | Toggle one LED |
| `POST` | `/song` | Classify song and play pattern |
| `POST` | `/music/start` | Start audio-reactive mode |
| `POST` | `/music/stop` | Stop audio-reactive mode |
| `GET` | `/music/status` | Audio mode status |

### Backend (port 8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/auth/signup` | Register user |
| `POST` | `/auth/signin` | Login (returns JWT) |
| `POST` | `/api/devices/register` | Register device |
| `POST` | `/api/devices/register/child` | Register child device |
| `POST` | `/api/devices/claim` | Claim device ownership |
| `GET` | `/api/devices/my-devices` | List user's devices |
| `POST` | `/api/devices/commands/log` | Log command |
| `GET` | `/api/devices/{id}/commands` | Command history |

## Repository Structure

```
smartAmbient/
├── backend/                    # Spring Boot cloud backend
│   ├── src/main/java/...       # Java source code
│   ├── src/main/resources/     # Configuration
│   └── pom.xml                 # Maven build
├── esp32/                      # ESP32 firmware
│   ├── src/                    # C++ source files
│   ├── include/                # Header files
│   └── platformio.ini          # PlatformIO config
├── raspberry-pi/               # Raspberry Pi hub
│   ├── main.py                 # Entry point
│   ├── hub.py                  # Core hub logic
│   ├── config.json             # Runtime configuration
│   ├── requirements.txt        # Python dependencies
│   ├── setup_mqtt.sh           # MQTT broker setup
│   ├── setup_service.sh        # Systemd service setup
│   └── install_deps.sh         # Dependency installer
├── setup.sh                    # GCP VM deployment script
├── DOCUMENTATION.md            # Project documentation
├── PROJECT_REPORT.md           # Project report
└── TECHNICAL_REFERENCE.md      # Detailed technical reference
```

## Technology Stack

- **Backend**: Java 25 (Amazon Corretto), Spring Boot 4.0.1, Spring Security (JWT/RS256), Spring Data JPA, MySQL 8
- **Raspberry Pi**: Python 3, Flask 3, Paho MQTT 2, NumPy, SoundDevice
- **ESP32**: Arduino framework, PubSubClient, ArduinoJson, PlatformIO
- **Infrastructure**: Google Cloud Platform (e2-highmem-2 VM), Mosquitto MQTT broker, systemd services
- **AI Integration**: Ollama with Llama 3.2 (3B) for music genre classification
