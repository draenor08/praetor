# PRAETOR — API Contracts

> Everyone **implements** these shapes, does not redesign them. Request/response shapes are fixed so frontend + engine integrate cleanly. Base path: `/api`. JSON everywhere. Auth = `Authorization: Bearer <jwt>`.
>
> Conventions: timestamps ISO-8601 UTC. Errors: `{ "error": "message", "status": 400 }`. Paginated lists: `?page=0&size=20` → `{ "content": [...], "page", "size", "totalElements" }`.
>
> Sections map to the backend module packages (`com.praetor.<module>`). FR numbers are the project's shared feature numbering. Rows marked **Optional** are stretch goals, out of the committed 21.

---

## Baseline — Auth (`identity`). NOT counted as a feature, but required.

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/auth/register` | `{handle, email, password}` | `201 {id, handle, role}` |
| POST | `/api/auth/login` | `{handle, password}` | `200 {token, user:{id,handle,role}}` |
| GET  | `/api/users/me` | — | `200 {id, handle, email, role, rating}` |

Roles (canonical — match DB `users.role` CHECK + backend `User.role`): `USER` (default coder), `PROBLEM_SETTER` (create problems), `ADMIN` (rejudge, announce). Spring authorities are `ROLE_<role>`. *(Earlier drafts said `CODER`/`SETTER`; the DB + auth code use `USER`/`PROBLEM_SETTER` — those win.)*

---

## Problem domain (`problem`) — FR-12, FR-13, FR-14, FR-15

### Problems
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET  | `/api/problems` | any | list; filters `?tag=math&difficulty=800-1200&q=text&page=&size=` (FR-15) |
| GET  | `/api/problems/{slug}` | any | full statement; hidden testcases NOT returned, samples only |
| POST | `/api/problems` | PROBLEM_SETTER | create (FR-12) |
| PUT  | `/api/problems/{slug}` | PROBLEM_SETTER | update (FR-12) |
| DELETE | `/api/problems/{slug}` | ADMIN | (FR-12) |

> **W-shim (temporary):** the two **read** endpoints above (`GET /api/problems`, `GET /api/problems/{slug}`) are currently served by `problem/controller/ProblemReadController` (an `@Immutable` `ProblemView` over the seeded rows) so the submit flow has problems to open before the problem module's write-CRUD exists. Response is the read subset — `{slug,title,difficulty,judgeMode}` for the list; `{slug,title,statement,constraints,difficulty,timeLimitMs,memLimitKb,judgeMode,samples:[{ord,input,expected}]}` for the detail (no filters/paging/tags/editorial yet). When the problem module ships its own `ProblemController` on these paths, Spring boot-fails on the duplicate mapping — delete the shim then. Tracked as W-9.

**Problem object**
```json
{
  "id": 1, "slug": "a-plus-b", "title": "A + B",
  "statement": "markdown...", "constraints": "...",
  "difficulty": 800, "timeLimitMs": 1000, "memLimitKb": 262144,
  "judgeMode": "EXACT", "floatEps": null,
  "tags": ["math","implementation"],
  "samples": [ {"input":"2 3","expected":"5"} ],
  "editorial": null
}
```

### Test cases (FR-13 — bulk upload)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET  | `/api/problems/{slug}/testcases` | PROBLEM_SETTER | full list incl. hidden |
| POST | `/api/problems/{slug}/testcases/bulk` | PROBLEM_SETTER | body below; replaces or appends |

```json
// bulk upload body
{ "mode": "APPEND",   // or "REPLACE"
  "cases": [
    {"ord":1,"kind":"SAMPLE","input":"2 3","expected":"5","points":0},
    {"ord":2,"kind":"HIDDEN","input":"100 200","expected":"300","points":0}
  ] }
```

### Tags (FR-14)
| Method | Path | Auth | |
|---|---|---|---|
| GET | `/api/tags` | any | `["math","greedy",...]` |

> **Optional (FR-16, not in the 21):** `PUT /api/problems/{slug}/editorial` (PROBLEM_SETTER) `{ "editorial": "markdown" }`.

---

## Identity & Insights (`identity`) — FR-24, FR-25, FR-26

### Rating / ELO + global rank (FR-24)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/users/{handle}/rating` | any | `{rating, rank, history:[{contestId,before,after,at}]}` |
| GET | `/api/leaderboard` | any | global rank by rating; `?page=&size=` |

> ELO recompute is triggered by the **contest engine** at contest end (`ContestService` calls `RatingService.applyContestResults(contestId)`). The `identity` module owns the formula (multi-player ELO, K≈24, clamp per-contest delta).

### Solve stats (FR-25)
| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/users/{handle}/stats` | any | below |
```json
{ "solved": 42, "attempted": 70, "accuracy": 0.60,
  "byVerdict": {"AC":42,"WA":18,"TLE":7,"RE":3} }
```
> **Optional (heatmap, not in the 21):** add `"heatmap": [ {"date":"2026-06-01","count":3}, ... ]` to the stats response.

### Rate-limit (FR-26)
Not an endpoint — a **filter/interceptor** on `POST /api/submissions` (`RateLimitFilter`). Rule: max **1 submission / 10s / user** (configurable). Exceed → `429 {error:"rate limited", retryAfterSec}`. Implement with a query on `idx_sub_user_recent` or an in-memory bucket.

---

## Submissions & Judging (`submission`) — FR-4–11, FR-10, FR-27

### Submissions
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/submissions` | USER | submit code; rate-limited (FR-26 filter) |
| GET  | `/api/submissions/{id}` | owner/ADMIN | full result incl. per-testcase |
| GET  | `/api/submissions?user=&problem=&contest=` | any | history list (FR-10) |
| POST | `/api/submissions/{id}/rejudge` | ADMIN | FR-27 |

