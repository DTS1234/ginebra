package com.ginebra.lobby.adapter.out;

import com.ginebra.game.application.BotRoster;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.port.out.BotSeats;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Makes up the players nobody is playing.
 *
 * The names are ordinary village names rather than "Bot 1", because they show on the seat
 * and in the log all round - but each is marked, because knowing which of the other four
 * are people changes how you play, and a table where you cannot tell is worse than one
 * where the labels are ugly.
 */
@Component
public class BotSeatsAdapter implements BotSeats {

    private static final List<String> NAMES = List.of(
        "Pep", "Vicent", "Tonica", "Rosa", "Quico", "Maria", "Batiste", "Neus"
    );

    private static final String MARK = " (bot)";

    private final BotRoster roster;
    private final Random random;

    public BotSeatsAdapter(BotRoster roster, Random gameRandom) {
        this.roster = Objects.requireNonNull(roster, "roster must not be null");
        this.random = Objects.requireNonNull(gameRandom, "gameRandom must not be null");
    }

    @Override
    public List<BotSeat> create(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }

        final var pool = new ArrayList<>(NAMES);
        java.util.Collections.shuffle(pool, random);

        final var seats = new ArrayList<BotSeat>(count);
        for (var i = 0; i < count; i++) {
            final var playerId = PlayerId.generate();
            roster.register(playerId);
            seats.add(new BotSeat(playerId, pool.get(i % pool.size()) + MARK));
        }
        return List.copyOf(seats);
    }
}
