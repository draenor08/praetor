package com.praetor.submission.engine;

/** Outcome of the compile phase. {@code log} is the compiler stderr (the CE message) when failed. */
public record CompileResult(boolean success, String log) {
}
