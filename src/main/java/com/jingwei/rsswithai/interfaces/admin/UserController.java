package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.dto.LoginRequest;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Slf4j
public class UserController {

    private final AppConfig appConfig;
    private final JwtUtils jwtUtils;

    public UserController(AppConfig appConfig, JwtUtils jwtUtils) {
        this.appConfig = appConfig;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        log.debug("Attempting login for user: {}", loginRequest.getUsername());
        if (appConfig.getAdminUsername() != null &&
                appConfig.getAdminUsername().equals(loginRequest.getUsername()) &&
                appConfig.getAdminPassword() != null &&
                appConfig.getAdminPassword().equals(loginRequest.getPassword())) {
            String token = jwtUtils.generateToken(loginRequest.getUsername());
            return ResponseEntity.ok(Map.of("token", token));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.extractUsername(token);
                String newToken = jwtUtils.generateToken(username);
                return ResponseEntity.ok(Map.of("token", newToken));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
    }
}