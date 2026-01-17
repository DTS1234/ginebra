package com.ginebra.game.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuitTest {

    @Test
    void shouldHaveExactlyFourSuits() {
        assertThat(Suit.values()).hasSize(4);
    }

    @Test
    void shouldContainAllSpanishDeckSuits() {
        assertThat(Suit.values()).containsExactlyInAnyOrder(
            Suit.COPAS,
            Suit.OROS,
            Suit.ESPADAS,
            Suit.BASTOS
        );
    }
}
