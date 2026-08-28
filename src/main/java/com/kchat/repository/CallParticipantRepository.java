package com.kchat.repository;

import com.kchat.entity.CallParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallParticipantRepository extends JpaRepository<CallParticipant, CallParticipant.Pk> {

    @Query("""
            SELECT p.userId FROM CallParticipant p
            WHERE p.callId = :callId
            """)
    List<UUID> findUserIdsByCallId(@Param("callId") UUID callId);

    boolean existsByCallIdAndUserId(UUID callId, UUID userId);
}
