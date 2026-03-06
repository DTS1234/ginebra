package com.ginebra.lobby.application;

import com.ginebra.identity.adapter.in.SecurityContextHelper;
import com.ginebra.lobby.domain.AddPlayerResult;
import com.ginebra.lobby.domain.RemovePlayerResult;
import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;
import com.ginebra.lobby.port.in.CreateRoomUseCase;
import com.ginebra.lobby.port.in.JoinRoomUseCase;
import com.ginebra.lobby.port.in.LeaveRoomUseCase;
import com.ginebra.lobby.port.in.ListRoomsUseCase;
import com.ginebra.lobby.port.out.GameStarter;
import com.ginebra.lobby.port.out.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoomService implements CreateRoomUseCase, ListRoomsUseCase, JoinRoomUseCase, LeaveRoomUseCase {

    private final RoomRepository roomRepository;
    private final GameStarter gameStarter;
    private final Clock clock;

    public RoomService(
        RoomRepository roomRepository,
        GameStarter gameStarter,
        Clock clock
    ) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository must not be null");
        this.gameStarter = Objects.requireNonNull(gameStarter, "gameStarter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CreateRoomResponse createRoom(CreateRoomCommand command) {
        // Get authenticated player from SecurityContext
        final var playerIdentity = SecurityContextHelper.requireCurrentPlayerIdentity();

        // Create room with current timestamp
        final var roomId = RoomId.generate();
        final var now = clock.instant();
        final var room = Room.create(roomId, now);

        // Add creator as first player
        final var addResult = room.addPlayer(
            playerIdentity.playerId(),
            playerIdentity.displayName(),
            now
        );

        // Validate result (should always be Success for first player)
        if (!(addResult instanceof AddPlayerResult.Success)) {
            throw new IllegalStateException(
                "Failed to add creator to new room: " + addResult.getClass().getSimpleName()
            );
        }

        // Persist room
        roomRepository.save(room);

        // Build response with DTOs (not domain objects)
        final var players = room.players().stream()
            .map(p -> new CreateRoomUseCase.PlayerDto(
                p.playerId().value().toString(),
                p.displayName()
            ))
            .collect(Collectors.toList());

        return new CreateRoomResponse(
            room.id().value().toString(),
            players,
            room.status().name()
        );
    }

    @Override
    public ListRoomsResponse listRooms() {
        final var joinableRooms = roomRepository.findAll().stream()
            .filter(Room::canJoin)
            .map(room -> new RoomSummaryDto(
                room.id().value().toString(),
                room.players().size(),
                room.createdAt().toString()
            ))
            .toList();

        return new ListRoomsResponse(joinableRooms);
    }

    @Override
    public JoinRoomResult joinRoom(JoinRoomCommand command) {
        final var playerIdentity = SecurityContextHelper.requireCurrentPlayerIdentity();

        // Parse room ID
        final var roomId = new RoomId(UUID.fromString(command.roomId()));

        // Find room
        final var roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return new JoinRoomResult.RoomNotFound();
        }

        final var room = roomOpt.get();
        final var now = clock.instant();

        // Try to add player
        final var addResult = room.addPlayer(
            playerIdentity.playerId(),
            playerIdentity.displayName(),
            now
        );

        // Map domain result to use case result
        if (addResult instanceof AddPlayerResult.Success) {
            roomRepository.save(room);
            return buildSuccessResponse(room);
        } else if (addResult instanceof AddPlayerResult.RoomFull_GameShouldStart) {
            final var playerIds = room.players().stream()
                .map(com.ginebra.lobby.domain.RoomPlayer::playerId)
                .toList();
            final var gameResult = gameStarter.startGame(playerIds);

            if (gameResult instanceof GameStarter.GameStartResult.Success success) {
                room.convertToGame(success.gameId());
                roomRepository.save(room);
                return buildSuccessResponseWithGame(room, success.gameId());
            }
            roomRepository.save(room);
            return buildSuccessResponse(room);
        } else if (addResult instanceof AddPlayerResult.RoomFull) {
            return new JoinRoomResult.RoomFull();
        } else if (addResult instanceof AddPlayerResult.RoomNotWaiting notWaiting) {
            return new JoinRoomResult.RoomNotWaiting(notWaiting.currentStatus().name());
        } else if (addResult instanceof AddPlayerResult.PlayerAlreadyInRoom) {
            return new JoinRoomResult.PlayerAlreadyInRoom();
        } else {
            throw new IllegalStateException("Unexpected result: " + addResult);
        }
    }

    private JoinRoomResult.Success buildSuccessResponse(Room room) {
        return buildSuccessResponseWithGame(room, null);
    }

    private JoinRoomResult.Success buildSuccessResponseWithGame(Room room, com.ginebra.lobby.domain.GameId gameId) {
        final var players = room.players().stream()
            .map(p -> new JoinRoomUseCase.PlayerDto(
                p.playerId().value().toString(),
                p.displayName()
            ))
            .toList();

        final var gameIdStr = gameId != null ? gameId.value().toString() : null;
        final var websocketUrl = gameId != null ? "/ws/game" : null;

        return new JoinRoomResult.Success(
            room.id().value().toString(),
            players,
            room.status().name(),
            gameIdStr,
            websocketUrl
        );
    }

    @Override
    public LeaveRoomResult leaveRoom(LeaveRoomCommand command) {
        final var playerIdentity = SecurityContextHelper.requireCurrentPlayerIdentity();

        // Parse room ID
        final var roomId = new RoomId(UUID.fromString(command.roomId()));

        // Find room
        final var roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return new LeaveRoomResult.RoomNotFound();
        }

        final var room = roomOpt.get();

        // Try to remove player
        final var removeResult = room.removePlayer(playerIdentity.playerId());

        // Map domain result to use case result
        if (removeResult instanceof RemovePlayerResult.Success) {
            roomRepository.save(room);
            return new LeaveRoomResult.Success();
        } else if (removeResult instanceof RemovePlayerResult.RoomNowEmpty_ShouldDelete) {
            roomRepository.delete(roomId);
            return new LeaveRoomResult.Success();
        } else if (removeResult instanceof RemovePlayerResult.PlayerNotInRoom) {
            return new LeaveRoomResult.PlayerNotInRoom();
        } else if (removeResult instanceof RemovePlayerResult.CannotLeaveAfterStarting) {
            return new LeaveRoomResult.CannotLeaveAfterStarting(room.status().name());
        } else {
            throw new IllegalStateException("Unexpected result: " + removeResult);
        }
    }
}
