package com.ginebra.lobby.application;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.port.out.BotSeats;

import java.util.ArrayList;
import java.util.List;

/**
 * Bots for the lobby tests: as many as asked for, named so assertions can read.
 */
final class TestBotSeats implements BotSeats {

    private final List<BotSeat> handedOut = new ArrayList<>();

    @Override
    public List<BotSeat> create(int count) {
        final var seats = new ArrayList<BotSeat>(count);
        for (var i = 0; i < count; i++) {
            seats.add(new BotSeat(PlayerId.generate(), "Bot " + (handedOut.size() + i + 1)));
        }
        handedOut.addAll(seats);
        return List.copyOf(seats);
    }

    List<BotSeat> handedOut() {
        return List.copyOf(handedOut);
    }
}
