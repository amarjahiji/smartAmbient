# SmartAmbient - Project Report

## 1. Project Overview

**Project Name**: SmartAmbient

**Description**: A distributed IoT system for music-reactive ambient LED lighting. The system enables users to control a set of three LEDs through manual commands, song-based genre patterns, and real-time audio-reactive visualizations.

**Repository**: `github.com/amarjahiji/smartAmbient`

## 2. Objectives

The primary objectives of the SmartAmbient project were:

1. **Build an IoT device control system** spanning embedded hardware, edge computing, and cloud services.
2. **Enable music-driven LED patterns** by classifying songs into genres using an LLM and mapping genres to predefined LED animations.
3. **Implement real-time audio reactivity** by capturing audio input, performing frequency analysis, and driving LEDs based on bass/mid/treble energy.
4. **Provide device lifecycle management** including self-registration, parent-child device hierarchies, ownership claiming, and command auditing.
5. **Deploy the cloud component** on Google Cloud Platform with automated provisioning.

## 3. System Architecture

SmartAmbient is structured as a three-tier distributed system:

| Tier | Component | Host | Technology |
|------|-----------|------|------------|
| Cloud | REST Backend | GCP VM (e2-highmem-2) | Java 25, Spring Boot 4, MySQL 8 |
| Edge | IoT Hub | Raspberry Pi | Python 3, Flask 3, Mosquitto MQTT |
| Device | LED Controller | ESP32 Dev Board | C++ (Arduino), PlatformIO |

### Communication Flow

```
User/Client ──HTTP──▸ Raspberry Pi Hub ──MQTT──▸ ESP32 (LEDs)
                           │
                     HTTP (REST API)
                           │
                           ▼
                     Cloud Backend ──▸ MySQL
                           │
                           ▼
                     Ollama LLM (genre classification)
```

## 4. Components Developed

### 4.1 Cloud Backend

A Spring Boot 4 REST API deployed on GCP providing:

- **User Authentication**: Registration and login with JWT (RS256) tokens. 24-hour token expiration with BCrypt password hashing.
- **Device Registry**: Devices self-register via API and receive a UUID API key. Hub devices can register child devices (ESP32s). Users claim devices by product ID.
- **Command Logging**: Every LED command is persisted with type, payload, status, and timestamps for audit purposes.
- **Ollama Proxy**: Forwards genre classification requests to a locally running Ollama instance (Llama 3.2, 3B parameters).

**Key stats**: 29 Java source files, ~1,400 lines of code across controllers, services, entities, repositories, DTOs, security, and exception handling.

### 4.2 Raspberry Pi Hub

A Python Flask application serving as the system's central coordinator:

- **MQTT Broker Host**: Runs Mosquitto for local device communication on port 1883.
- **HTTP REST API**: 11 endpoints for LED control, song classification, and audio mode management on port 5000.
- **Cloud Bridge**: Self-registers with the backend on startup, logs all commands, proxies Ollama requests.
- **Audio Processing**: Real-time FFT analysis of microphone input. Captures at 44.1 kHz, processes 2048-sample chunks, extracts bass/mid/treble bands, uses adaptive thresholds with exponential moving averages.
- **Device Registration Relay**: Receives ESP32 registration messages via MQTT and forwards them to the backend as child device registrations.

**Key stats**: 2 Python source files, ~1,000 lines of code.

### 4.3 ESP32 Firmware

Arduino-based C++ firmware for the ESP32 microcontroller:

- **LED Control**: Drives three individual LEDs (Red on GPIO 18, Yellow on GPIO 21, Green on GPIO 19) via digital GPIO output.
- **MQTT Client**: Connects to the Raspberry Pi's MQTT broker, subscribes to command topics, publishes status and registration data.
- **Pattern Engine**: Step-based animation system with 6 genre-specific patterns (Rock, Pop, Jazz, Classical, Electronic, Hip-Hop). Each pattern is a sequence of timed LED states that loop continuously.
- **Self-Registration**: Publishes device information to the registration topic on every MQTT connection.
- **Heartbeat**: Sends status (LED states, uptime, firmware version) every 60 seconds.

**Key stats**: 3 C++ source files, 3 header files, ~500 lines of code.

