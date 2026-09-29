package com.apollosuny.apolledgebe.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Month and day boundaries (budgets, standing orders) depend on the user's calendar,
 * not on the server's zone. The clock is injectable so time-based logic is testable.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
