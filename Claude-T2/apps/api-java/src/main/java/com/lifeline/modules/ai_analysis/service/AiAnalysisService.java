package com.lifeline.modules.ai_analysis.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_analysis.entity.AiAnalysisResult;
import com.lifeline.modules.ai_analysis.entity.AnalysisResult;
import com.lifeline.modules.ai_analysis.entity.AnalysisType;
import com.lifeline.modules.ai_analysis.repository.AiAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisResultRepository repository;

    public List<AiAnalysisResult> getLatestResults(String deviceId, AnalysisType type, int limit) {
        PageRequest pageable = PageRequest.of(0, Math.min(limit, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
        if (type != null) {
            return repository.findByDeviceIdOrderByTimestampDesc(deviceId, pageable)
                    .stream().filter(r -> r.getAnalysisType() == type).toList();
        }
        return repository.findByDeviceIdOrderByTimestampDesc(deviceId, pageable);
    }

    public PageResponse<AiAnalysisResult> getHistory(String deviceId, AnalysisType type,
                                                      AnalysisResult result, String startTime,
                                                      String endTime, int page, int pageSize) {
        String typeStr = type != null ? type.name() : null;
        String resultStr = result != null ? result.name() : null;
        Page<AiAnalysisResult> p = repository.findHistory(
                deviceId, typeStr, resultStr, startTime, endTime,
                PageRequest.of(page - 1, Math.min(pageSize, 200), Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        return PageResponse.of(p.getContent(), p.getTotalElements(), page, pageSize);
    }

    public AiAnalysisResult findOne(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("AI分析结果不存在: " + id));
    }

    @Transactional
    public AiAnalysisResult createResult(AiAnalysisResult result) {
        return repository.save(result);
    }
}
