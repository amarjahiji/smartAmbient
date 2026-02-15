"""
SmartAmbient Pi Hub - Simplified 3-LED Controller
Controls 3 individual LEDs (Red, Yellow, Green) via ESP32
"""

import json
import logging
import os
import re
import socket
import threading
import time
import uuid
from pathlib import Path

import paho.mqtt.client as mqtt
import requests
from flask import Flask, jsonify, request
from flask_cors import CORS

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Load configuration
CONFIG_PATH = Path(__file__).parent / "config.json"

def load_config():
    """Load configuration from config.json"""
    try:
        with open(CONFIG_PATH) as f:
            return json.load(f)
    except FileNotFoundError:
        logger.warning("config.json not found, using defaults")
        return {
            "mqtt": {
                "broker": "localhost",
                "port": 1883,
                "topic_command": "smartambient/led/command",
                "topic_status": "smartambient/led/status"
            },
            "flask": {
                "host": "0.0.0.0",
                "port": 5000,
                "debug": False
            }
        }

config = load_config()

# Cloud API settings
CLOUD_API_URL = config.get("cloud_api_url", "http://localhost:8080")

# MQTT settings
MQTT_BROKER = config.get("mqtt", {}).get("broker", "localhost")
MQTT_PORT = config.get("mqtt", {}).get("port", 1883)
MQTT_TOPIC_COMMAND = config.get("mqtt", {}).get("topic_command", "smartambient/led/command")
MQTT_TOPIC_STATUS = config.get("mqtt", {}).get("topic_status", "smartambient/led/status")
MQTT_TOPIC_REGISTER = config.get("mqtt", {}).get("topic_register", "smartambient/device/register")


def get_mac_address():
    """Get the MAC address of this device"""
    mac = uuid.getnode()
    return ':'.join(f'{(mac >> i) & 0xFF:02x}' for i in range(40, -1, -8))


