package com.amarjahiji.smartAmbient.controller;

import com.amarjahiji.smartAmbient.dto.*;
import com.amarjahiji.smartAmbient.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {
    
    private final DeviceService deviceService;

    @PostMapping("/register")
    public ResponseEntity<DeviceResponse> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceResponse response = deviceService.registerDevice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    

    @PostMapping("/register/child")
    public ResponseEntity<DeviceResponse> registerChildDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            @RequestHeader("X-Device-Api-Key") String parentApiKey) {
        DeviceResponse response = deviceService.registerChildDevice(request, parentApiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    

    @PostMapping("/claim")
    public ResponseEntity<DeviceResponse> claimDevice(
            @Valid @RequestBody ClaimDeviceRequest request,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DeviceResponse response = deviceService.claimDevice(request.getProductId(), userId);
        return ResponseEntity.ok(response);
    }
    

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable String deviceId) {
        DeviceResponse response = deviceService.getDeviceById(deviceId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/mac/{macAddress}")
    public ResponseEntity<DeviceResponse> getDeviceByMac(@PathVariable String macAddress) {
        DeviceResponse response = deviceService.getDeviceByMacAddress(macAddress);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-devices")
    public ResponseEntity<List<DeviceResponse>> getMyDevices(HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<DeviceResponse> devices = deviceService.getDevicesByOwner(userId);
        return ResponseEntity.ok(devices);
    }
    

    @GetMapping("/{deviceId}/children")
    public ResponseEntity<List<DeviceResponse>> getChildDevices(@PathVariable String deviceId) {
        List<DeviceResponse> children = deviceService.getChildDevices(deviceId);
        return ResponseEntity.ok(children);
    }

    @PostMapping("/commands/log")
    public ResponseEntity<CommandResponse> logCommand(
            @Valid @RequestBody CommandLogRequest request,
            @RequestHeader("X-Device-Api-Key") String apiKey) {
        if (!deviceService.validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }CommandResponse response = deviceService.logCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{deviceId}/commands")
    public ResponseEntity<List<CommandResponse>> getCommandHistory(@PathVariable String deviceId) {
        List<CommandResponse> history = deviceService.getCommandHistory(deviceId);
        return ResponseEntity.ok(history);
    }
    

    @PutMapping("/{deviceId}/status")
    public ResponseEntity<Void> updateDeviceStatus(
            @PathVariable String deviceId,
            @RequestParam boolean online,
            @RequestHeader("X-Device-Api-Key") String apiKey) {
        if (!deviceService.validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        deviceService.updateDeviceStatus(deviceId, online);
        return ResponseEntity.ok().build();
    }
}
