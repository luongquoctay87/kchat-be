package com.kchat.controller;

import com.kchat.common.dto.chat.AddMembersRequest;
import com.kchat.common.dto.chat.CreateDirectRoomRequest;
import com.kchat.common.dto.chat.CreateGroupRequest;
import com.kchat.common.dto.chat.DirectRoomDto;
import com.kchat.common.dto.chat.EditMessageRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.MessageSearchResultDto;
import com.kchat.common.dto.chat.PinMessageRequest;
import com.kchat.common.dto.chat.PinnedMessageDto;
import com.kchat.common.dto.chat.ReactMessageRequest;
import com.kchat.common.dto.chat.ReadReceiptDto;
import com.kchat.common.dto.chat.UpdateRoomRequest;
import com.kchat.common.dto.chat.CreateWebhookRequest;
import com.kchat.common.dto.chat.WebhookCreatedDto;
import com.kchat.common.dto.chat.WebhookDto;
import com.kchat.common.dto.chat.RoomDto;
import com.kchat.common.dto.chat.RoomMemberDto;
import com.kchat.common.dto.chat.SendMessageRequest;
import com.kchat.common.dto.chat.MuteRoomRequest;
import com.kchat.common.dto.chat.TypingRequest;
import com.kchat.common.exception.ApiException;
import com.kchat.media.MediaHttp;
import com.kchat.media.MediaStorage;
import com.kchat.security.SecurityUtils;
import com.kchat.service.BotWebhookService;
import com.kchat.service.ChatService;
import com.kchat.ws.TypingRelayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rooms")
@Validated
public class RoomController {

    private final ChatService chatService;
    private final BotWebhookService botWebhookService;
    private final TypingRelayService typingRelayService;
    private final MediaStorage mediaStorage;

    public RoomController(
            ChatService chatService,
            BotWebhookService botWebhookService,
            TypingRelayService typingRelayService,
            MediaStorage mediaStorage
    ) {
        this.chatService = chatService;
        this.botWebhookService = botWebhookService;
        this.typingRelayService = typingRelayService;
        this.mediaStorage = mediaStorage;
    }

    @GetMapping
    public List<RoomDto> listRooms() {
        return chatService.listRooms(SecurityUtils.requireUserId());
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public DirectRoomDto openDirectRoom(@Valid @RequestBody CreateDirectRoomRequest request) {
        return chatService.openOrCreateDirectRoom(SecurityUtils.requireUserId(), request.userId());
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return chatService.createGroup(SecurityUtils.requireUserId(), request);
    }

    @PostMapping(path = "/{roomId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomDto updateGroupAvatar(
            @PathVariable UUID roomId,
            @RequestPart("file") MultipartFile file
    ) {
        return chatService.updateGroupAvatar(SecurityUtils.requireUserId(), roomId, file);
    }

    @GetMapping("/{roomId}/avatar")
    public ResponseEntity<Resource> getGroupAvatar(@PathVariable UUID roomId) {
        UUID userId = SecurityUtils.requireUserId();
        try {
            return MediaHttp.inline(
                    mediaStorage.open(chatService.requireGroupAvatarKey(userId, roomId)),
                    "private, max-age=86400");
        } catch (IllegalArgumentException | IOException ex) {
            throw ApiException.notFound("Avatar file missing");
        }
    }

    @GetMapping("/{roomId}/members")
    public List<RoomMemberDto> listMembers(@PathVariable UUID roomId) {
        return chatService.listMembers(SecurityUtils.requireUserId(), roomId);
    }

    @PostMapping("/{roomId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMembers(
            @PathVariable UUID roomId,
            @Valid @RequestBody AddMembersRequest request
    ) {
        chatService.addMembers(SecurityUtils.requireUserId(), roomId, request);
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID roomId, @PathVariable UUID userId) {
        chatService.removeMember(SecurityUtils.requireUserId(), roomId, userId);
    }

    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(@PathVariable UUID roomId) {
        chatService.leaveRoom(SecurityUtils.requireUserId(), roomId);
    }

    @PatchMapping("/{roomId}")
    public RoomDto updateRoom(
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return chatService.updateRoom(SecurityUtils.requireUserId(), roomId, request);
    }

    @GetMapping("/{roomId}/pin")
    public List<PinnedMessageDto> listPinned(@PathVariable UUID roomId) {
        return chatService.listPinnedMessages(SecurityUtils.requireUserId(), roomId);
    }

    @PutMapping("/{roomId}/pin")
    public PinnedMessageDto pinMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody PinMessageRequest request
    ) {
        return chatService.pinMessage(SecurityUtils.requireUserId(), roomId, request);
    }

