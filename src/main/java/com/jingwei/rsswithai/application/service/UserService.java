package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.*;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.User;
import com.jingwei.rsswithai.domain.repository.UserRepository;
import com.jingwei.rsswithai.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AppConfig appConfig;
    private final JwtUtils jwtUtils;

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder hashtext = new StringBuilder(no.toString(16));
            while (hashtext.length() < 32) {
                hashtext.insert(0, "0");
            }
            return hashtext.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }

    @Transactional
    public UserDTO register(UserRegisterRequest request, String ipAddress) {
        long recentRegistrations = userRepository.countByRegistrationIpAndCreatedAtAfter(
            ipAddress, 
            LocalDateTime.now().minusHours(24)
        );
        
        if (recentRegistrations >= 10) {
            throw new RuntimeException("Registration limit exceeded for this IP address (max 10 per 24h)");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username already exists");
        }

        String defaultAvatar = appConfig.getDefaultAvatar();
        User user = User.builder()
                .username(request.username())
                .password(md5(request.password()))
                .avatarUrl(defaultAvatar != null ? defaultAvatar : "")
                .registrationIp(ipAddress)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user: {}", savedUser.getUsername());
        return UserDTO.from(savedUser);
    }

    public String login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found or invalid credentials"));

        if (!user.getPassword().equals(md5(request.password()))) {
            throw new RuntimeException("User not found or invalid credentials");
        }
        log.info("User logged in: {}", user.getUsername());

        return jwtUtils.generateToken(user.getUsername());
    }

    public String refreshToken(String token) {
        if (jwtUtils.validateToken(token)) {
            String username = jwtUtils.extractUsername(token);
            if (userRepository.existsByUsername(username)) {
                return jwtUtils.generateToken(username);
            }
        }
        throw new RuntimeException("Invalid token");
    }

    @Transactional
    public UserDTO updateUsername(String currentUsername, UpdateUsernameRequest request) {
        if (userRepository.existsByUsername(request.newUsername())) {
            throw new RuntimeException("Username already exists");
        }

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setUsername(request.newUsername());
        return UserDTO.from(userRepository.save(user));
    }

    @Transactional
    public void updatePassword(String currentUsername, UpdatePasswordRequest request) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getPassword().equals(md5(request.oldPassword()))) {
            throw new RuntimeException("Invalid old password");
        }

        user.setPassword(md5(request.newPassword()));
        userRepository.save(user);
    }

    public UserDTO getUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return UserDTO.from(user);
    }


}