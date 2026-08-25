package com.ginebra.game.adapter.out;

import com.ginebra.game.adapter.in.dto.GameStateMapper;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.TeamResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class GameBeanConfig {

    @Bean
    public CardRankingService cardRankingService() {
        return new CardRankingService();
    }

    @Bean
    public MoveValidator moveValidator() {
        return new MoveValidator(cardRankingService());
    }

    @Bean
    public BasaResolver basaResolver(CardRankingService cardRankingService) {
        return new BasaResolver(cardRankingService);
    }

    @Bean
    public TeamResolver teamResolver() {
        return new TeamResolver();
    }

    @Bean
    public Random gameRandom() {
        return new Random();
    }

    @Bean
    public GameStateMapper gameStateMapper() {
        return new GameStateMapper();
    }
}
