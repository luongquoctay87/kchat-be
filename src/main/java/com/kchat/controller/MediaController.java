package com.kchat.controller;

import com.kchat.common.exception.ApiException;
import com.kchat.entity.MessageAttachment;
import com.kchat.media.MediaHttp;
import com.kchat.media.MediaStorage;
import com.kchat.security.SecurityUtils;
import com.kchat.service.ChatService;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final ChatService chatService;
    private final MediaStorage mediaStorage;

    public MediaController(ChatService chatService, MediaStorage mediaStorage) {
        this.chatService = chatService;
        this.mediaStorage = mediaStorage;
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId) {
        MessageAttachment attachment = chatService.requireAttachmentForUser(
                SecurityUtils.requireUserId(), attachmentId);
        try {
            return MediaHttp.inline(mediaStorage.open(attachment.getS3Key()));
        } catch (IllegalArgumentException | IOException ex) {
            throw ApiException.notFound("Attachment file missing");
        }
    }
}
