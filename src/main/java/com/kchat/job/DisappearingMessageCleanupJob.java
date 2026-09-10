package com.kchat.job;

import com.kchat.config.DisappearingMessageCleanupProperties;
import com.kchat.service.DisappearingMessageCleanupService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "kchat.cleanup.disappearing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DisappearingMessageCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(DisappearingMessageCleanupJob.class);

  private final DisappearingMessageCleanupProperties properties;
  private final DisappearingMessageCleanupService cleanupService;

  public DisappearingMessageCleanupJob(
      DisappearingMessageCleanupProperties properties,
      DisappearingMessageCleanupService cleanupService) {
    this.properties = properties;
    this.cleanupService = cleanupService;
  }

  @Scheduled(cron = "${kchat.cleanup.disappearing.cron:0 */5 * * * *}", zone = "UTC")
  @SchedulerLock(
      name = "disappearingMessageCleanup",
      lockAtMostFor = "${kchat.cleanup.disappearing.lock-at-most-for:PT4M}",
      lockAtLeastFor = "${kchat.cleanup.disappearing.lock-at-least-for:PT1M}")
  public void run() {
    try {
      cleanupService.purgeExpiredMessages(properties.getBatchSize());
    } catch (RuntimeException ex) {
      log.error("Disappearing message cleanup failed", ex);
    }
  }
}
