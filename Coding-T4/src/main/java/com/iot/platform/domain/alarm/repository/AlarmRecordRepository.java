package com.iot.platform.domain.alarm.repository;

import com.iot.platform.domain.alarm.entity.AlarmRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, Long> {

    Optional<AlarmRecord> findByAlarmNo(String alarmNo);

    List<AlarmRecord> findByDeviceCodeAndStatus(String deviceCode, AlarmRecord.AlarmStatus status);

    long countByStatus(AlarmRecord.AlarmStatus status);

    /** 按严重级别统计 */
    @Query("SELECT a.severity, COUNT(a) FROM AlarmRecord a GROUP BY a.severity")
    List<Object[]> countBySeverity();

    /** 按设备类型统计 */
    @Query("SELECT a.deviceType, COUNT(a) FROM AlarmRecord a GROUP BY a.deviceType")
    List<Object[]> countByDeviceType();

    /** 按状态统计 */
    @Query("SELECT a.status, COUNT(a) FROM AlarmRecord a GROUP BY a.status")
    List<Object[]> countByStatus();

    /** 按天统计趋势 */
    @Query("SELECT CAST(a.createdAt AS date), COUNT(a) FROM AlarmRecord a WHERE a.createdAt >= :since GROUP BY CAST(a.createdAt AS date) ORDER BY CAST(a.createdAt AS date)")
    List<Object[]> countByDay(@Param("since") java.time.LocalDateTime since);

    /** 报警最多的设备 TOP N */
    @Query("SELECT a.deviceCode, a.deviceName, a.deviceType, COUNT(a) as cnt FROM AlarmRecord a GROUP BY a.deviceCode, a.deviceName, a.deviceType ORDER BY cnt DESC")
    List<Object[]> topDevices(Pageable pageable);

    @Query("SELECT a FROM AlarmRecord a WHERE " +
           "(:projectId IS NULL OR a.projectId = :projectId) AND " +
           "(:deviceCode IS NULL OR a.deviceCode = :deviceCode) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<AlarmRecord> queryAlarms(@Param("projectId") String projectId,
                                   @Param("deviceCode") String deviceCode,
                                   @Param("severity") AlarmRecord.Severity severity,
                                   @Param("status") AlarmRecord.AlarmStatus status,
                                   Pageable pageable);
}
