package com.kchat.repository;

import com.kchat.entity.UserDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByFcmToken(String fcmToken);

    List<UserDevice> findByUser_IdOrderByLastActiveAtDesc(UUID userId);

    void deleteByFcmToken(String fcmToken);

    void deleteByUser_IdAndFcmTokenStartingWith(UUID userId, String prefix);

    boolean existsByUser_IdAndFcmToken(UUID userId, String fcmToken);
}
