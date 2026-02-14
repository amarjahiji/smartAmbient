package com.amarjahiji.smartAmbient.repository;

import com.amarjahiji.smartAmbient.entity.Command;
import com.amarjahiji.smartAmbient.entity.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CommandRepository extends JpaRepository<Command, String> {
    
    List<Command> findByDeviceId(String deviceId);
    
    List<Command> findByDeviceIdAndStatus(String deviceId, CommandStatus status);
    
    List<Command> findByUserId(String userId);
    
    List<Command> findByDeviceIdOrderByCreatedAtDesc(String deviceId);
    
    List<Command> findByDeviceIdAndCreatedAtBetween(String deviceId, LocalDateTime start, LocalDateTime end);
    
    List<Command> findTop10ByDeviceIdOrderByCreatedAtDesc(String deviceId);
}
