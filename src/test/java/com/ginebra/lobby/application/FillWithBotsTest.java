package com.ginebra.lobby.application;

import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;
import com.ginebra.lobby.port.in.FillWithBotsUseCase;
import com.ginebra.lobby.port.out.GameStarter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Filling a room with bots")
class FillWithBotsTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC
    );

    private InMemoryRoomRepository roomRepository;
    private TestBotSeats botSeats;
    private RoomService roomService;
    private PlayerIdentity host;
    private final List<List<PlayerId>> gamesStarted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        roomRepository = new InMemoryRoomRepository();
        botSeats = new TestBotSeats();
        final GameStarter gameStarter = players -> {
            gamesStarted.add(List.copyOf(players));
            return new GameStarter.GameStartResult.Success(GameId.generate());
        };
        roomService = new RoomService(roomRepository, gameStarter, botSeats, CLOCK);

        host = new PlayerIdentity(PlayerId.generate(), "Ada", true);
        signIn(host);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(PlayerIdentity player) {
        SecurityContextHolder.getContext().setAuthentication(
            PlayerAuthentication.authenticated(player, "test-token")
        );
    }

    private String createRoom() {
        return roomService.createRoom(new com.ginebra.lobby.port.in.CreateRoomUseCase.CreateRoomCommand())
            .roomId();
    }

    private FillWithBotsUseCase.FillWithBotsResult fill(String roomId) {
        return roomService.fillWithBots(new FillWithBotsUseCase.FillWithBotsCommand(roomId));
    }

    @Test
    void shouldSeatABotInEveryEmptyChairAndStartTheGame() {
        final var roomId = createRoom();

        final var result = fill(roomId);

        assertThat(result).isInstanceOf(FillWithBotsUseCase.FillWithBotsResult.Success.class);
        final var success = (FillWithBotsUseCase.FillWithBotsResult.Success) result;

        assertThat(success.players()).hasSize(Room.MAX_PLAYERS);
        assertThat(botSeats.handedOut()).hasSize(Room.MAX_PLAYERS - 1);
        assertThat(success.gameId()).isNotNull();
        assertThat(success.websocketUrl()).isEqualTo("/ws/game");
        assertThat(gamesStarted)
            .as("the table filled, so the game started - once")
            .hasSize(1);
        assertThat(gamesStarted.get(0)).hasSize(Room.MAX_PLAYERS);
    }

    @Test
    void shouldOnlyFillTheSeatsThatAreActuallyEmpty() {
        final var roomId = createRoom();
        joinAsSomeoneElse(roomId);
        joinAsSomeoneElse(roomId);

        fill(roomId);

        assertThat(botSeats.handedOut())
            .as("three people short of five")
            .hasSize(Room.MAX_PLAYERS - 3);
    }

    @Test
    void shouldKeepTheHostAtTheTable() {
        final var roomId = createRoom();

        final var success = (FillWithBotsUseCase.FillWithBotsResult.Success) fill(roomId);

        assertThat(success.players())
            .extracting(FillWithBotsUseCase.PlayerDto::playerId)
            .contains(host.playerId().value().toString());
    }

    @Test
    void shouldRefuseARoomTheAskerIsNotIn() {
        final var roomId = createRoom();
        signIn(new PlayerIdentity(PlayerId.generate(), "Passer-by", true));

        assertThat(fill(roomId)).isInstanceOf(FillWithBotsUseCase.FillWithBotsResult.NotAMember.class);
    }

    @Test
    void shouldRefuseARoomThatDoesNotExist() {
        final var result = fill(RoomId.generate().value().toString());

        assertThat(result).isInstanceOf(FillWithBotsUseCase.FillWithBotsResult.RoomNotFound.class);
    }

    @Test
    void shouldRefuseARoomThatIsAlreadyPlaying() {
        final var roomId = createRoom();
        fill(roomId);

        final var again = fill(roomId);

        assertThat(again).isInstanceOf(FillWithBotsUseCase.FillWithBotsResult.RoomNotWaiting.class);
        assertThat(gamesStarted)
            .as("a second ask does not start a second game")
            .hasSize(1);
    }

    private void joinAsSomeoneElse(String roomId) {
        final var other = new PlayerIdentity(PlayerId.generate(), "Someone", true);
        signIn(other);
        roomService.joinRoom(new com.ginebra.lobby.port.in.JoinRoomUseCase.JoinRoomCommand(roomId));
        signIn(host);
    }
}
