package com.ginebra.lobby.application;

import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.lobby.port.in.CreateRoomUseCase;
import com.ginebra.lobby.port.in.GetRoomUseCase;
import com.ginebra.lobby.port.in.JoinRoomUseCase;
import com.ginebra.lobby.port.out.GameStarter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Looking a room up is how the four players who did not fill the room learn its game id:
 * only the fifth player to join gets one back from the join call.
 */
@DisplayName("Looking up a single room")
class GetRoomServiceTest {

    private static final GameId STARTED_GAME = GameId.generate();
    private static final GameStarter GAME_STARTER =
        players -> new GameStarter.GameStartResult.Success(STARTED_GAME);

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
            new InMemoryRoomRepository(),
            GAME_STARTER,
            new TestBotSeats(),
            Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnRoomWithoutGameIdWhileStillWaiting() {
        // Arrange
        authenticateAs("Creator");
        final var roomId = roomService.createRoom(new CreateRoomUseCase.CreateRoomCommand()).roomId();

        // Act
        final var result = roomService.getRoom(new GetRoomUseCase.GetRoomCommand(roomId));

        // Assert
        assertThat(result).isInstanceOf(GetRoomUseCase.GetRoomResult.Success.class);
        final var success = (GetRoomUseCase.GetRoomResult.Success) result;
        assertThat(success.roomId()).isEqualTo(roomId);
        assertThat(success.status()).isEqualTo("WAITING");
        assertThat(success.gameId()).as("no game until the room fills").isNull();
        assertThat(success.players()).hasSize(1);
    }

    @Test
    void shouldReportGameIdToAPlayerWhoDidNotFillTheRoom() {
        // Arrange: the creator joined first, so the fifth player is someone else
        final var creator = authenticateAs("Creator");
        final var roomId = roomService.createRoom(new CreateRoomUseCase.CreateRoomCommand()).roomId();
        for (var seat = 1; seat < 5; seat++) {
            authenticateAs("Player " + seat);
            roomService.joinRoom(new JoinRoomUseCase.JoinRoomCommand(roomId));
        }

        // Act: the creator asks what happened to the room
        authenticateAs(creator);
        final var result = roomService.getRoom(new GetRoomUseCase.GetRoomCommand(roomId));

        // Assert
        final var success = (GetRoomUseCase.GetRoomResult.Success) result;
        assertThat(success.status()).isEqualTo("CONVERTED");
        assertThat(success.gameId()).isEqualTo(STARTED_GAME.value().toString());
        assertThat(success.players()).hasSize(5);
    }

    @Test
    void shouldRejectLookupFromAPlayerOutsideTheRoom() {
        // Arrange
        authenticateAs("Creator");
        final var roomId = roomService.createRoom(new CreateRoomUseCase.CreateRoomCommand()).roomId();

        // Act
        authenticateAs("Stranger");
        final var result = roomService.getRoom(new GetRoomUseCase.GetRoomCommand(roomId));

        // Assert
        assertThat(result).isInstanceOf(GetRoomUseCase.GetRoomResult.NotAMember.class);
    }

    @Test
    void shouldReportRoomNotFoundForUnknownRoom() {
        // Arrange
        authenticateAs("Creator");

        // Act
        final var result = roomService.getRoom(
            new GetRoomUseCase.GetRoomCommand(UUID.randomUUID().toString())
        );

        // Assert
        assertThat(result).isInstanceOf(GetRoomUseCase.GetRoomResult.RoomNotFound.class);
    }

    private PlayerIdentity authenticateAs(String displayName) {
        return authenticateAs(new PlayerIdentity(PlayerId.generate(), displayName, true));
    }

    private PlayerIdentity authenticateAs(PlayerIdentity identity) {
        SecurityContextHolder.getContext().setAuthentication(
            PlayerAuthentication.authenticated(identity, "fake-token")
        );
        return identity;
    }
}
