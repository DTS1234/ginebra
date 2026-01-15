package com.ginebra.lobby.application;

import com.ginebra.identity.adapter.in.SecurityContextHelper;
import com.ginebra.lobby.domain.AddPlayerResult;
import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;
import com.ginebra.lobby.port.in.CreateRoomUseCase;
import com.ginebra.lobby.port.out.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RoomService implements CreateRoomUseCase {

    private final RoomRepository roomRepository;
    private final Clock clock;

    public RoomService(
        RoomRepository roomRepository,
        Clock clock
    ) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository must not be null");
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
            .map(p -> new PlayerDto(
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
}
