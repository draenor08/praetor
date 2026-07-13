package com.praetor.submission.engine;

import com.praetor.submission.entity.JudgeTestCase;

/**
 * Compiles and runs submitted code in isolation. PR-1 ships {@link StubSandboxRunner}; PR-2 swaps
 * in the real docker-backed implementation. Callers (the orchestrator) treat this as the only
 * boundary to code execution.
 *
 * <p>{@code run} receives the whole {@link JudgeTestCase}: the real implementation uses only its
 * {@code input} (fed on stdin); the stub uses {@code expected} to simulate a correct program so the
 * pipeline can be exercised end-to-end without docker.
 */
public interface SandboxRunner {

    CompileResult compile(String runId, Language language, String sourceCode);

    RunResult run(String runId, Language language, JudgeTestCase testCase, RunLimits limits);

    /** Remove the per-run working directory / any leftover containers. Best-effort. */
    default void cleanup(String runId) {
    }
}
