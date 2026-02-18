# SmartAmbient Backend

Spring Boot REST API providing cloud services for the SmartAmbient IoT system. Handles user authentication, device registration, device hierarchy management, and command audit logging.

## Technology

- Java 25 (Amazon Corretto)
- Spring Boot 4.0.1
- Spring Security with JWT (RS256)
- Spring Data JPA
- MySQL 8
- Maven

## Prerequisites

- Java 25+
- MySQL 8.0+
- Maven 3.9+ (or use included Maven wrapper)

## Setup

### Automated (GCP VM)

Run the root-level `setup.sh` script on a fresh Ubuntu VM. It installs MySQL, Java, builds the project, and creates a systemd service.

### Manual

1. Install and start MySQL:
   ```bash
   sudo apt install mysql-server
   sudo systemctl start mysql
   ```

2. Create the database:
   ```sql
   CREATE DATABASE smart_ambient;
   ```

3. Configure `src/main/resources/application.properties` with your database credentials.

4. Build and run:
   ```bash
   ./mvnw clean package -DskipTests
   java -jar target/smartAmbient-0.0.1-SNAPSHOT.jar
   ```

The server starts on port **8080**.

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/auth/signup` | Register a new user | None |
| `POST` | `/auth/signin` | Login, returns JWT access token | None |

**Sign Up** request body:
```json
{
  "firstName": "Amar",
  "lastName": "Jahiji",
  "username": "amar",
  "email": "amar@example.com",
  "password": "password123",
  "birthday": "2000-01-01"
}
```

**Sign In** request body:
```json
{
  "username": "amar",
  "password": "password123"
}
```

### Device Management

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/devices/register` | Register a new device (hub self-registration) | `X-Device-Api-Key` |
| `POST` | `/api/devices/register/child` | Register a child device (ESP32 via hub) | `X-Device-Api-Key` |
| `POST` | `/api/devices/claim` | Claim ownership of a device | JWT Bearer |
| `GET` | `/api/devices/{deviceId}` | Get device details | JWT Bearer |
| `GET` | `/api/devices/mac/{macAddress}` | Get device by MAC address | JWT Bearer |
| `GET` | `/api/devices/my-devices` | List authenticated user's devices | JWT Bearer |
| `GET` | `/api/devices/{deviceId}/children` | List child devices | JWT Bearer |
| `PUT` | `/api/devices/{deviceId}/status` | Update device online status | `X-Device-Api-Key` |

### Command Logging

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/devices/commands/log` | Log a command execution | `X-Device-Api-Key` |
| `GET` | `/api/devices/{deviceId}/commands` | Get command history | JWT Bearer |

### Proxy

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/devices/proxy/ollama` | Forward request to Ollama API | `X-Device-Api-Key` |

## Database Schema

The application uses Spring JPA with `ddl-auto: update`, so tables are created automatically. Three main entities:

- **User** - id (UUID), firstName, lastName, username, email, password, birthday, timestamps
- **Device** - id (UUID), deviceName, deviceType (enum), macAddress, ipAddress, apiKey, productId, ownerId, parentDeviceId, isOnline, lastSeen, firmwareVersion, capabilities, timestamps
- **Command** - id (UUID), deviceId, userId, commandType (enum), status (enum), payload (JSON), response (JSON), timestamps

### Enums

- **DeviceType**: `RASPBERRY_PI`, `ESP32`, `OTHER`
- **CommandType**: `LED_SET_COLOR`, `LED_SET_BRIGHTNESS`, `LED_SET_PATTERN`, `LED_TURN_ON`, `LED_TURN_OFF`, `SONG_PATTERN`, `MUSIC_START`, `MUSIC_STOP`, `DEVICE_STATUS`, `DEVICE_RESTART`, `DEVICE_UPDATE_FIRMWARE`, `CUSTOM`
- **CommandStatus**: `COMPLETED`, `FAILED`, `PENDING`

## Project Structure

```
backend/
├── src/main/java/com/amarjahiji/smartAmbient/
│   ├── SmartAmbientApplication.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   └── DeviceController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── AuthServiceImpl.java
│   │   ├── DeviceService.java
│   │   └── DeviceServiceImpl.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Device.java
│   │   ├── Command.java
│   │   ├── DeviceType.java
│   │   ├── CommandType.java
│   │   └── CommandStatus.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── DeviceRepository.java
│   │   └── CommandRepository.java
│   ├── dto/
│   │   ├── SignUp.java
│   │   ├── SignIn.java
│   │   ├── AccessTokenResponse.java
│   │   ├── DeviceRegistrationRequest.java
│   │   ├── DeviceResponse.java
│   │   ├── ClaimDeviceRequest.java
│   │   ├── CommandLogRequest.java
│   │   ├── CommandResponse.java
│   │   └── LedState.java
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   └── AuthFilter.java
│   ├── config/
│   │   └── PasswordConfig.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.properties
└── pom.xml
```

## Service Management

When deployed via `setup.sh`:

```bash
sudo systemctl status smartambient     # Check status
sudo systemctl restart smartambient    # Restart
sudo systemctl stop smartambient       # Stop
sudo journalctl -u smartambient -f     # View logs
```
