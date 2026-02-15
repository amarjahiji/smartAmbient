/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * MQTT Handler Header
 */

#ifndef MQTT_HANDLER_H
#define MQTT_HANDLER_H

#include <Arduino.h>
#include <WiFiClient.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "config.h"
#include "led_controller.h"

class MqttHandler {
public:
    MqttHandler(WiFiClient& wifiClient, LedController& ledController);
    
    void begin();
    void loop();
    bool isConnected();
    void sendStatus();

private:
    PubSubClient mqttClient;
    LedController& leds;
    unsigned long lastReconnectAttempt;
    unsigned long lastHeartbeat;
    bool registrationSent;

    void reconnect();
    void handleMessage(char* topic, byte* payload, unsigned int length);
    void sendRegistration();
    bool isRegistered();
    void markAsRegistered();
    static MqttHandler* instance;
    static void staticCallback(char* topic, byte* payload, unsigned int length);
};

#endif // MQTT_HANDLER_H
