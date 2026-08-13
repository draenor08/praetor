package com.praetor.submission.engine;

import java.util.List;

/**
 * A supported judge language and everything the sandbox needs to build + run it: the source file
 * name, the compile command (for interpreted languages this is a syntax check that still yields a
 * CE on error), the run command, and per-language limit multipliers (interpreted languages get more
 * wall time and memory than the problem's C++-oriented base limits).
 *
 * <p>Commands are argv fragments run inside the judge container's per-run work dir. Adding a
 * language is a new enum constant here + the toolchain in {@code judge/Dockerfile} — nothing in the
 * runner or the service is language-aware.
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
            3.0, 2.0),

    // The file name is fixed by the language: javac requires Main.java to hold `public class Main`,
    // so submissions must use that class name. A mismatch is a compile error, i.e. an honest CE.
    JAVA("Main.java",
            List.of("javac", "-encoding", "UTF-8", "Main.java"),
            // SerialGC holds the JVM's thread count near the floor — the sandbox runs with
            // --pids-limit and a parallel-GC JVM can spend that budget before main() starts.
            // -Xss64m gives deep recursion room, which C++ gets from the default stack.
            List.of("java", "-XX:+UseSerialGC", "-Xss64m", "-cp", ".", "Main"),
            // Wall time absorbs JVM startup (~100-250ms). Memory is ×3 because the measured figure
            // is whole-process RSS, and a JVM's floor is tens of MB before user code allocates.
            3.0, 3.0);

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
