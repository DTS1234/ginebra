package com.ginebra.connection.adapter;

import com.ginebra.connection.application.ConnectionTracker;
import com.ginebra.game.port.in.SeatTakeoverUseCase;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The sweep for seats nobody is in")
class AbandonedSeatSchedulerTest {

    private static final Instant LEFT_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    private ConnectionTracker tracker;
    private RecordingTakeover takeover;

    @BeforeEach
    void setUp() {
        tracker = new ConnectionTracker();
        takeover = new RecordingTakeover(true);
    }

    private AbandonedSeatScheduler schedulerAt(Instant now) {
        return new AbandonedSeatScheduler(
            tracker,
            takeover,
            Clock.fixed(now, ZoneOffset.UTC),
            FIVE_MINUTES
        );
    }

    @Test
    void shouldHandOverASeatOnceItsFiveMinutesAreUp() {
        final var playerId = PlayerId.generate();
        final var gameId = GameId.generate();
        tracker.playerConnected(playerId, gameId, "s1", LEFT_AT);
        tracker.playerDisconnected("s1", LEFT_AT);

        schedulerAt(LEFT_AT.plus(FIVE_MINUTES)).handOverSeatsNobodyIsIn();

        assertThat(takeover.takenOver).containsExactly(playerId);
    }

    @Test
    void shouldLeaveSomebodyWhoHasOnlyJustDroppedOut() {
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", LEFT_AT);
        tracker.playerDisconnected("s1", LEFT_AT);

        schedulerAt(LEFT_AT.plusSeconds(30)).handOverSeatsNobodyIsIn();

        assertThat(takeover.takenOver)
            .as("half a minute is a lost connection, not a walk-out")
            .isEmpty();
    }

    @Test
    void shouldOnlyLookAtAnAbsenceOnce() {
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", LEFT_AT);
        tracker.playerDisconnected("s1", LEFT_AT);
        final var scheduler = schedulerAt(LEFT_AT.plus(FIVE_MINUTES));

        scheduler.handOverSeatsNobodyIsIn();
        scheduler.handOverSeatsNobodyIsIn();

        assertThat(takeover.takenOver).hasSize(1);
    }

    @Test
    void shouldStopLookingAtAnAbsenceItCouldDoNothingAbout() {
        // A game that has since ended, or a player who was never in one. Left in place it
        // would be looked at every fifteen seconds for as long as the server is up.
        takeover = new RecordingTakeover(false);
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", LEFT_AT);
        tracker.playerDisconnected("s1", LEFT_AT);
        final var scheduler = schedulerAt(LEFT_AT.plus(FIVE_MINUTES));

        scheduler.handOverSeatsNobodyIsIn();
        scheduler.handOverSeatsNobodyIsIn();

        assertThat(takeover.takenOver).hasSize(1);
    }

    private static class RecordingTakeover implements SeatTakeoverUseCase {

        final List<PlayerId> takenOver = new ArrayList<>();
        private final boolean succeeds;

        RecordingTakeover(boolean succeeds) {
            this.succeeds = succeeds;
        }

        @Override
        public boolean takeOverSeat(GameId gameId, PlayerId playerId) {
            takenOver.add(playerId);
            return succeeds;
        }

        @Override
        public boolean returnSeat(GameId gameId, PlayerId playerId) {
            return false;
        }
    }
}
