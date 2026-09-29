package com.apollosuny.apolledgebe.standingorder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lives outside StandingOrderService on purpose: calling postDueOccurrences through the
 * Spring proxy gives every order its own transaction, which a self-invocation would not.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.standing-orders.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class StandingOrderScheduler {

    private final StandingOrderService standingOrderService;

    @Scheduled(
            cron = "${app.standing-orders.cron:0 5 0 * * *}",
            zone = "${app.timezone:Asia/Ho_Chi_Minh}"
    )
    public void postDueStandingOrders() {
        for (UUID standingOrderId : standingOrderService.findDueAutoPostIds()) {
            try {
                int posted = standingOrderService.postDueOccurrences(standingOrderId);
                if (posted > 0) {
                    log.info("Posted {} occurrence(s) of standing order {}", posted, standingOrderId);
                }
            } catch (Exception exception) {
                log.error("Failed to post standing order {}", standingOrderId, exception);
            }
        }
    }
}
