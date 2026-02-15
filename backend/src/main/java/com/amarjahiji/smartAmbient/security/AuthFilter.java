package com.amarjahiji.smartAmbient.security;


import com.amarjahiji.smartAmbient.service.AuthService;
import com.amarjahiji.smartAmbient.service.DeviceService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class AuthFilter implements Filter {

    private final AuthService authService;
    private final DeviceService deviceService;

    private static final String[] PUBLIC_PATHS = {
            "/auth"
    };
    
    private static final String[] DEVICE_API_KEY_PATHS = {
            "/api/devices/register",
            "/api/devices/register/child",
            "/api/devices/commands/log",
            "/api/devices/proxy/ollama"
    };
    
    // Paths that require device API key with device ID in path
    private static final String DEVICE_STATUS_PATTERN = "/api/devices/.*/status";

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getServletPath();
        
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                chain.doFilter(request, response);
                return;
            }
        }
        
        String deviceApiKey = httpRequest.getHeader("X-Device-Api-Key");
        if (deviceApiKey != null && !deviceApiKey.isEmpty()) {
            if (path.equals("/api/devices/register")) {
                chain.doFilter(request, response);
                return;
            }
            if (isDeviceApiKeyPath(path) && deviceService.validateApiKey(deviceApiKey)) {
                httpRequest.setAttribute("deviceApiKey", deviceApiKey);
                chain.doFilter(request, response);
                return;
            }
        }
        
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            String userId = authService.extractUsernameFromToken(jwt);
            if (userId != null && authService.isTokenValid(jwt, userId)) {
                httpRequest.setAttribute("userId", userId);
                chain.doFilter(request, response);
                return;
            }
        }
        
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType("application/json");
        httpResponse.getWriter().write("{\"error\": \"Invalid or missing authentication\"}");
    }
    
    private boolean isDeviceApiKeyPath(String path) {
        for (String devicePath : DEVICE_API_KEY_PATHS) {
            if (path.startsWith(devicePath)) {
                return true;
            }
        }
        // Check for device status path pattern: /api/devices/{id}/status
        if (path.matches(DEVICE_STATUS_PATTERN)) {
            return true;
        }
        return false;
    }
}