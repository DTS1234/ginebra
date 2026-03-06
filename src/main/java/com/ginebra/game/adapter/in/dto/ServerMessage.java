package com.ginebra.game.adapter.in.dto;

import java.util.Objects;

/**
 * Wrapper for all server-to-client messages sent over STOMP.
 * The type field identifies the message kind, payload contains the data.
 */
public record ServerMessage(String type, Object payload) {
    public ServerMessage {
        Objects.requireNonNull(type, "type must not be null");
    }
}
