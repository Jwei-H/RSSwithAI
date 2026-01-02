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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository templateRepository;
    private final PromptVersionRepository versionRepository;

    @Transactional
    public PromptTemplateDTO createTemplate(CreatePromptTemplateRequest request) {
        PromptTemplate template = PromptTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .latestVersion(1)
                .build();

        PromptTemplate savedTemplate = templateRepository.save(template);

        PromptVersion version = PromptVersion.builder()
                .template(savedTemplate)
                .version(1)
                .content("")
                .immutable(false)
                .build();

        versionRepository.save(version);

        return PromptTemplateDTO.from(savedTemplate, version);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        templateRepository.deleteById(templateId);
    }

    public Page<PromptTemplateDTO> getAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable)
                .map(t -> PromptTemplateDTO.from(t, versionRepository.findByTemplateIdAndVersion(t.getId(), t.getLatestVersion()).orElse(null)));
    }

    public PromptTemplateDTO getTemplate(Long templateId) {
        PromptTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        PromptVersion pv = versionRepository.findByTemplateIdAndVersion(templateId, template.getLatestVersion())
                .orElseThrow(() -> new RuntimeException("Version not found"));
        return PromptTemplateDTO.from(template, pv);
    }

    public PromptVersionDTO getVersion(Long templateId, Integer version) {
        PromptVersion pv = versionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        return PromptVersionDTO.from(pv);
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
        return PromptVersionDTO.from(saved);
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

        PromptVersion newVersion = PromptVersion.builder()
                .template(template)
                .version(currentLatestVersionNum + 1)
                .content(currentLatest.getContent())
                .immutable(false)
                .build();

        versionRepository.save(newVersion);

        template.setLatestVersion(newVersion.getVersion());
        templateRepository.save(template);

        return PromptVersionDTO.from(newVersion);
    }
}