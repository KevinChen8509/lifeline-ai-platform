package com.lifeline.modules.user.repository;

import com.lifeline.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role " +
           "WHERE (:status IS NULL OR u.status = :status) " +
           "AND (:roleId IS NULL OR u.roleId = :roleId) " +
           "AND (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAllWithFilters(@Param("status") String status,
                                  @Param("roleId") String roleId,
                                  @Param("search") String search,
                                  Pageable pageable);
}
