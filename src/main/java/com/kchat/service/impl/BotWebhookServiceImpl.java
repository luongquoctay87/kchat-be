package com.kchat.service.impl;

import com.kchat.common.dto.chat.BotAlertRequest;
import com.kchat.common.dto.chat.CreateWebhookRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.WebhookCreatedDto;
import com.kchat.common.dto.chat.WebhookDto;
import com.kchat.common.enums.MessageType;
import com.kchat.common.enums.RoomRoles;
import com.kchat.common.enums.RoomType;
import com.kchat.common.exception.ApiException;
import com.kchat.common.util.BotAlertFormat;
import com.kchat.common.util.ChatTimeFormat;
import com.kchat.common.util.WebhookSecrets;
import com.kchat.config.BotProperties;
import com.kchat.entity.ChannelWebhook;
import com.kchat.entity.ChatMessage;
import com.kchat.entity.ChatRoom;
import com.kchat.entity.RoomMember;
import com.kchat.repository.ChannelWebhookRepository;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ChatRoomRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.security.TokenHasher;
import com.kchat.service.BotWebhookService;
import com.kchat.service.mapper.ChatMapper;
import com.kchat.ws.MessageEventPublisher;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BotWebhookServiceImpl implements BotWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BotWebhookServiceImpl.class);
    private static final String DEFAULT_WEBHOOK_NAME = "default";
    /** Stable id for dev/docs; matches kchat-seed.sql when present. */
    public static final UUID DEFAULT_OPS_WEBHOOK_ID =
            UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    /** Viewer id for bot messages — no real user has this id, so is_mine is always false. */
    private static final UUID BOT_VIEWER_ID = new UUID(0L, 0L);

    private final ChannelWebhookRepository channelWebhookRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageEventPublisher messageEventPublisher;
    private final BotProperties botProperties;

    public BotWebhookServiceImpl(
            ChannelWebhookRepository channelWebhookRepository,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            RoomMemberRepository roomMemberRepository,
            MessageEventPublisher messageEventPublisher,
            BotProperties botProperties
    ) {
        this.channelWebhookRepository = channelWebhookRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.messageEventPublisher = messageEventPublisher;
        this.botProperties = botProperties;
    }

    @Override
    public MessageDto ingestAlert(UUID webhookId, String plainSecret, BotAlertRequest request) {
        ChannelWebhook webhook = resolveActiveWebhook(webhookId, plainSecret);
        ChatRoom room = webhook.getRoom();

        String content = BotAlertFormat.format(request.title(), request.service(), request.text());
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(null);
        message.setType(MessageType.system);
        message.setContent(content);
        chatMessageRepository.saveAndFlush(message);

        room.touch();
        roomMemberRepository.incrementUnreadForAll(room.getId());

        ChatMessage saved = chatMessageRepository.findActiveById(message.getId()).orElse(message);
        MessageDto dto = ChatMapper.toMessageDto(saved, BOT_VIEWER_ID, null, false);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(room.getId());
        messageEventPublisher.messageCreated(room.getId(), null, memberIds, dto);
        return dto;
    }

    @Override
    public WebhookCreatedDto createWebhook(UUID userId, UUID roomId, CreateWebhookRequest request) {
        RoomMember membership = requireChannelManager(roomId, userId);
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("validation_error", "name must not be blank");
        }

        String plainSecret = TokenHasher.newRefreshToken();
        ChannelWebhook webhook = new ChannelWebhook();
        webhook.setRoom(membership.getRoom());
        webhook.setName(name);
        webhook.setSecretHash(WebhookSecrets.hash(plainSecret));
        webhook.setCreatedBy(membership.getUser());
        webhook.setActive(true);
        channelWebhookRepository.save(webhook);

        return new WebhookCreatedDto(
                webhook.getId().toString(),
                webhook.getName(),
                plainSecret,
                formatInstant(webhook.getCreatedAt())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookDto> listWebhooks(UUID userId, UUID roomId) {
        requireChannelManager(roomId, userId);
        return channelWebhookRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(w -> new WebhookDto(
                        w.getId().toString(),
                        w.getName(),
                        w.isActive(),
                        formatInstant(w.getCreatedAt())
                ))
                .toList();
    }

    @Override
    public void deleteWebhook(UUID userId, UUID roomId, UUID webhookId) {
        requireChannelManager(roomId, userId);
        ChannelWebhook webhook = channelWebhookRepository.findByIdWithRoom(webhookId)
                .orElseThrow(() -> ApiException.notFound("Webhook not found"));
        if (!webhook.getRoom().getId().equals(roomId)) {
            throw ApiException.notFound("Webhook not found");
        }
        channelWebhookRepository.delete(webhook);
    }

    @Override
    public void ensureDefaultWebhooks() {
        ChatRoom ops = chatRoomRepository.findBySlug("ops-alerts").orElse(null);
        if (ops == null || ops.getType() != RoomType.channel) {
            log.debug("Skip default webhook: #ops-alerts not ready");
            return;
        }
        if (channelWebhookRepository.findById(DEFAULT_OPS_WEBHOOK_ID).isPresent()
                || channelWebhookRepository.findByRoomSlugAndName("ops-alerts", DEFAULT_WEBHOOK_NAME).isPresent()) {
            return;
        }
        try {
            ChannelWebhook webhook = new ChannelWebhook();
            webhook.setId(DEFAULT_OPS_WEBHOOK_ID);
            webhook.setRoom(ops);
            webhook.setName(DEFAULT_WEBHOOK_NAME);
            webhook.setSecretHash(WebhookSecrets.hash(botProperties.getOpsAlertsSecret()));
            webhook.setActive(true);
            channelWebhookRepository.saveAndFlush(webhook);
            log.info("Default webhook ready: POST /hooks/{} (header X-Webhook-Secret)", DEFAULT_OPS_WEBHOOK_ID);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Default webhook already created concurrently");
        }
    }

    private ChannelWebhook resolveActiveWebhook(UUID webhookId, String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw ApiException.unauthorized("Invalid webhook credentials");
        }
        ChannelWebhook webhook = channelWebhookRepository.findByIdWithRoom(webhookId)
                .orElseThrow(() -> ApiException.unauthorized("Invalid webhook credentials"));
        if (!webhook.isActive()) {
            throw ApiException.forbidden("Webhook is disabled");
        }
        if (!WebhookSecrets.matches(plainSecret, webhook.getSecretHash())) {
            throw ApiException.unauthorized("Invalid webhook credentials");
        }
        ChatRoom room = webhook.getRoom();
        if (room.getType() != RoomType.channel || room.isArchived()) {
            throw ApiException.badRequest("validation_error", "Webhook is not bound to an active channel");
        }
        return webhook;
    }

    private RoomMember requireChannelManager(UUID roomId, UUID userId) {
        RoomMember membership = roomMemberRepository.findActiveMembership(roomId, userId)
                .orElseThrow(() -> ApiException.forbidden("Not a room member"));
        ChatRoom room = membership.getRoom();
        if (room.getType() != RoomType.channel) {
            throw ApiException.badRequest("validation_error", "Webhooks are only for channels");
        }
        if (!RoomRoles.isManager(membership.getRole())) {
            throw ApiException.forbidden("Only channel admin can manage webhooks");
        }
        return membership;
    }

    private static String formatInstant(java.time.Instant instant) {
        return instant != null ? ChatTimeFormat.format(instant) : "";
    }
}
