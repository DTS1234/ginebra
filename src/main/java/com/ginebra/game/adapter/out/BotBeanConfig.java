package com.ginebra.game.adapter.out;

import com.ginebra.game.application.BotRoster;
import com.ginebra.game.application.BotTurnDriver;
import com.ginebra.game.domain.service.BotStrategy;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.RandomBotStrategy;
import com.ginebra.game.port.in.KingChoiceUseCase;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.SoledadUseCase;
import com.ginebra.game.port.in.TodoUseCase;
import com.ginebra.game.port.out.GameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bots, wired up.
 *
 * Built here rather than annotated, so the pieces that make a bot what it is - which
 * strategy, how long it thinks, how many tables it can play at once - are all in one
 * place and swapping the strategy is a one-line change.
 */
@Configuration
public class BotBeanConfig {

    /**
     * How many tables can have bots thinking at the same time. Each move costs a thread
     * only for as long as the pause below, so a handful covers a play-test comfortably.
     */
    private static final int BOT_THREADS = 4;

    @Bean
    public BotStrategy botStrategy(Random gameRandom) {
        return new RandomBotStrategy(gameRandom);
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService botExecutor() {
        final var counter = new AtomicInteger();
        final ThreadFactory factory = runnable -> {
            final var thread = new Thread(runnable, "ginebra-bot-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(BOT_THREADS, factory);
    }

    @Bean
    public BotTurnDriver botTurnDriver(
        GameRepository gameRepository,
        BotRoster botRoster,
        BotStrategy botStrategy,
        MoveValidator moveValidator,
        SoledadUseCase soledadUseCase,
        SelectTrumpUseCase selectTrumpUseCase,
        PlayCardUseCase playCardUseCase,
        TodoUseCase todoUseCase,
        KingChoiceUseCase kingChoiceUseCase,
        ExecutorService botExecutor,
        @Value("${ginebra.bots.move-delay:800ms}") Duration moveDelay
    ) {
        return new BotTurnDriver(
            gameRepository,
            botRoster,
            botStrategy,
            moveValidator,
            soledadUseCase,
            selectTrumpUseCase,
            playCardUseCase,
            todoUseCase,
            kingChoiceUseCase,
            botExecutor,
            moveDelay
        );
    }
}
