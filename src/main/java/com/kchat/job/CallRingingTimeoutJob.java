package com.kchat.job;

import com.kchat.service.CallService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallRingingTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(CallRingingTimeoutJob.class);

    private final CallService callService;

    public CallRingingTimeoutJob(CallService callService) {
        this.callService = callService;
    }

    @Scheduled(fixedDelayString = "${kchat.cleanup.call-ringing-interval-ms:15000}")
    @SchedulerLock(name = "callRingingTimeout", lockAtMostFor = "PT20S", lockAtLeastFor = "PT5S")
    public void expireRinging() {
        try {
            int n = callService.expireRingingCalls();
            if (n > 0) {
                log.info("Marked {} ringing call(s) as missed", n);
            }
        } catch (RuntimeException ex) {
            log.error("Call ringing timeout job failed", ex);
        }
    }
}
