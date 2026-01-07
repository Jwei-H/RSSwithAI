package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.UpdatePasswordRequest;
import com.jingwei.rsswithai.application.dto.UpdateUsernameRequest;
import com.jingwei.rsswithai.application.dto.UserLoginRequest;
import com.jingwei.rsswithai.application.dto.UserRegisterRequest;
import com.jingwei.rsswithai.application.service.UserService;
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
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
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
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(userService.getUserInfo(username));
    }

    @PutMapping("/user/username")
    public ResponseEntity<?> updateUsername(HttpServletRequest request, @RequestBody UpdateUsernameRequest updateRequest) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(userService.updateUsername(username, updateRequest));
    }

    @PutMapping("/user/password")
    public ResponseEntity<?> updatePassword(HttpServletRequest request, @RequestBody UpdatePasswordRequest updateRequest) {
        String username = (String) request.getAttribute("username");
        userService.updatePassword(username, updateRequest);
        return ResponseEntity.ok().build();
    }
}