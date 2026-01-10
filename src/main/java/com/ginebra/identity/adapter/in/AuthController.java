package com.ginebra.identity.adapter.in;

import com.ginebra.identity.application.InvalidTokenException;
import com.ginebra.identity.port.in.CreateAnonymousUseCase;
import com.ginebra.identity.port.in.GetCurrentPlayerUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CreateAnonymousUseCase createAnonymousUseCase;
    private final GetCurrentPlayerUseCase getCurrentPlayerUseCase;

    public AuthController(
        CreateAnonymousUseCase createAnonymousUseCase,
        GetCurrentPlayerUseCase getCurrentPlayerUseCase
    ) {
        this.createAnonymousUseCase = Objects.requireNonNull(createAnonymousUseCase, "createAnonymousUseCase must not be null");
        this.getCurrentPlayerUseCase = Objects.requireNonNull(getCurrentPlayerUseCase, "getCurrentPlayerUseCase must not be null");
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
    public ResponseEntity<GetCurrentPlayerResponseDto> getCurrentPlayer(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }

        final var token = authHeader.substring(7);
        final var response = getCurrentPlayerUseCase.getCurrentPlayer(token);

        final var dto = new GetCurrentPlayerResponseDto(
            response.playerId(),
            response.displayName(),
            response.anonymous()
        );

        return ResponseEntity.ok(dto);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidToken(InvalidTokenException ex) {
        final var error = new ErrorResponseDto("INVALID_TOKEN", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
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

    record ErrorResponseDto(String code, String message) {}
}
