package com.kchat.repository;

import com.kchat.entity.RoomMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {

    interface MemberCount {
        UUID getRoomId();

        long getMemberCount();
    }

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.room r
            JOIN FETCH m.user
            WHERE m.user.id = :userId AND m.leftAt IS NULL AND r.archived = FALSE
            ORDER BY r.updatedAt DESC
            """)
    List<RoomMember> findActiveMemberships(@Param("userId") UUID userId);

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.room
            JOIN FETCH m.user
            WHERE m.room.id = :roomId AND m.user.id = :userId AND m.leftAt IS NULL
            """)
    Optional<RoomMember> findActiveMembership(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.user
            WHERE m.room.id = :roomId AND m.leftAt IS NULL AND m.user.id <> :excludeUserId
            """)
    List<RoomMember> findOtherActiveMembers(
            @Param("roomId") UUID roomId,
            @Param("excludeUserId") UUID excludeUserId
    );

    @Query("""
            SELECT m.room.id AS roomId, COUNT(m) AS memberCount
            FROM RoomMember m
            WHERE m.room.id IN :roomIds AND m.leftAt IS NULL
            GROUP BY m.room.id
            """)
    List<MemberCount> countActiveMembers(@Param("roomIds") Collection<UUID> roomIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RoomMember m
            SET m.unreadCount = m.unreadCount + 1
            WHERE m.room.id = :roomId
              AND m.leftAt IS NULL
              AND m.user.id <> :senderId
            """)
    int incrementUnreadForOthers(@Param("roomId") UUID roomId, @Param("senderId") UUID senderId);

    /** Bot/system messages have no sender — increment unread for every active member. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RoomMember m
            SET m.unreadCount = m.unreadCount + 1
            WHERE m.room.id = :roomId AND m.leftAt IS NULL
            """)
    int incrementUnreadForAll(@Param("roomId") UUID roomId);

    @Query("""
            SELECT m.user.id
            FROM RoomMember m
            WHERE m.room.id = :roomId AND m.leftAt IS NULL
            """)
    List<UUID> findActiveMemberUserIds(@Param("roomId") UUID roomId);

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.user
            JOIN FETCH m.room
            WHERE m.room.id = :roomId AND m.leftAt IS NULL
            ORDER BY
              CASE m.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END,
              LOWER(m.user.displayName)
            """)
    List<RoomMember> findActiveMembersWithUser(@Param("roomId") UUID roomId);

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.user
            JOIN FETCH m.room
            WHERE m.room.id = :roomId AND m.user.id = :userId
            """)
    Optional<RoomMember> findByRoomIdAndUserId(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT m FROM RoomMember m
            JOIN FETCH m.user
            JOIN FETCH m.room
            WHERE m.room.id = :roomId AND m.leftAt IS NOT NULL
            """)
    List<RoomMember> findLeftMembers(@Param("roomId") UUID roomId);

    /**
     * Users who share at least one active room with {@code userId} (for presence fan-out).
     */
    @Query("""
            SELECT DISTINCT m2.user.id
            FROM RoomMember m1
            JOIN RoomMember m2 ON m2.room = m1.room
            WHERE m1.user.id = :userId
              AND m2.user.id <> :userId
              AND m1.leftAt IS NULL
              AND m2.leftAt IS NULL
            """)
    List<UUID> findPeerUserIds(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RoomMember m
            SET m.unreadCount = 0, m.lastReadMessageId = null
            WHERE m.room.id IN :roomIds
            """)
    int resetUnreadAndLastReadForRooms(@Param("roomIds") Collection<UUID> roomIds);
}
