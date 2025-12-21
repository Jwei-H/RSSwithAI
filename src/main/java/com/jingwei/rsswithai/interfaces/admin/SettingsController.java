package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.service.SettingsService;
import com.jingwei.rsswithai.domain.model.Setting;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<Setting>> getAllSettings() {
        return ResponseEntity.ok(settingsService.getAllSettings());
    }

    @PostMapping
    public ResponseEntity<Void> updateSettings(@RequestBody Map<String, String> settings) {
        settingsService.updateSettings(settings);
        return ResponseEntity.ok().build();
    }
}