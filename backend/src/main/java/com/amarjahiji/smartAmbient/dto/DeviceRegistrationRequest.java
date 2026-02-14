package com.amarjahiji.smartAmbient.dto;

import com.amarjahiji.smartAmbient.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {
    
    @NotBlank(message = "Device name is required")
    @Size(max = 100)
    private String deviceName;
    
    @NotNull(message = "Device type is required")
    private DeviceType deviceType;
    
    @NotBlank(message = "MAC address is required")
    @Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$", 
             message = "Invalid MAC address format")
    private String macAddress;
    
    @Size(max = 45)
    private String ipAddress;
    
    @Size(max = 50)
    private String firmwareVersion;
    
    @Size(max = 500)
    private String capabilities;
    
    // Optional: MAC address of parent device (for ESP32 registering via Pi)
    private String parentMacAddress;
}
