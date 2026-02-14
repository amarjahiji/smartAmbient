package com.amarjahiji.smartAmbient.dto;

import com.amarjahiji.smartAmbient.entity.CommandStatus;
import com.amarjahiji.smartAmbient.entity.CommandType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponse {
    private String id;
    private String deviceId;
    private String userId;
    private CommandType commandType;
    private String payload;
    private CommandStatus status;
    private String response;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
}
