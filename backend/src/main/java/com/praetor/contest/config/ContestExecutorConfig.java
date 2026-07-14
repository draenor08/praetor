package com.praetor.contest.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The standings recompute pool. Deliberately SINGLE-threaded so recomputes for a contest serialise
 * — a full board is published each time, and overlapping recomputes could publish an older board
 * after a newer one. {@code CallerRunsPolicy} rather than aborting: a standings recompute is
 * correctness-relevant (a dropped one would leave a stale board), so on a full queue the publishing
 * thread runs it inline instead of discarding it. {@code @EnableAsync} is already active (submission
 * {@code AsyncConfig}); this only contributes the bean.
 */
@Configuration
public class ContestExecutorConfig {

    @Bean("contestExecutor")
    public ThreadPoolTaskExecutor contestExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(64);
        ex.setThreadNamePrefix("contest-standings-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
