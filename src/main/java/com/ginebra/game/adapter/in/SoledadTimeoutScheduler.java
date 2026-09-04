package com.ginebra.game.adapter.in;

import com.ginebra.game.application.BotTurnDriver;
import com.ginebra.game.port.in.ExpireSoledadUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Closes soledad windows whose two minutes are up.
 *
 * Nudges the bots afterwards for the same reason every other way into the game does: the
 * window closing puts somebody on turn, and if that somebody is a bot, nothing else will
 * move it.
 */
@Component
public class SoledadTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SoledadTimeoutScheduler.class);

    private final ExpireSoledadUseCase expireSoledad;
    private final BotTurnDriver botTurnDriver;
    private final Clock clock;

    public SoledadTimeoutScheduler(
        ExpireSoledadUseCase expireSoledad,
        BotTurnDriver botTurnDriver,
        Clock clock
    ) {
        this.expireSoledad = Objects.requireNonNull(expireSoledad, "expireSoledad must not be null");
        this.botTurnDriver = Objects.requireNonNull(botTurnDriver, "botTurnDriver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(fixedDelayString = "${ginebra.timeouts.sweep-interval-seconds:15}", timeUnit = TimeUnit.SECONDS)
    public void closeWindowsNobodyAnswered() {
        for (final var gameId : expireSoledad.expireSoledadWindows(clock.instant())) {
            log.info("Soledad window in game {} closed on time", gameId.value());
            botTurnDriver.drive(gameId);
        }
    }
}
