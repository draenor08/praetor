package com.praetor.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JWT config must fail at BOOT, not at the first login. Mirrors JudgePropertiesTest.
 */
class JwtPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsSecretAndExpiryFromProperties() {
        runner.withPropertyValues(
                        "praetor.jwt.secret=an-explicitly-long-enough-dev-secret-value",
                        "praetor.jwt.expiry-min=120")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties props = context.getBean(JwtProperties.class);
                    assertThat(props.secret()).startsWith("an-explicitly-long-enough");
                    assertThat(props.expiryMin()).isEqualTo(120);
                });
    }

    @Test
    void rejectsSecretShorterThanAnHs256Key() {
        // Keys.hmacShaKeyFor would otherwise throw on the first token issued, at runtime.
        runner.withPropertyValues(
                        "praetor.jwt.secret=too-short",
                        "praetor.jwt.expiry-min=120")
                .run(context -> {
                    assertThat(context).hasFailed();
                    // the field name is inside the nested BindValidationException, not the outer msg
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("secret");
                });
    }

    @Test
    void refusesToStartWithNoSecretAndSaysHowToSetOne() {
        // application.yml bridges JWT_SECRET with an EMPTY default on purpose — there is no usable
        // fallback, so an unset env var must stop the app here, with actionable guidance.
        runner.withPropertyValues(
                        "praetor.jwt.secret=",
                        "praetor.jwt.expiry-min=120")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("JWT_SECRET");
                });
    }

    @Test
    void rejectsNonPositiveExpiry() {
        runner.withPropertyValues(
                        "praetor.jwt.secret=an-explicitly-long-enough-dev-secret-value",
                        "praetor.jwt.expiry-min=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("expiryMin");
                });
    }
}
