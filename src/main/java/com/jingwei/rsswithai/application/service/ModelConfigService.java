package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreateModelConfigRequest;
import com.jingwei.rsswithai.application.dto.ModelConfigDTO;
import com.jingwei.rsswithai.application.dto.UpdateModelConfigRequest;
import com.jingwei.rsswithai.domain.model.ModelConfig;
import com.jingwei.rsswithai.domain.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    public List<ModelConfigDTO> getAllConfigs() {
        return modelConfigRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ModelConfigDTO getConfig(Long id) {
        return modelConfigRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Model config not found"));
    }

    @Transactional
    public ModelConfigDTO createConfig(CreateModelConfigRequest request) {
        ModelConfig config = new ModelConfig();
        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setModelId(request.getModelId());
        config.setTemperature(request.getTemperature());
        config.setTopP(request.getTopP());
        config.setTopK(request.getTopK());
        config.setMaxTokens(request.getMaxTokens());
        config.setSeed(request.getSeed());

        ModelConfig saved = modelConfigRepository.save(config);
        return convertToDTO(saved);
    }

    @Transactional
    public ModelConfigDTO updateConfig(Long id, UpdateModelConfigRequest request) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Model config not found"));

        if (request.getName() != null) config.setName(request.getName());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        if (request.getModelId() != null) config.setModelId(request.getModelId());
        if (request.getTemperature() != null) config.setTemperature(request.getTemperature());
        if (request.getTopP() != null) config.setTopP(request.getTopP());
        if (request.getTopK() != null) config.setTopK(request.getTopK());
        if (request.getMaxTokens() != null) config.setMaxTokens(request.getMaxTokens());
        if (request.getSeed() != null) config.setSeed(request.getSeed());

        ModelConfig saved = modelConfigRepository.save(config);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteConfig(Long id) {
        modelConfigRepository.deleteById(id);
    }

    private ModelConfigDTO convertToDTO(ModelConfig entity) {
        ModelConfigDTO dto = new ModelConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setModelId(entity.getModelId());
        dto.setTemperature(entity.getTemperature());
        dto.setTopP(entity.getTopP());
        dto.setTopK(entity.getTopK());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setSeed(entity.getSeed());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
