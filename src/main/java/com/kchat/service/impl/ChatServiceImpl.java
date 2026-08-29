package com.kchat.service.impl;

import com.kchat.common.dto.chat.AddMembersRequest;
import com.kchat.common.dto.chat.ContactDto;
import com.kchat.common.dto.chat.CreateGroupRequest;
import com.kchat.common.dto.chat.DirectRoomDto;
import com.kchat.common.dto.chat.EditMessageRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.MessageSearchResultDto;
import com.kchat.common.dto.chat.MuteRoomRequest;
import com.kchat.common.util.RoomMuteOptions;
import com.kchat.common.dto.chat.PinMessageRequest;
import com.kchat.common.dto.chat.PinnedMessageDto;
import com.kchat.common.dto.chat.ReactMessageRequest;
import com.kchat.common.dto.chat.ReactionDto;
import com.kchat.common.dto.chat.ReadReceiptDto;
import com.kchat.common.dto.chat.RegisterDeviceRequest;
import com.kchat.common.dto.chat.RenameRoomRequest;
import com.kchat.common.dto.chat.RoomDto;
import com.kchat.common.dto.chat.RoomMemberDto;
import com.kchat.common.dto.chat.SendMessageRequest;
import com.kchat.common.dto.chat.UpdateRoomRequest;
import com.kchat.common.dto.user.WipeMessagesResultDto;
import com.kchat.common.enums.MessageType;
import com.kchat.common.enums.RoomRoles;
import com.kchat.common.enums.RoomType;
import com.kchat.common.exception.ApiException;
import com.kchat.common.util.ChannelRules;
import com.kchat.common.util.ChatTimeFormat;
import com.kchat.common.util.MessageStorageOptions;
import com.kchat.common.util.UuidOrder;
import com.kchat.entity.ChatMessage;
import com.kchat.entity.ChatRoom;
import com.kchat.entity.DirectRoomPair;
import com.kchat.entity.MessageAttachment;
import com.kchat.entity.MessageMention;
import com.kchat.entity.MessageReaction;
import com.kchat.entity.PinnedMessage;
import com.kchat.entity.RoomMember;
import com.kchat.entity.User;
import com.kchat.entity.UserDevice;
import com.kchat.entity.UserSettings;
import com.kchat.media.MediaStorage;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ChatRoomRepository;
import com.kchat.repository.DirectRoomPairRepository;
import com.kchat.repository.MessageAttachmentRepository;
import com.kchat.repository.MessageMentionRepository;
import com.kchat.repository.MessageReactionRepository;
import com.kchat.repository.MessageReadReceiptRepository;
import com.kchat.repository.PinnedMessageRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.repository.SenderMessageRow;
import com.kchat.repository.UserDeviceRepository;
import com.kchat.repository.UserRepository;
import com.kchat.repository.UserSettingsRepository;
import com.kchat.service.ChatService;
import com.kchat.service.mapper.ChatMapper;
import com.kchat.ws.MessageEventPublisher;
import com.kchat.ws.PresenceService;
import com.kchat.ws.TypingService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;
    private static final long MAX_MEDIA_BYTES = 25L * 1024 * 1024;
    private static final int MAX_GROUP_MEMBERS = 50;
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    private static final int WIPE_BATCH_SIZE = 500;
    private static final Set<String> ALLOWED_REACTION_EMOJIS = Set.of("👍", "❤️", "😂", "😮", "😢");
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![\\w.])@([A-Za-z0-9_.]{1,64})\\b");

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final DirectRoomPairRepository directRoomPairRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageReadReceiptRepository messageReadReceiptRepository;
    private final MessageMentionRepository messageMentionRepository;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final MessageEventPublisher messageEventPublisher;
    private final PresenceService presenceService;
    private final TypingService typingService;
    private final MediaStorage mediaStorage;

    public ChatServiceImpl(
            ChatRoomRepository chatRoomRepository,
            RoomMemberRepository roomMemberRepository,
            DirectRoomPairRepository directRoomPairRepository,
            ChatMessageRepository chatMessageRepository,
            MessageAttachmentRepository messageAttachmentRepository,
            MessageReactionRepository messageReactionRepository,
            MessageReadReceiptRepository messageReadReceiptRepository,
            MessageMentionRepository messageMentionRepository,
            PinnedMessageRepository pinnedMessageRepository,
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository,
            UserDeviceRepository userDeviceRepository,
            MessageEventPublisher messageEventPublisher,
            PresenceService presenceService,
            TypingService typingService,
            MediaStorage mediaStorage
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.directRoomPairRepository = directRoomPairRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageAttachmentRepository = messageAttachmentRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.messageReadReceiptRepository = messageReadReceiptRepository;
        this.messageMentionRepository = messageMentionRepository;
        this.pinnedMessageRepository = pinnedMessageRepository;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.messageEventPublisher = messageEventPublisher;
        this.presenceService = presenceService;
        this.typingService = typingService;
        this.mediaStorage = mediaStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomDto> listRooms(UUID userId) {
        List<RoomMember> memberships = roomMemberRepository.findActiveMemberships(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> roomIds = memberships.stream()
                .map(m -> m.getRoom().getId())
                .toList();

        Map<UUID, DirectRoomPair> pairs = directRoomPairRepository.findByRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(DirectRoomPair::getRoomId, Function.identity()));

        Map<UUID, Long> memberCounts = roomMemberRepository.countActiveMembers(roomIds).stream()
                .collect(Collectors.toMap(
                        RoomMemberRepository.MemberCount::getRoomId,
                        RoomMemberRepository.MemberCount::getMemberCount
                ));

        Map<UUID, ChatMessage> latestByRoom = loadLatestMessages(roomIds);

        List<UUID> dmPeerIds = pairs.values().stream()
                .map(pair -> pair.otherUser(userId).getId())
                .distinct()
                .toList();
        Set<UUID> onlinePeers = presenceService.filterVisibleOnline(dmPeerIds);

        List<RoomDto> result = new ArrayList<>(memberships.size());
        for (RoomMember membership : memberships) {
            ChatRoom room = membership.getRoom();
            RoomType type = room.getType();
            ChatMessage latest = latestByRoom.get(room.getId());
            DirectRoomPair pair = pairs.get(room.getId());
            boolean online = type == RoomType.direct
                    && pair != null
                    && onlinePeers.contains(pair.otherUser(userId).getId());

            result.add(toRoomDto(
                    room,
                    membership,
                    memberCounts.getOrDefault(room.getId(), 0L).intValue(),
                    latest,
                    online,
                    pair,
                    userId
            ));
        }
        return result;
    }

    @Override
    public List<MessageDto> listMessages(UUID userId, UUID roomId, int limit) {
        // GET is read-only — clients mark read via POST /rooms/{id}/read.
        requireMembership(roomId, userId);
        int pageSize = normalizeLimit(limit);

        List<ChatMessage> newestFirst = chatMessageRepository.findRecentByRoomId(
                roomId, PageRequest.of(0, pageSize));
        if (newestFirst.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);

        List<RoomMember> peers = roomMemberRepository.findOtherActiveMembers(roomId, userId);
        List<UUID> peerLastReadIds = peers.stream()
                .map(RoomMember::getLastReadMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Instant> lastReadCreatedAtById = peerLastReadIds.isEmpty()
                ? Map.of()
                : chatMessageRepository.findAllById(peerLastReadIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, ChatMessage::getCreatedAt));
        List<Instant> peerReadCursors = peers.stream()
                .map(RoomMember::getLastReadMessageId)
                .map(id -> id == null ? null : lastReadCreatedAtById.get(id))
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, MessageAttachment> attachments = firstAttachmentByMessage(chronological);
        List<UUID> replyTargetIds = chronological.stream()
                .map(ChatMessage::getReplyTo)
                .filter(Objects::nonNull)
                .filter(r -> !r.isDeleted())
                .map(ChatMessage::getId)
                .filter(id -> !attachments.containsKey(id))
                .distinct()
                .toList();
        if (!replyTargetIds.isEmpty()) {
            for (MessageAttachment attachment : messageAttachmentRepository.findByMessageIdIn(replyTargetIds)) {
                attachments.putIfAbsent(attachment.getMessage().getId(), attachment);
            }
        }
        Map<UUID, List<ReactionDto>> reactions = reactionsByMessage(
                chronological.stream().map(ChatMessage::getId).toList(),
                userId
        );

        List<MessageDto> dtos = new ArrayList<>(chronological.size());
        for (ChatMessage message : chronological) {
            boolean isRead = resolveIsRead(message, userId, peerReadCursors);
            ChatMessage reply = message.getReplyTo();
            MessageAttachment replyAttachment = reply != null && !reply.isDeleted()
                    ? attachments.get(reply.getId())
                    : null;
            dtos.add(ChatMapper.toMessageDto(
                    message,
                    userId,
                    attachments.get(message.getId()),
                    isRead,
                    reactions.getOrDefault(message.getId(), List.of()),
                    replyAttachment
            ));
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageSearchResultDto> searchMessages(UUID userId, UUID roomId, String query, int limit) {
        requireMembership(roomId, userId);
        String term = query != null ? query.trim() : "";
        if (term.isEmpty()) {
            throw ApiException.badRequest("validation_error", "q must not be blank");
        }
        if (term.length() > 100) {
            throw ApiException.badRequest("validation_error", "q must be at most 100 characters");
        }
        String escaped = escapeLike(term);
        int pageSize = normalizeLimit(limit);
        List<ChatMessage> matches = chatMessageRepository.searchByRoomIdAndContent(
                roomId,
                escaped,
                PageRequest.of(0, pageSize)
        );
        Map<UUID, MessageAttachment> attachments = firstAttachmentByMessage(matches);
        List<MessageSearchResultDto> results = new ArrayList<>(matches.size());
        for (ChatMessage message : matches) {
            results.add(toSearchResult(message, userId, term, attachments.get(message.getId())));
        }
        return results;
    }

    @Override
    public MessageDto sendMessage(UUID userId, UUID roomId, SendMessageRequest request) {
        RoomMember membership = requireMembership(roomId, userId);
        assertCanPost(membership);
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        String text = request.text().trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("validation_error", "text must not be blank");
        }

        ChatMessage replyTo = null;
        if (request.replyToId() != null) {
            if (!chatMessageRepository.existsActiveInRoom(request.replyToId(), roomId)) {
                throw ApiException.badRequest("validation_error", "Invalid reply_to_id");
            }
            replyTo = chatMessageRepository.findActiveById(request.replyToId())
                    .orElseThrow(() -> ApiException.badRequest("validation_error", "Invalid reply_to_id"));
        }

        ChatRoom room = membership.getRoom();
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setType(MessageType.text);
        message.setContent(text);
        message.setReplyTo(replyTo);
        chatMessageRepository.saveAndFlush(message);
        saveMentions(message.getId(), roomId, text);

        room.touch();
        markRead(membership, message);
        roomMemberRepository.incrementUnreadForOthers(roomId, userId);
        typingService.clearTyping(roomId, userId);

        ChatMessage saved = chatMessageRepository.findActiveById(message.getId())
                .orElse(message);
        MessageDto dto = toMessageDto(saved, userId, null, false);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        messageEventPublisher.messageCreated(roomId, userId, memberIds, dto);
        return dto;
    }

    @Override
    public MessageDto editMessage(UUID userId, UUID roomId, UUID messageId, EditMessageRequest request) {
        String text = request.text().trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("validation_error", "text must not be blank");
        }
        ChatMessage message = requireOwnModifiableMessage(userId, roomId, messageId, "edit_window_expired");
        if (message.getType() != MessageType.text) {
            throw ApiException.badRequest("validation_error", "Only text messages can be edited");
        }
        message.setContent(text);
        message.setEditedAt(Instant.now());
        chatMessageRepository.saveAndFlush(message);
        messageMentionRepository.deleteByMessageId(messageId);
        saveMentions(messageId, roomId, text);

        ChatMessage saved = chatMessageRepository.findActiveById(messageId).orElse(message);
        MessageAttachment attachment = messageAttachmentRepository.findByMessageIdIn(List.of(messageId))
                .stream().findFirst().orElse(null);
        MessageDto dto = toMessageDto(saved, userId, attachment, false);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        messageEventPublisher.messageUpdated(roomId, userId, memberIds, dto);
        return dto;
    }

    @Override
    public void deleteMessage(UUID userId, UUID roomId, UUID messageId) {
        ChatMessage message = requireOwnModifiableMessage(userId, roomId, messageId, "delete_window_expired");
        message.setDeletedAt(Instant.now());
        chatMessageRepository.saveAndFlush(message);
        pinnedMessageRepository.deleteByRoomIdAndMessageId(roomId, messageId);

        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        messageEventPublisher.messageDeleted(roomId, messageId.toString(), memberIds);
    }

    @Override
    public WipeMessagesResultDto wipeAllMyMessages(UUID userId) {
        Instant now = Instant.now();
        List<UUID> roomIds = roomMemberRepository.findActiveMemberships(userId).stream()
                .map(m -> m.getRoom().getId())
                .distinct()
                .toList();

        int total = wipeSentMessages(userId, now);
        int roomsLeft = roomIds.isEmpty() ? 0 : roomMemberRepository.leaveAllActiveForUser(userId, now);
        archiveRoomsWithNoActiveMembers(roomIds);

        return new WipeMessagesResultDto(total, roomsLeft);
    }

    private int wipeSentMessages(UUID userId, Instant now) {
        int total = 0;
        List<SenderMessageRow> pendingNotify = new ArrayList<>();
        while (true) {
            List<SenderMessageRow> batch = chatMessageRepository.findActiveIdsBySenderId(userId, WIPE_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            List<UUID> ids = batch.stream().map(SenderMessageRow::getId).toList();
            pinnedMessageRepository.deleteByMessageIdIn(ids);
            total += chatMessageRepository.softDeleteByIdIn(ids, now);
            pendingNotify.addAll(batch);
            if (batch.size() < WIPE_BATCH_SIZE) {
                break;
            }
        }
        if (!pendingNotify.isEmpty()) {
            List<SenderMessageRow> toNotify = List.copyOf(pendingNotify);
            runAfterCommit(() -> publishSenderMessageDeletions(toNotify));
        }
        return total;
    }

    private void archiveRoomsWithNoActiveMembers(List<UUID> roomIds) {
        for (UUID roomId : roomIds) {
            if (roomMemberRepository.findActiveMemberUserIds(roomId).isEmpty()) {
                chatRoomRepository.findById(roomId).ifPresent(room -> {
                    room.setArchived(true);
                    room.touch();
                    chatRoomRepository.save(room);
                });
            }
        }
    }

    private void reactivateMembershipIfNeeded(UUID userId, UUID roomId) {
        roomMemberRepository.findByRoomIdAndUserId(roomId, userId).ifPresent(membership -> {
            if (membership.getLeftAt() == null) {
                return;
            }
            membership.setLeftAt(null);
            membership.setUnreadCount(0);
            roomMemberRepository.save(membership);
            ChatRoom room = membership.getRoom();
            if (room.isArchived()) {
                room.setArchived(false);
                room.touch();
                chatRoomRepository.save(room);
            }
        });
    }

    private void publishSenderMessageDeletions(List<SenderMessageRow> rows) {
        Map<UUID, List<UUID>> byRoom = new LinkedHashMap<>();
        for (SenderMessageRow row : rows) {
            byRoom.computeIfAbsent(row.getRoomId(), ignored -> new ArrayList<>()).add(row.getId());
        }
        for (Map.Entry<UUID, List<UUID>> entry : byRoom.entrySet()) {
            UUID roomId = entry.getKey();
            List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
            for (UUID messageId : entry.getValue()) {
                messageEventPublisher.messageDeleted(roomId, messageId.toString(), memberIds);
            }
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    public MessageDto toggleReaction(UUID userId, UUID roomId, UUID messageId, ReactMessageRequest request) {
        requireMembership(roomId, userId);
        String emoji = request.emoji() != null ? request.emoji().trim() : "";
        if (emoji.isEmpty() || !ALLOWED_REACTION_EMOJIS.contains(emoji)) {
            throw ApiException.badRequest("validation_error", "Unsupported reaction emoji");
        }
        ChatMessage message = chatMessageRepository.findActiveById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        if (!message.getRoom().getId().equals(roomId)) {
            throw ApiException.notFound("Message not found");
        }

        var existing = messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);
        if (existing.isPresent()) {
            messageReactionRepository.delete(existing.get());
            messageReactionRepository.flush();
        } else {
            MessageReaction reaction = new MessageReaction();
            reaction.setMessageId(messageId);
            reaction.setUserId(userId);
            reaction.setEmoji(emoji);
            messageReactionRepository.saveAndFlush(reaction);
        }

        MessageAttachment attachment = messageAttachmentRepository.findByMessageIdIn(List.of(messageId))
                .stream().findFirst().orElse(null);
        MessageDto dto = toMessageDto(message, userId, attachment, false);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        // Fanout keys is_mine off message author, not the reactor.
        UUID authorId = message.getSender() != null ? message.getSender().getId() : userId;
        messageEventPublisher.messageUpdated(roomId, authorId, memberIds, dto);
        return dto;
    }

    @Override
    public MessageDto sendMediaMessage(
            UUID userId,
            UUID roomId,
            org.springframework.web.multipart.MultipartFile file,
            String caption
    ) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("validation_error", "file is required");
        }
        if (file.getSize() > MAX_MEDIA_BYTES) {
            throw ApiException.badRequest("validation_error", "file exceeds 25MB limit");
        }
        RoomMember membership = requireMembership(roomId, userId);
        assertCanPost(membership);
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        String contentType = normalizeContentType(file.getContentType());
        MessageType type = contentType.startsWith("image/") ? MessageType.image : MessageType.file;
        String originalName = sanitizeFileName(file.getOriginalFilename());

        MediaStorage.StoredObject stored;
        try {
            stored = mediaStorage.store(roomId, file);
        } catch (IOException ex) {
            throw ApiException.badRequest("upload_failed", "Could not store file");
        }

        ChatRoom room = membership.getRoom();
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setType(type);
        message.setContent(caption != null ? caption.trim() : "");
        chatMessageRepository.saveAndFlush(message);

        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);
        attachment.setFileName(originalName);
        attachment.setFileSize(stored.size() > 0 ? stored.size() : file.getSize());
        attachment.setMimeType(contentType);
        attachment.setS3Key(stored.key());
        messageAttachmentRepository.save(attachment);

        room.touch();
        markRead(membership, message);
        roomMemberRepository.incrementUnreadForOthers(roomId, userId);
        typingService.clearTyping(roomId, userId);

        ChatMessage saved = chatMessageRepository.findActiveById(message.getId())
                .orElse(message);
        MessageDto dto = toMessageDto(saved, userId, attachment, false);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        messageEventPublisher.messageCreated(roomId, userId, memberIds, dto);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageAttachment requireAttachmentForUser(UUID userId, UUID attachmentId) {
        MessageAttachment attachment = messageAttachmentRepository.findByIdWithMessageAndRoom(attachmentId)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
        UUID roomId = attachment.getMessage().getRoom().getId();
        requireMembership(roomId, userId);
        return attachment;
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("*")) {
            return "application/octet-stream";
        }
        return raw.trim().toLowerCase();
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"\\\\]", "_").trim();
        return name.isBlank() ? "file" : name;
    }

    @Override
    public void registerDevice(UUID userId, RegisterDeviceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        String fcmToken = request.fcmToken().trim();
        String platform = request.platform() == null || request.platform().isBlank()
                ? "android"
                : request.platform().trim().toLowerCase();
        String deviceName = request.deviceName().trim();

        saveRegisteredDevice(user, fcmToken, platform, deviceName, request.utcOffsetMinutes());

        // Token rotation: one real FCM row per user + device name (keep dev: session row).
        if (!fcmToken.startsWith("dev:")) {
            for (UserDevice other : userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(userId)) {
                if (other.getFcmToken() == null) {
                    continue;
                }
                String otherToken = other.getFcmToken();
                if (!otherToken.startsWith("dev:")
                        && !otherToken.equals(fcmToken)
                        && deviceName.equals(other.getDeviceName())) {
                    userDeviceRepository.delete(other);
                }
            }
        }
    }

    private void saveRegisteredDevice(
            User user,
            String fcmToken,
            String platform,
            String deviceName,
            Integer utcOffsetMinutes
    ) {
        UserDevice device = userDeviceRepository.findByFcmToken(fcmToken).orElseGet(UserDevice::new);
        applyDeviceFields(device, user, fcmToken, platform, deviceName, utcOffsetMinutes);
        try {
            userDeviceRepository.save(device);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent POST /devices with the same token — reload and upsert.
            UserDevice existing = userDeviceRepository.findByFcmToken(fcmToken)
                    .orElseThrow(() -> ex);
            applyDeviceFields(existing, user, fcmToken, platform, deviceName, utcOffsetMinutes);
            userDeviceRepository.save(existing);
        }
    }

    private static void applyDeviceFields(
            UserDevice device,
            User user,
            String fcmToken,
            String platform,
            String deviceName,
            Integer utcOffsetMinutes
    ) {
        device.setUser(user);
        device.setFcmToken(fcmToken);
        device.setDeviceName(deviceName);
        device.setPlatform(platform);
        device.setLastActiveAt(Instant.now());
        if (utcOffsetMinutes != null) {
            device.setUtcOffsetMinutes(utcOffsetMinutes);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDto> listContacts(UUID userId) {
        List<User> users = userRepository.findActiveExcluding(userId);
        Set<UUID> online = presenceService.filterVisibleOnline(
                users.stream().map(User::getId).toList());
        List<ContactDto> contacts = new ArrayList<>(users.size());
        for (User user : users) {
            boolean isOnline = online.contains(user.getId());
            String subtitle = isOnline
                    ? "online"
                    : ChatTimeFormat.formatLastSeen(user.getLastSeenAt());
            contacts.add(new ContactDto(
                    user.getId().toString(),
                    user.getDisplayName(),
                    subtitle,
                    isOnline,
                    user.getEmail() != null ? user.getEmail() : "",
                    UserServiceImpl.publicAvatarUrl(user)
            ));
        }
        return contacts;
    }

    @Override
    public DirectRoomDto openOrCreateDirectRoom(UUID userId, UUID peerUserId) {
        if (userId.equals(peerUserId)) {
            throw ApiException.badRequest("validation_error", "Cannot open a direct chat with yourself");
        }
        User me = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        User peer = userRepository.findById(peerUserId)
                .filter(u -> u.getStatus() == com.kchat.common.enums.UserStatus.active)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        UUID a = UuidOrder.smaller(userId, peerUserId);
        UUID b = UuidOrder.larger(userId, peerUserId);
        Optional<DirectRoomPair> existing = directRoomPairRepository.findByOrderedUserPair(a, b);
        if (existing.isPresent()) {
            UUID roomId = existing.get().getRoomId();
            reactivateMembershipIfNeeded(userId, roomId);
            return new DirectRoomDto(roomId.toString());
        }
        assertCanStartDirectChat(userId, peerUserId);
        try {
            return createDirectRoom(me, peer, a, b);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            return directRoomPairRepository.findByOrderedUserPair(a, b)
                    .map(pair -> new DirectRoomDto(pair.getRoomId().toString()))
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Enforces the peer's {@code privacy_dm} when starting a <em>new</em> direct chat.
     * Existing DMs always reopen.
     * <ul>
     *   <li>{@code everyone} — anyone may start</li>
     *   <li>{@code contacts} — only users who already share a room/group</li>
     *   <li>{@code none} — nobody may start a new DM</li>
     * </ul>
     */
    private void assertCanStartDirectChat(UUID initiatorId, UUID peerUserId) {
        UserSettings peerSettings = userSettingsRepository.findById(peerUserId)
                .orElse(null);
        String privacy = peerSettings != null ? peerSettings.getPrivacyDm() : "everyone";
        if (privacy == null || privacy.isBlank()) {
            privacy = "everyone";
        }
        privacy = privacy.toLowerCase(Locale.ROOT);

        if ("none".equals(privacy)) {
            throw ApiException.forbidden("Người này không nhận tin nhắn riêng mới");
        }
        if ("contacts".equals(privacy)) {
            boolean isContact = roomMemberRepository.findPeerUserIds(peerUserId).contains(initiatorId);
            if (!isContact) {
                throw ApiException.forbidden(
                        "Người này chỉ nhận tin nhắn riêng từ người đã từng chat chung");
            }
        }
    }

    private DirectRoomDto createDirectRoom(User me, User peer, UUID orderedA, UUID orderedB) {
        ChatRoom room = new ChatRoom();
        room.setType(RoomType.direct);
        room.setCreatedBy(me);
        room.setArchived(false);
        applyDefaultDisappearing(room, me.getId());
        chatRoomRepository.save(room);

        RoomMember memberMe = new RoomMember();
        memberMe.setRoom(room);
        memberMe.setUser(me);
        RoomMember memberPeer = new RoomMember();
        memberPeer.setRoom(room);
        memberPeer.setUser(peer);
        roomMemberRepository.save(memberMe);
        roomMemberRepository.save(memberPeer);

        DirectRoomPair pair = new DirectRoomPair();
        pair.setRoom(room);
        User userA = me.getId().equals(orderedA) ? me : peer;
        User userB = me.getId().equals(orderedB) ? me : peer;
        pair.setUserA(userA);
        pair.setUserB(userB);
        directRoomPairRepository.save(pair);

        return new DirectRoomDto(room.getId().toString());
    }

    private void applyDefaultDisappearing(ChatRoom room, UUID creatorId) {
        userSettingsRepository.findById(creatorId)
                .map(UserSettings::getDefaultDisappearingSeconds)
                .filter(Objects::nonNull)
                .ifPresent(room::setDisappearingAfterSeconds);
    }

    @Override
    public RoomDto createGroup(UUID userId, CreateGroupRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("validation_error", "name must not be blank");
        }
        User me = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        LinkedHashSet<UUID> memberIds = new LinkedHashSet<>(request.memberIds());
        memberIds.remove(userId);
        if (memberIds.isEmpty()) {
            throw ApiException.badRequest("validation_error", "Select at least one other member");
        }
        if (memberIds.size() + 1 > MAX_GROUP_MEMBERS) {
            throw ApiException.badRequest("validation_error", "Group supports at most " + MAX_GROUP_MEMBERS + " members");
        }

        List<User> members = userRepository.findActiveByIdIn(memberIds);
        if (members.size() != memberIds.size()) {
            throw ApiException.badRequest("validation_error", "One or more users not found");
        }

        ChatRoom room = new ChatRoom();
        room.setType(RoomType.group);
        room.setName(name);
        room.setCreatedBy(me);
        room.setArchived(false);
        applyDefaultDisappearing(room, me.getId());
        chatRoomRepository.save(room);

        List<RoomMember> memberships = new ArrayList<>(members.size() + 1);
        RoomMember owner = new RoomMember();
        owner.setRoom(room);
        owner.setUser(me);
        owner.setRole(RoomRoles.OWNER);
        memberships.add(owner);
        for (User member : members) {
            RoomMember membership = new RoomMember();
            membership.setRoom(room);
            membership.setUser(member);
            membership.setRole(RoomRoles.MEMBER);
            memberships.add(membership);
        }
        roomMemberRepository.saveAll(memberships);

        return toRoomDto(room, owner, memberships.size(), null, false, null, userId);
    }

    @Override
    @Transactional
    public RoomDto muteRoom(UUID userId, UUID roomId, MuteRoomRequest request) {
        RoomMember membership = requireMembership(roomId, userId);
        int duration = request.durationSeconds();
        if (!RoomMuteOptions.isValidDuration(duration)) {
            throw ApiException.badRequest("validation_error", "Invalid mute duration");
        }
        membership.setMutedUntil(RoomMuteOptions.resolveMutedUntil(duration, Instant.now()));
        roomMemberRepository.save(membership);

        ChatRoom room = membership.getRoom();
        int count = roomMemberRepository.findActiveMemberUserIds(roomId).size();
        ChatMessage latest = loadLatestMessages(List.of(roomId)).get(room.getId());
        DirectRoomPair pair = directRoomPairRepository.findByRoomIdIn(List.of(roomId)).stream()
                .findFirst()
                .orElse(null);
        boolean online = resolveOnlineForRoom(room, pair, userId);
        return toRoomDto(room, membership, count, latest, online, pair, userId);
    }

    private boolean resolveOnlineForRoom(ChatRoom room, DirectRoomPair pair, UUID userId) {
        if (room.getType() != RoomType.direct || pair == null) {
            return false;
        }
        UUID peerId = pair.otherUser(userId).getId();
        return presenceService.filterVisibleOnline(List.of(peerId)).contains(peerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberDto> listMembers(UUID userId, UUID roomId) {
        requireMembership(roomId, userId);
        List<RoomMember> members = roomMemberRepository.findActiveMembersWithUser(roomId);
        Set<UUID> online = presenceService.filterVisibleOnline(
                members.stream().map(m -> m.getUser().getId()).toList());
        List<RoomMemberDto> result = new ArrayList<>(members.size());
        for (RoomMember member : members) {
            UUID id = member.getUser().getId();
            boolean isMe = id.equals(userId);
            result.add(new RoomMemberDto(
                    id.toString(),
                    member.getUser().getUsername(),
                    isMe ? "Bạn" : member.getUser().getDisplayName(),
                    member.getRole(),
                    online.contains(id),
                    isMe
            ));
        }
        return result;
    }

    @Override
    public void addMembers(UUID userId, UUID roomId, AddMembersRequest request) {
        RoomMember actor = requireMembership(roomId, userId);
        ChatRoom room = actor.getRoom();
        if (room.getType() == RoomType.channel) {
            throw ApiException.forbidden("Channel cố định tự thêm thành viên khi đăng ký");
        }
        if (room.getType() != RoomType.group) {
            throw ApiException.badRequest("validation_error", "Not a group room");
        }
        if (!RoomRoles.isManager(actor.getRole())) {
            throw ApiException.forbidden("Only owner or admin can manage the group");
        }

        LinkedHashSet<UUID> ids = new LinkedHashSet<>(request.userIds());
        ids.remove(userId);
        if (ids.isEmpty()) {
            return;
        }

        List<User> users = userRepository.findActiveByIdIn(ids);
        if (users.size() != ids.size()) {
            throw ApiException.badRequest("validation_error", "One or more users not found");
        }

        Instant now = Instant.now();
        for (User user : users) {
            RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(roomId, user.getId())
                    .orElse(null);
            if (existing != null && existing.getLeftAt() == null) {
                continue;
            }
            if (existing != null) {
                existing.setLeftAt(null);
                existing.setJoinedAt(now);
                existing.setRole(RoomRoles.MEMBER);
                existing.setUnreadCount(0);
                roomMemberRepository.save(existing);
            } else {
                RoomMember membership = new RoomMember();
                membership.setRoom(room);
                membership.setUser(user);
                membership.setRole(RoomRoles.MEMBER);
                roomMemberRepository.save(membership);
            }
        }
        if (roomMemberRepository.findActiveMemberUserIds(roomId).size() > MAX_GROUP_MEMBERS) {
            throw ApiException.badRequest(
                    "validation_error",
                    "Group supports at most " + MAX_GROUP_MEMBERS + " members");
        }
        room.touch();
    }

    @Override
    public void removeMember(UUID userId, UUID roomId, UUID targetUserId) {
        RoomMember actor = requireMembership(roomId, userId);
        ChatRoom room = actor.getRoom();
        if (room.getType() == RoomType.channel) {
            throw ApiException.forbidden("Channel cố định không hỗ trợ xóa thành viên");
        }
        if (room.getType() != RoomType.group) {
            throw ApiException.badRequest("validation_error", "Not a group room");
        }
        if (!RoomRoles.isManager(actor.getRole())) {
            throw ApiException.forbidden("Only owner or admin can manage the group");
        }
        if (userId.equals(targetUserId)) {
            throw ApiException.badRequest("validation_error", "Use leave to remove yourself");
        }
        RoomMember target = roomMemberRepository.findActiveMembership(roomId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (RoomRoles.OWNER.equals(target.getRole())) {
            throw ApiException.forbidden("Cannot remove the group owner");
        }
        if (RoomRoles.ADMIN.equals(target.getRole()) && !RoomRoles.OWNER.equals(actor.getRole())) {
            throw ApiException.forbidden("Only the owner can remove an admin");
        }
        target.setLeftAt(Instant.now());
        roomMemberRepository.save(target);
        room.touch();
    }

    @Override
    public void leaveRoom(UUID userId, UUID roomId) {
        RoomMember membership = requireMembership(roomId, userId);
        ChatRoom room = membership.getRoom();
        if (room.getType() == RoomType.channel) {
            throw ApiException.forbidden("Không thể rời channel cố định");
        }
        if (room.getType() != RoomType.group) {
            throw ApiException.badRequest("validation_error", "Cannot leave a direct chat");
        }

        if (RoomRoles.OWNER.equals(membership.getRole())) {
            List<RoomMember> others = roomMemberRepository.findOtherActiveMembers(roomId, userId);
            pickOwnershipSuccessor(others).ifPresent(successor -> {
                successor.setRole(RoomRoles.OWNER);
                roomMemberRepository.save(successor);
            });
        }

        membership.setLeftAt(Instant.now());
        if (RoomRoles.OWNER.equals(membership.getRole())) {
            membership.setRole(RoomRoles.MEMBER);
        }
        roomMemberRepository.save(membership);

        if (roomMemberRepository.findActiveMemberUserIds(roomId).isEmpty()) {
            room.setArchived(true);
        }
        room.touch();
    }

    @Override
    public RoomDto renameRoom(UUID userId, UUID roomId, RenameRoomRequest request) {
        return updateRoom(userId, roomId, new UpdateRoomRequest(request.name(), null));
    }

    @Override
    public RoomDto updateRoom(UUID userId, UUID roomId, UpdateRoomRequest request) {
        if (request.name() == null && request.disappearingAfterSeconds() == null) {
            throw ApiException.badRequest("validation_error", "No fields to update");
        }

        RoomMember membership = requireMembership(roomId, userId);
        ChatRoom room = membership.getRoom();

        if (request.name() != null) {
            if (room.getType() != RoomType.group) {
                throw ApiException.badRequest("validation_error", "Not a group room");
            }
            if (!RoomRoles.isManager(membership.getRole())) {
                throw ApiException.forbidden("Only owner or admin can manage the group");
            }
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw ApiException.badRequest("validation_error", "name must not be blank");
            }
            room.setName(name);
        }

        if (request.disappearingAfterSeconds() != null) {
            applyDisappearingUpdate(room, membership, request.disappearingAfterSeconds());
        }

        room.touch();
        int count = roomMemberRepository.findActiveMemberUserIds(roomId).size();
        DirectRoomPair pair = directRoomPairRepository.findByRoomIdIn(List.of(roomId)).stream()
                .findFirst()
                .orElse(null);
        ChatMessage latest = loadLatestMessages(List.of(roomId)).get(room.getId());
        boolean online = resolveOnlineForRoom(room, pair, userId);
        return toRoomDto(room, membership, count, latest, online, pair, userId);
    }

    private void applyDisappearingUpdate(ChatRoom room, RoomMember membership, int seconds) {
        if (room.getType() == RoomType.channel) {
            throw ApiException.forbidden("Channel không hỗ trợ tin biến mất");
        }
        if (room.getType() == RoomType.group && !RoomRoles.isManager(membership.getRole())) {
            throw ApiException.forbidden("Chỉ owner/admin mới đổi tin biến mất");
        }
        if (!MessageStorageOptions.isValidDefaultDisappearing(seconds)) {
            throw ApiException.badRequest("validation_error", "Invalid disappearing duration");
        }
        if (seconds == MessageStorageOptions.CLEAR_DEFAULT_DISAPPEARING) {
            room.setDisappearingAfterSeconds(null);
        } else {
            room.setDisappearingAfterSeconds(seconds);
        }
    }

    private RoomMember requireGroupManager(UUID roomId, UUID userId) {
        RoomMember membership = requireMembership(roomId, userId);
        ChatRoom room = membership.getRoom();
        if (room.getType() != RoomType.group) {
            throw ApiException.badRequest("validation_error", "Not a group room");
        }
        if (!RoomRoles.isManager(membership.getRole())) {
            throw ApiException.forbidden("Only owner or admin can manage the group");
        }
        return membership;
    }

    private static java.util.Optional<RoomMember> pickOwnershipSuccessor(List<RoomMember> others) {
        if (others.isEmpty()) {
            return java.util.Optional.empty();
        }
        return others.stream()
                .sorted(java.util.Comparator
                        .comparingInt((RoomMember m) -> RoomRoles.ADMIN.equals(m.getRole()) ? 0 : 1)
                        .thenComparing(RoomMember::getJoinedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .findFirst();
    }

    private RoomDto toRoomDto(
            ChatRoom room,
            RoomMember membership,
            int memberCount,
            ChatMessage latest,
            boolean online,
            DirectRoomPair pair,
            UUID viewerId
    ) {
        Instant now = Instant.now();
        Instant mutedUntil = membership.getMutedUntil();
        boolean isMuted = RoomMuteOptions.isMuted(mutedUntil, now);
        Long mutedUntilEpoch = mutedUntil != null ? mutedUntil.toEpochMilli() : null;
        RoomType type = room.getType();
        return new RoomDto(
                room.getId().toString(),
                resolveTitle(room, pair, viewerId),
                ChatMapper.previewText(latest, type),
                ChatTimeFormat.format(latest != null ? latest.getCreatedAt() : room.getUpdatedAt()),
                membership.getUnreadCount(),
                online,
                type == RoomType.channel,
                type == RoomType.group,
                memberCount,
                room.getDisappearingAfterSeconds(),
                membership.getRole(),
                isMuted,
                mutedUntilEpoch
        );
    }

    @Override
    public void markRoomRead(UUID userId, UUID roomId) {
        RoomMember membership = requireMembership(roomId, userId);
        List<ChatMessage> newest = chatMessageRepository.findRecentByRoomId(
                roomId, PageRequest.of(0, 1));
        ChatMessage latest = newest.isEmpty() ? null : newest.get(0);

        UUID previousLastReadId = membership.getLastReadMessageId();
        Instant previousCursor = Instant.EPOCH;
        if (previousLastReadId != null) {
            previousCursor = chatMessageRepository.findById(previousLastReadId)
                    .map(ChatMessage::getCreatedAt)
                    .orElse(Instant.EPOCH);
        }

        boolean advanced = latest != null
                && latest.getCreatedAt() != null
                && !Objects.equals(previousLastReadId, latest.getId());

        // Persist cursor/unread first — native receipt insert must not clear this entity.
        markRead(membership, latest);
        roomMemberRepository.saveAndFlush(membership);

        if (advanced) {
            Instant readAt = Instant.now();
            messageReadReceiptRepository.insertReceiptsUpTo(
                    roomId,
                    userId,
                    latest.getCreatedAt(),
                    previousCursor,
                    readAt
            );
            List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
            messageEventPublisher.messagesRead(
                    roomId,
                    userId,
                    latest.getId().toString(),
                    latest.getCreatedAt().toEpochMilli(),
                    memberIds
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadReceiptDto> listMessageReceipts(UUID userId, UUID roomId, UUID messageId) {
        requireMembership(roomId, userId);
        ChatMessage message = chatMessageRepository.findActiveById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        if (!Objects.equals(message.getRoom().getId(), roomId)) {
            throw ApiException.notFound("Message not found");
        }
        UUID senderId = message.getSender() != null ? message.getSender().getId() : null;
        if (senderId == null || !Objects.equals(senderId, userId)) {
            throw ApiException.forbidden("Only the sender can view read receipts");
        }

        List<ReadReceiptDto> fromTable = messageReadReceiptRepository.findRowsByMessageId(messageId).stream()
                .map(row -> new ReadReceiptDto(
                        row.getDisplayName(),
                        ChatTimeFormat.formatMessageClock(row.getReadAt())
                ))
                .toList();
        if (!fromTable.isEmpty()) {
            return fromTable;
        }

        // Fallback when receipts were never written (legacy last_read only).
        // Do not invent a clock time from last_read message timestamp.
        Instant messageAt = message.getCreatedAt();
        if (messageAt == null) {
            return List.of();
        }
        List<RoomMember> peers = roomMemberRepository.findActiveMembersWithUser(roomId).stream()
                .filter(m -> !Objects.equals(m.getUser().getId(), userId))
                .toList();
        List<UUID> lastReadIds = peers.stream()
                .map(RoomMember::getLastReadMessageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Instant> lastReadAt = lastReadIds.isEmpty()
                ? Map.of()
                : chatMessageRepository.findAllById(lastReadIds).stream()
                        .filter(m -> m.getCreatedAt() != null)
                        .collect(Collectors.toMap(ChatMessage::getId, ChatMessage::getCreatedAt));
        List<ReadReceiptDto> derived = new ArrayList<>();
        for (RoomMember peer : peers) {
            UUID lastId = peer.getLastReadMessageId();
            Instant cursor = lastId == null ? null : lastReadAt.get(lastId);
            if (cursor != null && !cursor.isBefore(messageAt)) {
                derived.add(new ReadReceiptDto(peer.getUser().getDisplayName(), ""));
            }
        }
        return derived;
    }

    @Override
    @Transactional(readOnly = true)
    public PinnedMessageDto getPinnedMessage(UUID userId, UUID roomId) {
        requireMembership(roomId, userId);
        return pinnedMessageRepository.findLatestByRoomId(roomId)
                .map(this::toPinnedDto)
                .orElse(null);
    }

    @Override
    public PinnedMessageDto pinMessage(UUID userId, UUID roomId, PinMessageRequest request) {
        RoomMember membership = requireMembership(roomId, userId);
        assertChannelManagerIfChannel(membership);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (!chatMessageRepository.existsActiveInRoom(request.messageId(), roomId)) {
            throw ApiException.badRequest("validation_error", "Invalid message_id");
        }
        ChatMessage message = chatMessageRepository.findActiveById(request.messageId())
                .orElseThrow(() -> ApiException.notFound("Message not found"));

        // One pin per room for the banner UI.
        // deleteAllByRoomId clears the persistence context — do not re-query via JOIN FETCH
        // on the association; build the DTO from the message we already loaded.
        pinnedMessageRepository.deleteAllByRoomId(roomId);
        PinnedMessage pin = new PinnedMessage();
        pin.setRoomId(roomId);
        pin.setMessage(message);
        pin.setPinnedBy(actor);
        pinnedMessageRepository.saveAndFlush(pin);
        return new PinnedMessageDto(message.getId().toString(), pinPreview(message));
    }

    @Override
    public void unpinMessage(UUID userId, UUID roomId) {
        RoomMember membership = requireMembership(roomId, userId);
        assertChannelManagerIfChannel(membership);
        pinnedMessageRepository.deleteAllByRoomId(roomId);
    }

    private PinnedMessageDto toPinnedDto(PinnedMessage pin) {
        ChatMessage message = pin.getMessage();
        String id = message != null
                ? message.getId().toString()
                : pin.getMessageId().toString();
        return new PinnedMessageDto(id, pinPreview(message));
    }

    private static String pinPreview(ChatMessage message) {
        if (message == null) {
            return "Tin nhắn";
        }
        MessageType type = message.getType() != null ? message.getType() : MessageType.text;
        return switch (type) {
            case image -> "[Ảnh]";
            case file -> "[File]";
            case call_event -> "[Cuộc gọi]";
            case system -> {
                String c = message.getContent();
                yield c == null || c.isBlank() ? "[Bot]" : c;
            }
            case text -> {
                String c = message.getContent();
                yield c == null || c.isBlank() ? "Tin nhắn" : c;
            }
        };
    }

    private Map<UUID, ChatMessage> loadLatestMessages(List<UUID> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> latestIds = chatMessageRepository.findLatestMessageIdsByRoomIds(roomIds);
        if (latestIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageRepository.findAllWithSenderByIdIn(latestIds).stream()
                .collect(Collectors.toMap(m -> m.getRoom().getId(), Function.identity(), (a, b) -> a));
    }

    private RoomMember requireMembership(UUID roomId, UUID userId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw ApiException.notFound("Room not found");
        }
        RoomMember membership = roomMemberRepository.findActiveMembership(roomId, userId)
                .orElseThrow(() -> ApiException.forbidden("Not a member of this room"));
        if (membership.getRoom().isArchived()) {
            throw ApiException.forbidden("Room is archived");
        }
        return membership;
    }

    /** Own message, in room, within the 15-minute modify window. */
    private ChatMessage requireOwnModifiableMessage(
            UUID userId,
            UUID roomId,
            UUID messageId,
            String windowExpiredCode
    ) {
        requireMembership(roomId, userId);
        ChatMessage message = chatMessageRepository.findActiveById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        if (!message.getRoom().getId().equals(roomId)) {
            throw ApiException.notFound("Message not found");
        }
        if (message.getSender() == null || !userId.equals(message.getSender().getId())) {
            throw ApiException.forbidden("Only the sender can modify this message");
        }
        Instant created = message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now();
        if (Duration.between(created, Instant.now()).compareTo(EDIT_WINDOW) > 0) {
            throw ApiException.badRequest(windowExpiredCode, "Modify window is 15 minutes");
        }
        return message;
    }

    private MessageDto toMessageDto(
            ChatMessage message,
            UUID userId,
            MessageAttachment attachment,
            boolean isRead
    ) {
        MessageAttachment replyAttachment = null;
        ChatMessage reply = message.getReplyTo();
        if (reply != null && !reply.isDeleted()) {
            replyAttachment = messageAttachmentRepository.findByMessageIdIn(List.of(reply.getId()))
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
        return ChatMapper.toMessageDto(
                message,
                userId,
                attachment,
                isRead,
                aggregateReactions(messageReactionRepository.findByMessageId(message.getId()), userId),
                replyAttachment
        );
    }

    private Map<UUID, List<ReactionDto>> reactionsByMessage(List<UUID> messageIds, UUID viewerId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MessageReaction>> grouped = messageReactionRepository.findByMessageIdIn(messageIds)
                .stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));
        Map<UUID, List<ReactionDto>> result = new HashMap<>();
        for (Map.Entry<UUID, List<MessageReaction>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), aggregateReactions(entry.getValue(), viewerId));
        }
        return result;
    }

    private static List<ReactionDto> aggregateReactions(List<MessageReaction> rows, UUID viewerId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting()));
        Set<String> mine = rows.stream()
                .filter(r -> viewerId != null && viewerId.equals(r.getUserId()))
                .map(MessageReaction::getEmoji)
                .collect(Collectors.toSet());
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ReactionDto(e.getKey(), e.getValue().intValue(), mine.contains(e.getKey())))
                .toList();
    }

    private static MessageSearchResultDto toSearchResult(
            ChatMessage message,
            UUID viewerId,
            String query,
            MessageAttachment attachment
    ) {
        boolean mine = message.getSender() != null && viewerId.equals(message.getSender().getId());
        String author = mine
                ? "Bạn"
                : message.getSender() != null ? message.getSender().getDisplayName() : "System";
        String content = message.getContent() != null ? message.getContent() : "";
        String fileName = attachment != null && attachment.getFileName() != null
                ? attachment.getFileName()
                : "";
        String snippetSource = !content.isBlank()
                ? content
                : !fileName.isBlank()
                        ? fileName
                        : searchFallbackLabel(message.getType());
        return new MessageSearchResultDto(
                message.getId().toString(),
                author,
                ChatTimeFormat.formatMessageClock(message.getCreatedAt()),
                buildSearchSnippet(snippetSource, query),
                message.getCreatedAt() != null ? message.getCreatedAt().toEpochMilli() : null
        );
    }

    private static String searchFallbackLabel(MessageType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case image -> "[Ảnh]";
            case file -> "[File]";
            case call_event -> "[Cuộc gọi]";
            case system -> "[Bot]";
            case text -> "";
        };
    }

    private static String buildSearchSnippet(String content, String query) {
        if (content.isBlank()) {
            return "";
        }
        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int idx = lowerContent.indexOf(lowerQuery);
        if (idx < 0) {
            return truncate(content, 80);
        }
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + query.length() + 30);
        String excerpt = content.substring(start, end);
        if (start > 0) {
            excerpt = "..." + excerpt;
        }
        if (end < content.length()) {
            excerpt = excerpt + "...";
        }
        return excerpt;
    }

    private static String escapeLike(String raw) {
        StringBuilder out = new StringBuilder(raw.length() * 2);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '!' || c == '%' || c == '_') {
                out.append('!');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 1) + "…";
    }

    private void markRead(RoomMember membership, ChatMessage latest) {
        membership.setUnreadCount(0);
        if (latest != null) {
            membership.setLastReadMessageId(latest.getId());
        }
    }

    private String resolveTitle(ChatRoom room, DirectRoomPair pair, UUID userId) {
        if (room.getType() == RoomType.direct) {
            if (pair == null) {
                return "Direct";
            }
            return pair.otherUser(userId).getDisplayName();
        }
        if (room.getType() == RoomType.channel) {
            return ChannelRules.displayTitle(room.getName(), room.getSlug());
        }
        if (room.getName() != null && !room.getName().isBlank()) {
            return room.getName();
        }
        if (room.getSlug() != null && !room.getSlug().isBlank()) {
            return room.getSlug();
        }
        return "Chat";
    }

    private static void assertCanPost(RoomMember membership) {
        if (!ChannelRules.canPost(membership.getRoom().getType(), membership.getRole())) {
            throw ApiException.forbidden("Chỉ admin được gửi tin trong channel");
        }
    }

    private static void assertChannelManagerIfChannel(RoomMember membership) {
        ChatRoom room = membership.getRoom();
        if (room.getType() == RoomType.channel && !RoomRoles.isManager(membership.getRole())) {
            throw ApiException.forbidden("Chỉ admin được ghim tin trong channel");
        }
    }

    private Map<UUID, MessageAttachment> firstAttachmentByMessage(List<ChatMessage> messages) {
        List<UUID> ids = messages.stream().map(ChatMessage::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MessageAttachment> map = new HashMap<>();
        for (MessageAttachment attachment : messageAttachmentRepository.findByMessageIdIn(ids)) {
            map.putIfAbsent(attachment.getMessage().getId(), attachment);
        }
        return map;
    }

    private void saveMentions(UUID messageId, UUID roomId, String text) {
        Set<String> usernames = extractMentionUsernames(text);
        if (usernames.isEmpty()) {
            return;
        }
        Map<String, UUID> memberByUsername = roomMemberRepository.findActiveMembersWithUser(roomId).stream()
                .map(RoomMember::getUser)
                .filter(u -> u.getUsername() != null && !u.getUsername().isBlank())
                .collect(Collectors.toMap(
                        u -> u.getUsername().toLowerCase(Locale.ROOT),
                        User::getId,
                        (a, b) -> a
                ));
        List<MessageMention> mentions = new ArrayList<>();
        for (String username : usernames) {
            UUID mentionedId = memberByUsername.get(username.toLowerCase(Locale.ROOT));
            if (mentionedId == null) {
                continue;
            }
            MessageMention mention = new MessageMention();
            mention.setMessageId(messageId);
            mention.setUserId(mentionedId);
            mentions.add(mention);
        }
        if (!mentions.isEmpty()) {
            messageMentionRepository.saveAll(mentions);
        }
    }

    private static Set<String> extractMentionUsernames(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Matcher matcher = MENTION_PATTERN.matcher(text);
        Set<String> usernames = new LinkedHashSet<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return usernames;
    }

    private static boolean resolveIsRead(
            ChatMessage message,
            UUID me,
            List<Instant> peerReadCursors
    ) {
        if (message.getSender() == null || !Objects.equals(message.getSender().getId(), me)) {
            return false;
        }
        Instant createdAt = message.getCreatedAt();
        if (createdAt == null || peerReadCursors.isEmpty()) {
            return false;
        }
        // ✓✓ when at least one peer has read up to/past this message.
        return peerReadCursors.stream().anyMatch(cursor -> !cursor.isBefore(createdAt));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }
}
