package com.iot.platform.domain.push.repository;

import com.iot.platform.domain.push.entity.PushTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PushTaskRepository extends JpaRepository<PushTask, Long> {

    /** Layer 2 扫表兜底：捡起待调度任务（优先级高优先） */
    @Query("SELECT t FROM PushTask t WHERE t.status IN :statuses AND t.nextRetryAt <= :now ORDER BY t.priority DESC, t.nextRetryAt ASC")
    List<PushTask> findScheduledTasks(@Param("statuses") List<String> statuses,
                                      @Param("now") Instant now,
                                      Pageable pageable);

    /** 按订阅ID查询 */
    List<PushTask> findBySubscriptionId(String subscriptionId);

    /** 按状态统计 */
    long countByStatus(String status);

    /** 按订阅+状态统计 */
    long countBySubscriptionIdAndStatus(String subscriptionId, String status);

    /** 按状态分页查询 */
    Page<PushTask> findByStatus(String status, Pageable pageable);

    /** 按数据类型+状态分页查询 */
    Page<PushTask> findByDataTypeAndStatus(String dataType, String status, Pageable pageable);

    /** 死信列表 */
    default List<PushTask> findDeadLetters(int count) {
        return findByStatus("DEAD_LETTER", Pageable.ofSize(count)).getContent();
    }
}
