package com.kchat.repository;

import com.kchat.common.enums.CallStatus;
import com.kchat.entity.CallSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {

    @Query("""
            SELECT c FROM CallSession c
            JOIN FETCH c.room
            JOIN FETCH c.initiator
            WHERE c.id = :id
            """)
    Optional<CallSession> findByIdWithRoomAndInitiator(@Param("id") UUID id);

    @Query("""
            SELECT c FROM CallSession c
            JOIN FETCH c.room
            JOIN FETCH c.initiator
            WHERE c.room.id = :roomId
              AND c.status IN :statuses
            ORDER BY c.createdAt DESC
            """)
    List<CallSession> findByRoomIdAndStatusIn(
            @Param("roomId") UUID roomId,
            @Param("statuses") Collection<CallStatus> statuses
    );

    @Query("""
            SELECT c FROM CallSession c
            JOIN FETCH c.room
            JOIN FETCH c.initiator
            WHERE c.status = :status
              AND c.createdAt < :before
            """)
    List<CallSession> findByStatusAndCreatedAtBefore(
            @Param("status") CallStatus status,
            @Param("before") Instant before
    );

    @Query("""
            SELECT c FROM CallSession c
            JOIN FETCH c.room
            JOIN FETCH c.initiator
            WHERE c.status = com.kchat.common.enums.CallStatus.active
              AND c.startedAt IS NOT NULL
              AND c.startedAt < :before
            """)
    List<CallSession> findStaleActiveCalls(@Param("before") Instant before);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM CallSession c, CallParticipant p
            WHERE p.callId = c.id
              AND p.userId = :userId
              AND c.status IN :statuses
            """)
    boolean existsLiveCallForUser(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<CallStatus> statuses
    );

    @Query("""
            SELECT c FROM CallSession c
            JOIN FETCH c.room
            JOIN FETCH c.initiator
            WHERE c.status IN :statuses
              AND EXISTS (
                  SELECT 1 FROM CallParticipant p
                  WHERE p.callId = c.id AND p.userId = :userId
              )
            """)
    List<CallSession> findLiveCallsForUser(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<CallStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CallSession c
            SET c.status = :toStatus, c.startedAt = :startedAt
            WHERE c.id = :id AND c.status = :fromStatus
            """)
    int transitionStatus(
            @Param("id") UUID id,
            @Param("fromStatus") CallStatus fromStatus,
            @Param("toStatus") CallStatus toStatus,
            @Param("startedAt") Instant startedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CallSession c
            SET c.status = :toStatus, c.endedAt = :endedAt
            WHERE c.id = :id AND c.status = :fromStatus
            """)
    int endIfStatus(
            @Param("id") UUID id,
            @Param("fromStatus") CallStatus fromStatus,
            @Param("toStatus") CallStatus toStatus,
            @Param("endedAt") Instant endedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CallSession c
            SET c.status = com.kchat.common.enums.CallStatus.missed, c.endedAt = :endedAt
            WHERE c.id = :id
              AND c.status = com.kchat.common.enums.CallStatus.ringing
              AND c.createdAt < :before
            """)
    int markMissedIfStillRinging(
            @Param("id") UUID id,
            @Param("endedAt") Instant endedAt,
            @Param("before") Instant before
    );
}
