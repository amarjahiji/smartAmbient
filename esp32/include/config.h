/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * Configuration Header
 */

#ifndef CONFIG_H
#define CONFIG_H

// ============================================
// WiFi Configuration
// ============================================
#define WIFI_SSID "Amar Jahiji\xe2\x80\x99s iPhone"
#define WIFI_PASSWORD "12345678"

// ============================================
// MQTT Configuration
// ============================================
#define MQTT_BROKER_IP "172.20.10.5"  // Raspberry Pi IP address
#define MQTT_BROKER_PORT 1883
#define MQTT_CLIENT_ID "esp32-3led"

// MQTT Topics
#define MQTT_TOPIC_COMMAND "smartambient/led/command"
#define MQTT_TOPIC_STATUS "smartambient/led/status"
#define MQTT_TOPIC_REGISTER "smartambient/device/register"

// ============================================
// LED GPIO Configuration (3 Individual LEDs)
// ============================================
#define LED_RED_PIN 18      // Red LED GPIO pin
#define LED_YELLOW_PIN 21   // Yellow LED GPIO pin
#define LED_GREEN_PIN 19    // Green LED GPIO pin

// ============================================
// Device Information
// ============================================
#define DEVICE_NAME "SmartAmbient-3LED"
#define DEVICE_TYPE "ESP32"
#define FIRMWARE_VERSION "2.0.0"
#define DEVICE_CAPABILITIES "led_control,mqtt_client"

// ============================================
// Timing Configuration
// ============================================
#define WIFI_CONNECT_TIMEOUT 30000    // 30 seconds
#define MQTT_RECONNECT_DELAY 5000     // 5 seconds
#define HEARTBEAT_INTERVAL 60000      // 1 minute

#endif // CONFIG_H