## 5. Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Backend Language | Java | 25 (Amazon Corretto) |
| Backend Framework | Spring Boot | 4.0.1 |
| Authentication | Spring Security + JJWT | JWT RS256 |
| ORM | Spring Data JPA | - |
| Database | MySQL | 8.0 |
| Build Tool | Maven | 3.9+ |
| Hub Language | Python | 3.8+ |
| Hub Framework | Flask | 3.0 |
| MQTT Client (Hub) | Paho MQTT | 2.0 |
| Audio Processing | SoundDevice + NumPy | 0.4.6 / 1.24 |
| MQTT Broker | Mosquitto | Latest |
| Firmware Language | C++ | Arduino |
| Firmware Build | PlatformIO | Latest |
| MQTT Client (ESP32) | PubSubClient | 2.8 |
| JSON Library (ESP32) | ArduinoJson | 7.0.0 |
| LLM | Ollama (Llama 3.2) | 3B |
| Cloud Platform | Google Cloud Platform | e2-highmem-2 |
| OS (Server) | Ubuntu Linux | Latest LTS |

## 6. Features Implemented

### Manual LED Control
Users send REST API calls to the Raspberry Pi to turn LEDs on, off, set individual states, or toggle specific LEDs. Commands are relayed via MQTT to the ESP32 and logged to the cloud backend.

### Song-Based Pattern Mode
Users provide a song name. The system uses Ollama (Llama 3.2) to classify the song into one of six genres. The corresponding LED animation pattern is then activated on the ESP32.

### Audio-Reactive Mode
A USB microphone connected to the Raspberry Pi captures ambient audio. The system performs real-time FFT analysis, splitting the frequency spectrum into bass (20-300 Hz), mid (300-2000 Hz), and treble (2000-8000 Hz) bands. Each band maps to one LED: bass to red, mid to yellow, treble to green. Adaptive thresholds ensure responsiveness across different volume levels.

### Device Registration and Hierarchy
The Raspberry Pi hub self-registers with the cloud backend on startup and receives an API key. When ESP32 devices connect and publish registration messages, the hub forwards them to the backend as child devices, establishing a parent-child hierarchy.

### User Device Ownership
Users create accounts, log in with JWT tokens, and claim devices by product ID. They can view their devices and command history through the backend API.

### Command Audit Trail
Every command executed (LED on/off/set, pattern, music start/stop) is logged to the cloud database with command type, payload, status (completed/failed), and timestamps.

## 7. Deployment

### Cloud Backend
Deployed on a GCP e2-highmem-2 VM (2 vCPUs, 16 GB RAM) running Ubuntu. An automated `setup.sh` script provisions the entire environment: MySQL 8, Java 25 (via SDKMAN), repository clone, Maven build, and systemd service creation. The backend runs as a persistent service on port 8080.

### Raspberry Pi
Two setup scripts handle deployment: `setup_mqtt.sh` installs and configures Mosquitto, and `setup_service.sh` creates a Python virtual environment, installs dependencies, and sets up a systemd service. Both the MQTT broker and the hub application start automatically on boot.

### ESP32
Firmware is built and uploaded via PlatformIO over USB. Configuration (WiFi credentials, MQTT broker IP, GPIO pins) is set at compile time in `config.h`.

## 8. Database

MySQL 8 with three tables:

- **users**: User accounts with UUID primary keys, unique username/email, BCrypt-hashed passwords, birthday, timestamps, soft delete support.
- **devices**: Device registry with UUID primary keys, unique MAC/API key/product ID, device type enum, parent-child foreign keys, online status tracking, capability strings.
- **commands**: Command log with UUID primary keys, foreign keys to device and user, command type and status enums, JSON payload and response fields, execution timestamps.

## 9. Protocols and Ports

| Protocol | Port | Usage |
|----------|------|-------|
| HTTP | 8080 | Backend REST API (GCP VM) |
| HTTP | 5000 | Hub REST API (Raspberry Pi) |
| MQTT | 1883 | Device communication (Raspberry Pi broker) |
| MySQL | 3306 | Database (GCP VM, localhost only) |

## 10. Conclusion

SmartAmbient delivers a functional IoT system spanning three tiers of computing. The system successfully integrates embedded hardware control, edge computing with real-time audio processing, cloud-based device management, and LLM-powered music classification into a cohesive ambient lighting platform. The automated deployment scripts and systemd service configurations ensure the system is production-ready and can be reproduced on fresh hardware.
