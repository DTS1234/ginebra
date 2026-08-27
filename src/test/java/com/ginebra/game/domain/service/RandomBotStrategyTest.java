package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Game;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RandomBotStrategy - plays anything legal, bets on nothing")
class RandomBotStrategyTest {

    private static final Random SEEDED = new Random(7);

    private final RandomBotStrategy strategy = new RandomBotStrategy(SEEDED);

    /** Any dealt round will do: none of these decisions look at it yet. */
    private BotStrategy.BotView anyView() {
        final var players = new ArrayList<PlayerId>();
        for (var i = 0; i < Round.PLAYER_COUNT; i++) {
            players.add(PlayerId.generate());
        }
        final var game = Game.start(GameId.generate(), players, new Random(3), Instant.EPOCH);
        return new BotStrategy.BotView(game.currentRound().orElseThrow(), players.get(0));
    }

    @Test
    void shouldOnlyEverPlayACardItWasOffered() {
        final var view = anyView();
        final var legal = List.of(
            new Card(Suit.OROS, Rank.REY),
            new Card(Suit.COPAS, Rank.DOS)
        );

        for (var draw = 0; draw < 200; draw++) {
            assertThat(strategy.chooseCard(view, legal)).isIn(legal);
        }
    }

    @Test
    void shouldEventuallyPlayEveryCardItIsOffered() {
        // The point of a random bot: over a play-test it will try every shape of move the
        // validator has to cope with, rather than the same one every time.
        final var view = anyView();
        final var legal = List.of(
            new Card(Suit.OROS, Rank.REY),
            new Card(Suit.COPAS, Rank.DOS),
            new Card(Suit.ESPADAS, Rank.SIETE),
            new Card(Suit.BASTOS, Rank.SOTA)
        );

        final var seen = new HashSet<Card>();
        for (var draw = 0; draw < 500; draw++) {
            seen.add(strategy.chooseCard(view, legal));
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(legal);
    }

    @Test
    void shouldRefuseAnEmptyChoiceRatherThanReturnNothing() {
        final var view = anyView();

        assertThatThrownBy(() -> strategy.chooseCard(view, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("legal card");
    }

    @Test
    void shouldEventuallyNameEverySuitAsTrump() {
        final var view = anyView();
        final var named = new HashSet<Suit>();

        for (var draw = 0; draw < 200; draw++) {
            named.add(strategy.chooseTrump(view));
        }

        assertThat(named).containsExactlyInAnyOrder(Suit.values());
    }

    @Test
    void shouldNeverTakeAWager() {
        // Deliberate, and the first thing a better opponent should change. A random
        // Soledad or "fer todo" would make one hand in two an oddity, and a play-test
        // would never see a normal round.
        final var view = anyView();

        for (var draw = 0; draw < 200; draw++) {
            assertThat(strategy.declaresSoledad(view)).isFalse();
            assertThat(strategy.callsTodo(view)).isFalse();
        }
    }
}