def get_ip_address():
    """Get the local IP address of this device"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "unknown"


def save_config():
    """Save current config back to config.json"""
    with open(CONFIG_PATH, 'w') as f:
        json.dump(config, f, indent=4)


def register_with_backend():
    """Register this Pi hub with the cloud backend"""
    api_key = config.get("device_api_key", "")
    if api_key:
        logger.info("Device already registered (API key found in config)")
        return True

    mac = get_mac_address()
    ip = get_ip_address()
    device_name = config.get("device_name", "SmartAmbient-Pi-Hub")
    product_id = config.get("product_id", "SMART-AMBIENT-HUB-001")

    payload = {
        "deviceName": device_name,
        "deviceType": "RASPBERRY_PI",
        "macAddress": mac,
        "ipAddress": ip,
        "productId": product_id,
        "firmwareVersion": "1.0.0",
        "capabilities": "mqtt_broker,led_hub,flask_api"
    }

    try:
        logger.info(f"Registering with backend at {CLOUD_API_URL}/api/devices/register")
        resp = requests.post(
            f"{CLOUD_API_URL}/api/devices/register",
            json=payload,
            headers={"X-Device-Api-Key": "registering"},
            timeout=10
        )
        resp.raise_for_status()
        data = resp.json()

        config["device_api_key"] = data.get("apiKey", "")
        config["device_id"] = data.get("id", "")
        save_config()
        logger.info(f"Registered successfully. Device ID: {data.get('id')}")
        return True
    except requests.exceptions.ConnectionError:
        logger.warning("Could not reach backend — will retry on next startup")
        return False
    except Exception as e:
        logger.error(f"Registration failed: {e}")
        return False


def log_command_to_backend(command_type, payload_data, success):
    """Log a command execution to the backend for audit trail"""
    device_id = config.get("device_id")
    api_key = config.get("device_api_key")

    if not device_id or not api_key:
        logger.debug("Skipping command log - device not fully registered")
        return

    try:
        log_payload = {
            "deviceId": device_id,
            "commandType": command_type,
            "payload": json.dumps(payload_data),
            "status": "COMPLETED" if success else "FAILED",
            "response": json.dumps(led_state) if success else None
        }

        response = requests.post(
            f"{CLOUD_API_URL}/api/devices/commands/log",
            json=log_payload,
            headers={"X-Device-Api-Key": api_key},
            timeout=3
        )
        response.raise_for_status()
        logger.debug(f"Command logged: {command_type}")

    except requests.exceptions.Timeout:
        logger.warning("Command logging timeout - skipped")
    except requests.exceptions.ConnectionError:
        logger.warning("Command logging failed - backend offline")
    except Exception as e:
        logger.error(f"Command logging error: {e}")


def register_child_device(device_data):
    """Register a child device (ESP32) with the cloud backend"""
    try:
        mac_address = device_data.get("macAddress")

        # Check if already registered in this session
        if mac_address in registered_devices:
            logger.info(f"Device {mac_address} already registered in this session, skipping")
            return True

        api_key = config.get("device_api_key")
        if not api_key:
            logger.error("Cannot register child device - Pi not registered (no API key)")
            return False

        payload = {
            "deviceName": device_data.get("deviceName", "Unknown-ESP32"),
            "deviceType": device_data.get("deviceType", "ESP32"),
            "macAddress": mac_address,
            "ipAddress": device_data.get("ipAddress"),
            "firmwareVersion": device_data.get("firmwareVersion", "unknown"),
            "capabilities": device_data.get("capabilities", "")
        }

        logger.info(f"Registering child device {mac_address} with backend...")
        logger.debug(f"Registration payload: {payload}")

        response = requests.post(
            f"{CLOUD_API_URL}/api/devices/register/child",
            json=payload,
            headers={"X-Device-Api-Key": api_key},
            timeout=10
        )
        response.raise_for_status()

        result = response.json()
        logger.info(f"Child device registered successfully: {result.get('deviceName')} (ID: {result.get('id')})")

        # Mark as registered
        registered_devices.add(mac_address)
        return True

    except requests.exceptions.ConnectionError:
        logger.warning("Could not reach backend for child registration - backend offline")
        return False
    except requests.exceptions.Timeout:
        logger.warning("Child device registration timed out")
        return False
    except requests.exceptions.HTTPError as e:
        logger.error(f"Child device registration failed with HTTP error: {e.response.status_code} - {e.response.text}")
        return False
    except Exception as e:
        logger.error(f"Child device registration error: {e}")
        return False


# Genre mapping for song classification
GENRE_MAP = {
    1: "Rock",
    2: "Pop",
    3: "Jazz",
    4: "Classical",
    5: "Electronic",
    6: "Hip-Hop"
}


def classify_song_genre(song):
    """Classify a song into one of 6 genres using Ollama (Llama 3.2:1b)"""
    try:
        # Derive Ollama host from backend URL (same machine, port 11434)
        backend_url = CLOUD_API_URL
        ollama_host = backend_url.split("//")[1].split(":")[0]
        ollama_url = f"http://{ollama_host}:11434/api/generate"

        prompt = (
            f"You are a music classifier. Reply with only the index number.\n1: Rock\n2: Pop\n3: Jazz\n4: Classical\n5: Electronic\n6: Hip-Hop\n\nSong: {song}\nIndex:"
        )
        

        payload = {
            "model": "llama3.2:3b",
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.3,
                "num_predict": 10
            }
        }

        logger.info(f"Classifying song: {song}")
        response = requests.post(ollama_url, json=payload, timeout=30)
        response.raise_for_status()

        result = response.json()
        response_text = result.get("response", "").strip()
        logger.info(f"Ollama response: {response_text}")

        # Extract a number 1-6 from the response
        match = re.search(r'[1-6]', response_text)
        if match:
            genre_id = int(match.group())
            logger.info(f"Classified as genre {genre_id}: {GENRE_MAP[genre_id]}")
            return genre_id

        # Fallback to Pop if parsing fails
        logger.warning(f"Could not parse genre from response: {response_text}, defaulting to Pop (2)")
        return 2

    except requests.exceptions.ConnectionError:
        logger.error("Could not reach Ollama - is it running?")
        raise
    except requests.exceptions.Timeout:
        logger.error("Ollama request timed out")
        raise
    except Exception as e:
        logger.error(f"Genre classification error: {e}")
        raise


def send_pattern_command(pattern_id):
    """Send a pattern command to ESP32 via MQTT"""
    if not mqtt_connected:
        logger.error("MQTT not connected")
        return False

    try:
        payload = json.dumps({"command": "pattern", "patternId": pattern_id})
        mqtt_client.publish(MQTT_TOPIC_COMMAND, payload)
        logger.info(f"Sent pattern command: {payload}")
        return True
    except Exception as e:
        logger.error(f"Failed to send pattern command: {e}")
        return False


# ============== Audio Processing ==============

AUDIO_SAMPLE_RATE = 44100
AUDIO_CHUNK_SIZE = 2048
AUDIO_CHANNELS = 1

# Audio state
audio_active = False
audio_stream = None
audio_device_info = None

# Adaptive threshold state (exponential moving average)
_band_avg = [0.0, 0.0, 0.0]  # bass, mid, treble running averages
_ema_alpha = 0.3  # smoothing factor (higher = adapts faster)
_threshold_multiplier = 1.5  # LED on when energy > avg * multiplier
_last_mqtt_send = 0.0
_last_led_state = [False, False, False]  # red, yellow, green


def audio_callback(indata, frames, time_info, status):
    """Process audio chunk and update LEDs in real time"""
    global _band_avg, _last_mqtt_send, _last_led_state

    if status:
        logger.debug(f"Audio status: {status}")

    # Get mono audio data
    audio = indata[:, 0]

    # Apply window function to reduce spectral leakage
    windowed = audio * np.hanning(len(audio))

    # Compute FFT
    fft_data = np.abs(np.fft.rfft(windowed))

    # Frequency resolution: sample_rate / chunk_size = ~21.5 Hz per bin
    # Bass (20-300 Hz): bins 1-14
    # Mid (300-2000 Hz): bins 14-93
    # Treble (2000-8000 Hz): bins 93-372
    bass_energy = np.mean(fft_data[1:14])
    mid_energy = np.mean(fft_data[14:93])
    treble_energy = np.mean(fft_data[93:372])

    energies = [bass_energy, mid_energy, treble_energy]

    # Update adaptive thresholds with exponential moving average
    for i in range(3):
        _band_avg[i] = _ema_alpha * energies[i] + (1 - _ema_alpha) * _band_avg[i]

    # Determine LED states based on adaptive threshold
    new_state = [
        energies[i] > _band_avg[i] * _threshold_multiplier
        for i in range(3)
    ]

    # Rate-limit MQTT: only send when state changes or every 100ms
    now = time.time()
    state_changed = new_state != _last_led_state
    time_elapsed = (now - _last_mqtt_send) >= 0.1

    if state_changed or time_elapsed:
        if mqtt_connected and mqtt_client:
            try:
                payload = json.dumps({
                    "command": "set",
                    "red": new_state[0],
                    "yellow": new_state[1],
                    "green": new_state[2]
                })
                mqtt_client.publish(MQTT_TOPIC_COMMAND, payload)
            except Exception:
                pass
        _last_led_state = new_state
        _last_mqtt_send = now


def start_audio_listening():
    """Start capturing audio from USB mic and processing it"""
    global audio_active, audio_stream, audio_device_info
    global _band_avg, _last_mqtt_send, _last_led_state

    # Reset adaptive thresholds
    _band_avg = [0.0, 0.0, 0.0]
    _last_mqtt_send = 0.0
    _last_led_state = [False, False, False]

    # Find an input device
    try:
        devices = sd.query_devices()
        input_device = None
        input_device_idx = None

        # Look for a USB audio input device
        for idx, dev in enumerate(devices):
            if dev['max_input_channels'] > 0:
                name = dev['name'].lower()
                if 'usb' in name or 'headset' in name or 'microphone' in name or 'mic' in name:
                    input_device = dev
                    input_device_idx = idx
                    break

        # Fallback to default input device
        if input_device is None:
            default_idx = sd.default.device[0]
            if default_idx is not None and default_idx >= 0:
                input_device = sd.query_devices(default_idx)
                input_device_idx = default_idx

        if input_device is None:
            raise RuntimeError("No audio input device found")

        audio_device_info = {
            "name": input_device['name'],
            "index": input_device_idx,
            "sample_rate": AUDIO_SAMPLE_RATE,
            "channels": AUDIO_CHANNELS
        }

        logger.info(f"Using audio device: {input_device['name']} (index {input_device_idx})")

        audio_stream = sd.InputStream(
            device=input_device_idx,
            channels=AUDIO_CHANNELS,
            samplerate=AUDIO_SAMPLE_RATE,
            blocksize=AUDIO_CHUNK_SIZE,
            callback=audio_callback
        )
        audio_stream.start()
        audio_active = True

        logger.info("Audio listening started")
        return audio_device_info

    except Exception as e:
        logger.error(f"Failed to start audio: {e}")
        audio_active = False
        audio_stream = None
        raise


def stop_audio_listening():
    """Stop audio capture and turn LEDs off"""
    global audio_active, audio_stream, audio_device_info

    if audio_stream is not None:
        try:
            audio_stream.stop()
            audio_stream.close()
        except Exception as e:
            logger.error(f"Error stopping audio stream: {e}")
        audio_stream = None

    audio_active = False
    audio_device_info = None

    # Turn LEDs off
    if mqtt_connected and mqtt_client:
        try:
            payload = json.dumps({"command": "off", "red": False, "yellow": False, "green": False})
            mqtt_client.publish(MQTT_TOPIC_COMMAND, payload)
        except Exception:
            pass

    logger.info("Audio listening stopped")


# LED state - simple booleans for each LED
led_state = {
    "red": False,
    "yellow": False,
    "green": False
}

# ESP32 connection status
esp32_status = {
    "connected": False,
    "last_seen": None
}

# Track registration attempts to prevent duplicates
registered_devices = set()  # Store MAC addresses of registered devices

# MQTT client
mqtt_client = None
mqtt_connected = False


def on_mqtt_connect(client, userdata, flags, rc, properties=None):
    """Callback when connected to MQTT broker"""
    global mqtt_connected
    if rc == 0:
        logger.info("Connected to MQTT broker")
        mqtt_connected = True
        # Subscribe to status and registration topics
        client.subscribe(MQTT_TOPIC_STATUS)
        client.subscribe(MQTT_TOPIC_REGISTER)
        logger.info(f"Subscribed to {MQTT_TOPIC_STATUS} and {MQTT_TOPIC_REGISTER}")
    else:
        logger.error(f"Failed to connect to MQTT broker, return code {rc}")
        mqtt_connected = False


def on_mqtt_disconnect(client, userdata, rc, properties=None):
    """Callback when disconnected from MQTT broker"""
    global mqtt_connected
    logger.warning(f"Disconnected from MQTT broker (rc={rc})")
    mqtt_connected = False


def on_mqtt_message(client, userdata, msg):
    """Callback when message received from ESP32"""
    global esp32_status, led_state

    try:
        payload = json.loads(msg.payload.decode())
        topic = msg.topic

        logger.info(f"Received [{topic}]: {payload}")

        # Handle registration messages
        if topic == MQTT_TOPIC_REGISTER:
            logger.info("Processing device registration request")
            register_child_device(payload)
            return

        # Handle status messages (existing logic)
        if topic == MQTT_TOPIC_STATUS:
            # Update ESP32 status
            esp32_status["connected"] = True
            esp32_status["last_seen"] = time.strftime("%Y-%m-%d %H:%M:%S")

            # Update LED state if included
            if "red" in payload:
                led_state["red"] = bool(payload["red"])
            if "yellow" in payload:
                led_state["yellow"] = bool(payload["yellow"])
            if "green" in payload:
                led_state["green"] = bool(payload["green"])

    except json.JSONDecodeError:
        logger.error(f"Invalid JSON received: {msg.payload}")
    except Exception as e:
        logger.error(f"Error processing message: {e}")


def init_mqtt():
    """Initialize MQTT client"""
    global mqtt_client
    try:
        mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        mqtt_client.on_connect = on_mqtt_connect
        mqtt_client.on_disconnect = on_mqtt_disconnect
        mqtt_client.on_message = on_mqtt_message
        
        logger.info(f"Connecting to MQTT broker at {MQTT_BROKER}:{MQTT_PORT}")
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
        mqtt_client.loop_start()
        return True
    except Exception as e:
        logger.error(f"Failed to initialize MQTT: {e}")
        return False


def send_command(command_type: str, red: bool = None, yellow: bool = None, green: bool = None):
    """Send command to ESP32 via MQTT"""
    global led_state
    
    if not mqtt_connected:
        logger.error("MQTT not connected")
        return False
    
    try:
        payload = {"command": command_type}
        
        if command_type == "set":
            # Use provided values or keep current state
            payload["red"] = red if red is not None else led_state["red"]
            payload["yellow"] = yellow if yellow is not None else led_state["yellow"]
            payload["green"] = green if green is not None else led_state["green"]
            
            # Update local state
            led_state["red"] = payload["red"]
            led_state["yellow"] = payload["yellow"]
            led_state["green"] = payload["green"]
            
        elif command_type == "on":
            # Turn all LEDs on
            payload["red"] = True
            payload["yellow"] = True
            payload["green"] = True
            led_state = {"red": True, "yellow": True, "green": True}
            
        elif command_type == "off":
            # Turn all LEDs off
            payload["red"] = False
            payload["yellow"] = False
            payload["green"] = False
            led_state = {"red": False, "yellow": False, "green": False}
        
        message = json.dumps(payload)
        mqtt_client.publish(MQTT_TOPIC_COMMAND, message)
        logger.info(f"Sent command: {message}")
        return True
        
    except Exception as e:
        logger.error(f"Failed to send command: {e}")
        return False


# ============== API Routes ==============

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "mqtt_connected": mqtt_connected,
        "esp32_connected": esp32_status["connected"],
        "esp32_last_seen": esp32_status["last_seen"]
    })


@app.route('/status', methods=['GET'])
def get_status():
    """Get full system status"""
    return jsonify({
        "led_state": led_state,
        "mqtt_connected": mqtt_connected,
        "esp32": esp32_status
    })


@app.route('/led/state', methods=['GET'])
def get_led_state():
    """Get current LED state"""
    return jsonify(led_state)


@app.route('/led/on', methods=['POST'])
def turn_all_on():
    """Turn all LEDs on"""
    success = send_command("on")
    log_command_to_backend("LED_TURN_ON", {"command": "on"}, success)
    if success:
        return jsonify({
            "success": True,
            "message": "All LEDs turned on",
            "state": led_state
        })
    return jsonify({
        "success": False,
        "message": "Failed to send command"
    }), 500


@app.route('/led/off', methods=['POST'])
def turn_all_off():
    """Turn all LEDs off"""
    success = send_command("off")
    log_command_to_backend("LED_TURN_OFF", {"command": "off"}, success)
    if success:
        return jsonify({
            "success": True,
            "message": "All LEDs turned off",
            "state": led_state
        })
    return jsonify({
        "success": False,
        "message": "Failed to send command"
    }), 500


@app.route('/led/set', methods=['POST'])
def set_leds():
    """
    Set individual LED states
    
    Request body:
    {
        "red": true/false,
        "yellow": true/false,
        "green": true/false
    }
    
    All fields are optional - only provided fields will be changed
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({
                "success": False,
                "message": "No JSON data provided"
            }), 400
        
        # Get LED values (None if not provided)
        red = data.get("red")
        yellow = data.get("yellow")
        green = data.get("green")
        
        # Validate types
        for name, value in [("red", red), ("yellow", yellow), ("green", green)]:
            if value is not None and not isinstance(value, bool):
                return jsonify({
                    "success": False,
                    "message": f"'{name}' must be a boolean (true/false)"
                }), 400

        success = send_command("set", red=red, yellow=yellow, green=green)
        log_command_to_backend("LED_SET_COLOR", data, success)
        if success:
            return jsonify({
                "success": True,
                "message": "LED state updated",
                "state": led_state
            })
        return jsonify({
            "success": False,
            "message": "Failed to send command"
        }), 500

    except Exception as e:
        logger.error(f"Error in set_leds: {e}")
        log_command_to_backend("LED_SET_COLOR", data if 'data' in locals() else {}, False)
        return jsonify({
            "success": False,
            "message": str(e)
        }), 500


