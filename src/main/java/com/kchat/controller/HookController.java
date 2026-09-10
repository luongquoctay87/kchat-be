package com.kchat.controller;

import com.kchat.common.dto.chat.BotAlertRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.service.BotWebhookService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public inbound webhook for bot alerts (#17). Auth via X-Webhook-Secret, not JWT. */
@RestController
@RequestMapping("/hooks")
public class HookController {

  private final BotWebhookService botWebhookService;

  public HookController(BotWebhookService botWebhookService) {
    this.botWebhookService = botWebhookService;
  }

  @PostMapping("/{webhookId}")
  @ResponseStatus(HttpStatus.CREATED)
  public MessageDto ingest(
      @PathVariable UUID webhookId,
      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
      @Valid @RequestBody BotAlertRequest request) {
    return botWebhookService.ingestAlert(webhookId, secret, request);
  }
}
