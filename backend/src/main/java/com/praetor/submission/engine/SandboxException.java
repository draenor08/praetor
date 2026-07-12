package com.praetor.submission.engine;

/**
 * Infrastructure fault while compiling/running in the sandbox (docker launch failed, work dir
 * unwritable, etc.) — as opposed to a normal verdict. The orchestrator catches this and marks the
 * submission {@code ERROR}, never {@code DONE}.
 */
public class SandboxException extends RuntimeException {
    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
