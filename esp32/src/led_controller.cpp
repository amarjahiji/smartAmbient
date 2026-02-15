/**
 * SmartAmbient ESP32 - Simplified 3-LED Controller
 * LED Controller Implementation
 */

#include "led_controller.h"

// ============== Pattern Definitions ==============

// Pattern 1: Rock - Aggressive red flashing with yellow bursts
static const PatternStep pattern1_steps[] = {
    {true,  false, false, 150},   // Red ON
    {false, false, false, 100},   // All OFF
    {true,  true,  false, 150},   // Red+Yellow ON
    {false, false, false, 100}    // All OFF
};
static const LedPattern pattern1 = {1, pattern1_steps, 4};

// Pattern 2: Pop - Upbeat cycling through all colors
static const PatternStep pattern2_steps[] = {
    {true,  false, false, 300},   // Red
    {false, true,  false, 300},   // Yellow
    {false, false, true,  300},   // Green
    {true,  true,  false, 200},   // Red+Yellow
    {false, true,  true,  200},   // Yellow+Green
    {true,  true,  true,  300}    // All ON
};
static const LedPattern pattern2 = {2, pattern2_steps, 6};

// Pattern 3: Jazz - Slow warm red/yellow alternating
static const PatternStep pattern3_steps[] = {
    {true,  false, false, 800},   // Red
    {true,  true,  false, 600},   // Red+Yellow
    {false, true,  false, 800},   // Yellow
    {false, true,  false, 400},   // Yellow hold
    {false, false, false, 400}    // All OFF
};
static const LedPattern pattern3 = {3, pattern3_steps, 5};

// Pattern 4: Classical - Gentle sequential sweep
static const PatternStep pattern4_steps[] = {
    {true,  false, false, 500},   // Red
    {true,  true,  false, 400},   // Red+Yellow
    {false, true,  false, 500},   // Yellow
    {false, true,  true,  400},   // Yellow+Green
    {false, false, true,  500},   // Green
    {false, false, false, 300}    // All OFF
};
static const LedPattern pattern4 = {4, pattern4_steps, 6};

// Pattern 5: Electronic - Fast strobing all colors
static const PatternStep pattern5_steps[] = {
    {true,  true,  true,  80},    // All ON
    {false, false, false, 50},    // All OFF
    {true,  false, true,  100},   // Red+Green
    {false, false, false, 50},    // All OFF
    {false, true,  false, 100},   // Yellow
    {false, false, false, 50}     // All OFF
};
static const LedPattern pattern5 = {5, pattern5_steps, 6};

// Pattern 6: Hip-Hop - Rhythmic beats with pauses
static const PatternStep pattern6_steps[] = {
    {true,  true,  false, 200},   // Red+Yellow beat
    {false, false, false, 300},   // Pause
    {true,  true,  false, 200},   // Red+Yellow beat
    {false, false, false, 300},   // Pause
    {true,  true,  true,  150},   // All ON accent
    {false, false, false, 500}    // Long pause
};
static const LedPattern pattern6 = {6, pattern6_steps, 6};

// ============== Implementation ==============

LedController::LedController()
    : redState(false), yellowState(false), greenState(false),
      patternActive(false), currentPatternId(0), currentStepIndex(0),
      stepStartTime(0), activePattern(nullptr) {
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

// ============== Pattern Engine ==============

const LedPattern* LedController::getPatternById(uint8_t id) {
    switch (id) {
        case 1: return &pattern1;
        case 2: return &pattern2;
        case 3: return &pattern3;
        case 4: return &pattern4;
        case 5: return &pattern5;
        case 6: return &pattern6;
        default: return nullptr;
    }
}

void LedController::startPattern(uint8_t patternId) {
    activePattern = getPatternById(patternId);

    if (activePattern == nullptr) {
        Serial.printf("Invalid pattern ID: %d\n", patternId);
        return;
    }

    patternActive = true;
    currentPatternId = patternId;
    currentStepIndex = 0;
    stepStartTime = millis();

    // Execute first step immediately
    executePatternStep(activePattern->steps[0]);

    Serial.printf("Pattern %d started (%d steps)\n", patternId, activePattern->numSteps);
}

void LedController::stopPattern() {
    if (patternActive) {
        Serial.printf("Pattern %d stopped\n", currentPatternId);
    }
    patternActive = false;
    activePattern = nullptr;
}

void LedController::updatePattern() {
    if (!patternActive || activePattern == nullptr) {
        return;
    }

    unsigned long currentTime = millis();
    const PatternStep& currentStep = activePattern->steps[currentStepIndex];

    // Check if enough time has elapsed for current step
    if (currentTime - stepStartTime >= currentStep.duration_ms) {
        // Advance to next step
        currentStepIndex++;

        // Loop back to start if at end
        if (currentStepIndex >= activePattern->numSteps) {
            currentStepIndex = 0;
        }

        // Execute new step
        executePatternStep(activePattern->steps[currentStepIndex]);
        stepStartTime = currentTime;
    }
}

void LedController::executePatternStep(const PatternStep& step) {
    setAll(step.red, step.yellow, step.green);
}
