package com.ginebra.lobby.domain;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomTest {

    private RoomId roomId;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        roomId = RoomId.generate();
        createdAt = Instant.now();
    }

    @Nested
    class Creation {

        @Test
        void shouldCreateRoomInWaitingStatus() {
            final var room = Room.create(roomId, createdAt);

            assertThat(room.id()).isEqualTo(roomId);
            assertThat(room.createdAt()).isEqualTo(createdAt);
            assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        }

        @Test
        void shouldCreateRoomWithEmptyPlayersList() {
            final var room = Room.create(roomId, createdAt);

            assertThat(room.players()).isEmpty();
            assertThat(room.isEmpty()).isTrue();
        }

        @Test
        void shouldCreateRoomWithNoGameId() {
            final var room = Room.create(roomId, createdAt);

            assertThat(room.gameId()).isEmpty();
        }
    }

    @Nested
    class AddingPlayers {

        @Test
        void shouldAddFirstPlayerSuccessfully() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();

            final var result = room.addPlayer(playerId, "Player1", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.Success.class);
            assertThat(room.players()).hasSize(1);
            assertThat(room.hasPlayer(playerId)).isTrue();
        }

        @Test
        void shouldAddMultiplePlayersSuccessfully() {
            final var room = Room.create(roomId, createdAt);

            final var result1 = room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            final var result2 = room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            final var result3 = room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            final var result4 = room.addPlayer(PlayerId.generate(), "Player4", Instant.now());

            assertThat(result1).isInstanceOf(AddPlayerResult.Success.class);
            assertThat(result2).isInstanceOf(AddPlayerResult.Success.class);
            assertThat(result3).isInstanceOf(AddPlayerResult.Success.class);
            assertThat(result4).isInstanceOf(AddPlayerResult.Success.class);
            assertThat(room.players()).hasSize(4);
            assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        }

        @Test
        void shouldReturnRoomFullGameShouldStartWhenAddingFifthPlayer() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());

            final var result = room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.RoomFull_GameShouldStart.class);
            assertThat(room.players()).hasSize(5);
        }

        @Test
        void shouldTransitionToStartingWhenFifthPlayerJoins() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());

            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            assertThat(room.status()).isEqualTo(RoomStatus.STARTING);
        }

        @Test
        void shouldRejectSixthPlayer() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            final var result = room.addPlayer(PlayerId.generate(), "Player6", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.RoomNotWaiting.class);
            assertThat(((AddPlayerResult.RoomNotWaiting) result).currentStatus()).isEqualTo(RoomStatus.STARTING);
            assertThat(room.players()).hasSize(5);
        }

        @Test
        void shouldRejectSamePlayerTwice() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());

            final var result = room.addPlayer(playerId, "Player1", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.PlayerAlreadyInRoom.class);
            assertThat(room.players()).hasSize(1);
        }

        @Test
        void shouldRejectPlayerWhenRoomIsStarting() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            final var result = room.addPlayer(PlayerId.generate(), "Player6", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.RoomNotWaiting.class);
            assertThat(((AddPlayerResult.RoomNotWaiting) result).currentStatus()).isEqualTo(RoomStatus.STARTING);
        }

        @Test
        void shouldRejectPlayerWhenRoomIsConverted() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());
            room.convertToGame(GameId.generate());

            final var result = room.addPlayer(PlayerId.generate(), "Player6", Instant.now());

            assertThat(result).isInstanceOf(AddPlayerResult.RoomNotWaiting.class);
            assertThat(((AddPlayerResult.RoomNotWaiting) result).currentStatus()).isEqualTo(RoomStatus.CONVERTED);
        }
    }

    @Nested
    class RemovingPlayers {

        @Test
        void shouldRemovePlayerSuccessfully() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());

            final var result = room.removePlayer(playerId);

            assertThat(result).isInstanceOf(RemovePlayerResult.Success.class);
            assertThat(room.players()).hasSize(1);
            assertThat(room.hasPlayer(playerId)).isFalse();
        }

        @Test
        void shouldReturnRoomNowEmptyWhenRemovingLastPlayer() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());

            final var result = room.removePlayer(playerId);

            assertThat(result).isInstanceOf(RemovePlayerResult.RoomNowEmpty_ShouldDelete.class);
            assertThat(room.players()).isEmpty();
        }

        @Test
        void shouldTransitionToAbandonedWhenLastPlayerLeaves() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());

            room.removePlayer(playerId);

            assertThat(room.status()).isEqualTo(RoomStatus.ABANDONED);
        }

        @Test
        void shouldRejectRemovingPlayerNotInRoom() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            final var notInRoom = PlayerId.generate();

            final var result = room.removePlayer(notInRoom);

            assertThat(result).isInstanceOf(RemovePlayerResult.PlayerNotInRoom.class);
        }

        @Test
        void shouldRejectRemovingPlayerAfterStarting() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            final var result = room.removePlayer(playerId);

            assertThat(result).isInstanceOf(RemovePlayerResult.CannotLeaveAfterStarting.class);
        }
    }

    @Nested
    class ConvertingToGame {

        @Test
        void shouldConvertToGameSuccessfully() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());
            final var gameId = GameId.generate();

            room.convertToGame(gameId);

            assertThat(room.gameId()).isPresent();
            assertThat(room.gameId().get()).isEqualTo(gameId);
        }

        @Test
        void shouldTransitionToConvertedAfterConversion() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            room.convertToGame(GameId.generate());

            assertThat(room.status()).isEqualTo(RoomStatus.CONVERTED);
        }

        @Test
        void shouldRejectConversionWhenWaiting() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            assertThatThrownBy(() -> room.convertToGame(GameId.generate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only convert STARTING room to game");
        }

        @Test
        void shouldRejectConversionWhenAbandoned() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());
            room.removePlayer(playerId);

            assertThatThrownBy(() -> room.convertToGame(GameId.generate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only convert STARTING room to game");
        }

        @Test
        void shouldRejectConversionWhenConverted() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());
            room.convertToGame(GameId.generate());

            assertThatThrownBy(() -> room.convertToGame(GameId.generate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Can only convert STARTING room to game");
        }
    }

    @Nested
    class QueryMethods {

        @Test
        void canJoinShouldReturnTrueWhenWaitingAndNotFull() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            assertThat(room.canJoin()).isTrue();
        }

        @Test
        void canJoinShouldReturnFalseWhenStarting() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            assertThat(room.canJoin()).isFalse();
        }

        @Test
        void canJoinShouldReturnFalseWhenFull() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            assertThat(room.canJoin()).isFalse();
        }

        @Test
        void hasPlayerShouldReturnTrueForExistingPlayer() {
            final var room = Room.create(roomId, createdAt);
            final var playerId = PlayerId.generate();
            room.addPlayer(playerId, "Player1", Instant.now());

            assertThat(room.hasPlayer(playerId)).isTrue();
        }

        @Test
        void hasPlayerShouldReturnFalseForNonExistingPlayer() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            assertThat(room.hasPlayer(PlayerId.generate())).isFalse();
        }

        @Test
        void isFullShouldReturnTrueWithFivePlayers() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player2", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player3", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player4", Instant.now());
            room.addPlayer(PlayerId.generate(), "Player5", Instant.now());

            assertThat(room.isFull()).isTrue();
        }

        @Test
        void isFullShouldReturnFalseWithFewerThanFivePlayers() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            assertThat(room.isFull()).isFalse();
        }

        @Test
        void isEmptyShouldReturnTrueWithNoPlayers() {
            final var room = Room.create(roomId, createdAt);

            assertThat(room.isEmpty()).isTrue();
        }

        @Test
        void isEmptyShouldReturnFalseWithPlayers() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            assertThat(room.isEmpty()).isFalse();
        }

        @Test
        void playersShouldReturnDefensiveCopy() {
            final var room = Room.create(roomId, createdAt);
            room.addPlayer(PlayerId.generate(), "Player1", Instant.now());

            final var playersCopy = room.players();
            assertThatThrownBy(() -> playersCopy.add(RoomPlayer.create(PlayerId.generate(), "Hacker", Instant.now())))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
