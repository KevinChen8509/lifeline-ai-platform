package com.lifeline.modules.ai_model.repository;

import com.lifeline.modules.ai_model.entity.AiModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiModelRepository extends JpaRepository<AiModel, String> {

    @Query("SELECT m FROM AiModel m WHERE " +
           "(:status IS NULL OR m.status = :status) " +
           "AND (:type IS NULL OR m.type = :type) " +
           "AND (:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(m.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AiModel> findAllWithFilters(@Param("status") String status,
                                     @Param("type") String type,
                                     @Param("search") String search,
                                     Pageable pageable);
}
