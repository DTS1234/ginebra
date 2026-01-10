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

    record CreateAnonymousRequestDto(String displayName) {}

    record CreateAnonymousResponseDto(
        String token,
        String playerId,
        String displayName
    ) {}
}
