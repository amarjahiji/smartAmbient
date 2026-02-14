package com.amarjahiji.smartAmbient.entity;

public enum CommandType {
    // LED Control Commands
    LED_SET_COLOR,
    LED_SET_BRIGHTNESS,
    LED_SET_PATTERN,
    LED_TURN_ON,
    LED_TURN_OFF,
    
    // Device Commands
    DEVICE_STATUS,
    DEVICE_RESTART,
    DEVICE_UPDATE_FIRMWARE,
    
    // Custom
    CUSTOM
}
