package com.kchat.service;

import com.kchat.common.enums.RoomRoles;
import com.kchat.common.enums.RoomType;
import com.kchat.entity.ChatRoom;
import com.kchat.entity.RoomMember;
import com.kchat.entity.User;
import com.kchat.repository.ChatRoomRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures default fixed channels exist and every active user is enrolled (#16).
 */
@Service
public class ChannelEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(ChannelEnrollmentService.class);

    /** slug → display name (without #). */
    static final List<DefaultChannel> DEFAULT_CHANNELS = List.of(
            new DefaultChannel("ops-alerts", "ops-alerts"),
            new DefaultChannel("support", "support")
    );

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    public ChannelEnrollmentService(
            ChatRoomRepository chatRoomRepository,
            RoomMemberRepository roomMemberRepository,
            UserRepository userRepository
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void ensureDefaultChannels() {
        List<User> activeUsers = userRepository.findAllActive();
        for (DefaultChannel def : DEFAULT_CHANNELS) {
            ChatRoom room = findOrCreateChannel(def, activeUsers);
            enrollMissingMembers(room, activeUsers);
        }
    }

    @Transactional
    public void enrollUserInAllChannels(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        // New installs may register before bootstrap finishes; ensure channels exist.
        ensureDefaultChannels();
        List<ChatRoom> channels = chatRoomRepository.findByTypeAndArchivedFalse(RoomType.channel);
        for (ChatRoom room : channels) {
            ensureMembership(room, user, RoomRoles.MEMBER);
        }
    }

    private ChatRoom findOrCreateChannel(DefaultChannel def, List<User> activeUsers) {
        Optional<ChatRoom> existing = chatRoomRepository.findBySlug(def.slug());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            ChatRoom room = new ChatRoom();
            room.setType(RoomType.channel);
            room.setSlug(def.slug());
            room.setName(def.name());
            room.setArchived(false);
            pickOwner(activeUsers).ifPresent(room::setCreatedBy);
            chatRoomRepository.saveAndFlush(room);
            log.info("Created default channel #{}", def.slug());
            return room;
        } catch (DataIntegrityViolationException ex) {
            return chatRoomRepository.findBySlug(def.slug())
                    .orElseThrow(() -> ex);
        }
    }

    private void enrollMissingMembers(ChatRoom room, List<User> activeUsers) {
        boolean hasOwner = roomMemberRepository.findActiveMembersWithUser(room.getId()).stream()
                .anyMatch(m -> RoomRoles.OWNER.equals(m.getRole()));
        Optional<User> ownerUser = pickOwner(activeUsers);

        for (User user : activeUsers) {
            String role = RoomRoles.MEMBER;
            if (!hasOwner && ownerUser.isPresent() && ownerUser.get().getId().equals(user.getId())) {
                role = RoomRoles.OWNER;
                hasOwner = true;
            }
            ensureMembership(room, user, role);
        }
    }

    private void ensureMembership(ChatRoom room, User user, String preferredRole) {
        roomMemberRepository.findByRoomIdAndUserId(room.getId(), user.getId()).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (existing.getLeftAt() != null) {
                existing.setLeftAt(null);
                existing.setJoinedAt(Instant.now());
                existing.setUnreadCount(0);
                changed = true;
            }
            if (RoomRoles.OWNER.equals(preferredRole) && !RoomRoles.OWNER.equals(existing.getRole())) {
                existing.setRole(RoomRoles.OWNER);
                changed = true;
            } else if (existing.getRole() == null || existing.getRole().isBlank()) {
                existing.setRole(preferredRole);
                changed = true;
            }
            if (changed) {
                roomMemberRepository.save(existing);
            }
        }, () -> {
            RoomMember membership = new RoomMember();
            membership.setRoom(room);
            membership.setUser(user);
            membership.setRole(preferredRole);
            roomMemberRepository.save(membership);
        });
    }

    public static Optional<User> pickOwner(List<User> activeUsers) {
        return activeUsers.stream()
                .filter(u -> "admin".equalsIgnoreCase(u.getUsername()))
                .findFirst()
                .or(() -> activeUsers.stream().findFirst());
    }

    record DefaultChannel(String slug, String name) {
    }
}
