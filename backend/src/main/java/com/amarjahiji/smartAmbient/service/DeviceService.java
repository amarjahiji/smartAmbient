package com.amarjahiji.smartAmbient.service;

import com.amarjahiji.smartAmbient.dto.*;
import com.amarjahiji.smartAmbient.entity.Device;

import java.util.List;

public interface DeviceService {
    

    DeviceResponse registerDevice(DeviceRegistrationRequest request);
    
    /**
     * Register a child device (ESP32 via Pi)
     */
    DeviceResponse registerChildDevice(DeviceRegistrationRequest request, String parentApiKey);
    
    /**
     * Claim ownership of a device by a user
     */
    DeviceResponse claimDevice(String productId, String userId);
    
    /**
     * Get device by ID
     */
    DeviceResponse getDeviceById(String deviceId);
    
    /**
     * Get device by MAC address
     */
    DeviceResponse getDeviceByMacAddress(String macAddress);
    
    /**
     * Get device by API key
     */
    Device getDeviceByApiKey(String apiKey);
    
    /**
     * Get all devices owned by a user
     */
    List<DeviceResponse> getDevicesByOwner(String ownerId);
    
    /**
     * Get all child devices of a parent device
     */
    List<DeviceResponse> getChildDevices(String parentDeviceId);
    
    /**
     * Update device online status
     */
    void updateDeviceStatus(String deviceId, boolean isOnline);
    
    /**
     * Log a command executed on a device
     */
    CommandResponse logCommand(CommandLogRequest request);
    
    /**
     * Get command history for a device
     */
    List<CommandResponse> getCommandHistory(String deviceId);
    

    boolean validateApiKey(String apiKey);
}
