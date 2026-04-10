package com.lifeline.modules.project.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.project.dto.CreateProjectDto;
import com.lifeline.modules.project.dto.UpdateProjectDto;
import com.lifeline.modules.project.entity.Project;
import com.lifeline.modules.project.entity.ProjectStatus;
import com.lifeline.modules.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @RequirePermission(action = "read", subject = "Project")
    public PageResponse<Project> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status) {
        return projectService.findAll(page, pageSize, search, status);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Project")
    public Project findOne(@PathVariable String id) {
        return projectService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Project")
    public Project create(@Valid @RequestBody CreateProjectDto dto) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return projectService.create(dto, userId);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "manage", subject = "Project")
    public Project update(@PathVariable String id, @Valid @RequestBody UpdateProjectDto dto) {
        return projectService.update(id, dto);
    }

    @PutMapping("/{id}/archive")
    @RequirePermission(action = "manage", subject = "Project")
    public Project archive(@PathVariable String id) {
        return projectService.archive(id);
    }

    @PutMapping("/{id}/restore")
    @RequirePermission(action = "manage", subject = "Project")
    public Project restore(@PathVariable String id) {
        return projectService.restore(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "Project")
    public void remove(@PathVariable String id) {
        projectService.remove(id);
    }
}