```json
// POST /api/submissions  request
{ "problemSlug":"a-plus-b", "contestId": 1, "language":"CPP", "sourceCode":"..." }
// 202 Accepted response (async — FR-8)
{ "id": 99, "status":"QUEUED" }
```
```json
// GET /api/submissions/{id}  response (after judging)
{ "id":99, "handle":"alice", "problemSlug":"a-plus-b", "language":"CPP",
  "status":"DONE", "verdict":"AC", "timeMs":12, "memKb":2048,
  "compileLog":"", "createdAt":"2026-06-26T12:00:00Z",
  "results":[ {"ord":1,"verdict":"AC","timeMs":10,"memKb":2000},
              {"ord":2,"verdict":"AC","timeMs":12,"memKb":2048} ] }
```
Verdicts: `AC WA TLE MLE RE CE PE`. Status lifecycle: `QUEUED → JUDGING → DONE|ERROR`.
Judging covers: sandboxed execution (FR-4), multi-language (FR-5), per-test-case verdict (FR-6), enforced limits (FR-7), async queue (FR-8), compile-error capture (FR-9), special/float judge (FR-11).

**Rejudge (FR-27) — FROZEN.** Re-enqueues an existing submission through the pipeline (same source/language, fresh verdict). ADMIN only. Re-uses the submission id; resets `status→QUEUED`, clears prior verdict/results, then judges again. If the submission belongs to a contest, judging completion triggers a standings **recompute** (not just a delta) so a flipped verdict (e.g. WA→AC) propagates to the board.
```json
// POST /api/submissions/{id}/rejudge   (ADMIN)   → 202 Accepted
{ "id": 99, "status": "QUEUED" }
// 403 if caller not ADMIN, 404 if submission id unknown
```

**Multi-language (FR-5) — scope:** `language ∈ {CPP, PYTHON}` for the committed build (`JAVA` is a documented seam, deferred). Per-language time/memory multipliers are applied over the problem's `timeLimitMs`/`memLimitKb` so an interpreted language isn't falsely TLE/MLE'd.

---

## Contest & Realtime (`contest`, `ws`) — FR-17, FR-18, FR-19, FR-20, FR-21

### Live updates over WebSocket (STOMP) — FR-18
- Connect: `/ws` (SockJS/STOMP).
- Subscribe `/topic/contest/{id}/standings` → standings deltas on each judged submission (FR-18).
- Subscribe `/user/queue/submission/{id}` → own submission status changes (drives the live verdict UI).
- *Optional (FR-23):* `/topic/contest/{id}/clar` → new clarifications/announcements.

### Contest
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/contests` | ADMIN | create; time window + problem set (FR-17) |
| GET  | `/api/contests/{id}` | any | meta + problem labels |
| GET  | `/api/contests/{id}/standings` | any | snapshot; respects freeze (FR-18, FR-19, FR-21) |
| POST | `/api/contests/{id}/register` | USER | `{virtual:false}` (FR-20) |

Standings use ICPC-style scoring with penalty (FR-19) and freeze the last N minutes (FR-21).

**Standings payload — FROZEN** (identical shape for the `GET /api/contests/{id}/standings` snapshot **and** each `/topic/contest/{id}/standings` WS push):
```json
{ "contestId": 1,
  "frozen": true,                     // true if a freeze window is active right now
  "updatedAt": "2026-07-12T10:00:00Z",
  "rows": [
    { "rank": 1, "handle": "alice", "solved": 2, "penalty": 45,
      "problems": [
        {"label":"A","attempts":1,"solvedAtMin":12,"frozen":false},
        {"label":"B","attempts":3,"solvedAtMin":33,"frozen":false},
        {"label":"C","attempts":2,"solvedAtMin":null,"frozen":true}
      ] } ] }
```
- `attempts` = rejected submissions **before** the AC (AC-attempt not counted); `solvedAtMin` = minutes from contest start to the accepted submission, `null` if unsolved.
- `frozen:true` on a problem cell = there is post-freeze activity hidden from this viewer (show as "?"/pending).

**ICPC scoring rule — FROZEN (FR-19):** rank by `solved` desc, then `penalty` asc. `penalty = Σ over solved problems (solvedAtMin + 20 × rejectedAttemptsBeforeAC)`. **CE does not count** as a rejected attempt. Unsolved problems contribute nothing. `scoring='POINTS'` contests are **deferred** (schema supports it; the committed build renders ICPC only).

**Freeze rule — FROZEN (FR-21):** during the last `contests.freeze_min` minutes, standings changes are hidden from non-privileged viewers on **both** the snapshot and the live WS stream — the publisher is freeze-aware per recipient (contestants see the frozen board; ADMIN/PROBLEM_SETTER see live). A single unfiltered broadcast would leak post-freeze results and is not allowed.

> **Optional (FR-22, not in the 21):** `{virtual:true}` on register enables virtual participation / upsolving.
> **Optional (FR-23, not in the 21):** clarifications/announcements —
> `POST /api/contests/{id}/clarifications` (USER) `{problemId?, question}`,
> `POST /api/contests/{id}/announcements` (ADMIN) `{problemId?, answer, isPublic:true}`,
> `GET /api/contests/{id}/clarifications` (participant).

---

## Integration rules
- The engine reads problems/testcases **directly from the DB** (`ProblemRepository`, `TestCaseRepository`) — it does NOT call the `problem` module's controllers. If problem CRUD is broken, seed SQL fills the tables and judging still works.
- Cross-module calls go through **Spring service beans** (e.g. `ContestService` calls `RatingService`), never HTTP between in-process modules.
- All write endpoints validate role server-side (don't trust the frontend).
