package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.UpdatePasswordRequest;
import com.jingwei.rsswithai.application.dto.UpdateUsernameRequest;
import com.jingwei.rsswithai.application.dto.UserLoginRequest;
import com.jingwei.rsswithai.application.dto.UserRegisterRequest;
import com.jingwei.rsswithai.application.service.UserService;
import com.jingwei.rsswithai.interfaces.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(userService.register(request, ipAddress));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequest request) {
        String token = userService.login(request);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String newToken = userService.refreshToken(token);
                return ResponseEntity.ok(Map.of("token", newToken));
            } catch (Exception e) {
                // Ignore, return unauthorized below
            }
        }
        return ResponseEntity.status(401).body("Invalid token");
    }

    @GetMapping("/user/profile")
    public ResponseEntity<?> getProfile() {
        String username = UserContext.requireUsername();
        return ResponseEntity.ok(userService.getUserInfo(username));
    }

    @PutMapping("/user/username")
    public ResponseEntity<?> updateUsername(@RequestBody UpdateUsernameRequest updateRequest) {
        String username = UserContext.requireUsername();
        return ResponseEntity.ok(userService.updateUsername(username, updateRequest));
    }

    @PutMapping("/user/password")
    public ResponseEntity<?> updatePassword(@RequestBody UpdatePasswordRequest updateRequest) {
        String username = UserContext.requireUsername();
        userService.updatePassword(username, updateRequest);
        return ResponseEntity.ok().build();
    }
}