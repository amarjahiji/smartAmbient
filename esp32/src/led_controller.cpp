/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * LED Controller Implementation
 */

#include "led_controller.h"

LedController::LedController() 
    : redState(false), yellowState(false), greenState(false) {
}

void LedController::begin() {
    // Configure GPIO pins as outputs
    pinMode(LED_RED_PIN, OUTPUT);
    pinMode(LED_YELLOW_PIN, OUTPUT);
    pinMode(LED_GREEN_PIN, OUTPUT);
    
    // Start with all LEDs off
    allOff();
    
    Serial.println("LED Controller initialized");
    Serial.printf("  Red LED: GPIO %d\n", LED_RED_PIN);
    Serial.printf("  Yellow LED: GPIO %d\n", LED_YELLOW_PIN);
    Serial.printf("  Green LED: GPIO %d\n", LED_GREEN_PIN);
}

void LedController::setAll(bool red, bool yellow, bool green) {
    setRed(red);
    setYellow(yellow);
    setGreen(green);
}

void LedController::setRed(bool on) {
    redState = on;
    updateLed(LED_RED_PIN, on);
}

void LedController::setYellow(bool on) {
    yellowState = on;
    updateLed(LED_YELLOW_PIN, on);
}

void LedController::setGreen(bool on) {
    greenState = on;
    updateLed(LED_GREEN_PIN, on);
}

void LedController::allOn() {
    setAll(true, true, true);
}

void LedController::allOff() {
    setAll(false, false, false);
}

void LedController::updateLed(uint8_t pin, bool state) {
    digitalWrite(pin, state ? HIGH : LOW);
}
