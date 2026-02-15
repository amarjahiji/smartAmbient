/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * MQTT Handler Implementation
 */

#include "mqtt_handler.h"
#include <Preferences.h>

// Static instance for callback
MqttHandler* MqttHandler::instance = nullptr;

MqttHandler::MqttHandler(WiFiClient& wifiClient, LedController& ledController)
    : mqttClient(wifiClient), leds(ledController), lastReconnectAttempt(0), lastHeartbeat(0), registrationSent(false) {
    instance = this;
}

void MqttHandler::begin() {
    mqttClient.setServer(MQTT_BROKER_IP, MQTT_BROKER_PORT);
    mqttClient.setCallback(staticCallback);
    mqttClient.setBufferSize(512);
    
    Serial.printf("MQTT configured for %s:%d\n", MQTT_BROKER_IP, MQTT_BROKER_PORT);
}

void MqttHandler::loop() {
    if (!mqttClient.connected()) {
        unsigned long now = millis();
        if (now - lastReconnectAttempt > MQTT_RECONNECT_DELAY) {
            lastReconnectAttempt = now;
            reconnect();
        }
    } else {
        mqttClient.loop();
        
        unsigned long now = millis();
        if (now - lastHeartbeat > HEARTBEAT_INTERVAL) {
            lastHeartbeat = now;
            sendStatus();
        }
    }
}

bool MqttHandler::isConnected() {
    return mqttClient.connected();
}

void MqttHandler::reconnect() {
    Serial.print("Connecting to MQTT broker...");

    if (mqttClient.connect(MQTT_CLIENT_ID)) {
        Serial.println(" connected!");

        // Subscribe to command topic
        mqttClient.subscribe(MQTT_TOPIC_COMMAND);
        Serial.printf("Subscribed to: %s\n", MQTT_TOPIC_COMMAND);

        // Send registration if not already registered
        sendRegistration();

        // Send initial status
        sendStatus();
    } else {
        Serial.printf(" failed (rc=%d)\n", mqttClient.state());
    }
}

void MqttHandler::staticCallback(char* topic, byte* payload, unsigned int length) {
    if (instance) {
        instance->handleMessage(topic, payload, length);
    }
}

void MqttHandler::handleMessage(char* topic, byte* payload, unsigned int length) {
    // Null-terminate the payload
    char message[length + 1];
    memcpy(message, payload, length);
    message[length] = '\0';
    
    Serial.printf("Received [%s]: %s\n", topic, message);
    
    // Parse JSON
    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, message);
    
    if (error) {
        Serial.printf("JSON parse error: %s\n", error.c_str());
        return;
    }
    
    // Get command type
    const char* command = doc["command"] | "";
    
    if (strcmp(command, "on") == 0) {
        leds.stopPattern();
        leds.allOn();
        Serial.println("Command: All LEDs ON");

    } else if (strcmp(command, "off") == 0) {
        leds.stopPattern();
        leds.allOff();
        Serial.println("Command: All LEDs OFF");

    } else if (strcmp(command, "set") == 0) {
        leds.stopPattern();
        bool red = doc["red"] | leds.isRedOn();
        bool yellow = doc["yellow"] | leds.isYellowOn();
        bool green = doc["green"] | leds.isGreenOn();

        leds.setAll(red, yellow, green);
        Serial.printf("Command: Set LEDs - R:%d Y:%d G:%d\n", red, yellow, green);

    } else if (strcmp(command, "pattern") == 0) {
        uint8_t patternId = doc["patternId"] | 0;
        leds.stopPattern();
        leds.startPattern(patternId);
        Serial.printf("Command: Start pattern %d\n", patternId);

    } else {
        Serial.printf("Unknown command: %s\n", command);
        return;
    }
    
    // Send updated status
    sendStatus();
}

void MqttHandler::sendStatus() {
    JsonDocument doc;

    doc["device"] = DEVICE_NAME;
    doc["version"] = FIRMWARE_VERSION;
    doc["red"] = leds.isRedOn();
    doc["yellow"] = leds.isYellowOn();
    doc["green"] = leds.isGreenOn();
    doc["uptime"] = millis() / 1000;

    char buffer[256];
    serializeJson(doc, buffer);

    mqttClient.publish(MQTT_TOPIC_STATUS, buffer);
    Serial.printf("Status sent: %s\n", buffer);
}

bool MqttHandler::isRegistered() {
    Preferences preferences;
    preferences.begin("smartambient", true);  // read-only
    bool registered = preferences.getBool("is_registered", false);
    preferences.end();
    return registered;
}

void MqttHandler::markAsRegistered() {
    Preferences preferences;
    preferences.begin("smartambient", false);  // read-write
    preferences.putBool("is_registered", true);
    preferences.end();
    Serial.println("Device marked as registered in NVS");
}

void MqttHandler::sendRegistration() {
    if (registrationSent || isRegistered()) {
        Serial.println("Registration already sent or device registered, skipping");
        return;
    }

    JsonDocument doc;
    doc["deviceName"] = DEVICE_NAME;
    doc["deviceType"] = DEVICE_TYPE;
    doc["macAddress"] = WiFi.macAddress();
    doc["ipAddress"] = WiFi.localIP().toString();
    doc["firmwareVersion"] = FIRMWARE_VERSION;
    doc["capabilities"] = DEVICE_CAPABILITIES;

    char buffer[512];
    serializeJson(doc, buffer);

    bool published = mqttClient.publish(MQTT_TOPIC_REGISTER, buffer);
    if (published) {
        Serial.printf("Registration sent: %s\n", buffer);
        registrationSent = true;
        // Mark as registered immediately (fire-and-forget approach)
        markAsRegistered();
    } else {
        Serial.println("Failed to publish registration message");
    }
}
