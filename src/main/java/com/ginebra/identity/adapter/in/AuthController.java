package com.ginebra.identity.adapter.in;

import com.ginebra.identity.port.in.CreateAnonymousUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CreateAnonymousUseCase createAnonymousUseCase;

    public AuthController(CreateAnonymousUseCase createAnonymousUseCase) {
        this.createAnonymousUseCase = Objects.requireNonNull(createAnonymousUseCase, "createAnonymousUseCase must not be null");
    }

    @PostMapping("/anonymous")
    public ResponseEntity<CreateAnonymousResponseDto> createAnonymous(
        @RequestBody(required = false) CreateAnonymousRequestDto request
    ) {
        final var displayName = Optional.ofNullable(request)
            .flatMap(r -> Optional.ofNullable(r.displayName()));

        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(displayName);
        final var response = createAnonymousUseCase.createAnonymous(command);

        final var dto = new CreateAnonymousResponseDto(
            response.token(),
            response.playerId(),
            response.displayName()
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/me")
    public ResponseEntity<GetCurrentPlayerResponseDto> getCurrentPlayer() {
        // Get PlayerIdentity from SecurityContext (populated by JwtAuthenticationFilter)
        final var playerIdentity = SecurityContextHelper.requireCurrentPlayerIdentity();

        final var dto = new GetCurrentPlayerResponseDto(
            playerIdentity.playerId().value().toString(),
            playerIdentity.displayName(),
            playerIdentity.anonymous()
        );

        return ResponseEntity.ok(dto);
    }

    record CreateAnonymousRequestDto(String displayName) {}

    record CreateAnonymousResponseDto(
        String token,
        String playerId,
        String displayName
    ) {}

    record GetCurrentPlayerResponseDto(
        String playerId,
        String displayName,
        boolean anonymous
    ) {}
}
