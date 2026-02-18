# SmartAmbient - Technical Reference

A detailed technical reference describing the complete system internals, data flows, and implementation specifics.

## Table of Contents

1. [System Boot Sequence](#1-system-boot-sequence)
2. [Device Registration Flow](#2-device-registration-flow)
3. [LED Command Flow (Manual)](#3-led-command-flow-manual)
4. [Song Classification Flow](#4-song-classification-flow)
5. [Audio-Reactive Mode Flow](#5-audio-reactive-mode-flow)
6. [User Authentication Flow](#6-user-authentication-flow)
7. [Device Claiming Flow](#7-device-claiming-flow)
8. [MQTT Protocol Details](#8-mqtt-protocol-details)
9. [Backend Internals](#9-backend-internals)
10. [Raspberry Pi Hub Internals](#10-raspberry-pi-hub-internals)
11. [ESP32 Firmware Internals](#11-esp32-firmware-internals)
12. [Database Schema Details](#12-database-schema-details)
13. [Security Implementation](#13-security-implementation)
14. [Infrastructure and Deployment](#14-infrastructure-and-deployment)
15. [Error Handling and Resilience](#15-error-handling-and-resilience)

---

## 1. System Boot Sequence

### 1.1 Backend Boot (GCP VM)

```
systemd starts smartambient.service
  │
  ▼
Java loads SmartAmbientApplication.java
  │
  ▼
Spring Boot autoconfigures:
  ├── DataSource → MySQL (localhost:3306/smart_ambient)
  ├── JPA/Hibernate → DDL update (create/alter tables)
  ├── SecurityConfig → filter chain, CORS, CSRF disabled
  ├── AuthFilter → registered in filter chain
  └── PasswordConfig → BCrypt encoder bean
  │
  ▼
Embedded Tomcat starts on port 8080
  │
  ▼
Application ready (accepting REST requests)
```

### 1.2 Raspberry Pi Boot

```
systemd starts smartambient.service
  │
  ├── ExecStartPre: install_deps.sh
  │     ├── Creates venv if missing
  │     ├── pip install requirements.txt
  │     └── Ensures libportaudio2 installed
  │
  ▼
python main.py
  │
  ├── load_dotenv() → reads .env file
  ├── Configure logging (level from LOG_LEVEL env var)
  ├── Register signal handlers (SIGINT, SIGTERM)
  │
  ▼
register_with_backend()
  │
  ├── If config.json has device_api_key AND device_id → skip (already registered)
  ├── Otherwise:
  │     ├── Get MAC address (uuid.getnode())
  │     ├── Get IP address (UDP socket to 8.8.8.8)
  │     ├── POST /api/devices/register to cloud backend
  │     ├── Extract apiKey and id from response
  │     └── Save to config.json (persistent across restarts)
  │
  ▼
init_mqtt()
  │
  ├── Create mqtt.Client(CallbackAPIVersion.VERSION2)
  ├── Set callbacks: on_connect, on_disconnect, on_message
  ├── Connect to localhost:1883
  └── Start loop_start() (background thread)
  │
  ▼
On MQTT connect:
  ├── Subscribe to smartambient/led/status
  └── Subscribe to smartambient/device/register
  │
  ▼
time.sleep(1)  ← allow MQTT to settle
  │
  ▼
app.run(host="0.0.0.0", port=5000)  ← Flask HTTP server starts
```

### 1.3 ESP32 Boot

```
setup() called once:
  │
  ├── Serial.begin(115200)
  ├── printStartupBanner() → prints GPIO config, firmware version
  │
  ├── ledController.begin()
  │     ├── pinMode(18, OUTPUT)  ← Red
  │     ├── pinMode(21, OUTPUT)  ← Yellow
  │     ├── pinMode(19, OUTPUT)  ← Green
  │     └── allOff()
  │
  ├── LED test sequence:
  │     ├── Red ON → 300ms → Red OFF
  │     ├── Yellow ON → 300ms → Yellow OFF
  │     └── Green ON → 300ms → Green OFF
  │
  ├── setupWiFi()
  │     ├── WiFi.mode(WIFI_STA)
  │     ├── WiFi.begin(SSID, PASSWORD)
  │     ├── WiFi.setTxPower(WIFI_POWER_8_5dBm)  ← prevents brownout
  │     ├── Wait up to 30s for connection
  │     └── On timeout: ESP.restart()
  │
  └── mqttHandler.begin()
        ├── mqttClient.setServer("172.20.10.5", 1883)
        ├── mqttClient.setCallback(staticCallback)
        └── mqttClient.setBufferSize(512)

loop() called continuously (~10ms delay):
  │
  ├── mqttHandler.loop()
  │     ├── If disconnected and 5s since last attempt → reconnect()
  │     │     ├── mqttClient.connect("esp32-3led")
  │     │     ├── Subscribe to smartambient/led/command
  │     │     ├── sendRegistration()  ← publish to smartambient/device/register
  │     │     └── sendStatus()  ← publish to smartambient/led/status
  │     ├── If connected → mqttClient.loop() (process incoming)
  │     └── Every 60s → sendStatus() (heartbeat)
  │
  └── ledController.updatePattern()
        └── If pattern active: check if step duration elapsed, advance step
```

## 2. Device Registration Flow

### 2.1 Raspberry Pi Self-Registration

```
Pi Hub                                  Cloud Backend
  │                                         │
  ├── POST /api/devices/register            │
  │   Headers: X-Device-Api-Key: registering│
  │   Body: {                               │
  │     "deviceName": "SmartAmbient-Pi-Hub", │
  │     "deviceType": "RASPBERRY_PI",       │
  │     "macAddress": "aa:bb:cc:dd:ee:ff",  │
  │     "ipAddress": "192.168.1.100",       │
  │     "productId": "SMART-AMBIENT-HUB-001",│
  │     "firmwareVersion": "1.0.0",         │
  │     "capabilities": "mqtt_broker,led_hub,flask_api"
  │   }                                     │
  │ ────────────────────────────────────────▸│
  │                                         ├── Check if macAddress already exists
  │                                         ├── If exists: return existing device
  │                                         ├── If new:
  │                                         │     ├── Generate UUID for id
  │                                         │     ├── Generate UUID for apiKey
  │                                         │     ├── Save to devices table
  │                                         │     └── Return DeviceResponse
  │ ◂────────────────────────────────────────┤
  │   Response: {                           │
  │     "id": "uuid-...",                   │
  │     "apiKey": "uuid-...",               │
  │     "deviceName": "SmartAmbient-Pi-Hub",│
  │     ...                                 │
  │   }                                     │
  │                                         │
  ├── Save id + apiKey to config.json       │
  └── All future requests use this apiKey   │
```

### 2.2 ESP32 Child Device Registration

```
ESP32                    Pi Hub (MQTT)               Cloud Backend
  │                         │                            │
  ├── MQTT PUBLISH          │                            │
  │   Topic: smartambient/device/register                │
  │   Payload: {            │                            │
  │     "deviceName": "SmartAmbient-3LED",               │
  │     "deviceType": "ESP32",                           │
  │     "macAddress": "AA:BB:CC:DD:EE:FF",               │
  │     "ipAddress": "192.168.1.50",                     │
  │     "firmwareVersion": "2.0.0",                      │
  │     "capabilities": "led_control,mqtt_client"        │
  │   }                     │                            │
  │ ────────────────────────▸│                            │
  │                         │                            │
  │                         ├── on_mqtt_message() fires  │
  │                         ├── Detects registration topic│
  │                         ├── Calls register_child_device()
  │                         │                            │
  │                         ├── POST /api/devices/register/child
  │                         │   Headers: X-Device-Api-Key: <pi-api-key>
  │                         │   Body: (same payload)     │
  │                         │ ──────────────────────────▸│
  │                         │                            ├── Lookup parent by apiKey
  │                         │                            ├── Check if MAC exists
  │                         │                            ├── Create device with parentDeviceId
  │                         │                            ├── Save to devices table
  │                         │ ◂──────────────────────────┤
  │                         │   Response: DeviceResponse │
  │                         │                            │
```

## 3. LED Command Flow (Manual)

Example: User sends `POST /led/set {"red": true, "yellow": false, "green": true}`

```
Client                  Pi Hub                    MQTT Broker        ESP32
  │                       │                          │                 │
  ├── POST /led/set ─────▸│                          │                 │
  │   {"red":true,        │                          │                 │
  │    "yellow":false,    │                          │                 │
  │    "green":true}      │                          │                 │
  │                       │                          │                 │
  │                       ├── If audio_active:       │                 │
  │                       │     stop_audio_listening()│                 │
  │                       │                          │                 │
  │                       ├── Validate JSON body     │                 │
  │                       ├── Validate boolean types │                 │
  │                       │                          │                 │
  │                       ├── send_command("set",    │                 │
  │                       │     red=True, green=True)│                 │
  │                       │                          │                 │
  │                       │   Update local led_state │                 │
  │                       │   Build JSON payload     │                 │
  │                       │                          │                 │
  │                       ├── PUBLISH ──────────────▸│                 │
  │                       │   Topic: smartambient/led/command          │
  │                       │   Payload: {"command":"set",               │
  │                       │     "red":true,"yellow":false,"green":true}│
  │                       │                          │                 │
  │                       │                          ├── Deliver ─────▸│
  │                       │                          │                 │
  │                       │                          │                 ├── handleMessage()
  │                       │                          │                 ├── Parse JSON
  │                       │                          │                 ├── leds.stopPattern()
  │                       │                          │                 ├── leds.setAll(T,F,T)
  │                       │                          │                 │     ├── GPIO 18 HIGH
  │                       │                          │                 │     ├── GPIO 21 LOW
  │                       │                          │                 │     └── GPIO 19 HIGH
  │                       │                          │                 │
  │                       │                          │                 ├── sendStatus()
  │                       │                          │◂── PUBLISH ─────┤
  │                       │                          │   Topic: .../status
  │                       │                          │   {"red":true,  │
  │                       │                          │    "yellow":false,
  │                       │◂── Deliver ──────────────│    "green":true,│
  │                       │                          │    "uptime":..} │
  │                       │                          │                 │
  │                       ├── on_mqtt_message()      │                 │
  │                       │   Update esp32_status    │                 │
  │                       │   Update led_state       │                 │
  │                       │                          │                 │
  │                       ├── log_command_to_backend()│                │
  │                       │   POST /api/devices/commands/log           │
  │                       │   (async, non-blocking on failure)         │
  │                       │                          │                 │
  │◂── 200 OK ────────────┤                          │                 │
  │   {"success":true,    │                          │                 │
  │    "message":"LED state updated",                │                 │
  │    "state":{"red":true,"yellow":false,"green":true}}              │
```

## 4. Song Classification Flow

```
Client              Pi Hub               Cloud Backend           Ollama
  │                   │                       │                     │
  ├── POST /song ────▸│                       │                     │
  │   {"song":        │                       │                     │
  │    "Bohemian      │                       │                     │
  │     Rhapsody      │                       │                     │
  │     by Queen"}    │                       │                     │
  │                   │                       │                     │
  │                   ├── classify_song_genre()│                     │
  │                   │                       │                     │
  │                   │   Build prompt:       │                     │
  │                   │   "You are a music    │                     │
  │                   │    classifier. Reply  │                     │
  │                   │    with only the      │                     │
  │                   │    index number.      │                     │
  │                   │    1: Rock            │                     │
  │                   │    2: Pop             │                     │
  │                   │    3: Jazz            │                     │
  │                   │    4: Classical       │                     │
  │                   │    5: Electronic      │                     │
  │                   │    6: Hip-Hop         │                     │
  │                   │    Song: Bohemian..." │                     │
  │                   │                       │                     │
  │                   ├── POST /api/devices/proxy/ollama             │
  │                   │   Headers: X-Device-Api-Key: <key>           │
  │                   │   Body: {             │                     │
  │                   │     "model": "llama3.2:3b",                  │
  │                   │     "prompt": "...",  │                     │
  │                   │     "stream": false,  │                     │
  │                   │     "options": {      │                     │
  │                   │       "temperature": 0.3,                    │
  │                   │       "num_predict": 10                      │
  │                   │     }                 │                     │
  │                   │   }                   │                     │
  │                   │ ─────────────────────▸│                     │
  │                   │                       │                     │
  │                   │                       ├── Validate API key  │
  │                   │                       ├── Forward to Ollama │
  │                   │                       │ ───────────────────▸│
  │                   │                       │                     │
  │                   │                       │                     ├── LLM inference
  │                   │                       │                     ├── Returns "1"
  │                   │                       │ ◂───────────────────┤
  │                   │                       │                     │
  │                   │ ◂─────────────────────┤                     │
  │                   │   {"response": "1"}   │                     │
  │                   │                       │                     │
  │                   ├── Parse response      │                     │
  │                   │   regex [1-6] → 1     │                     │
  │                   │   Genre: Rock         │                     │
  │                   │                       │                     │
  │                   ├── send_pattern_command(1)                    │
  │                   │   MQTT PUBLISH:       │                     │
  │                   │   Topic: smartambient/led/command             │
  │                   │   {"command":"pattern","patternId":1}        │
  │                   │                       │                     │
  │                   ├── log_command_to_backend()                   │
  │                   │   type: SONG_PATTERN  │                     │
  │                   │   payload: {song, genre, patternId}          │
  │                   │                       │                     │
  │◂──────────────────┤                       │                     │
  │   {"success":true,│                       │                     │
  │    "song":"Bohemian Rhapsody by Queen",   │                     │
  │    "genre":"Rock",│                       │                     │
  │    "genreId":1,   │                       │                     │
  │    "message":"Playing Rock pattern..."}   │                     │
```

### Genre → Pattern Mapping

| Genre ID | Genre | Pattern Description | Timing |
|----------|-------|-------------------|--------|
| 1 | Rock | Red ON → OFF → Red+Yellow → OFF (loop) | 150/100/150/100ms |
| 2 | Pop | Red → Yellow → Green → R+Y → Y+G → All (loop) | 200-300ms per step |
| 3 | Jazz | Red → R+Y → Yellow → Yellow hold → OFF (loop) | 400-800ms per step |
| 4 | Classical | Red → R+Y → Yellow → Y+G → Green → OFF (loop) | 300-500ms per step |
| 5 | Electronic | All → OFF → R+G → OFF → Yellow → OFF (loop) | 50-100ms per step |
| 6 | Hip-Hop | R+Y beat → pause → R+Y beat → pause → All accent → long pause | 150-500ms per step |

## 5. Audio-Reactive Mode Flow

### 5.1 Startup

```
Client              Pi Hub                    MQTT            ESP32
  │                   │                         │                │
  ├── POST            │                         │                │
  │   /music/start ──▸│                         │                │
  │                   │                         │                │
  │                   ├── Check audio_active    │                │
  │                   │   (reject if already on)│                │
  │                   │                         │                │
  │                   ├── Send "off" to ESP32   │                │
  │                   │   (stop any pattern)    │                │
  │                   │                         │                │
  │                   ├── start_audio_listening()│                │
  │                   │   ├── Reset adaptive thresholds          │
  │                   │   │   _band_avg = [0,0,0]                │
  │                   │   │   _band_min = [inf,inf,inf]          │
  │                   │   │   _band_max = [0,0,0]                │
  │                   │   │                     │                │
  │                   │   ├── Query audio devices                │
  │                   │   │   Priority: USB/headset/mic          │
  │                   │   │   Fallback: system default           │
  │                   │   │                     │                │
  │                   │   ├── Open InputStream  │                │
  │                   │   │   device: selected  │                │
  │                   │   │   channels: 1 (mono)│                │
  │                   │   │   samplerate: 44100 │                │
  │                   │   │   blocksize: 2048   │                │
  │                   │   │   callback: audio_callback           │
  │                   │   │                     │                │
  │                   │   └── audio_active = True                │
  │                   │                         │                │
  │◂──────────────────┤                         │                │
  │   {"success":true,│                         │                │
  │    "message":"Audio-reactive LED mode started",              │
  │    "device":{"name":"USB Mic","index":3,    │                │
  │              "sample_rate":44100,"channels":1}}              │
```

### 5.2 Audio Callback (runs ~21 times/second)

```
audio_callback(indata, frames, time_info, status)
  │
  ├── audio = indata[:, 0]          ← mono channel
  │
  ├── windowed = audio * hanning(2048)  ← reduce spectral leakage
  │
  ├── fft_data = abs(rfft(windowed))    ← 1025 frequency bins
  │   Frequency resolution: 44100 / 2048 = ~21.5 Hz per bin
  │
  ├── Extract energy bands:
  │   ├── bass_energy   = mean(fft[1:14])    ←  21-300 Hz
  │   ├── mid_energy    = mean(fft[14:93])   ← 300-2000 Hz
  │   └── treble_energy = mean(fft[93:372])  ← 2000-8000 Hz
  │
  ├── Update adaptive thresholds (for each band i):
  │   ├── _band_avg[i] = 0.05 * energy + 0.95 * _band_avg[i]   ← EMA
  │   ├── _band_min[i] = min(_band_min[i], energy)
  │   ├── _band_max[i] = max(_band_max[i], energy)
  │   ├── _band_min[i] = 0.999 * _band_min[i] + 0.001 * avg   ← slow decay
  │   └── _band_max[i] = 0.999 * _band_max[i] + 0.001 * avg   ← slow decay
  │
  ├── Determine LED states:
  │   For each band:
  │   ├── range = max - min
  │   ├── If range > 0.01:
  │   │     position = (energy - min) / range    ← 0.0 to 1.0
  │   │     LED ON if position > 0.6             ← upper 40%
  │   └── Else:
  │         LED ON if energy > avg * 1.1         ← fallback
  │
  │   Result: new_state = [bass_on, mid_on, treble_on]
  │
  ├── Rate limiting:
  │   ├── state_changed = (new_state != _last_led_state)
  │   ├── time_elapsed  = (now - _last_mqtt_send) >= 0.1s
  │   └── Send only if state_changed OR time_elapsed
  │
  └── If sending:
      ├── MQTT PUBLISH smartambient/led/command
      │   {"command":"set","red":true,"yellow":false,"green":true}
      ├── _last_led_state = new_state
      └── _last_mqtt_send = now

Mapping:
  Bass energy   (20-300 Hz)   → Red LED    (GPIO 18)
  Mid energy    (300-2000 Hz) → Yellow LED (GPIO 21)
  Treble energy (2000-8000 Hz)→ Green LED  (GPIO 19)
```

### 5.3 Shutdown

```
POST /music/stop
  │
  ├── stop_audio_listening()
  │   ├── audio_stream.stop()
  │   ├── audio_stream.close()
  │   ├── audio_active = False
  │   └── MQTT PUBLISH: {"command":"off","red":false,"yellow":false,"green":false}
  │
  └── log_command_to_backend("MUSIC_STOP", {}, True)
```

Note: Audio mode is also auto-stopped when any manual LED command is received (`/led/on`, `/led/off`, `/led/set`, `/led/toggle`).

## 6. User Authentication Flow

### 6.1 Registration

```
Client                          Backend
  │                               │
  ├── POST /auth/signup ─────────▸│
  │   {                           │
  │     "firstName": "Amar",      │
  │     "lastName": "Jahiji",     │
  │     "username": "amar",       │
  │     "email": "amar@email.com",│
  │     "password": "pass123",    │
  │     "birthday": "2000-01-01"  │
  │   }                           │
  │                               ├── Validate username/email unique
  │                               ├── BCrypt.encode(password)
  │                               ├── Create User entity
  │                               ├── Save to users table
  │                               │
  │ ◂─────────────────────────────┤
  │   "User registered successfully"
```

### 6.2 Login

```
Client                          Backend (AuthServiceImpl)
  │                               │
  ├── POST /auth/signin ─────────▸│
  │   {"username":"amar",         │
  │    "password":"pass123"}      │
  │                               ├── Find user by username
  │                               ├── BCrypt.matches(password, hash)
  │                               ├── If match:
  │                               │   ├── Build JWT claims:
  │                               │   │   subject = user.id
  │                               │   │   "username" = user.username
  │                               │   │   issued_at = now
  │                               │   │   expiration = now + 24h
  │                               │   ├── Sign with RS256 private key
  │                               │   └── Return AccessTokenResponse
  │ ◂─────────────────────────────┤
  │   {"accessToken": "eyJ..."}   │
```

### 6.3 Protected Request

```
Client                          Backend (SecurityConfig → AuthFilter)
  │                               │
  ├── GET /api/devices/my-devices │
  │   Authorization: Bearer eyJ...│
  │ ─────────────────────────────▸│
  │                               ├── SecurityConfig filter chain:
  │                               │   ├── Check if path matches /api/devices/**
  │                               │   ├── Extract JWT from Authorization header
  │                               │   ├── Verify RS256 signature
  │                               │   ├── Check expiration
  │                               │   ├── Extract user ID from subject
  │                               │   └── Set SecurityContext
  │                               │
  │                               ├── OR: AuthFilter checks X-Device-Api-Key
  │                               │   ├── Lookup device by apiKey in DB
  │                               │   ├── If found: set authentication
  │                               │   └── If not: continue chain (may fail)
  │                               │
  │                               ├── DeviceController.getMyDevices()
  │                               │   ├── Extract userId from SecurityContext
  │                               │   └── Query devices WHERE owner_id = userId
  │ ◂─────────────────────────────┤
  │   [list of DeviceResponse]    │
```

## 7. Device Claiming Flow

```
Client                          Backend
  │                               │
  ├── POST /api/devices/claim ───▸│
  │   Authorization: Bearer eyJ.. │
  │   {"productId":"SMART-AMBIENT-HUB-001"}
  │                               │
  │                               ├── Extract userId from JWT
  │                               ├── Find device by productId
  │                               ├── Check device exists
  │                               ├── Check device not already claimed
  │                               ├── Set device.ownerId = userId
  │                               ├── Save device
  │ ◂─────────────────────────────┤
  │   DeviceResponse (with owner) │
```

After claiming, the user can see this device in `GET /api/devices/my-devices` and access its command history.

## 8. MQTT Protocol Details

### 8.1 Broker Configuration

Mosquitto runs on the Raspberry Pi with the following configuration (`/etc/mosquitto/conf.d/smartambient.conf`):

```
listener 1883
allow_anonymous true
persistence true
persistence_location /var/lib/mosquitto/
max_connections -1
max_inflight_messages 20
max_queued_messages 100
```

### 8.2 Topics

| Topic | Publisher | Subscriber | QoS | Payload |
|-------|----------|-----------|-----|---------|
| `smartambient/led/command` | Pi Hub | ESP32 | 0 | JSON command |
| `smartambient/led/status` | ESP32 | Pi Hub | 0 | JSON state |
| `smartambient/device/register` | ESP32 | Pi Hub | 0 | JSON registration |

### 8.3 Message Formats

**Command messages** (Pi → ESP32):
```json
{"command": "on"}
{"command": "off"}
{"command": "set", "red": true, "yellow": false, "green": true}
{"command": "pattern", "patternId": 3}
```

**Status messages** (ESP32 → Pi):
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

**Registration messages** (ESP32 → Pi):
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

### 8.4 Client Configuration

**ESP32 MQTT Client**:
- Client ID: `esp32-3led`
- Buffer size: 512 bytes
- Reconnect delay: 5 seconds
- Heartbeat interval: 60 seconds
- Library: PubSubClient 2.8

**Pi Hub MQTT Client**:
- API version: CallbackAPIVersion.VERSION2
- Keepalive: 60 seconds
- Library: Paho MQTT 2.0
- Runs loop in background thread (`loop_start()`)

## 9. Backend Internals

### 9.1 Spring Security Filter Chain

```java
SecurityConfig.securityFilterChain():
  ├── csrf().disable()
  ├── cors(defaults)
  ├── sessionManagement(STATELESS)
  ├── authorizeHttpRequests:
  │   ├── /auth/** → permitAll
  │   └── /api/devices/** → authenticated
  └── addFilterBefore(AuthFilter, UsernamePasswordAuthenticationFilter)
```

### 9.2 AuthFilter (Device API Key)

The `AuthFilter` intercepts all requests and checks for the `X-Device-Api-Key` header:

1. Extract key from header
2. Look up device in database by `apiKey`
3. If found: create `UsernamePasswordAuthenticationToken` with device ID as principal
4. If not found: continue filter chain (JWT filter handles it, or returns 401)

### 9.3 JWT Implementation

- **Algorithm**: RS256 (RSA with SHA-256)
- **Key pair**: Embedded RSA private/public keys in `AuthServiceImpl`
- **Token lifetime**: 24 hours
- **Claims**: `sub` (user UUID), `username`, `iat`, `exp`
- **Library**: JJWT 0.12.6

### 9.4 Device Registration Logic

```
DeviceServiceImpl.registerDevice(request):
  ├── Check if device exists by macAddress
  │   ├── If exists: return existing device (idempotent)
  │   └── If new:
  │       ├── Generate UUID apiKey
  │       ├── Create Device entity
  │       ├── Set registeredAt = now
  │       ├── Set isOnline = true
  │       ├── Save to DB
  │       └── Return DeviceResponse

DeviceServiceImpl.registerChildDevice(request, parentApiKey):
  ├── Find parent device by apiKey
  ├── Check child not already registered (by macAddress)
  │   ├── If exists: return existing
  │   └── If new:
  │       ├── Create Device entity
  │       ├── Set parentDeviceId = parent.id
  │       ├── Generate UUID apiKey for child
  │       ├── Save to DB
  │       └── Return DeviceResponse
```

### 9.5 Command Logging Logic

```
DeviceServiceImpl.logCommand(request):
  ├── Find device by deviceId
  ├── Create Command entity:
  │   ├── device = found device
  │   ├── commandType = request.commandType (enum)
  │   ├── status = request.status (enum)
  │   ├── payload = request.payload (JSON string)
  │   ├── response = request.response (JSON string)
  │   └── createdAt = now
  ├── Save to commands table
  └── Return CommandResponse
```

### 9.6 Ollama Proxy Logic

The backend proxies requests from the Pi Hub to a local Ollama instance:

```
DeviceController.proxyOllama(body, apiKeyHeader):
  ├── Validate API key (device exists)
  ├── Forward request body to http://localhost:11434/api/generate
  ├── Return Ollama response as-is
```

This allows the Pi Hub (on a different network) to access the LLM running on the GCP VM.

## 10. Raspberry Pi Hub Internals

### 10.1 State Management

Global state variables in `hub.py`:

```python
led_state = {"red": False, "yellow": False, "green": False}  # LED states
esp32_status = {"connected": False, "last_seen": None}       # ESP32 info
mqtt_client = None     # Paho MQTT client instance
mqtt_connected = False # MQTT connection status
audio_active = False   # Audio-reactive mode flag
audio_stream = None    # SoundDevice InputStream
audio_device_info = None  # Selected audio device info
```

Audio adaptive threshold state:
```python
_band_avg = [0.0, 0.0, 0.0]           # EMA baseline per band
_band_min = [inf, inf, inf]            # Tracked minimum per band
_band_max = [0.0, 0.0, 0.0]           # Tracked maximum per band
_ema_alpha = 0.05                       # Smoothing factor
_last_mqtt_send = 0.0                   # Rate limiting timestamp
_last_led_state = [False, False, False] # Last sent state (dedup)
_callback_count = 0                     # Debug counter
```

### 10.2 MQTT Callback Routing

```python
on_mqtt_message(client, userdata, msg):
  ├── Parse JSON payload
  ├── Check topic:
  │   ├── If smartambient/device/register:
  │   │     └── register_child_device(payload)
  │   └── If smartambient/led/status:
  │         ├── Update esp32_status.connected = True
  │         ├── Update esp32_status.last_seen = timestamp
  │         └── Update led_state from payload
```

### 10.3 Command Logging (Non-Blocking)

All command log calls are synchronous but wrapped in try/except. Timeouts are set to 5 seconds. Failures are logged but do not affect the user-facing response. The hub continues operating normally even if the backend is offline.

### 10.4 Configuration Persistence

`config.json` is read on startup and written to on:
- Successful device registration (saves `device_id` and `device_api_key`)

This means the hub only needs to register once. On subsequent restarts, it reuses the stored credentials.

## 11. ESP32 Firmware Internals

### 11.1 Pattern Engine Architecture

```cpp
struct PatternStep {
    bool red, yellow, green;
    unsigned int duration_ms;
};

struct LedPattern {
    uint8_t id;
    const PatternStep* steps;
    uint8_t numSteps;
};
```

Patterns are stored as `const` arrays in flash memory (not RAM). The engine tracks:
- `patternActive` - whether a pattern is running
- `currentPatternId` - which pattern
- `currentStepIndex` - current step in the sequence
- `stepStartTime` - millis() when current step started
- `activePattern` - pointer to the pattern struct

`updatePattern()` is called every ~10ms in the main loop. It checks if `millis() - stepStartTime >= currentStep.duration_ms` and advances to the next step (wrapping to 0 at the end).

### 11.2 Static Callback Pattern

PubSubClient requires a C-style function pointer for callbacks. Since `MqttHandler` is a class, a static member function (`staticCallback`) is used as the callback, which delegates to the instance method via a static pointer:

```cpp
MqttHandler* MqttHandler::instance = nullptr;

void MqttHandler::staticCallback(char* topic, byte* payload, unsigned int length) {
    if (instance) {
        instance->handleMessage(topic, payload, length);
    }
}
```

### 11.3 WiFi Power Management

TX power is explicitly set to `WIFI_POWER_8_5dBm` (lower than default) to prevent brownout resets when running from USB power. The ESP32's WiFi radio at full power can draw enough current to cause voltage drops on some USB supplies.

### 11.4 Memory Usage

- MQTT buffer: 512 bytes (set via `setBufferSize`)
- JSON documents: stack-allocated `JsonDocument` (ArduinoJson 7 auto-sizes)
- Pattern data: stored in flash (PROGMEM-like, `const` arrays)
- Status buffer: 256 bytes on stack

## 12. Database Schema Details

### 12.1 Users Table

```sql
CREATE TABLE users (
    id              BINARY(16) PRIMARY KEY,      -- UUID
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,        -- BCrypt hash
    birthday        DATE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP NULL                -- Soft delete
);
```

### 12.2 Devices Table

```sql
CREATE TABLE devices (
    id                  BINARY(16) PRIMARY KEY,      -- UUID
    device_name         VARCHAR(255) NOT NULL,
    device_type         ENUM('RASPBERRY_PI','ESP32','OTHER') NOT NULL,
    mac_address         VARCHAR(17) UNIQUE NOT NULL,
    ip_address          VARCHAR(45),
    api_key             VARCHAR(255) UNIQUE,
    product_id          VARCHAR(255) UNIQUE,
    owner_id            BINARY(16),                   -- FK → users.id
    parent_device_id    BINARY(16),                   -- FK → devices.id
    is_online           BOOLEAN DEFAULT FALSE,
    last_seen           TIMESTAMP,
    firmware_version    VARCHAR(50),
    capabilities        VARCHAR(500),
    registered_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_product_id (product_id),
    FOREIGN KEY (owner_id) REFERENCES users(id),
    FOREIGN KEY (parent_device_id) REFERENCES devices(id)
);
```

### 12.3 Commands Table

```sql
CREATE TABLE commands (
    id              BINARY(16) PRIMARY KEY,      -- UUID
    device_id       BINARY(16) NOT NULL,          -- FK → devices.id
    user_id         BINARY(16),                   -- FK → users.id (nullable)
    command_type    ENUM('LED_SET_COLOR','LED_SET_BRIGHTNESS','LED_SET_PATTERN',
                        'LED_TURN_ON','LED_TURN_OFF','SONG_PATTERN',
                        'MUSIC_START','MUSIC_STOP','DEVICE_STATUS',
                        'DEVICE_RESTART','DEVICE_UPDATE_FIRMWARE','CUSTOM') NOT NULL,
    status          ENUM('COMPLETED','FAILED','PENDING') NOT NULL,
    payload         TEXT,                          -- JSON string
    response        TEXT,                          -- JSON string
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at     TIMESTAMP,

    FOREIGN KEY (device_id) REFERENCES devices(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## 13. Security Implementation

### 13.1 JWT Token Structure

```
Header:
{
  "alg": "RS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // user UUID
  "username": "amar",
  "iat": 1700000000,
  "exp": 1700086400  // +24 hours
}

Signature:
  RS256(header + "." + payload, RSA_PRIVATE_KEY)
```

### 13.2 Request Authentication Decision Tree

```
Incoming request
  │
  ├── Path matches /auth/** ?
  │   └── YES → Allow (public)
  │
  ├── Has X-Device-Api-Key header?
  │   ├── YES → Lookup in devices table
  │   │   ├── Found → Authenticate as device
  │   │   └── Not found → Continue to JWT check
  │   └── NO → Continue to JWT check
  │
  ├── Has Authorization: Bearer header?
  │   ├── YES → Validate JWT
  │   │   ├── Valid → Authenticate as user
  │   │   └── Invalid → 401 Unauthorized
  │   └── NO → 401 Unauthorized
```

### 13.3 Password Hashing

- Algorithm: BCrypt (via Spring Security's `BCryptPasswordEncoder`)
- Configured in `PasswordConfig.java` as a Spring bean
- Used during signup (encoding) and signin (matching)

## 14. Infrastructure and Deployment

### 14.1 GCP VM Specifications

| Property | Value |
|----------|-------|
| Machine type | e2-highmem-2 |
| vCPUs | 2 |
| Memory | 16 GB |
| OS | Ubuntu Linux (latest LTS) |
| Disk | Standard persistent |
| Region | europe-west3 (Frankfurt) |
| External IP | 34.159.129.225 |

### 14.2 Systemd Services

**Backend service** (`/etc/systemd/system/smartambient.service` on GCP VM):
- After: `network.target`, `mysql.service`
- Wants: `mysql.service`
- Restart: always, 10s delay
- User: deployment user

**Hub service** (`/etc/systemd/system/smartambient.service` on Raspberry Pi):
- After: `network.target`, `mosquitto.service`
- Wants: `mosquitto.service`
- ExecStartPre: `install_deps.sh` (ensures venv and deps)
- Restart: always, 10s delay
- User: pi user

### 14.3 Network Ports

| Host | Port | Protocol | Service | Access |
|------|------|----------|---------|--------|
| GCP VM | 8080 | TCP/HTTP | Spring Boot | Public (firewall rule required) |
| GCP VM | 3306 | TCP | MySQL | Localhost only |
| GCP VM | 11434 | TCP/HTTP | Ollama | Localhost only |
| Raspberry Pi | 5000 | TCP/HTTP | Flask | Local network |
| Raspberry Pi | 1883 | TCP | Mosquitto MQTT | Local network |

### 14.4 Firewall

GCP firewall rule required for backend access:
```bash
gcloud compute firewall-rules create allow-8080 \
  --allow tcp:8080 --direction INGRESS --priority 1000 \
  --target-tags=http-server --source-ranges=0.0.0.0/0
```

## 15. Error Handling and Resilience

### 15.1 ESP32

| Scenario | Behavior |
|----------|----------|
| WiFi connection fails | Retries for 30s, then `ESP.restart()` |
| MQTT broker unreachable | Retries every 5 seconds indefinitely |
| Invalid JSON received | Logs error, ignores message |
| Unknown command | Logs warning, no state change |
| Invalid pattern ID | Logs error, no pattern started |

### 15.2 Raspberry Pi Hub

| Scenario | Behavior |
|----------|----------|
| Backend unreachable (registration) | Logs warning, continues without registration |
| Backend unreachable (command log) | Logs warning, command still executes locally |
| Backend unreachable (Ollama proxy) | Returns 503 to client |
| Ollama timeout | Returns 504 to client (30s timeout) |
| MQTT broker disconnects | Paho auto-reconnects, state tracked via `mqtt_connected` |
| No audio device found | Returns 500 with error message |
| Audio stream error | Logged, stream cleaned up |
| Invalid JSON in request | Returns 400 with validation error |
| Genre parse failure | Falls back to Pop (genre 2) |

### 15.3 Backend

| Scenario | Behavior |
|----------|----------|
| Duplicate device registration | Returns existing device (idempotent) |
| Invalid JWT | Returns 401 |
| Invalid API key | Returns 401 |
| Duplicate username/email | Returns error response |
| Database connection failure | Spring Boot error handling, service restarts via systemd |
