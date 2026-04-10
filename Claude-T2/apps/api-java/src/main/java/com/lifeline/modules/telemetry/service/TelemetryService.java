package com.lifeline.modules.telemetry.service;

import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.telemetry.entity.DeviceTelemetry;
import com.lifeline.modules.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;

    public PageResponse<DeviceTelemetry> findAll(int page, int pageSize, String deviceId,
                                                  String startTime, String endTime) {
        Page<DeviceTelemetry> result = telemetryRepository.findAllWithFilters(
                deviceId, startTime, endTime,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }
}
