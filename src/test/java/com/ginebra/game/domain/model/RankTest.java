package com.ginebra.game.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankTest {

    @Test
    void shouldHaveExactlyTenRanks() {
        assertThat(Rank.values()).hasSize(10);
    }

    @Test
    void shouldContainAllSpanishDeckRanks() {
        assertThat(Rank.values()).containsExactlyInAnyOrder(
            Rank.AS,
            Rank.DOS,
            Rank.TRES,
            Rank.CUATRO,
            Rank.CINCO,
            Rank.SEIS,
            Rank.SIETE,
            Rank.SOTA,
            Rank.CABALLO,
            Rank.REY
        );
    }
}
