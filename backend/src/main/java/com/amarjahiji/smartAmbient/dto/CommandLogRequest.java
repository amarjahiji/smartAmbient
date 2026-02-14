package com.amarjahiji.smartAmbient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandLogRequest {
    
    @NotNull(message = "Device ID is required")
    private String deviceId;
    
    private String userId;
    
    @NotBlank(message = "Command type is required")
    private String commandType;
    
    private String payload;
    
    private String response;
    
    private String status;
}
