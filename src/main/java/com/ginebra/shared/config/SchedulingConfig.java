package com.ginebra.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the sweeps that stop a table waiting forever.
 *
 * Every timeout in the game is swept for rather than scheduled at: a deadline is written
 * down when it is set, and a sweep looks at what has passed. It costs a few wasted passes
 * over an empty map a minute and saves cancelling, rescheduling and leaking a timer per
 * round - and it is the shape that survives Phase 5, where the deadlines come back off a
 * database after a restart with no timers to rebuild.
 *
 * Set {@code ginebra.timeouts.enabled=false} to stop the lot, which is what a test that
 * needs the clock to stand still does.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "ginebra.timeouts.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
