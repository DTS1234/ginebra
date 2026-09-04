package com.ginebra.connection.adapter;

import com.ginebra.connection.application.ConnectionTracker;
import com.ginebra.game.port.in.SeatTakeoverUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Gives away the seats of people who have not come back.
 *
 * Five minutes (design §8) is long enough to lose your connection and get it back, and
 * short enough that the other four have not given up on the hand.
 *
 * The absence is forgotten whether or not a seat changed hands, so a game that has since
 * ended, or a player who was never in one, is not looked at again every sweep for as long
 * as the server is up.
 */
@Component
public class AbandonedSeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(AbandonedSeatScheduler.class);

    private final ConnectionTracker connectionTracker;
    private final SeatTakeoverUseCase seatTakeover;
    private final Clock clock;
    private final Duration limit;

    public AbandonedSeatScheduler(
        ConnectionTracker connectionTracker,
        SeatTakeoverUseCase seatTakeover,
        Clock clock,
        @Value("${ginebra.timeouts.disconnect:5m}") Duration limit
    ) {
        this.connectionTracker = Objects.requireNonNull(connectionTracker, "connectionTracker must not be null");
        this.seatTakeover = Objects.requireNonNull(seatTakeover, "seatTakeover must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.limit = Objects.requireNonNull(limit, "limit must not be null");
    }

    @Scheduled(fixedDelayString = "${ginebra.timeouts.sweep-interval-seconds:15}", timeUnit = TimeUnit.SECONDS)
    public void handOverSeatsNobodyIsIn() {
        for (final var absence : connectionTracker.absencesLongerThan(limit, clock.instant())) {
            connectionTracker.forgetAbsence(absence.playerId());

            if (seatTakeover.takeOverSeat(absence.gameId(), absence.playerId())) {
                log.info(
                    "Player {} has been gone {}; a bot has their seat in game {}",
                    absence.playerId().value(), limit, absence.gameId().value()
                );
            }
        }
    }
}
