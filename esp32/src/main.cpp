/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * Main Application
 * 
 * Controls 3 individual LEDs (Red, Yellow, Green) via MQTT
 */

#include <Arduino.h>
#include <WiFi.h>

#include "config.h"
#include "led_controller.h"
#include "mqtt_handler.h"

// WiFi client for MQTT
WiFiClient wifiClient;

// LED controller
LedController ledController;

// MQTT handler
MqttHandler mqttHandler(wifiClient, ledController);

// Function declarations
void setupWiFi();
void printStartupBanner();

void setup() {
    // Initialize serial
    Serial.begin(115200);
    delay(1000);
    
    printStartupBanner();
    
    // Initialize LED controller
    Serial.println("Initializing LEDs...");
    ledController.begin();
    
    // LED test sequence
    Serial.println("Running LED test...");
    ledController.setRed(true);
    delay(300);
    ledController.setRed(false);
    
    ledController.setYellow(true);
    delay(300);
    ledController.setYellow(false);
    
    ledController.setGreen(true);
    delay(300);
    ledController.setGreen(false);
    
    Serial.println("LED test complete!");
    
    // Connect to WiFi
    setupWiFi();
    
    // Initialize MQTT
    mqttHandler.begin();
    
    Serial.println("\n========================================");
    Serial.println("Setup complete! Waiting for commands...");
    Serial.println("========================================\n");
}

void loop() {
    // Handle MQTT
    mqttHandler.loop();

    // Update LED pattern animation
    ledController.updatePattern();

    // Small delay
    delay(10);
}

void setupWiFi() {
    Serial.printf("\nConnecting to WiFi: %s", WIFI_SSID);
    
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    unsigned long startTime = millis();
    while (WiFi.status() != WL_CONNECTED) {
        if (millis() - startTime > WIFI_CONNECT_TIMEOUT) {
            Serial.println("\nWiFi connection timeout! Restarting...");
            ESP.restart();
        }
        delay(500);
        Serial.print(".");
    }
    
    Serial.println(" Connected!");
    Serial.printf("IP Address: %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("MAC Address: %s\n", WiFi.macAddress().c_str());
}

void printStartupBanner() {
    Serial.println();
    Serial.println("========================================");
    Serial.println("   SmartAmbient 3-LED Controller");
    Serial.printf("   Firmware: %s\n", FIRMWARE_VERSION);
    Serial.println("========================================");
    Serial.println();
    Serial.println("Hardware: 3 Individual LEDs");
    Serial.printf("  Red:    GPIO %d\n", LED_RED_PIN);
    Serial.printf("  Yellow: GPIO %d\n", LED_YELLOW_PIN);
    Serial.printf("  Green:  GPIO %d\n", LED_GREEN_PIN);
    Serial.println();
}
