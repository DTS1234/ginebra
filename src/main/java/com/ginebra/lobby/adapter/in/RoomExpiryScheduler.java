package com.ginebra.lobby.adapter.in;

import com.ginebra.lobby.port.in.ExpireRoomsUseCase;
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
 * Deletes rooms nobody ever filled, so the lobby lists tables people are actually at.
 */
@Component
public class RoomExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomExpiryScheduler.class);

    private final ExpireRoomsUseCase expireRooms;
    private final Clock clock;
    private final Duration idleFor;

    public RoomExpiryScheduler(
        ExpireRoomsUseCase expireRooms,
        Clock clock,
        @Value("${ginebra.timeouts.room-expiry:30m}") Duration idleFor
    ) {
        this.expireRooms = Objects.requireNonNull(expireRooms, "expireRooms must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idleFor = Objects.requireNonNull(idleFor, "idleFor must not be null");
    }

    @Scheduled(fixedDelayString = "${ginebra.timeouts.sweep-interval-seconds:15}", timeUnit = TimeUnit.SECONDS)
    public void deleteRoomsNobodyCameBackTo() {
        final var expired = expireRooms.expireStaleRooms(idleFor, clock.instant());
        if (!expired.isEmpty()) {
            log.info("Cleared {} room(s) that had sat untouched for {}", expired.size(), idleFor);
        }
    }
}
