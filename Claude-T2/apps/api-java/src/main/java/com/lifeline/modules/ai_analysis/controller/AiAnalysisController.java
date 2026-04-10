package com.lifeline.modules.ai_analysis.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_analysis.entity.AiAnalysisResult;
import com.lifeline.modules.ai_analysis.entity.AnalysisResult;
import com.lifeline.modules.ai_analysis.entity.AnalysisType;
import com.lifeline.modules.ai_analysis.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/devices/{deviceId}/ai-results")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @GetMapping("/latest")
    @RequirePermission(action = "read", subject = "Device")
    public List<AiAnalysisResult> getLatest(
            @PathVariable String deviceId,
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(defaultValue = "20") int limit) {
        return aiAnalysisService.getLatestResults(deviceId, type, limit);
    }

    @GetMapping("/history")
    @RequirePermission(action = "read", subject = "Device")
    public PageResponse<AiAnalysisResult> getHistory(
            @PathVariable String deviceId,
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(required = false) AnalysisResult result,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return aiAnalysisService.getHistory(deviceId, type, result, startTime, endTime, page, pageSize);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Device")
    public AiAnalysisResult findOne(@PathVariable String deviceId, @PathVariable String id) {
        return aiAnalysisService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Device")
    public AiAnalysisResult createResult(@PathVariable String deviceId, @RequestBody AiAnalysisResult body) {
        body.setDeviceId(deviceId);
        return aiAnalysisService.createResult(body);
    }
}
