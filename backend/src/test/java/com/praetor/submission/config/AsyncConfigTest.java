package com.praetor.submission.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void judge_executor_sized_to_workers() {
        JudgeProperties props =
                new JudgeProperties("img", 2, 256, 64, 4, "/judge", "praetor_work", false);
        ThreadPoolTaskExecutor ex = new AsyncConfig().judgeExecutor(props);
        assertThat(ex.getCorePoolSize()).isEqualTo(4);
        assertThat(ex.getMaxPoolSize()).isEqualTo(4);
    }
}
