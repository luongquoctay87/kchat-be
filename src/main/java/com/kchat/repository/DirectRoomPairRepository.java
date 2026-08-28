package com.kchat.repository;

import com.kchat.entity.DirectRoomPair;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectRoomPairRepository extends JpaRepository<DirectRoomPair, UUID> {

    @Query("""
            SELECT p FROM DirectRoomPair p
            JOIN FETCH p.userA
            JOIN FETCH p.userB
            WHERE p.roomId IN :roomIds
            """)
    List<DirectRoomPair> findByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

    @Query("""
            SELECT p FROM DirectRoomPair p
            WHERE p.userA.id = :userAId AND p.userB.id = :userBId
            """)
    Optional<DirectRoomPair> findByOrderedUserPair(
            @Param("userAId") UUID userAId,
            @Param("userBId") UUID userBId
    );
}
