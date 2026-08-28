package com.kchat.config;

import com.kchat.service.BotWebhookService;
import com.kchat.service.ChannelEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class ChannelBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelBootstrap.class);

    private final ChannelEnrollmentService channelEnrollmentService;
    private final BotWebhookService botWebhookService;

    public ChannelBootstrap(
            ChannelEnrollmentService channelEnrollmentService,
            BotWebhookService botWebhookService
    ) {
        this.channelEnrollmentService = channelEnrollmentService;
        this.botWebhookService = botWebhookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            channelEnrollmentService.ensureDefaultChannels();
            botWebhookService.ensureDefaultWebhooks();
            log.info("Default channels and webhooks ready");
        } catch (Exception ex) {
            log.error("Channel/webhook bootstrap failed", ex);
        }
    }
}
