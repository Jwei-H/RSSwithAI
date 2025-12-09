package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.config.SettingKey;
import com.jingwei.rsswithai.domain.model.Setting;
import com.jingwei.rsswithai.domain.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final SettingRepository settingRepository;
    private final AppConfig appConfig;

    @PostConstruct
    public void init() {
        loadSettings();
    }

    public void loadSettings() {
        log.info("Loading settings from database...");
        List<Setting> settings = settingRepository.findAll();
        Map<String, String> settingMap = settings.stream()
                .collect(Collectors.toMap(Setting::getKey, Setting::getValue));

        updateAppConfig(settingMap);
        log.info("Settings loaded successfully.");
    }

    public List<Setting> getAllSettings() {
        return settingRepository.findAll();
    }

    @Transactional
    public void updateSettings(Map<String, String> newSettings) {
        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Setting setting = settingRepository.findByKey(key)
                    .orElse(new Setting(key, value, null));
            setting.setValue(value);
            settingRepository.save(setting);
        }
        updateAppConfig(newSettings);
    }

    private synchronized void updateAppConfig(Map<String, String> settings) {
        Field[] fields = AppConfig.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(SettingKey.class)) {
                SettingKey annotation = field.getAnnotation(SettingKey.class);
                String key = annotation.value();
                if (settings.containsKey(key)) {
                    String value = settings.get(key);
                    setFieldValue(field, value);
                }
            }
        }
    }

    private void setFieldValue(Field field, String value) {
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == String.class) {
                field.set(appConfig, value);
            } else if (type == Integer.class || type == int.class) {
                field.set(appConfig, Integer.parseInt(value));
            } else if (type == Long.class || type == long.class) {
                field.set(appConfig, Long.parseLong(value));
            } else if (type == Boolean.class || type == boolean.class) {
                field.set(appConfig, Boolean.parseBoolean(value));
            }
        } catch (Exception e) {
            log.error("Failed to set field {} with value {}", field.getName(), value, e);
        }
    }
}
