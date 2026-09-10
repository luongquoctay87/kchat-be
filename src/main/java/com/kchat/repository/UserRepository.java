package com.kchat.repository;

import com.kchat.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsernameIgnoreCase(String username);

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);

  @Query(
      """
            SELECT u FROM User u
            WHERE LOWER(u.username) = LOWER(:identifier)
               OR LOWER(u.email) = LOWER(:identifier)
            """)
  Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

  @Query(
      """
            SELECT u FROM User u
            WHERE u.status = com.kchat.common.enums.UserStatus.active
              AND u.id <> :excludeId
            ORDER BY LOWER(u.displayName) ASC
            """)
  List<User> findActiveExcluding(@Param("excludeId") UUID excludeId);

  @Query(
      """
            SELECT u FROM User u
            WHERE u.status = com.kchat.common.enums.UserStatus.active
              AND u.id <> :excludeId
              AND (
                   LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY LOWER(u.displayName) ASC
            """)
  List<User> searchActiveExcluding(
      @Param("excludeId") UUID excludeId,
      @Param("query") String query,
      org.springframework.data.domain.Pageable pageable);

  @Query(
      """
            SELECT u FROM User u
            WHERE u.status = com.kchat.common.enums.UserStatus.active
              AND u.id IN :ids
            """)
  List<User> findActiveByIdIn(@Param("ids") Collection<UUID> ids);

  @Query(
      """
            SELECT u FROM User u
            WHERE u.status = com.kchat.common.enums.UserStatus.active
            ORDER BY u.createdAt ASC
            """)
  List<User> findAllActive();
}
