# Praetor — your remaining features, scoped

**To:** Mahir · **From:** Sazid · **Date:** 24 Aug 2026
**PR 1 (FR-10) due Thu 27 Aug · PR 2 (FR-25) due Fri 28 Aug**

Companion to `praetor-mahir-handoff.md`. That doc is still correct and still the source of truth —
this page only says what order to do it in, and answers the two questions it told you to ask me.

---

## 1. First, one question

Did you start FR-10 and hit something, or not get to it yet? If you got stuck, tell me where and I'll
unblock it — that's usually a ten-minute conversation. I'd rather answer it than have you work around
it.

## 2. PR 1 — FR-10 backend

`praetor-mahir-handoff.md`, **§4.4 (line 454) through §4.5 (ends line 668).**

**Stop at line 669.** §4.6 is the frontend — that's a later PR, not this one.

Read §4.2 (line 396) and §4.3 (line 422) first for the security rule and the contract. Then:

| | |
|---|---|
| Step 1 | new `dto/SubmissionSummary.java` |
| Step 2 | new `dto/SubmissionPage.java` |
| Step 3 | add query to `SubmissionRepository` |
| Step 4 | add method to `SubmissionService` — **authorisation goes here, not the controller** |
| Step 5 | one thin method on `SubmissionController` |
| §4.5 | 4 test cases in the existing `SubmissionServiceTest.java` |

Two new files, three edits, four tests. All the code lives in `com.praetor.submission` — your own
package, which owns the `submissions` table.

The **only** file you touch outside your own package is `docs/api-contracts.md`, adding the §4.3
contract block. That's additive and expected. Anything else outside `com.praetor.submission`, stop and
message me.

## 3. PR 2 — FR-25, solve statistics

Handoff **§5**, starting line 773. Do this after PR 1 is open — don't run them together.

### The two decisions §5.2 told you to get from me — here are the answers

**Decision 1 — which package?** Use **`com.praetor.identity`**, beside `UserController` and
`RatingController`. The endpoint is `/api/users/{handle}/stats` and `IdentityReadSecurityConfig`
already matches `/api/users/**`, so splitting that path across two packages would be worse than one
additive cross-package method. This is the one place you're cleared to add to `identity` — a new DTO
and a new controller method only. Don't modify anything already in there.

**Decision 2 — anonymous or authenticated?** **Authenticated.** Leave it on the default chain, which
already requires a token. No security config change, and the dashboard is behind `authGuard` anyway.
Do **not** make it anonymous like `/api/users/*/rating` — that would mean editing
`IdentityReadSecurityConfig`, which is not yours.

So §5.4 (line 841) applies as written — it already assumes `identity`.

### §5.3 is not optional

**Read §5.3 (line 817) before writing the query.** `solved` must exclude submissions belonging to a
contest that hasn't ended, or the endpoint leaks exactly what the standings freeze exists to hide —
anyone polling it during a freeze watches the count tick up. Use Postgres `now()`, the server clock,
never a Java timestamp. `SubmissionRepository.existsLiveContestForProblem` is the pattern to copy.

Put the reason in a comment above the query. It isn't obvious, and without it someone deletes it later
as a simplification.

### Then

- §5.4 (line 841) backend · §5.5 (line 989) tests · §5.6 (line 1003) the dashboard card, ends line 1061.
- Two traps in there that are easy to hit: **`attempted = 0` gives you `NaN`, which is invalid JSON —
  every fresh account breaks.** And **`loadSolveStats()` must be called *inside* the `getMe()`
  subscription** — outside it, `this.user` is still null and the card stays blank forever.

## 4. One rule

**Apply the steps as written. Edit the files, don't regenerate them.**

If you're using an AI: give it one step at a time and ask for an edit, not a new version of the file.
If it hands back a rewritten file, that's the wrong output — discard it and ask again for just the
change.

§0.2 of the handoff still governs. The important line: **when the doc and the code disagree, the code
wins — stop and report.**

## 5. Done means, for each PR

A PR against `main` with the template filled in, containing:

- the steps and tests for that feature, and nothing from the other one
- `./mvnw test` green
- the contract block added to `docs/api-contracts.md` in the same PR
- one concern only — no unrelated files

**PR 1 open by Thu 27 Aug. PR 2 open by Fri 28 Aug.** Not the 30th — the 30th is when code has to be
*merged*, and a PR needs review and probably a fix round before that. See §8.

I know PR 2 on top of PR 1 in that window is tight. If you get FR-10 in and FR-25 doesn't make it,
say so and I'll take FR-25 — that's a normal outcome, not a failure.

If something is going to slip, tell me early. That's genuinely fine and I can plan around it. Silence
is the thing that hurts, because then I find out on the 30th.

## 6. Your toast branch — it's yours, I'm not touching it

I'm leaving `Toast-Notification` alone. It's your branch and your feature, and I'd rather it land as
entirely your work than have my commits mixed into it.

Two things stand between it and `main`:

**1. The duration bug.** `error()` and `warning()` pass their duration as the 4th parameter of
`show()`, but `show()` only reads that parameter when `options` is a string. So a normal call like
`toast.error('x')` falls back to 4000 ms instead of the 5000 the comment promises. Two extra fallback
branches in `show()` fixes it.

**2. The call sites.** Nothing calls `ToastService` right now, so it renders an empty container.
~30 minutes, and the rule is already in handoff **§D-3**:

> Transient, non-blocking outcomes -> toast. Persistent state that explains what the page is currently
> showing -> inline. Never both for the same failure.

| Where | What |
|---|---|
| `contest-detail` register success | `toast.success(...)`, drop the `registerMsg` field and its `<p class="register-msg">` |
| `contest-detail` register **409** | `toast.info('You are already registered for this contest.')`, still call `this.load()`. **Not** an error toast — a 409 here means "already registered", which is a success from the user's side. §D-1. |
| `contest-detail` other errors | `toast.error('Registration failed.')` |
| `submission-detail` load error | **leave inline, no toast.** "Could not load this submission" explains why the page is empty — persistent state. |
| `submission-detail` rejudge error | `toast.error(...)` only, drop the inline `this.error`. Transient. |

Verify with `npm install && npx ng build` — expect only the existing budget and stompjs/sockjs
warnings. There's no frontend test runner, so don't add spec files; that's separate infrastructure work
and its own PR.

**When it's complete, open the PR and I'll review and merge it.** I'll use a merge commit rather than a
squash so your commits stay attributed to you in the history.

This one is small and self-contained, so it's a reasonable thing to knock out first if you want a quick
win before FR-10. Just don't let it eat the week.

## 7. I'm building these in parallel

Being straight with you: I'm going to build FR-10 and FR-25 myself at the same time, as insurance. The
demo is 6 Sept and integration has to start by the 30th, so I can't reach that date with no fallback.

That isn't a vote against you finishing them. **If your PR is open, yours is the one that merges** —
even if mine is further along, I bin mine. That's the outcome I'd prefer anyway; I still have the
browser pass and the report to get through and I'd rather spend the time there.

I just didn't want you finding out later and thinking I'd gone behind your back.

## 8. The window

All code has to be on `main` by **30 Aug**. After that I run the pre-demo browser pass — around 105
steps against the exact tree we submit — and then write the report. A late merge means the pass
validated something we didn't ship, so the 30th isn't movable.

**6 Sept is the demo. 30 Aug is the real deadline for code.**
