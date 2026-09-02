# Demo solutions

An accepted solution to **every seeded problem** in **every language the judge offers**, so a demo
never involves writing code in front of an audience. Open the file, paste, submit.

```
demo/solutions/
  <problem-slug>/
    main.cpp     → language CPP
    main.py      → language PYTHON
    Main.java    → language JAVA
  verify.mjs     → compiles and runs all of them against the seeded test cases
```

**The file names are not decorative.** The judge fixes them per language
(`backend/.../submission/engine/Language.java`): C++ is always `main.cpp`, Python `main.py`, and
Java `Main.java` holding `public class Main` — `javac` requires the class name to match the file, so
a submission calling it anything else is an honest CE. The paste target in the UI is a single editor,
so the file name only matters when you copy the *file*; the class name inside the Java one matters
always.

## Verified

```bash
node demo/solutions/verify.mjs            # all of them
node demo/solutions/verify.mjs a-plus-b   # one problem
```

**Last run 2026-09-02 on `bf59aad`: 45 accepted · 0 failed · 1 not verifiable.** Slowest case was
164 ms against a 3000 ms Java budget, so nothing here is anywhere near a time limit.

`verify.mjs` compiles and runs each file **inside the real `praetor-judge:latest` image** with the
real compile and run commands, and decides AC/WA with the same rules the backend uses — the
`ExactChecker` (trailing whitespace tolerant, internal spacing significant), `TokenChecker`
(whitespace-insensitive) and `FloatChecker` (absolute *or* relative epsilon) semantics are
reimplemented in it, with the Java files named as the source of truth.

⚠️ It deliberately **does not submit through the API.** Submissions would write rows and perturb the
seed shape that `praetor-browser-check.md` asserts throughout — solve stats, dashboard tiles,
standings, first-solve highlights. Verifying through the toolchain instead costs nothing and changes
no state.

## Which problem to reach for

| Contest | Label | Slug | Use it to demo |
|---|---|---|---|
| **Demo Round 1** (live) | A | `a-plus-b` | submitting **inside a running contest**, standings updating, the freeze |
| | B | `reverse-string` | a second contest problem, for a rival's board row |
| | C | `circle-area` | **FLOAT** judging — 8 decimals, eps 1e-6 |
| Warm-up Round 0 (ended) | A | `count-vowels` | editorials (its contest has ended, so the gate opens) |
| | B | `max-of-three` | first-solve highlight on `/standings/2` |
| | C | `is-prime` | — |
| Round 2 (upcoming) | A | `sort-array` | **TOKEN** judging; embargoed until it starts |
| | B | `coin-change-greedy` | — |
| | C | `triangle-area` | FLOAT again |
| — | — | `palindrome-check`, `sum-to-n` | free of any contest: submit any time, no embargo |
| draft | — | `two-sums`, `bracket-balance`, `grid-walk` | contest **authoring** — they are the pool at `/contests/new` |
| draft | — | `median-stream` | the deliberately **unjudgeable** one, see below |

**A contest's problems are embargoed from `/problems` until it ends**, so `sort-array` and friends
are not browsable as a plain user before Round 2 starts — reach them through the contest page, or as
staff.

## Notes per solution, where there is something to get wrong

- **`a-plus-b`** — both operands reach 1e9, so test 4 (`1000000000 1000000000`) overflows 32 bits.
  C++ and Java use 64-bit. A deliberately `int` version is the cleanest way to demo a **WA** that is
  not a typo.
- **`sum-to-n`** — n up to 1e9 makes the answer ~5e17, and `n*(n+1)` reaches ~1e18. 64-bit both.
- **`is-prime`** — trial division to √n is ~31 623 steps at n = 1e9; comfortable.
- **`circle-area` / `triangle-area`** — FLOAT mode, eps 1e-6, printed to 8 decimals to match the
  expected values. `b * h / 2.0`, never `/ 2`.
- **`two-sums`** — test 3 is `2 4 / 2 2` → **YES**, so two equal values at different positions are a
  valid pair. The seen-set walk gets this right; a naive "value != value" guard does not.
- **`coin-change-greedy`** — greedy is optimal for *this* coin set only. Do not present it as the
  general algorithm.
- **`grid-walk`** — C(n+m−2, n−1) mod 1e9+7, done as a rolling Pascal row so there is no modular
  inverse to get wrong.
- **`median-stream`** — ⚠️ **seeded with zero test cases on purpose.** It is the problem the browser
  pass checks for a red `0` in Manage's Tests column (step A5). A submission here cannot be
  accepted, and the statement never defines the even-length tie-break, so these solutions assume the
  **lower** median. Kept for completeness; **do not pick it for the demo.**

## If a solution ever fails

Run `verify.mjs` first — it separates "the solution is wrong" from "the judge is misbehaving". If
`verify.mjs` passes and the UI still refuses the same code, the difference is the API path, not the
toolchain, and `scripts/e2e.mjs` is the thing that exercises it.

A CE mentioning `javac: not found` means the judge image predates the JDK going into it — rebuild on
the host: `docker build -t praetor-judge:latest judge/`.
