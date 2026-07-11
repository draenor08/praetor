package com.praetor.submission.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The judge worker pool, sized by {@code praetor.judge.workers}. Bounded queue + AbortPolicy: a
 * burst past capacity is rejected rather than blocking the HTTP thread — the reaper re-enqueues any
 * rejected (still-QUEUED) submission. {@code @EnableScheduling} powers that reaper.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("judgeExecutor")
    public ThreadPoolTaskExecutor judgeExecutor(JudgeProperties props) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(props.workers());
        ex.setMaxPoolSize(props.workers());
        ex.setQueueCapacity(props.workers() * 8);
        ex.setThreadNamePrefix("judge-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}
