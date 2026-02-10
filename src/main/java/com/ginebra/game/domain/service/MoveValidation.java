package com.ginebra.game.domain.service;

/**
 * Result of validating a card play attempt.
 * Uses sealed interface pattern to represent success or specific failure reasons.
 */
public sealed interface MoveValidation {

    record Valid() implements MoveValidation {}

    record Invalid(String code, String message) implements MoveValidation {}
}
