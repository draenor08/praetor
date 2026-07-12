package com.praetor.submission.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test for the GNU-time elapsed parser. No docker — just the fiddly string→ms logic that
 * TLE detection depends on. (The full sandbox is verified live via the end-to-end submit flow.)
 */
class DockerSandboxRunnerTest {

    @Test
    void parsesSecondsWithFraction() {
        assertThat(DockerSandboxRunner.parseElapsedMs("0:00.01")).isEqualTo(10);
        assertThat(DockerSandboxRunner.parseElapsedMs("0:01.50")).isEqualTo(1500);
    }

    @Test
    void parsesMinutesAndSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("1:02.5")).isEqualTo(62_500);
        assertThat(DockerSandboxRunner.parseElapsedMs("2:00")).isEqualTo(120_000);
    }

    @Test
    void parsesHoursMinutesSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("1:02:03")).isEqualTo(3_723_000);
    }

    @Test
    void parsesBareSeconds() {
        assertThat(DockerSandboxRunner.parseElapsedMs("0.25")).isEqualTo(250);
    }

    @Test
    void returnsMinusOneOnGarbage() {
        assertThat(DockerSandboxRunner.parseElapsedMs("n/a")).isEqualTo(-1);
    }
}
