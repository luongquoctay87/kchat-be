package com.kchat.service;

import com.kchat.common.dto.chat.BotAlertRequest;
import com.kchat.common.dto.chat.CreateWebhookRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.WebhookCreatedDto;
import com.kchat.common.dto.chat.WebhookDto;
import java.util.List;
import java.util.UUID;

public interface BotWebhookService {

    MessageDto ingestAlert(UUID webhookId, String plainSecret, BotAlertRequest request);

    WebhookCreatedDto createWebhook(UUID userId, UUID roomId, CreateWebhookRequest request);

    List<WebhookDto> listWebhooks(UUID userId, UUID roomId);

    void deleteWebhook(UUID userId, UUID roomId, UUID webhookId);

    /** Ensures default ops-alerts webhook exists (startup). */
    void ensureDefaultWebhooks();
}
