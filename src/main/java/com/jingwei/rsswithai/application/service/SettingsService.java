package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ConfigUpdateEvent;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.config.SettingKey;
import com.jingwei.rsswithai.domain.model.Setting;
import com.jingwei.rsswithai.domain.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final Set<String> NON_EDITABLE_SETTING_KEYS = Set.of(
            "trends_word_cloud_prompt",
            "subscription_topic_threshold",
            "trends_hot_events_map_prompt",
            "trends_hot_events_reduce_prompt");

    private final SettingRepository settingRepository;
    private final AppConfig appConfig;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        backfillMissingSettings();
        loadSettings();
    }

    private void backfillMissingSettings() {
        Field[] fields = AppConfig.class.getDeclaredFields();
        Set<String> existingKeys = new HashSet<>(settingRepository.findAll().stream()
                .map(Setting::getKey)
                .toList());

        int createdCount = 0;
        for (Field field : fields) {
            if (!field.isAnnotationPresent(SettingKey.class)) {
                continue;
            }

            SettingKey annotation = field.getAnnotation(SettingKey.class);
            String key = annotation.value();
            if (isNonEditableSetting(key)) {
                continue;
            }
            if (existingKeys.contains(key)) {
                continue;
            }

            String defaultValue = getFieldDefaultValue(field);
            if (defaultValue == null) {
                continue;
            }

            settingRepository.save(new Setting(key, defaultValue, null));
            createdCount++;
            log.info("Backfilled missing setting key: {}", key);
        }

        if (createdCount > 0) {
            log.info("Backfilled {} missing settings with default values.", createdCount);
        }
    }

    private void loadSettings() {
        log.info("Loading settings from database...");
        List<Setting> settings = settingRepository.findAll();
        Map<String, String> settingMap = settings.stream().filter(s -> s.getValue() != null)
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

            if (isNonEditableSetting(key)) {
                log.warn("Skip updating non-editable setting key: {}", key);
                continue;
            }

            Setting setting = settingRepository.findByKey(key)
                    .orElse(new Setting(key, value, null));
            setting.setValue(value);
            settingRepository.save(setting);
        }
        updateAppConfig(newSettings);
        eventPublisher.publishEvent(new ConfigUpdateEvent(this));
    }

    private void updateAppConfig(Map<String, String> settings) {
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
            } else if (type == Double.class || type == double.class) {
                field.set(appConfig, Double.parseDouble(value));
            } else if (type == Float.class || type == float.class) {
                field.set(appConfig, Float.parseFloat(value));
            } else if (type == Boolean.class || type == boolean.class) {
                field.set(appConfig, Boolean.parseBoolean(value));
            } else if (List.class.isAssignableFrom(type) && isStringList(field)) {
                field.set(appConfig, parseStringList(value));
            }
        } catch (Exception e) {
            log.error("Failed to set field {} with value {}", field.getName(), value, e);
        }
    }

    private boolean isStringList(Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }

        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        return actualTypeArguments.length == 1 && actualTypeArguments[0] == String.class;
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("[\\r\\n,，]+"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();
    }

    private String getFieldDefaultValue(Field field) {
        try {
            field.setAccessible(true);
            Object value = field.get(appConfig);
            if (value == null) {
                return null;
            }
            if (value instanceof List<?> listValue) {
                return listValue.stream().map(String::valueOf).collect(Collectors.joining("\n"));
            }
            return String.valueOf(value);
        } catch (IllegalAccessException e) {
            log.error("Failed to read default value for field {}", field.getName(), e);
            return null;
        }
    }

    private boolean isNonEditableSetting(String key) {
        return NON_EDITABLE_SETTING_KEYS.contains(key);
    }
}