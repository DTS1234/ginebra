package com.ginebra.lobby.adapter.in;

import com.ginebra.lobby.port.in.CreateRoomUseCase;
import com.ginebra.lobby.port.in.GetRoomUseCase;
import com.ginebra.lobby.port.in.JoinRoomUseCase;
import com.ginebra.lobby.port.in.LeaveRoomUseCase;
import com.ginebra.lobby.port.in.ListRoomsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final CreateRoomUseCase createRoomUseCase;
    private final ListRoomsUseCase listRoomsUseCase;
    private final JoinRoomUseCase joinRoomUseCase;
    private final LeaveRoomUseCase leaveRoomUseCase;
    private final GetRoomUseCase getRoomUseCase;

    public RoomController(
        CreateRoomUseCase createRoomUseCase,
        ListRoomsUseCase listRoomsUseCase,
        JoinRoomUseCase joinRoomUseCase,
        LeaveRoomUseCase leaveRoomUseCase,
        GetRoomUseCase getRoomUseCase
    ) {
        this.createRoomUseCase = Objects.requireNonNull(
            createRoomUseCase,
            "createRoomUseCase must not be null"
        );
        this.listRoomsUseCase = Objects.requireNonNull(
            listRoomsUseCase,
            "listRoomsUseCase must not be null"
        );
        this.joinRoomUseCase = Objects.requireNonNull(
            joinRoomUseCase,
            "joinRoomUseCase must not be null"
        );
        this.leaveRoomUseCase = Objects.requireNonNull(
            leaveRoomUseCase,
            "leaveRoomUseCase must not be null"
        );
        this.getRoomUseCase = Objects.requireNonNull(
            getRoomUseCase,
            "getRoomUseCase must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponseDto> createRoom(
        @RequestBody(required = false) CreateRoomRequestDto request
    ) {
        // Create command (empty for now)
        final var command = new CreateRoomUseCase.CreateRoomCommand();
        final var response = createRoomUseCase.createRoom(command);

        // Map to response DTO
        final var dto = new CreateRoomResponseDto(
            response.roomId(),
            response.players().stream()
                .map(p -> new PlayerDto(p.playerId(), p.displayName()))
                .toList(),
            response.status()
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<ListRoomsResponseDto> listRooms() {
        final var response = listRoomsUseCase.listRooms();

        final var rooms = response.rooms().stream()
            .map(r -> new RoomSummaryDto(r.roomId(), r.playerCount(), r.createdAt()))
            .toList();

        return ResponseEntity.ok(new ListRoomsResponseDto(rooms));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable String roomId) {
        final var result = getRoomUseCase.getRoom(new GetRoomUseCase.GetRoomCommand(roomId));

        if (result instanceof GetRoomUseCase.GetRoomResult.Success success) {
            final var players = success.players().stream()
                .map(p -> new PlayerDto(p.playerId(), p.displayName()))
                .toList();
            return ResponseEntity.ok(new GetRoomResponseDto(
                success.roomId(),
                players,
                success.status(),
                success.gameId()
            ));
        } else if (result instanceof GetRoomUseCase.GetRoomResult.RoomNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto("ROOM_NOT_FOUND", "Room not found"));
        } else if (result instanceof GetRoomUseCase.GetRoomResult.NotAMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponseDto("NOT_A_MEMBER", "You are not in this room"));
        } else {
            throw new IllegalStateException("Unexpected result: " + result);
        }
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId) {
        final var command = new JoinRoomUseCase.JoinRoomCommand(roomId);
        final var result = joinRoomUseCase.joinRoom(command);

        if (result instanceof JoinRoomUseCase.JoinRoomResult.Success success) {
            final var players = success.players().stream()
                .map(p -> new PlayerDto(p.playerId(), p.displayName()))
                .toList();
            return ResponseEntity.ok(new JoinRoomResponseDto(
                success.roomId(),
                players,
                success.status(),
                success.gameId(),
                success.websocketUrl()
            ));
        } else if (result instanceof JoinRoomUseCase.JoinRoomResult.RoomNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto("ROOM_NOT_FOUND", "Room not found"));
        } else if (result instanceof JoinRoomUseCase.JoinRoomResult.RoomFull) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("ROOM_FULL", "Room is full"));
        } else if (result instanceof JoinRoomUseCase.JoinRoomResult.RoomNotWaiting notWaiting) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("ROOM_NOT_WAITING", "Room is not accepting players, status: " + notWaiting.currentStatus()));
        } else if (result instanceof JoinRoomUseCase.JoinRoomResult.PlayerAlreadyInRoom) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("PLAYER_ALREADY_IN_ROOM", "You are already in this room"));
        } else {
            throw new IllegalStateException("Unexpected result: " + result);
        }
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId) {
        final var command = new LeaveRoomUseCase.LeaveRoomCommand(roomId);
        final var result = leaveRoomUseCase.leaveRoom(command);

        if (result instanceof LeaveRoomUseCase.LeaveRoomResult.Success) {
            return ResponseEntity.ok(new LeaveRoomResponseDto(true));
        } else if (result instanceof LeaveRoomUseCase.LeaveRoomResult.RoomNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto("ROOM_NOT_FOUND", "Room not found"));
        } else if (result instanceof LeaveRoomUseCase.LeaveRoomResult.PlayerNotInRoom) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("PLAYER_NOT_IN_ROOM", "You are not in this room"));
        } else if (result instanceof LeaveRoomUseCase.LeaveRoomResult.CannotLeaveAfterStarting cannotLeave) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("CANNOT_LEAVE_AFTER_STARTING", "Cannot leave room after game has started, status: " + cannotLeave.currentStatus()));
        } else {
            throw new IllegalStateException("Unexpected result: " + result);
        }
    }

    // HTTP DTOs (separate from use case models)
    record GetRoomResponseDto(
        String roomId,
        List<PlayerDto> players,
        String status,
        String gameId
    ) {}

    record CreateRoomRequestDto() {
        // Empty for now - future: room options
    }

    record CreateRoomResponseDto(
        String roomId,
        List<PlayerDto> players,
        String status
    ) {}

    record PlayerDto(
        String playerId,
        String displayName
    ) {}

    record ListRoomsResponseDto(List<RoomSummaryDto> rooms) {}

    record RoomSummaryDto(
        String roomId,
        int playerCount,
        String createdAt
    ) {}

    record JoinRoomResponseDto(
        String roomId,
        List<PlayerDto> players,
        String status,
        String gameId,
        String websocketUrl
    ) {}

    record ErrorResponseDto(String code, String message) {}

    record LeaveRoomResponseDto(boolean success) {}
}
