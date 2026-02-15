/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * LED Controller Header
 */

#ifndef LED_CONTROLLER_H
#define LED_CONTROLLER_H

#include <Arduino.h>
#include "config.h"

struct PatternStep {
    bool red;
    bool yellow;
    bool green;
    unsigned int duration_ms;
};

struct LedPattern {
    uint8_t id;
    const PatternStep* steps;
    uint8_t numSteps;
};

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

    // Pattern control
    void startPattern(uint8_t patternId);
    void stopPattern();
    void updatePattern();
    bool isPatternActive() const { return patternActive; }

    // Get current states
    bool isRedOn() const { return redState; }
    bool isYellowOn() const { return yellowState; }
    bool isGreenOn() const { return greenState; }

private:
    bool redState;
    bool yellowState;
    bool greenState;

    // Pattern state
    bool patternActive;
    uint8_t currentPatternId;
    uint8_t currentStepIndex;
    unsigned long stepStartTime;
    const LedPattern* activePattern;

    void updateLed(uint8_t pin, bool state);
    void executePatternStep(const PatternStep& step);
    const LedPattern* getPatternById(uint8_t id);
};

#endif // LED_CONTROLLER_H
