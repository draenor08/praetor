package com.praetor.submission.engine;

import java.util.List;

/**
 * A supported judge language and everything the sandbox needs to build + run it: the source file
 * name, the compile command (for interpreted languages this is a syntax check that still yields a
 * CE on error), the run command, and per-language limit multipliers (interpreted languages get more
 * wall time and memory than the problem's C++-oriented base limits).
 *
 * <p>Commands are argv fragments run inside the judge container's per-run work dir. Adding a
 * language (e.g. Java) is a new enum constant + the toolchain in {@code judge/Dockerfile}.
 */
public enum Language {

    CPP("main.cpp",
            List.of("g++", "-O2", "-std=gnu++17", "-o", "prog", "main.cpp"),
            List.of("./prog"),
            1.0, 1.0),

    PYTHON("main.py",
            // py_compile is a syntax check; a SyntaxError exits non-zero with stderr → CE.
            List.of("python3", "-m", "py_compile", "main.py"),
            List.of("python3", "main.py"),
            3.0, 2.0);

    private final String sourceFile;
    private final List<String> compileCmd;
    private final List<String> runCmd;
    private final double timeMultiplier;
    private final double memMultiplier;

    Language(String sourceFile, List<String> compileCmd, List<String> runCmd,
             double timeMultiplier, double memMultiplier) {
        this.sourceFile = sourceFile;
        this.compileCmd = compileCmd;
        this.runCmd = runCmd;
        this.timeMultiplier = timeMultiplier;
        this.memMultiplier = memMultiplier;
    }

    public String sourceFile() {
        return sourceFile;
    }

    public List<String> compileCmd() {
        return compileCmd;
    }

    public List<String> runCmd() {
        return runCmd;
    }

    public double timeMultiplier() {
        return timeMultiplier;
    }

    public double memMultiplier() {
        return memMultiplier;
    }

    /** Resolve the {@code submissions.language} string, or null if unsupported. */
    public static Language from(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Language.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
