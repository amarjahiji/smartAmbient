package com.amarjahiji.smartAmbient.service;

import com.amarjahiji.smartAmbient.dto.*;
import com.amarjahiji.smartAmbient.entity.*;
import com.amarjahiji.smartAmbient.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {
    
    private final DeviceRepository deviceRepository;
    private final CommandRepository commandRepository;
    private final UserRepository userRepository;
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    @Override
    @Transactional
    public DeviceResponse registerDevice(DeviceRegistrationRequest request) {
        if (deviceRepository.existsByMacAddress(request.getMacAddress())) {
            Device existingDevice = deviceRepository.findByMacAddress(request.getMacAddress())
                    .orElseThrow();
            existingDevice.setDeviceName(request.getDeviceName());
            existingDevice.setIpAddress(request.getIpAddress());
            existingDevice.setFirmwareVersion(request.getFirmwareVersion());
            existingDevice.setCapabilities(request.getCapabilities());
            existingDevice.setIsOnline(true);
            existingDevice.setLastSeen(LocalDateTime.now());
            
            Device updated = deviceRepository.save(existingDevice);
            return mapToResponse(updated);
        }
        Device device = Device.builder()
                .deviceName(request.getDeviceName())
                .deviceType(request.getDeviceType())
                .macAddress(request.getMacAddress())
                .ipAddress(request.getIpAddress())
                .productId(request.getProductId())
                .firmwareVersion(request.getFirmwareVersion())
                .capabilities(request.getCapabilities())
                .apiKey(generateApiKey())
                .isOnline(true)
                .lastSeen(LocalDateTime.now())
                .build();
        
        Device saved = deviceRepository.save(device);
        return mapToResponse(saved);
    }
    
    @Override
    @Transactional
    public DeviceResponse registerChildDevice(DeviceRegistrationRequest request, String parentApiKey) {
        Device parentDevice = deviceRepository.findByApiKey(parentApiKey)
                .orElseThrow(() -> new RuntimeException("Invalid parent device API key"));
        if (deviceRepository.existsByMacAddress(request.getMacAddress())) {
            Device existingDevice = deviceRepository.findByMacAddress(request.getMacAddress())
                    .orElseThrow();
            existingDevice.setDeviceName(request.getDeviceName());
            existingDevice.setIpAddress(request.getIpAddress());
            existingDevice.setFirmwareVersion(request.getFirmwareVersion());
            existingDevice.setCapabilities(request.getCapabilities());
            existingDevice.setParentDevice(parentDevice);
            existingDevice.setOwner(parentDevice.getOwner());
            existingDevice.setIsOnline(true);
            existingDevice.setLastSeen(LocalDateTime.now());
            
            Device updated = deviceRepository.save(existingDevice);
            return mapToResponse(updated);
        }
        
        Device device = Device.builder()
                .deviceName(request.getDeviceName())
                .deviceType(request.getDeviceType())
                .macAddress(request.getMacAddress())
                .ipAddress(request.getIpAddress())
                .firmwareVersion(request.getFirmwareVersion())
                .capabilities(request.getCapabilities())
                .apiKey(generateApiKey())
                .parentDevice(parentDevice)
                .owner(parentDevice.getOwner())
                .isOnline(true)
                .lastSeen(LocalDateTime.now())
                .build();
        
        Device saved = deviceRepository.save(device);
        return mapToResponse(saved);
    }
    
    @Override
    @Transactional
    public DeviceResponse claimDevice(String productId, String userId) {
        Device device = deviceRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (device.getOwner() != null) {
            throw new RuntimeException("Device already has an owner");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        device.setOwner(user);

        List<Device> childDevices = deviceRepository.findByParentDeviceId(device.getId());
        for (Device child : childDevices) {
            child.setOwner(user);
            deviceRepository.save(child);
        }

        Device saved = deviceRepository.save(device);
        return mapToResponse(saved);
    }
    
    @Override
    public DeviceResponse getDeviceById(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        return mapToResponse(device);
    }
    
    @Override
    public DeviceResponse getDeviceByMacAddress(String macAddress) {
        Device device = deviceRepository.findByMacAddress(macAddress)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        return mapToResponse(device);
    }
    
    @Override
    public Device getDeviceByApiKey(String apiKey) {
        return deviceRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new RuntimeException("Invalid API key"));
    }
    
    @Override
    public List<DeviceResponse> getDevicesByOwner(String ownerId) {
        return deviceRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<DeviceResponse> getChildDevices(String parentDeviceId) {
        return deviceRepository.findByParentDeviceId(parentDeviceId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateDeviceStatus(String deviceId, boolean isOnline) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        device.setIsOnline(isOnline);
        device.setLastSeen(LocalDateTime.now());
        deviceRepository.save(device);
    }
    
    @Override
    @Transactional
    public CommandResponse logCommand(CommandLogRequest request) {
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new RuntimeException("Device not found"));
        
        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId()).orElse(null);
        }
        
        CommandStatus status = CommandStatus.COMPLETED;
        if (request.getStatus() != null) {
            try {
                status = CommandStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException _) {
            }
        }
        
        CommandType commandType = CommandType.CUSTOM;
        if (request.getCommandType() != null) {
            try {
                commandType = CommandType.valueOf(request.getCommandType().toUpperCase());
            } catch (IllegalArgumentException _) {
            }
        }
        
        Command command = Command.builder()
                .device(device)
                .user(user)
                .commandType(commandType)
                .payload(request.getPayload())
                .status(status)
                .response(request.getResponse())
                .executedAt(LocalDateTime.now())
                .build();
        
        Command saved = commandRepository.save(command);
        return mapToCommandResponse(saved);
    }
    
    @Override
    public List<CommandResponse> getCommandHistory(String deviceId) {
        return commandRepository.findTop10ByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(this::mapToCommandResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean validateApiKey(String apiKey) {
        return deviceRepository.existsByApiKey(apiKey);
    }
    
    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private DeviceResponse mapToResponse(Device device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .macAddress(device.getMacAddress())
                .ipAddress(device.getIpAddress())
                .apiKey(device.getApiKey())
                .productId(device.getProductId())
                .ownerId(device.getOwner() != null ? device.getOwner().getId() : null)
                .ownerUsername(device.getOwner() != null ? device.getOwner().getUsername() : null)
                .parentDeviceId(device.getParentDevice() != null ? device.getParentDevice().getId() : null)
                .isOnline(device.getIsOnline())
                .lastSeen(device.getLastSeen())
                .firmwareVersion(device.getFirmwareVersion())
                .capabilities(device.getCapabilities())
                .registeredAt(device.getRegisteredAt())
                .build();
    }
    
    private CommandResponse mapToCommandResponse(Command command) {
        return CommandResponse.builder()
                .id(command.getId())
                .deviceId(command.getDevice().getId())
                .userId(command.getUser() != null ? command.getUser().getId() : null)
                .commandType(command.getCommandType())
                .payload(command.getPayload())
                .status(command.getStatus())
                .response(command.getResponse())
                .createdAt(command.getCreatedAt())
                .executedAt(command.getExecutedAt())
                .build();
    }
}
