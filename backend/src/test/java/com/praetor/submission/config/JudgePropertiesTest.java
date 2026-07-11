package com.praetor.submission.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binding + fail-fast validation for {@link JudgeProperties}, without a full
 * {@code @SpringBootTest} (no web, no datasource, no Testcontainers). Uses
 * {@link ApplicationContextRunner} with {@link ValidationAutoConfiguration} so the
 * {@code @Validated} constraints actually fire during binding.
 */
class JudgePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(JudgeProperties.class)
    static class TestConfig {
    }

    @Test
    void binds_all_five_knobs() {
        runner.withPropertyValues(
                "praetor.judge.image=custom-judge:9",
                "praetor.judge.cpu-seconds=5",
                "praetor.judge.mem-mb=512",
                "praetor.judge.pids-max=128",
                "praetor.judge.workers=4"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            JudgeProperties p = ctx.getBean(JudgeProperties.class);
            assertThat(p.image()).isEqualTo("custom-judge:9");
            assertThat(p.cpuSeconds()).isEqualTo(5);
            assertThat(p.memMb()).isEqualTo(512);
            assertThat(p.pidsMax()).isEqualTo(128);
            assertThat(p.workers()).isEqualTo(4);
        });
    }

    @Test
    void rejects_non_positive_mem_at_boot() {
        runner.withPropertyValues(
                "praetor.judge.image=x",
                "praetor.judge.cpu-seconds=2",
                "praetor.judge.mem-mb=0",
                "praetor.judge.pids-max=64",
                "praetor.judge.workers=2"
        ).run(ctx -> {
            assertThat(ctx).hasFailed();
            // the offending field ("memMb") is in the nested BindValidationException,
            // not the outer message → assert against the whole stack trace.
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("memMb");
        });
    }

    @Test
    void rejects_blank_image_at_boot() {
        runner.withPropertyValues(
                "praetor.judge.image=",
                "praetor.judge.cpu-seconds=2",
                "praetor.judge.mem-mb=256",
                "praetor.judge.pids-max=64",
                "praetor.judge.workers=2"
        ).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("image");
        });
    }
}
