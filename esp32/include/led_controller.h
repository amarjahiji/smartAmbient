/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * LED Controller Header
 */

#ifndef LED_CONTROLLER_H
#define LED_CONTROLLER_H

#include <Arduino.h>
#include "config.h"

class LedController {
public:
    LedController();
    
    void begin();
    
    // Set all LEDs at once
    void setAll(bool red, bool yellow, bool green);
    
    // Individual LED control
    void setRed(bool on);
    void setYellow(bool on);
    void setGreen(bool on);
    
    // Convenience methods
    void allOn();
    void allOff();
    
    // Get current states
    bool isRedOn() const { return redState; }
    bool isYellowOn() const { return yellowState; }
    bool isGreenOn() const { return greenState; }

private:
    bool redState;
    bool yellowState;
    bool greenState;
    
    void updateLed(uint8_t pin, bool state);
};

#endif // LED_CONTROLLER_H