@app.route('/led/toggle/<led_name>', methods=['POST'])
def toggle_led(led_name):
    """Toggle a specific LED"""
    led_name = led_name.lower()
    
    if led_name not in ["red", "yellow", "green"]:
        return jsonify({
            "success": False,
            "message": f"Unknown LED: {led_name}. Use 'red', 'yellow', or 'green'"
        }), 400
    
    # Toggle the specified LED
    new_state = not led_state[led_name]

    kwargs = {led_name: new_state}
    success = send_command("set", **kwargs)
    log_command_to_backend("LED_SET_COLOR", {"led": led_name, "new_state": new_state}, success)
    if success:
        return jsonify({
            "success": True,
            "message": f"{led_name.capitalize()} LED toggled to {'on' if new_state else 'off'}",
            "state": led_state
        })
    return jsonify({
        "success": False,
        "message": "Failed to send command"
    }), 500


@app.route('/song', methods=['POST'])
def classify_song():
    """
    Classify a song's genre and play matching LED pattern

    Request body:
    {
        "song": "Bohemian Rhapsody by Queen"
    }
    """
    try:
        data = request.get_json()
        if not data or "song" not in data:
            return jsonify({
                "success": False,
                "message": "Missing 'song' field in request body"
            }), 400

        song = data["song"]

        # Classify genre via Ollama
        genre_id = classify_song_genre(song)
        genre_name = GENRE_MAP.get(genre_id, "Unknown")

        # Send pattern command to ESP32
        success = send_pattern_command(genre_id)
        log_command_to_backend("SONG_PATTERN", {"song": song, "genre": genre_name, "patternId": genre_id}, success)

        if success:
            return jsonify({
                "success": True,
                "song": song,
                "genre": genre_name,
                "genreId": genre_id,
                "message": f"Playing {genre_name} pattern for '{song}'"
            })
        return jsonify({
            "success": False,
            "message": "Failed to send pattern command to ESP32"
        }), 500

    except requests.exceptions.ConnectionError:
        return jsonify({
            "success": False,
            "message": "Could not reach Ollama - is it running?"
        }), 503
    except requests.exceptions.Timeout:
        return jsonify({
            "success": False,
            "message": "Ollama request timed out"
        }), 504
    except Exception as e:
        logger.error(f"Error in /song endpoint: {e}")
        return jsonify({
            "success": False,
            "message": str(e)
        }), 500


