package com.praetor.submission.engine;

import com.praetor.submission.entity.JudgeTestCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub runner: compiles OK and "runs" by echoing the test case's expected output, so every test
 * yields AC through the real {@link VerdictEvaluator}. Proves submit→async→verdict→WS→GET without
 * docker. Since PR-2a, {@link DockerSandboxRunner} is {@code @Primary} and wins injection by
 * default; this stub is only active under the {@code stub} profile
 * ({@code SPRING_PROFILES_ACTIVE=stub}), e.g. for docker-less local runs or tests.
 */
@Component
@Profile("stub")
public class StubSandboxRunner implements SandboxRunner {

    @Override
    public CompileResult compile(String runId, Language language, String sourceCode) {
        return new CompileResult(true, "");
    }

    @Override
    public RunResult run(String runId, Language language, JudgeTestCase testCase, RunLimits limits) {
        // simulate a correct, fast, light program
        return new RunResult(0, testCase.getExpected(), 1, 1024, false, false);
    }
}
