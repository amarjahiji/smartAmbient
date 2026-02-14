package com.amarjahiji.smartAmbient.dto;

import com.amarjahiji.smartAmbient.entity.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private String id;
    private String deviceName;
    private DeviceType deviceType;
    private String macAddress;
    private String ipAddress;
    private String apiKey;
    private String ownerId;
    private String ownerUsername;
    private String parentDeviceId;
    private Boolean isOnline;
    private LocalDateTime lastSeen;
    private String firmwareVersion;
    private String capabilities;
    private LocalDateTime registeredAt;
}
