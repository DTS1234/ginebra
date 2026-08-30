package com.ginebra.game.adapter.in.dto;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.*;
import com.ginebra.identity.domain.PlayerId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps domain objects to DTOs for STOMP communication.
 */
public class GameStateMapper {

    public GameStatePayload toGameState(Game game, PlayerId forPlayer) {
        Objects.requireNonNull(game, "game must not be null");
        Objects.requireNonNull(forPlayer, "forPlayer must not be null");

        final var playerDtos = new ArrayList<GameStatePayload.PlayerDto>();
        for (var i = 0; i < game.players().size(); i++) {
            playerDtos.add(new GameStatePayload.PlayerDto(
                game.players().get(i).value().toString(),
                i
            ));
        }

        final var coinBalances = game.coinBalances().entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().value().toString(),
                Map.Entry::getValue
            ));

        final var roundPayload = game.currentRound()
            .map(round -> toRoundPayload(round, forPlayer))
            .orElse(null);

        return new GameStatePayload(
            game.gameId().value().toString(),
            game.status().name(),
            playerDtos,
            coinBalances,
            game.posso(),
            roundPayload
        );
    }

    public ServerMessage toServerMessage(GameEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (event instanceof GameEvent.SoledadPassed sp) {
            final var payload = new HashMap<String, Object>();
            payload.put("playerId", sp.playerId().value().toString());
            payload.put("remainingPlayers", sp.remainingPlayers().stream()
                .map(p -> p.value().toString()).toList());
            return new ServerMessage("SOLEDAD_PASSED", payload);
        } else if (event instanceof GameEvent.SoledadDeclared sd) {
            return new ServerMessage("SOLEDAD_DECLARED", Map.of(
                "byPlayer", sd.byPlayer().value().toString()
            ));
        } else if (event instanceof GameEvent.SoledadWindowClosed swc) {
            return new ServerMessage("SOLEDAD_WINDOW_CLOSED", Map.of(
                "declared", swc.declared(),
                "awaitingTrumpFrom", swc.awaitingTrumpFrom().value().toString()
            ));
        } else if (event instanceof GameEvent.TrumpSelected ts) {
            final var payload = new HashMap<String, Object>();
            payload.put("suit", ts.suit().name());
            payload.put("byPlayer", ts.byPlayer().value().toString());
            payload.put("currentTurn", ts.currentTurn() != null ? ts.currentTurn().value().toString() : null);
            return new ServerMessage("TRUMP_SELECTED", payload);
        } else if (event instanceof GameEvent.CardPlayed cp) {
            final var payload = new HashMap<String, Object>();
            payload.put("playerId", cp.playerId().value().toString());
            payload.put("card", toCardDto(cp.card()));
            payload.put("nextTurn", cp.nextTurn() != null ? cp.nextTurn().value().toString() : null);
            payload.put("basaNumber", cp.basaNumber());
            return new ServerMessage("CARD_PLAYED", payload);
        } else if (event instanceof GameEvent.KingFellPending kfp) {
            return new ServerMessage("KING_FELL_PENDING", Map.of(
                "playerWhoGoes", kfp.playerWhoGoes().value().toString(),
                "king", toCardDto(kfp.king())
            ));
        } else if (event instanceof GameEvent.KingChoiceMade kcm) {
            final var payload = new HashMap<String, Object>();
            payload.put("playerWhoGoes", kcm.playerWhoGoes().value().toString());
            payload.put("carriedOn", kcm.carriedOn());
            payload.put("currentTurn", kcm.currentTurn() != null
                ? kcm.currentTurn().value().toString() : null);
            return new ServerMessage("KING_CHOICE_MADE", payload);
        } else if (event instanceof GameEvent.BasaWon bw) {
            final var payload = new HashMap<String, Object>();
            payload.put("basaNumber", bw.basaNumber());
            payload.put("winner", bw.winner().value().toString());
            payload.put("cards", bw.cards().stream().map(pc -> Map.of(
                "playerId", pc.playerId().value().toString(),
                "card", toCardDto(pc.card())
            )).toList());
            payload.put("basasWon", bw.basasWon().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().value().toString(), Map.Entry::getValue)));
            payload.put("nextBasaNumber", bw.nextBasaNumber());
            payload.put("nextStarter", bw.nextStarter() != null ? bw.nextStarter().value().toString() : null);
            return new ServerMessage("BASA_WON", payload);
        } else if (event instanceof GameEvent.SideDecided sd) {
            final var payload = new HashMap<String, Object>();
            payload.put("mode", sd.mode().name());
            payload.put("goingSide", sd.goingSide().stream().map(p -> p.value().toString()).toList());
            payload.put("opposingSide", sd.opposingSide().stream().map(p -> p.value().toString()).toList());
            payload.put("byPlayer", sd.byPlayer().value().toString());
            payload.put("king", sd.king() != null ? toCardDto(sd.king()) : null);
            payload.put("forced", sd.forced());
            return new ServerMessage("SIDE_DECIDED", payload);
        } else if (event instanceof GameEvent.TodoDecided td) {
            final var payload = new HashMap<String, Object>();
            payload.put("byPlayer", td.byPlayer().value().toString());
            payload.put("called", td.called());
            payload.put("currentTurn", td.currentTurn() != null ? td.currentTurn().value().toString() : null);
            return new ServerMessage("TODO_DECIDED", payload);
        } else if (event instanceof GameEvent.RoundEnded re) {
            final var payload = new HashMap<String, Object>();
            payload.put("roundNumber", re.roundNumber());
            payload.put("result", resultName(re.result()));
            payload.put("winners", re.result().winners().stream().map(p -> p.value().toString()).toList());
            payload.put("coinChanges", re.coinChanges().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().value().toString(), Map.Entry::getValue)));
            payload.put("coinBalances", re.newBalances().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().value().toString(), Map.Entry::getValue)));
            payload.put("charges", re.charges().entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().value().toString(),
                    e -> e.getValue().stream()
                        .map(c -> Map.of("reason", c.reason().name(), "amount", c.amount()))
                        .toList()
                )));
            payload.put("posso", re.posso());
            return new ServerMessage("ROUND_ENDED", payload);
        } else if (event instanceof GameEvent.GameEnded ge) {
            return new ServerMessage("GAME_ENDED", Map.of(
                "reason", ge.reason(),
                "finalBalances", ge.finalBalances().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().value().toString(), Map.Entry::getValue))
            ));
        } else if (event instanceof GameEvent.PlayerConnected pc) {
            return new ServerMessage("PLAYER_CONNECTED", Map.of(
                "playerId", pc.playerId().value().toString()
            ));
        } else if (event instanceof GameEvent.PlayerDisconnected pd) {
            return new ServerMessage("PLAYER_DISCONNECTED", Map.of(
                "playerId", pd.playerId().value().toString()
            ));
        } else if (event instanceof GameEvent.GameError ge) {
            return new ServerMessage("ERROR", Map.of(
                "code", ge.code(),
                "message", ge.message()
            ));
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
    }

    private GameStatePayload.RoundPayload toRoundPayload(Round round, PlayerId forPlayer) {
        final var hand = round.getHand(forPlayer).stream()
            .map(this::toCardDto)
            .toList();

        final var basasWon = round.basasWonByAll().entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().value().toString(),
                Map.Entry::getValue
            ));

        final var basaPayload = round.currentBasa()
            .map(this::toBasaPayload)
            .orElse(null);

        final var teamsPayload = round.teams()
            .map(this::toTeamsPayload)
            .orElse(null);

        final var soledadPasses = round.soledadPasses().stream()
            .map(p -> p.value().toString())
            .toList();

        final var soledadPlayer = round.soledadPlayer()
            .map(p -> p.value().toString())
            .orElse(null);

        final var soledadDeadline = round.soledadDeadline()
            .map(Object::toString)
            .orElse(null);

        return new GameStatePayload.RoundPayload(
            round.roundNumber(),
            round.status().name(),
            round.trumpSuit().map(Suit::name).orElse(null),
            round.playerWhoGoes().value().toString(),
            round.trumpChooser().value().toString(),
            hand,
            round.currentPlayer().map(p -> p.value().toString()).orElse(null),
            basasWon,
            basaPayload,
            teamsPayload,
            round.mode().map(Enum::name).orElse(null),
            round.ledSuits().stream().map(Suit::name).sorted().toList(),
            round.goingSide().stream().map(p -> p.value().toString()).toList(),
            round.opposingSide().stream().map(p -> p.value().toString()).toList(),
            round.firstKingCalled(),
            round.todoCaller().map(p -> p.value().toString()).orElse(null),
            round.todoCalled(),
            soledadPasses,
            soledadPlayer,
            round.fourKingHolder().map(p -> p.value().toString()).orElse(null),
            soledadDeadline
        );
    }

    private GameStatePayload.BasaPayload toBasaPayload(Basa basa) {
        final var cardsPlayed = basa.cardsPlayed().stream()
            .map(pc -> new GameStatePayload.PlayedCardDto(
                pc.playerId().value().toString(),
                toCardDto(pc.card())
            ))
            .toList();

        return new GameStatePayload.BasaPayload(
            basa.basaNumber(),
            basa.startingPlayer().value().toString(),
            cardsPlayed
        );
    }

    private static String resultName(RoundResult result) {
        if (result instanceof RoundResult.GoingSideWon) {
            return "GOING_SIDE_WON";
        }
        if (result instanceof RoundResult.GoingSideFailed) {
            return "GOING_SIDE_FAILED";
        }
        if (result instanceof RoundResult.FourKings) {
            return "FOUR_KINGS";
        }
        return "KING_FELL";
    }

    private GameStatePayload.TeamsPayload toTeamsPayload(Teams teams) {
        return new GameStatePayload.TeamsPayload(
            teams.teamOfTwo().stream().map(p -> p.value().toString()).toList(),
            teams.teamOfThree().stream().map(p -> p.value().toString()).toList()
        );
    }

    private CardDto toCardDto(Card card) {
        return new CardDto(card.suit().name(), card.rank().name());
    }
}
