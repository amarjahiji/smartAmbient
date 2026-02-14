"""
SmartAmbient Pi Hub - Simplified 3-LED Controller
Controls 3 individual LEDs (Red, Yellow, Green) via ESP32
"""

import json
import logging
import os
import threading
import time
from pathlib import Path

import paho.mqtt.client as mqtt
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

# MQTT settings
MQTT_BROKER = config.get("mqtt", {}).get("broker", "localhost")
MQTT_PORT = config.get("mqtt", {}).get("port", 1883)
MQTT_TOPIC_COMMAND = config.get("mqtt", {}).get("topic_command", "smartambient/led/command")
MQTT_TOPIC_STATUS = config.get("mqtt", {}).get("topic_status", "smartambient/led/status")

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

# MQTT client
mqtt_client = None
mqtt_connected = False


def on_mqtt_connect(client, userdata, flags, rc, properties=None):
    """Callback when connected to MQTT broker"""
    global mqtt_connected
    if rc == 0:
        logger.info("Connected to MQTT broker")
        mqtt_connected = True
        # Subscribe to status topic
        client.subscribe(MQTT_TOPIC_STATUS)
        logger.info(f"Subscribed to {MQTT_TOPIC_STATUS}")
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
        logger.info(f"Received from ESP32: {payload}")
        
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
    if send_command("on"):
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
    if send_command("off"):
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
        
        if send_command("set", red=red, yellow=yellow, green=green):
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
    if send_command("set", **kwargs):
        return jsonify({
            "success": True,
            "message": f"{led_name.capitalize()} LED toggled to {'on' if new_state else 'off'}",
            "state": led_state
        })
    return jsonify({
        "success": False,
        "message": "Failed to send command"
    }), 500


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
