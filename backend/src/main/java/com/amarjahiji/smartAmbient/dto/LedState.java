package com.amarjahiji.smartAmbient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the state of the 3 individual LEDs (Red, Yellow, Green)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedState {
    private boolean red;
    private boolean yellow;
    private boolean green;
}
