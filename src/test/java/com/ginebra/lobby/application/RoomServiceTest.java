package com.ginebra.lobby.application;

import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.lobby.port.in.CreateRoomUseCase;
import com.ginebra.lobby.port.out.GameStarter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceTest {

    private RoomService roomService;
    private InMemoryRoomRepository roomRepository;
    private Clock clock;
    private PlayerIdentity testPlayer;

    private static final GameStarter NO_OP_GAME_STARTER =
        players -> new GameStarter.GameStartResult.Success(GameId.generate());

    @BeforeEach
    void setUp() {
        roomRepository = new InMemoryRoomRepository();
        clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
        roomService = new RoomService(roomRepository, NO_OP_GAME_STARTER, new TestBotSeats(), clock);

        // Set up SecurityContext with authenticated player
        testPlayer = new PlayerIdentity(
            PlayerId.generate(),
            "TestPlayer",
            false
        );
        final var authentication = PlayerAuthentication.authenticated(testPlayer, "fake-token");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateRoomWithCreatorAsFirstPlayer() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        final var response = roomService.createRoom(command);

        assertThat(response.roomId()).isNotBlank();
        assertThat(response.players()).hasSize(1);
        assertThat(response.players().get(0).playerId()).isEqualTo(testPlayer.playerId().value().toString());
        assertThat(response.players().get(0).displayName()).isEqualTo("TestPlayer");
    }

    @Test
    void shouldPersistRoomInRepository() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        final var response = roomService.createRoom(command);

        assertThat(roomRepository.size()).isEqualTo(1);
        assertThat(roomRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldGenerateUniqueRoomIds() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        final var response1 = roomService.createRoom(command);
        final var response2 = roomService.createRoom(command);

        assertThat(response1.roomId()).isNotEqualTo(response2.roomId());
        assertThat(roomRepository.size()).isEqualTo(2);
    }

    @Test
    void shouldUseClockForTimestamps() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        roomService.createRoom(command);

        final var room = roomRepository.findAll().get(0);
        assertThat(room.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
        assertThat(room.players().get(0).joinedAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
    }

    @Test
    void shouldIncludeCreatorInPlayersList() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        final var response = roomService.createRoom(command);

        assertThat(response.players()).isNotEmpty();
        assertThat(response.players()).extracting("displayName").containsExactly("TestPlayer");
    }

    @Test
    void shouldSetRoomStatusToWaiting() {
        final var command = new CreateRoomUseCase.CreateRoomCommand();

        final var response = roomService.createRoom(command);

        assertThat(response.status()).isEqualTo("WAITING");
    }
}
