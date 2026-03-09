package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.AbuseAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AbuseAuditLogRepository extends JpaRepository<AbuseAuditLog, UUID> {

    @Query("SELECT a FROM AbuseAuditLog a WHERE a.sourceIdentifier = :source ORDER BY a.createdAt DESC")
    List<AbuseAuditLog> findBySourceIdentifier(@Param("source") String sourceIdentifier);

    @Query("SELECT a FROM AbuseAuditLog a WHERE a.sourceIdentifier = :source AND a.eventType = 'BLOCKED' " +
           "AND a.blockedUntil > :now ORDER BY a.createdAt DESC")
    List<AbuseAuditLog> findActiveBlocks(
            @Param("source") String sourceIdentifier,
            @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(a) FROM AbuseAuditLog a WHERE a.eventType = :eventType AND a.createdAt > :since")
    long countByEventTypeSince(@Param("eventType") String eventType, @Param("since") LocalDateTime since);
}

