package com.ginebra.lobby.adapter.in;

import com.ginebra.lobby.port.in.CreateRoomUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final CreateRoomUseCase createRoomUseCase;

    public RoomController(CreateRoomUseCase createRoomUseCase) {
        this.createRoomUseCase = Objects.requireNonNull(
            createRoomUseCase,
            "createRoomUseCase must not be null"
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

    // HTTP DTOs (separate from use case models)
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
}
