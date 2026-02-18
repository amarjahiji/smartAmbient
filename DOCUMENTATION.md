# SmartAmbient - Project Documentation

## Table of Contents

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [Architecture](#3-architecture)
4. [Backend Component](#4-backend-component)
5. [Raspberry Pi Hub Component](#5-raspberry-pi-hub-component)
6. [ESP32 Firmware Component](#6-esp32-firmware-component)
7. [Communication Protocols](#7-communication-protocols)
8. [Database Design](#8-database-design)
9. [Security](#9-security)
10. [Deployment](#10-deployment)
11. [API Reference](#11-api-reference)

---

## 1. Introduction

SmartAmbient is a distributed IoT system that creates ambient LED lighting effects driven by music. The system allows users to control three LEDs (red, yellow, green) manually, play genre-specific light patterns based on song classification, and run a real-time audio-reactive mode where LEDs respond to live audio input.

The project is structured as a three-tier architecture: an embedded device layer (ESP32), an edge computing layer (Raspberry Pi), and a cloud backend layer (Spring Boot on GCP).

## 2. System Overview

### Capabilities

| Capability | Description |
|------------|-------------|
| Manual LED Control | Turn individual LEDs on/off or toggle them via REST API |
| Song Pattern Mode | Classify a song name into one of six genres using an LLM and play the matching LED animation |
| Audio-Reactive Mode | Capture live audio, analyze frequency bands via FFT, and map bass/mid/treble energy to the three LEDs |
| Device Management | Automatic device registration, parent-child relationships, ownership claiming |
| Command Logging | Every command sent to LEDs is logged in the cloud database for audit |
| User Authentication | JWT-based user accounts with registration and login |

### Components

| Component | Runs On | Language | Framework |
|-----------|---------|----------|-----------|
| Cloud Backend | GCP VM (Ubuntu) | Java 25 | Spring Boot 4.0.1 |
| IoT Hub | Raspberry Pi | Python 3 | Flask 3.0 |
| LED Controller | ESP32 Dev Board | C++ | Arduino / PlatformIO |

## 3. Architecture

### Three-Tier Topology

```
┌──────────────────────────────────────────────────────────────┐
│                        CLOUD TIER                            │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  GCP VM (e2-highmem-2)                                 │  │
│  │  ┌──────────────┐    ┌───────────────┐                 │  │
│  │  │ Spring Boot  │───▸│  MySQL 8.0    │                 │  │
│  │  │ Port 8080    │    │ smart_ambient │                 │  │
│  │  └──────┬───────┘    └───────────────┘                 │  │
│  │         │ REST API                                     │  │
│  └─────────┼──────────────────────────────────────────────┘  │
│            │                                                  │
└────────────┼──────────────────────────────────────────────────┘
             │ HTTP (port 8080)
             │
┌────────────┼──────────────────────────────────────────────────┐
│            │              EDGE TIER                            │
│  ┌─────────┴──────────────────────────────────────────────┐  │
│  │  Raspberry Pi                                          │  │
│  │  ┌──────────────┐    ┌───────────────┐                 │  │
│  │  │ Flask Hub    │───▸│  Mosquitto    │                 │  │
│  │  │ Port 5000    │    │  Port 1883    │                 │  │
│  │  └──────────────┘    └───────┬───────┘                 │  │
│  │  ┌──────────────┐            │                         │  │
│  │  │ Audio Input  │            │ MQTT                    │  │
│  │  │ (USB Mic)    │            │                         │  │
│  │  └──────────────┘            │                         │  │
│  └──────────────────────────────┼─────────────────────────┘  │
│                                 │                              │
└─────────────────────────────────┼──────────────────────────────┘
                                  │ MQTT (port 1883)
                                  │
┌─────────────────────────────────┼──────────────────────────────┐
│                                 │       DEVICE TIER            │
│  ┌──────────────────────────────┴─────────────────────────┐  │
│  │  ESP32 Dev Board                                       │  │
│  │  ┌──────────────┐    ┌───────────────┐                 │  │
│  │  │ MQTT Client  │───▸│ LED Controller│                 │  │
│  │  │              │    │ R/Y/G GPIOs   │                 │  │
│  │  └──────────────┘    └───────────────┘                 │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **User sends command** via HTTP to Raspberry Pi (port 5000)
2. **Pi Hub** translates HTTP request into an MQTT message and publishes to `smartambient/led/command`
3. **ESP32** receives the MQTT message, parses JSON, and controls the LEDs
4. **ESP32** publishes updated LED state to `smartambient/led/status`
5. **Pi Hub** receives status update, updates local state
6. **Pi Hub** logs the command to the cloud backend via REST API
7. **Backend** persists the command record in MySQL

## 4. Backend Component

### Purpose

The backend provides persistent storage and cloud-accessible APIs for:
- User account management with JWT authentication
- Device registry (registration, claiming, hierarchy)
- Command audit trail
- Ollama LLM proxy for genre classification

### Source Organization

```
com.amarjahiji.smartAmbient/
├── SmartAmbientApplication.java    # Spring Boot entry point
├── controller/
│   ├── AuthController.java         # /auth/* endpoints
│   └── DeviceController.java       # /api/devices/* endpoints
├── service/
│   ├── AuthService.java            # Auth interface
│   ├── AuthServiceImpl.java        # JWT generation, user auth logic
│   ├── DeviceService.java          # Device interface
│   └── DeviceServiceImpl.java      # Device CRUD, registration, commands
├── entity/
│   ├── User.java                   # User JPA entity
│   ├── Device.java                 # Device JPA entity
│   ├── Command.java                # Command JPA entity
│   ├── DeviceType.java             # Enum: RASPBERRY_PI, ESP32, OTHER
│   ├── CommandType.java            # Enum: LED commands, device commands
│   └── CommandStatus.java          # Enum: COMPLETED, FAILED, PENDING
├── repository/
│   ├── UserRepository.java         # User data access
│   ├── DeviceRepository.java       # Device data access
│   └── CommandRepository.java      # Command data access
├── dto/                            # Request/response data transfer objects
├── security/
│   ├── SecurityConfig.java         # Spring Security filter chain
│   └── AuthFilter.java             # Device API key validation filter
├── config/
│   └── PasswordConfig.java         # BCrypt password encoder
└── exception/
    └── GlobalExceptionHandler.java # Centralized error handling
```

### Authentication Model

Two authentication mechanisms are used:

1. **JWT Bearer Tokens** (for user-facing endpoints): RS256-signed tokens with 24-hour expiration. Users obtain tokens via `/auth/signin`.

2. **Device API Keys** (for device-facing endpoints): UUID-based keys assigned during device registration. Sent in the `X-Device-Api-Key` header.

### Configuration

Key properties in `application.properties`:

| Property | Value |
|----------|-------|
| Server port | 8080 |
| Database | MySQL (`smart_ambient`) |
| JPA DDL | `update` (auto-create/alter tables) |
| SQL dialect | MySQL8Dialect |

## 5. Raspberry Pi Hub Component

### Purpose

The hub serves as the edge computing layer:
- Hosts the Mosquitto MQTT broker for local device communication
- Provides an HTTP REST API that clients use to control LEDs
- Bridges local MQTT traffic to the cloud backend via REST
- Performs real-time audio processing for music-reactive LED mode
- Manages device registration (self and child devices)

### Source Organization

| File | Lines | Purpose |
|------|-------|---------|
| `main.py` | 66 | Entry point, signal handling, startup orchestration |
| `hub.py` | 948 | Core logic: Flask app, MQTT, audio, cloud integration |
| `config.json` | 19 | Runtime configuration |
| `requirements.txt` | 12 | Python dependencies |

### Audio Processing Pipeline

The audio-reactive mode captures live audio from a USB microphone and drives LEDs based on frequency content:

1. **Capture**: `sounddevice` InputStream at 44.1 kHz, 2048-sample chunks, mono
2. **Windowing**: Hanning window applied to reduce spectral leakage
3. **FFT**: `numpy.fft.rfft` computes frequency spectrum
4. **Band Extraction**:
   - Bass (20-300 Hz): FFT bins 1-14 → Red LED
   - Mid (300-2000 Hz): FFT bins 14-93 → Yellow LED
   - Treble (2000-8000 Hz): FFT bins 93-372 → Green LED
5. **Adaptive Thresholds**: Exponential moving average (alpha=0.05) with tracked min/max range
6. **LED Decision**: LED turns on when energy is in the upper 40% of the observed range
7. **Rate Limiting**: MQTT messages sent at most every 100ms or on state change

### Cloud Integration

On startup, the hub:
1. Attempts to register itself with the backend (`POST /api/devices/register`)
2. Stores the returned `device_id` and `api_key` in `config.json`
3. All subsequent API calls use the stored API key in the `X-Device-Api-Key` header
4. Each LED command is logged asynchronously to the backend

### Song Classification Flow

1. Client sends `POST /song` with `{"song": "Bohemian Rhapsody by Queen"}`
2. Hub constructs a classification prompt and sends it to `POST /api/devices/proxy/ollama` on the backend
3. Backend forwards the request to a local Ollama instance running Llama 3.2 (3B)
4. Ollama returns a genre index (1-6)
5. Hub sends `{"command": "pattern", "patternId": N}` via MQTT to the ESP32
6. ESP32 starts the corresponding LED animation pattern

## 6. ESP32 Firmware Component

### Purpose

The ESP32 is the hardware controller that directly drives three LEDs through GPIO pins. It receives commands over MQTT and executes them immediately.

### Source Organization

| File | Purpose |
|------|---------|
| `main.cpp` | WiFi setup, component initialization, main loop |
| `led_controller.cpp` | GPIO control, pattern engine with 6 patterns |
| `mqtt_handler.cpp` | MQTT connection, message parsing, registration, heartbeat |
| `config.h` | All compile-time configuration constants |

### LED Pattern Engine

The pattern engine uses a step-based animation system. Each pattern is defined as an array of `PatternStep` structs:

```c
struct PatternStep {
    bool red;
    bool yellow;
    bool green;
    unsigned int duration_ms;
};
```

The `updatePattern()` method is called every loop iteration (~10ms) and advances to the next step when the current step's duration has elapsed. Patterns loop continuously.

### MQTT Message Processing

Commands are received as JSON on `smartambient/led/command`:

| Command | Action |
|---------|--------|
| `on` | Stop pattern, all LEDs on |
| `off` | Stop pattern, all LEDs off |
| `set` | Stop pattern, set individual LED states |
| `pattern` | Stop current pattern, start new pattern by ID |

After each command, the ESP32 publishes its current state to `smartambient/led/status`.

## 7. Communication Protocols

### MQTT (ESP32 <-> Raspberry Pi)

| Topic | Direction | Payload |
|-------|-----------|---------|
| `smartambient/led/command` | Pi → ESP32 | JSON command (`on`, `off`, `set`, `pattern`) |
| `smartambient/led/status` | ESP32 → Pi | JSON status (LED states, device info, uptime) |
| `smartambient/device/register` | ESP32 → Pi | JSON device registration data |

- **Broker**: Mosquitto on Raspberry Pi (port 1883)
- **QoS**: 0 (at most once)
- **Authentication**: Anonymous (local network only)
- **Heartbeat**: ESP32 sends status every 60 seconds

### HTTP/REST (Clients <-> Raspberry Pi)

- **Protocol**: HTTP on port 5000
- **Format**: JSON request/response bodies
- **CORS**: Enabled for all origins

### HTTP/REST (Raspberry Pi <-> Backend)

- **Protocol**: HTTP on port 8080
- **Authentication**: `X-Device-Api-Key` header
- **Endpoints used**: `/api/devices/register`, `/api/devices/register/child`, `/api/devices/commands/log`, `/api/devices/proxy/ollama`

### HTTP/REST (Clients <-> Backend)

- **Protocol**: HTTP on port 8080
- **Authentication**: JWT Bearer token in `Authorization` header
- **Token validity**: 24 hours

## 8. Database Design

### Entity Relationship

```
┌──────────┐       ┌──────────┐       ┌──────────┐
│  Users   │       │ Devices  │       │ Commands │
├──────────┤       ├──────────┤       ├──────────┤
│ id (PK)  │◄──┐   │ id (PK)  │◄──┐   │ id (PK)  │
│ username │   └───│ owner_id │   └───│ device_id│
│ email    │       │ parent_id│───┐   │ user_id  │
│ password │       │ mac_addr │   │   │ cmd_type │
│ ...      │       │ api_key  │   │   │ status   │
└──────────┘       │ ...      │◄──┘   │ payload  │
                   └──────────┘       │ response │
                                      └──────────┘
```

- A **User** can own multiple **Devices**
- A **Device** can have child **Devices** (parent-child hierarchy)
- A **Command** belongs to a **Device** and optionally to a **User**

### Key Indexes

- `devices.product_id` - unique, indexed for fast lookup
- `devices.mac_address` - unique
- `devices.api_key` - unique
- `users.username` - unique
- `users.email` - unique

## 9. Security

### Authentication

| Layer | Mechanism |
|-------|-----------|
| User → Backend | JWT (RS256), 24h expiry |
| Device → Backend | UUID API key in `X-Device-Api-Key` header |
| ESP32 → MQTT | Anonymous (local network) |

### Spring Security Configuration

- CSRF disabled (stateless REST API)
- Session management: stateless
- Public endpoints: `/auth/**`
- Protected endpoints: `/api/devices/**` (requires JWT or API key)
- CORS: all origins allowed

### Password Storage

User passwords are hashed with BCrypt via Spring Security's `PasswordEncoder`.

## 10. Deployment

### Backend (GCP VM)

The `setup.sh` script automates deployment on a fresh Ubuntu VM:

1. Updates system packages
2. Installs MySQL 8, creates `smart_ambient` database
3. Installs Java 25 via SDKMAN (Amazon Corretto)
4. Clones the repository
5. Builds the backend with Maven
6. Creates a systemd service (`smartambient.service`)

**VM specs**: e2-highmem-2 (2 vCPUs, 16 GB RAM)

**Firewall requirement**: TCP port 8080 must be open for inbound traffic.

### Raspberry Pi

Two setup scripts:

1. `setup_mqtt.sh` - Installs Mosquitto, configures for local anonymous access on port 1883
2. `setup_service.sh` - Creates Python venv, installs dependencies, creates systemd service

Both services start automatically on boot.

### ESP32

Firmware is uploaded via USB using PlatformIO:

```bash
pio run --target upload
```

Configuration is compile-time only (edit `config.h` before building).

## 11. API Reference

### Raspberry Pi Hub API (Port 5000)

#### GET /health
Returns system health including MQTT and ESP32 connection status.

#### GET /status
Returns full status: LED states, MQTT connection, ESP32 info.

#### GET /led/state
Returns current LED states: `{"red": false, "yellow": true, "green": false}`.

#### POST /led/on
Turns all LEDs on. Stops audio-reactive mode if active.

#### POST /led/off
Turns all LEDs off. Stops audio-reactive mode if active.

#### POST /led/set
Sets individual LEDs. Body: `{"red": true, "yellow": false, "green": true}`. All fields optional.

#### POST /led/toggle/{name}
Toggles one LED. `name` must be `red`, `yellow`, or `green`.

#### POST /song
Classifies a song and plays matching pattern. Body: `{"song": "Song Name by Artist"}`.

#### POST /music/start
Starts audio-reactive mode. Requires USB microphone.

#### POST /music/stop
Stops audio-reactive mode and turns LEDs off.

#### GET /music/status
Returns audio mode status: `{"active": true, "device": {...}}`.

### Backend API (Port 8080)

#### POST /auth/signup
Registers a new user account.

#### POST /auth/signin
Authenticates user, returns JWT access token.

#### POST /api/devices/register
Registers a new hub device. Returns device ID and API key.

#### POST /api/devices/register/child
Registers a child device under the calling hub. Requires `X-Device-Api-Key`.

#### POST /api/devices/claim
Claims ownership of a device by product ID. Requires JWT.

#### GET /api/devices/{deviceId}
Returns device details. Requires JWT.

#### GET /api/devices/my-devices
Lists all devices owned by the authenticated user. Requires JWT.

#### GET /api/devices/{deviceId}/children
Lists child devices of a parent device. Requires JWT.

#### POST /api/devices/commands/log
Logs a command execution. Requires `X-Device-Api-Key`.

#### GET /api/devices/{deviceId}/commands
Returns command history for a device. Requires JWT.

#### POST /api/devices/proxy/ollama
Forwards a request to the Ollama LLM API. Requires `X-Device-Api-Key`.
