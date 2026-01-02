package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.service.SettingsService;
import com.jingwei.rsswithai.domain.model.Setting;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public ResponseEntity<Void> updateSettings(@RequestBody List<Map<String, String>> settings) {
        Map<String, String> settingsMap = settings.stream()
                .collect(Collectors.toMap(
                        m -> m.get("key"),
                        m -> m.get("value"),
                        (a, b) -> b,
                        HashMap::new
                ));
        settingsService.updateSettings(settingsMap);
        return ResponseEntity.ok().build();
    }
}