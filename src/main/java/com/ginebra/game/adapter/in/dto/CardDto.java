package com.ginebra.game.adapter.in.dto;

import java.util.Objects;

public record CardDto(String suit, String rank) {
    public CardDto {
        Objects.requireNonNull(suit, "suit must not be null");
        Objects.requireNonNull(rank, "rank must not be null");
    }
}
