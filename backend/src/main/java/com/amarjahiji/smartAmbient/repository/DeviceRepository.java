package com.amarjahiji.smartAmbient.repository;

import com.amarjahiji.smartAmbient.entity.Device;
import com.amarjahiji.smartAmbient.entity.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    
    Optional<Device> findByMacAddress(String macAddress);
    
    Optional<Device> findByApiKey(String apiKey);
    
    List<Device> findByOwnerId(String ownerId);
    
    List<Device> findByParentDeviceId(String parentDeviceId);
    
    List<Device> findByDeviceType(DeviceType deviceType);
    
    List<Device> findByOwnerIdAndDeviceType(String ownerId, DeviceType deviceType);
    
    boolean existsByMacAddress(String macAddress);
    
    boolean existsByApiKey(String apiKey);
}
