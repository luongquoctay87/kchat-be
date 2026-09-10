package com.kchat.repository;

import com.kchat.entity.RegistrationOtpToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationOtpTokenRepository extends JpaRepository<RegistrationOtpToken, UUID> {

  Optional<RegistrationOtpToken>
      findFirstByEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
          String email, Instant expiresAfter);

  Optional<RegistrationOtpToken> findByRegistrationTokenHash(String registrationTokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            UPDATE RegistrationOtpToken t
            SET t.usedAt = CURRENT_TIMESTAMP
            WHERE LOWER(t.email) = LOWER(:email) AND t.usedAt IS NULL
            """)
  void markAllUsedForEmail(@Param("email") String email);
}
