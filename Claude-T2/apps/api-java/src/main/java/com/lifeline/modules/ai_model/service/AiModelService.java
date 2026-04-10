package com.lifeline.modules.ai_model.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_model.dto.CreateAiModelDto;
import com.lifeline.modules.ai_model.dto.UpdateAiModelDto;
import com.lifeline.modules.ai_model.entity.AiModel;
import com.lifeline.modules.ai_model.entity.AiModelStatus;
import com.lifeline.modules.ai_model.entity.AiModelType;
import com.lifeline.modules.ai_model.repository.AiModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository aiModelRepository;

    public PageResponse<AiModel> findAll(int page, int pageSize, AiModelStatus status,
                                         AiModelType type, String search) {
        String statusStr = status != null ? status.name() : null;
        String typeStr = type != null ? type.name() : null;
        Page<AiModel> result = aiModelRepository.findAllWithFilters(
                statusStr, typeStr, search,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public AiModel findOne(String id) {
        return aiModelRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("模型不存在: " + id));
    }

    @Transactional
    public AiModel create(CreateAiModelDto dto) {
        AiModel model = new AiModel();
        model.setName(dto.getName());
        model.setCode(dto.getCode());
        model.setVersion(dto.getVersion());
        model.setType(AiModelType.valueOf(dto.getType()));
        model.setDescription(dto.getDescription());
        model.setFileUrl(dto.getFileUrl());
        model.setFileSize(dto.getFileSize());
        model.setChecksum(dto.getChecksum());
        model.setSpecs(dto.getSpecs());
        model.setApplicableDeviceTypes(dto.getApplicableDeviceTypes());
        model.setStatus(AiModelStatus.draft);
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel update(String id, UpdateAiModelDto dto) {
        AiModel model = findOne(id);
        if (dto.getName() != null) model.setName(dto.getName());
        if (dto.getVersion() != null) model.setVersion(dto.getVersion());
        if (dto.getDescription() != null) model.setDescription(dto.getDescription());
        if (dto.getFileUrl() != null) model.setFileUrl(dto.getFileUrl());
        if (dto.getFileSize() != null) model.setFileSize(dto.getFileSize());
        if (dto.getChecksum() != null) model.setChecksum(dto.getChecksum());
        if (dto.getSpecs() != null) model.setSpecs(dto.getSpecs());
        if (dto.getApplicableDeviceTypes() != null) model.setApplicableDeviceTypes(dto.getApplicableDeviceTypes());
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel publish(String id) {
        AiModel model = findOne(id);
        model.setStatus(AiModelStatus.published);
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel deprecate(String id) {
        AiModel model = findOne(id);
        model.setStatus(AiModelStatus.deprecated);
        return aiModelRepository.save(model);
    }

    @Transactional
    public void remove(String id) {
        aiModelRepository.delete(findOne(id));
    }
}
