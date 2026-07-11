package com.praetor.submission.engine;

import com.praetor.submission.entity.JudgeTestCase;
import org.springframework.stereotype.Component;

/**
 * PR-1 stub: compiles OK and "runs" by echoing the test case's expected output, so every test
 * yields AC through the real {@link VerdictEvaluator}. Proves submit→async→verdict→WS→GET without
 * docker. PR-2 adds the real {@code DockerSandboxRunner} annotated {@code @Primary}, which then
 * wins injection; this stub is retired to a {@code @Profile("stub")} at that point.
 */
@Component
public class StubSandboxRunner implements SandboxRunner {

    @Override
    public CompileResult compile(String runId, String sourceCode) {
        return new CompileResult(true, "");
    }

    @Override
    public RunResult run(String runId, JudgeTestCase testCase, RunLimits limits) {
        // simulate a correct, fast, light program
        return new RunResult(0, testCase.getExpected(), 1, 1024, false, false);
    }
}
