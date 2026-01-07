package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreateModelConfigRequest;
import com.jingwei.rsswithai.application.dto.ModelConfigDTO;
import com.jingwei.rsswithai.application.dto.UpdateModelConfigRequest;
import com.jingwei.rsswithai.domain.model.ModelConfig;
import com.jingwei.rsswithai.domain.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    public Page<ModelConfigDTO> getAllConfigs(Pageable pageable) {
        return modelConfigRepository.findAll(pageable)
                .map(ModelConfigDTO::from);
    }

    public ModelConfigDTO getConfig(Long id) {
        return modelConfigRepository.findById(id)
                .map(ModelConfigDTO::from)
                .orElseThrow(() -> new RuntimeException("Model config not found"));
    }

    @Transactional
    public ModelConfigDTO createConfig(CreateModelConfigRequest request) {
        ModelConfig config = ModelConfig.builder()
                .name(request.name())
                .description(request.description())
                .modelId(request.modelId())
                .temperature(request.temperature())
                .topP(request.topP())
                .topK(request.topK())
                .maxTokens(request.maxTokens())
                .seed(request.seed())
                .build();

        ModelConfig saved = modelConfigRepository.save(config);
        return ModelConfigDTO.from(saved);
    }

    @Transactional
    public ModelConfigDTO updateConfig(Long id, UpdateModelConfigRequest request) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Model config not found"));

        config = ModelConfig.builder()
                .id(config.getId())
                .name(request.name())
                .description(request.description())
                .modelId(request.modelId())
                .temperature(request.temperature())
                .topP(request.topP())
                .topK(request.topK())
                .maxTokens(request.maxTokens())
                .seed(request.seed())
                .build();

        ModelConfig saved = modelConfigRepository.save(config);
        return ModelConfigDTO.from(saved);
    }

    @Transactional
    public void deleteConfig(Long id) {
        modelConfigRepository.deleteById(id);
    }
}