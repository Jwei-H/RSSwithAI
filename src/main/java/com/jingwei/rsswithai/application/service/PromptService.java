package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreatePromptTemplateRequest;
import com.jingwei.rsswithai.application.dto.PromptTemplateDTO;
import com.jingwei.rsswithai.application.dto.PromptVersionDTO;
import com.jingwei.rsswithai.application.dto.UpdatePromptVersionRequest;
import com.jingwei.rsswithai.domain.model.PromptTemplate;
import com.jingwei.rsswithai.domain.model.PromptVersion;
import com.jingwei.rsswithai.domain.repository.PromptTemplateRepository;
import com.jingwei.rsswithai.domain.repository.PromptVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository templateRepository;
    private final PromptVersionRepository versionRepository;

    @Transactional
    public PromptTemplateDTO createTemplate(CreatePromptTemplateRequest request) {
        PromptTemplate template = new PromptTemplate();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setLatestVersion(1);

        PromptTemplate savedTemplate = templateRepository.save(template);

        PromptVersion version = new PromptVersion();
        version.setTemplate(savedTemplate);
        version.setVersion(1);
        version.setContent("");
        version.setImmutable(false);

        versionRepository.save(version);

        return convertToDTO(savedTemplate, version);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        templateRepository.deleteById(templateId);
    }

    public List<PromptTemplateDTO> getAllTemplates() {
        return templateRepository.findAll().stream()
                .map(t -> {
                    PromptVersion latest = versionRepository.findByTemplateIdAndVersion(t.getId(), t.getLatestVersion())
                            .orElse(null);
                    return convertToDTO(t, latest);
                })
                .collect(Collectors.toList());
    }

    public PromptVersionDTO getVersion(Long templateId, Integer version) {
        PromptVersion pv = versionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        return convertToVersionDTO(pv);
    }

    @Transactional
    public PromptVersionDTO updateVersionContent(Long templateId, Integer version, UpdatePromptVersionRequest request) {
        PromptVersion pv = versionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        if (pv.getImmutable()) {
            throw new RuntimeException("Cannot update immutable version");
        }
        PromptTemplate template = pv.getTemplate();
        if (!template.getLatestVersion().equals(version)) {
             throw new RuntimeException("Only the latest version can be modified");
        }
        pv.setContent(request.getContent());
        PromptVersion saved = versionRepository.save(pv);
        return convertToVersionDTO(saved);
    }

    @Transactional
    public void freezeVersion(Long templateId, Integer version) {
        PromptVersion pv = versionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        
        pv.setImmutable(true);
        versionRepository.save(pv);
    }

    @Transactional
    public PromptVersionDTO createNewVersion(Long templateId) {
        PromptTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        Integer currentLatestVersionNum = template.getLatestVersion();
        PromptVersion currentLatest = versionRepository.findByTemplateIdAndVersion(templateId, currentLatestVersionNum)
                .orElseThrow(() -> new RuntimeException("Latest version not found"));

        if (!currentLatest.getImmutable()) {
             throw new RuntimeException("Current latest version must be locked (frozen) before creating a new version.");
        }

        PromptVersion newVersion = new PromptVersion();
        newVersion.setTemplate(template);
        newVersion.setVersion(currentLatestVersionNum + 1);
        newVersion.setContent(currentLatest.getContent());
        newVersion.setImmutable(false);

        versionRepository.save(newVersion);

        template.setLatestVersion(newVersion.getVersion());
        templateRepository.save(template);

        return convertToVersionDTO(newVersion);
    }

    private PromptTemplateDTO convertToDTO(PromptTemplate template, PromptVersion latestVersion) {
        PromptTemplateDTO dto = new PromptTemplateDTO();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setLatestVersion(template.getLatestVersion());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        if (latestVersion != null) {
            dto.setLatestVersionDetail(convertToVersionDTO(latestVersion));
        }
        return dto;
    }

    private PromptVersionDTO convertToVersionDTO(PromptVersion version) {
        PromptVersionDTO dto = new PromptVersionDTO();
        dto.setId(version.getId());
        dto.setTemplateId(version.getTemplate().getId());
        dto.setVersion(version.getVersion());
        dto.setContent(version.getContent());
        dto.setImmutable(version.getImmutable());
        dto.setCreatedAt(version.getCreatedAt());
        return dto;
    }
}
