package com.kchat.job;

import com.kchat.config.MessageRetentionCleanupProperties;
import com.kchat.service.MessageRetentionCleanupService;
import java.time.Instant;
import java.time.Period;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kchat.cleanup.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageRetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionCleanupJob.class);

    private final MessageRetentionCleanupProperties properties;
    private final MessageRetentionCleanupService cleanupService;

    public MessageRetentionCleanupJob(
            MessageRetentionCleanupProperties properties,
            MessageRetentionCleanupService cleanupService
    ) {
        this.properties = properties;
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${kchat.cleanup.retention.cron:0 0 2 1 * *}", zone = "UTC")
    @SchedulerLock(
            name = "messageRetentionCleanup",
            lockAtMostFor = "${kchat.cleanup.retention.lock-at-most-for:PT45M}",
            lockAtLeastFor = "${kchat.cleanup.retention.lock-at-least-for:PT5M}"
    )
    public void run() {
        int months = Math.max(properties.getOlderThanMonths(), 1);
        Instant cutoff = Instant.now().minus(Period.ofMonths(months));
        int batchSize = properties.getBatchSize();
        int maxBatches = Math.max(properties.getMaxBatches(), 1);

        log.info(
                "Retention cleanup started: purge messages older than {} month(s) (cutoff={}, batchSize={}, maxBatches={})",
                months,
                cutoff,
                batchSize,
                maxBatches
        );

        int totalDeleted = 0;
        try {
            for (int i = 0; i < maxBatches; i++) {
                int deleted = cleanupService.purgeMessagesOlderThan(cutoff, batchSize);
                totalDeleted += deleted;
                if (deleted == 0) {
                    break;
                }
            }
            log.info("Retention cleanup finished: hard-deleted {} message(s) total", totalDeleted);
        } catch (RuntimeException ex) {
            log.error("Retention cleanup failed after deleting {} message(s)", totalDeleted, ex);
        }
    }
}
