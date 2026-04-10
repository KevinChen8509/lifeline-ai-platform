package com.lifeline.modules.project.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.project.dto.CreateProjectDto;
import com.lifeline.modules.project.dto.UpdateProjectDto;
import com.lifeline.modules.project.entity.Project;
import com.lifeline.modules.project.entity.ProjectStatus;
import com.lifeline.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public PageResponse<Project> findAll(int page, int pageSize, String search, ProjectStatus status) {
        String statusStr = status != null ? status.name() : null;
        Page<Project> result = projectRepository.findAllWithFilters(
                search, statusStr,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public Project findOne(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("项目不存在: " + id));
    }

    @Transactional
    public Project create(CreateProjectDto dto, String creatorId) {
        if (projectRepository.findByCode(dto.getCode()).isPresent()) {
            throw BusinessException.badRequest("项目编码 \"" + dto.getCode() + "\" 已存在");
        }
        Project project = new Project();
        project.setName(dto.getName());
        project.setCode(dto.getCode());
        project.setDescription(dto.getDescription());
        project.setStatus(ProjectStatus.active);
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(String id, UpdateProjectDto dto) {
        Project project = findOne(id);
        if (dto.getCode() != null && !dto.getCode().equals(project.getCode())) {
            if (projectRepository.findByCode(dto.getCode()).isPresent()) {
                throw BusinessException.badRequest("项目编码 \"" + dto.getCode() + "\" 已存在");
            }
        }
        if (dto.getName() != null) project.setName(dto.getName());
        if (dto.getCode() != null) project.setCode(dto.getCode());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        return projectRepository.save(project);
    }

    @Transactional
    public Project archive(String id) {
        Project project = findOne(id);
        if (project.getStatus() == ProjectStatus.archived) {
            throw BusinessException.badRequest("项目已处于归档状态");
        }
        project.setStatus(ProjectStatus.archived);
        return projectRepository.save(project);
    }

    @Transactional
    public Project restore(String id) {
        Project project = findOne(id);
        if (project.getStatus() != ProjectStatus.archived) {
            throw BusinessException.badRequest("项目未处于归档状态");
        }
        project.setStatus(ProjectStatus.active);
        return projectRepository.save(project);
    }

    @Transactional
    public void remove(String id) {
        archive(id);
    }
}