    @DeleteMapping("/{roomId}/pin/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpinMessage(@PathVariable UUID roomId, @PathVariable UUID messageId) {
        chatService.unpinMessage(SecurityUtils.requireUserId(), roomId, messageId);
    }

    @PostMapping("/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRoomRead(@PathVariable UUID roomId) {
        chatService.markRoomRead(SecurityUtils.requireUserId(), roomId);
    }

    @PostMapping("/{roomId}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relayTyping(
            @PathVariable UUID roomId,
            @RequestBody(required = false) TypingRequest request
    ) {
        boolean typing = request == null || request.typing();
        typingRelayService.relayTyping(SecurityUtils.requireUserId(), roomId, typing);
    }

    @PatchMapping("/{roomId}/members/me/mute")
    public RoomDto muteRoom(
            @PathVariable UUID roomId,
            @Valid @RequestBody MuteRoomRequest request
    ) {
        return chatService.muteRoom(SecurityUtils.requireUserId(), roomId, request);
    }

    @GetMapping("/{roomId}/messages/{messageId}/receipts")
    public List<ReadReceiptDto> listMessageReceipts(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId
    ) {
        return chatService.listMessageReceipts(SecurityUtils.requireUserId(), roomId, messageId);
    }

    @GetMapping("/{roomId}/messages")
    public List<MessageDto> listMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return chatService.listMessages(SecurityUtils.requireUserId(), roomId, limit);
    }

    @GetMapping("/{roomId}/search")
    public List<MessageSearchResultDto> searchMessages(
            @PathVariable UUID roomId,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return chatService.searchMessages(SecurityUtils.requireUserId(), roomId, query, limit);
    }

    @PostMapping("/{roomId}/messages")
    public MessageDto sendMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return chatService.sendMessage(SecurityUtils.requireUserId(), roomId, request);
    }

    @PatchMapping("/{roomId}/messages/{messageId}")
    public MessageDto editMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        return chatService.editMessage(SecurityUtils.requireUserId(), roomId, messageId, request);
    }

    @DeleteMapping("/{roomId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId
    ) {
        chatService.deleteMessage(SecurityUtils.requireUserId(), roomId, messageId);
    }

    @PutMapping("/{roomId}/messages/{messageId}/reactions")
    public MessageDto toggleReaction(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ReactMessageRequest request
    ) {
        return chatService.toggleReaction(SecurityUtils.requireUserId(), roomId, messageId, request);
    }

    @PostMapping(path = "/{roomId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto sendMedia(
            @PathVariable UUID roomId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption
    ) {
        return chatService.sendMediaMessage(SecurityUtils.requireUserId(), roomId, file, caption);
    }

    @GetMapping("/{roomId}/webhooks")
    public List<WebhookDto> listWebhooks(@PathVariable UUID roomId) {
        return botWebhookService.listWebhooks(SecurityUtils.requireUserId(), roomId);
    }

    @PostMapping("/{roomId}/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookCreatedDto createWebhook(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateWebhookRequest request
    ) {
        return botWebhookService.createWebhook(SecurityUtils.requireUserId(), roomId, request);
    }

    @DeleteMapping("/{roomId}/webhooks/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWebhook(@PathVariable UUID roomId, @PathVariable UUID webhookId) {
        botWebhookService.deleteWebhook(SecurityUtils.requireUserId(), roomId, webhookId);
    }
}