@app.route('/music/start', methods=['POST'])
def music_start():
    """Start real-time audio-reactive LED mode"""
    global audio_active

    if audio_active:
        return jsonify({
            "success": False,
            "message": "Audio listening is already active",
            "device": audio_device_info
        }), 409

    try:
        # Stop any running pattern first
        if mqtt_connected and mqtt_client:
            mqtt_client.publish(MQTT_TOPIC_COMMAND, json.dumps({"command": "off", "red": False, "yellow": False, "green": False}))

        device_info = start_audio_listening()
        log_command_to_backend("MUSIC_START", {"device": device_info}, True)

        return jsonify({
            "success": True,
            "message": "Audio-reactive LED mode started",
            "device": device_info
        })

    except Exception as e:
        logger.error(f"Error starting music mode: {e}")
        return jsonify({
            "success": False,
            "message": f"Failed to start audio: {str(e)}"
        }), 500


@app.route('/music/stop', methods=['POST'])
def music_stop():
    """Stop real-time audio-reactive LED mode"""
    if not audio_active:
        return jsonify({
            "success": False,
            "message": "Audio listening is not active"
        }), 400

    stop_audio_listening()
    log_command_to_backend("MUSIC_STOP", {}, True)

    return jsonify({
        "success": True,
        "message": "Audio-reactive LED mode stopped"
    })


@app.route('/music/status', methods=['GET'])
def music_status():
    """Get audio listening status"""
    return jsonify({
        "active": audio_active,
        "device": audio_device_info
    })


# ============== Main ==============

if __name__ == '__main__':
    logger.info("Starting SmartAmbient Pi Hub (3-LED Controller)")
    
    # Initialize MQTT
    if not init_mqtt():
        logger.error("Failed to initialize MQTT, exiting")
        exit(1)
    
    # Give MQTT time to connect
    time.sleep(1)
    
    # Start Flask
    flask_config = config.get("flask", {})
    app.run(
        host=flask_config.get("host", "0.0.0.0"),
        port=flask_config.get("port", 5000),
        debug=flask_config.get("debug", False)
    )
