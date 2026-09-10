package com.kchat.repository;

import com.kchat.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @Query(
      """
            SELECT t FROM RefreshToken t
            JOIN FETCH t.user
            WHERE t.tokenHash = :tokenHash
            """)
  Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            UPDATE RefreshToken t
            SET t.revokedAt = CURRENT_TIMESTAMP
            WHERE t.user.id = :userId AND t.revokedAt IS NULL
            """)
  void revokeAllActiveForUser(@Param("userId") UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            UPDATE RefreshToken t
            SET t.revokedAt = CURRENT_TIMESTAMP
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
              AND (t.deviceId = :deviceId OR t.deviceId IS NULL)
            """)
  void revokeActiveForUserAndDevice(
      @Param("userId") UUID userId, @Param("deviceId") String deviceId);
}
