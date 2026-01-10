package com.ginebra.identity.application;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.in.CreateAnonymousUseCase;
import com.ginebra.identity.port.out.SessionRepository;
import com.ginebra.identity.port.out.TokenGenerator;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AnonymousAuthService implements CreateAnonymousUseCase {

    private final TokenGenerator tokenGenerator;
    private final SessionRepository sessionRepository;

    public AnonymousAuthService(
        TokenGenerator tokenGenerator,
        SessionRepository sessionRepository
    ) {
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    }

    @Override
    public CreateAnonymousResponse createAnonymous(CreateAnonymousCommand command) {
        // Generate identity
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, command.displayName());

        // Persist session
        sessionRepository.save(identity);

        // Generate JWT
        final var token = tokenGenerator.generateToken(identity);

        // Return response
        return new CreateAnonymousResponse(
            token,
            identity.playerId().toString(),
            identity.displayName()
        );
    }
}
