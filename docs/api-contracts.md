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
| POST | `/api/auth/register` | `{fullName, username, email, password, confirmPassword}` | `201 {token, user}` |
| POST | `/api/auth/login` | `{identifier, password}` | `200 {token, user}` |
| POST | `/api/auth/logout` | — | `200` (client discards the token; JWTs are stateless) |
| GET  | `/api/auth/me` | — | `200 user` |
| GET  | `/api/users/me` | — | `200 {id, username, fullName, email, role, rating}` |

`identifier` on login accepts **either** the username or the email.

**`user` object** (returned by register/login/`/api/auth/me`):
```json
{ "id": 3, "fullName": "Alice Coder", "username": "alice", "email": "alice@praetor.local",
  "role": "USER", "createdAt": "...", "updatedAt": "..." }
```

> **The field is `username`, never `handle`.** `/api/users/me` and the `user` object must keep
> identical names: the frontend caches whichever it saw last under one key, so a divergence
> silently blanks the topbar handle and the standings self-row. (`/api/users/me` briefly returned
> `handle` — fixed. The leaderboard and rating endpoints below do use `handle` as a *path/field*
> name for a looked-up user, which is a different thing.)

Roles (canonical — match DB `users.role` CHECK + backend `User.role`): `USER` (default coder), `PROBLEM_SETTER` (create problems), `ADMIN` (rejudge, announce, apply ratings). Spring authorities are `ROLE_<role>`, but services compare the bare `user.getRole()` string. *(Earlier drafts said `CODER`/`SETTER`; the DB + auth code use `USER`/`PROBLEM_SETTER` — those win.)*

---

## Problem domain (`problem`) — FR-12, FR-13, FR-14, FR-15

### Problems
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET  | `/api/problems` | **anonymous** | non-archived problems, **minus any under contest embargo** (staff see all); filterable — see Search below |
| GET  | `/api/problems/{slug}` | **anonymous** | full statement; hidden testcases NOT returned, samples only. **`403` while embargoed** |
| POST | `/api/problems` | SETTER or ADMIN | create (FR-12) → `201` problem object |
| PUT  | `/api/problems/{slug}` | SETTER or ADMIN | update (FR-12) |
| DELETE | `/api/problems/{slug}` | SETTER or ADMIN | `204`; **`409` when anything references it** — see below |
| POST | `/api/problems/{slug}/archive` | SETTER or ADMIN | hide from the public list, keep all history |
| POST | `/api/problems/{slug}/unarchive` | SETTER or ADMIN | restore to the public list |

Both GET reads are served **anonymously** (`ProblemWebSecurityConfig`, `@Order(4)`) — the problem
list and a statement are public. Every write falls through to the authenticated main chain, and the
role is re-checked in `ProblemService`. That chain also runs the **JWT filter**: it stays
`permitAll`, but a token must still resolve to a principal, because the embargo below has to tell a
registered participant from an anonymous reader. Without the filter `@AuthenticationPrincipal` is
always `null` there and the rule cannot be evaluated at all.

**Contest embargo — FROZEN.** A problem belonging to a contest that has **not ended** is withheld:
absent from `GET /api/problems`, `403` on its statement, and `403` on submitting to it. It is
visible to **staff** always, and to a **registered participant while the contest is running** —
registration alone is not enough before the window opens. Once the contest ends the problem returns
to normal public practice. The rule lives in `ContestAccessService` (contest module; problem and
submission call in, never the reverse) and is enforced at all three points, because gating only one
leaves the other two open — the public list filters embargoed rows **in SQL**, so such a row never
leaves the database.

**Draft / publication — FROZEN.** `problems.published_at` is stamped the **first** time a problem
becomes publicly visible (created without `draft`, restored from archive, or released by the
post-contest sweep) and is **never cleared**. `POST /api/problems` accepts `draft: true`, which
creates it archived and unpublished. A contest may only use a problem whose `published_at` is null
and which no contest has claimed, so **publication is one-way and a contest problem is effectively
single-use**; `unarchive` is therefore a destructive action for eligibility, not a neutral toggle.
`ContestProblemPublisher` (`@Scheduled`, problem module) unarchives and publishes the problems of
contests that have ended, so a finished round leaves its problems in public practice.

**Delete is only allowed while the problem is unused.** `test_cases` and `problem_tags` cascade, but
`submissions`, `contest_problems` and `clarifications` are RESTRICT, so deleting a used problem
would fail in the database. The service refuses first with
`409 {error: "problem is used by contest \"X\" — archive it instead of deleting"}` (or
`… has N submission(s) …`). **Archive is the answer for used problems**: it drops them from the
public list and keeps every submission, standing and rating intact.

**Live-contest freeze.** While a contest containing the problem is running:
* `PUT` rejects changes to `slug`, `timeLimitMs`, `memLimitKb`, `judgeMode`, `floatEps`,
  `checkerCode` with `409` — those redefine how already-submitted code is judged.
* `title`, `statement`, `constraints`, `editorial` stay editable, so typo fixes still work.
* every test-case write is rejected (see below).

> **W-9 shim retired.** `ProblemReadController` is gone; `ProblemController` now serves both reads by
> delegating to `ProblemReadService`, which still projects through the `@Immutable` `ProblemView`
> (that projection stayed — it is the read model, and samples still come from the engine's
> `test_cases` view filtered to `kind='SAMPLE'`).

**Problem object** (write responses, and `/api/setter/problems/{slug}`)
```json
{
  "id": 1, "slug": "a-plus-b", "title": "A + B",
  "statement": "markdown...", "constraints": "...",
  "difficulty": 800, "timeLimitMs": 1000, "memLimitKb": 262144,
  "judgeMode": "EXACT", "floatEps": null, "checkerCode": null,
  "editorial": null, "createdBy": 2, "archived": false
}
```

**Public read shapes** (narrower on purpose — no checker, no hidden data; `editorial` only through
the FR-16 gate below):
```json
// GET /api/problems
[ {"slug":"a-plus-b","title":"A + B","difficulty":800,"judgeMode":"EXACT",
   "tags":["implementation","math"]} ]

// GET /api/problems/{slug}
{ "slug":"a-plus-b","title":"A + B","statement":"...","constraints":"...",
  "difficulty":800,"timeLimitMs":1000,"memLimitKb":262144,"judgeMode":"EXACT",
  "tags":["implementation","math"], "editorial":null,
  "samples":[{"ord":1,"input":"2 3","expected":"5"}] }
```
`tags` is always present and never null — an untagged problem sends `[]`.

**Validation** (`POST`/`PUT`, all `400`): slug required, lowercased/trimmed, must match
`^[a-z0-9]+(?:-[a-z0-9]+)*$`, ≤80 chars · title required, ≤200 · statement required ·
`difficulty` 0–4000 (default 800) · `timeLimitMs` ≥1 (default 1000) · `memLimitKb` ≥1
(default 262144) · `judgeMode` ∈ `EXACT|TOKEN|FLOAT` (default `EXACT`) ·
`FLOAT` requires `floatEps > 0`.
Duplicate slug → `409`.

> **`SPECIAL` is rejected with `400` on both `POST` and `PUT`.** The mode remains in the schema's
> CHECK constraint and in `judge_mode`'s accepted values so existing rows still read, but the engine
> has no custom-checker runner — `Checkers.from()` returns null for it and `JudgeService` fails the
> submission. Accepting one would create a problem that can be authored, published, added to a
> contest and submitted to, yet never judged. The `checker_code` column and the `checkerCode` field
> stay in place for when the runner lands; until then the field is ignored on write.

### Setter workspace reads (`/api/setter/**`)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/setter/problems` | SETTER or ADMIN | every problem **including archived**, one query, usage-enriched |
| GET | `/api/setter/problems/{slug}` | SETTER or ADMIN | the full problem object above — the editor form's load |
| GET | `/api/setter/problems/{slug}/usage` | SETTER or ADMIN | why delete is or isn't allowed |

> Mounted off `/api/problems` deliberately: `GET /api/problems/*` is anonymous, so a management
> read parked under that prefix would arrive with no `Authentication` and could never be authorized.

```json
// GET /api/setter/problems — deletable/lockReason are computed by the SAME rule DELETE enforces,
// so the UI never offers an action the endpoint would refuse.
[ { "slug":"a-plus-b","title":"A + B","difficulty":800,"judgeMode":"EXACT","archived":false,
    "testCases":4,"submissions":0,"contests":1,"inLiveContest":true,
    "deletable":false,"lockReason":"problem is used by a contest" } ]

// GET /api/setter/problems/{slug}/usage
{ "slug":"a-plus-b","deletable":false,"archived":false,"inLiveContest":true,
  "submissions":0,"clarifications":0,"contestTitle":"Praetor Demo Round 1",
  "reason":"problem is used by contest \"Praetor Demo Round 1\"" }
```

### Test cases (FR-13 — bulk upload)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET  | `/api/problems/{slug}/testcases` | SETTER or ADMIN | full list incl. hidden |
| POST | `/api/problems/{slug}/testcases/bulk` | SETTER or ADMIN | body below → `201` with the resulting full list |

**Anyone else gets `403` — including anonymous.** This is the anti-cheat boundary: hidden inputs and
expected outputs exist nowhere in a contestant-reachable response. (Samples reach the public
statement via `kind='SAMPLE'`; the per-testcase verdict table carries verdict/time/memory only.)

```json
// bulk upload body
{ "mode": "APPEND",   // or "REPLACE"
  "cases": [
    {"ord":1,"kind":"SAMPLE","input":"2 3","expected":"5","points":0},
    {"ord":2,"kind":"HIDDEN","input":"100 200","expected":"300","points":0}
  ] }
```

* `REPLACE` deletes every stored case for the problem, then inserts these. It is the only way to
  edit an existing case — there is no per-case update endpoint.
* `APPEND` keeps stored cases; an `ord` that already exists → `409`.
* Validation (`400`): at least one case · `ord` ≥1 and distinct within the request ·
  `kind` ∈ `SAMPLE|HIDDEN` · `input`/`expected` present · `points` ≥0.
* **While a contest using the problem is live, both are rejected with `409`** — a `REPLACE`
  mid-contest would invalidate verdicts already awarded, and an `APPEND` adds a case earlier
  submissions were never judged against.

### Tags (FR-14) — FROZEN
| Method | Path | Auth | |
|---|---|---|---|
| GET | `/api/tags` | **anonymous** | `["brute force","geometry","greedy",...]` — every tag in use, alphabetical |

Tags are authored as part of the problem: `POST`/`PUT /api/problems` accept `"tags": ["math","greedy"]`.

* **Normalised on write** — trimmed, lowercased, de-duplicated, blanks dropped. The vocabulary is
  shared, so `"Math"`, `"math "` and `"math"` must not become three tags that each filter differently.
* **Replace, not merge.** The list given becomes the problem's complete set, so a tag can be removed.
* **`null` means "not part of this request"** and leaves existing tags untouched; `[]` clears them.
  A client written before tags existed therefore cannot wipe them by omission.
* Validation (`400`): at most **8** tags per problem · each ≤40 chars · **no commas** (the search
  filter passes selected tags to SQL as one comma-separated string, so a comma in a stored name
  would split into two filters and quietly widen every search using it).
* `/api/tags` is public because tag names carry no problem identity — nothing here is embargoed.

### Search & filter (FR-15) — FROZEN
`GET /api/problems` takes four optional query parameters. Omitting all of them returns the plain
full list, so the unfiltered contract is unchanged.

| Param | Type | Meaning |
|---|---|---|
| `q` | string | substring of title **or** slug, case-insensitive |
| `minDifficulty` | int | inclusive lower bound; omit for unbounded |
| `maxDifficulty` | int | inclusive upper bound; omit for unbounded |
| `tags` | repeated | `?tags=math&tags=greedy` — **AND**: the problem must carry every one |

* `q` matches literally: it compiles to `position()`, not `LIKE`, so a `%` or `_` typed into the
  search box is an ordinary character rather than a wildcard.
* Filter values are normalised like stored tags, so `?tags=%20MATH%20` finds `math`.
* Validation (`400`): `minDifficulty > maxDifficulty` · more than 8 `tags` · a `tags` value
  containing a comma.
* **No pagination.** FR-15 does not ask for it and the deployment is a single-machine demo; adding
  it later is additive (`page`/`size`) and does not change these parameters.

> ⚠ **The filters live in the same SQL statement as the contest embargo, deliberately.** The public
> list is `archived = false AND NOT EXISTS (unended contest using it)`, and every filter is an
> additional `AND` on that one query — not a second search query beside it. A parallel query would
> be a path on which an embargoed problem could be found by name or tag while its contest runs.
> Verified: with three problems under embargo, searching each one's exact slug anonymously returns
> zero rows, and their tags never list them.

### Editorial (FR-16, Optional) — FROZEN
No dedicated endpoint: `editorial` is an ordinary field on `POST`/`PUT /api/problems`, so the setter
writes it in the problem editor like any other prose. What is new is who may **read** it back on
`GET /api/problems/{slug}`:

| Caller | `editorial` |
|---|---|
| `PROBLEM_SETTER` / `ADMIN` | always |
| Anonymous | `null` |
| Logged in, no accepted submission for this problem | `null` |
| Logged in, has an `AC` — **and no contest is using the problem** | the editorial |
| Logged in, has an `AC`, but a contest using it has not ended | `null` |

* **Absence is the enforcement.** A withheld editorial is omitted from the payload, never sent with a
  flag the client is trusted to respect.
* Keyed on the caller's **own** accepted submission, so it cannot be borrowed from another user.
* The contest clause is stricter than the statement embargo on purpose. A registered participant can
  legitimately read a statement mid-round; handing that same participant the solution would be worse.
  So the editorial keys off "is any contest still using this problem" (upcoming **or** running),
  not "may this caller see the statement".

---

## Identity & Insights (`identity`) — FR-24, FR-25, FR-26

### Rating / ELO + global rank (FR-24)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/users/{handle}/rating` | **anonymous** | `{rating, rank, history:[{contestId,before,after,at}]}` |
| GET | `/api/leaderboard` | **anonymous** | global rank by rating; `?page=0&size=20` |
| POST | `/api/ratings/apply/{contestId}` | ADMIN | apply a finished contest's ratings now → `202 {contestId, status:"APPLIED"}` |

Unknown handle → `404`. Unrated user → `rating: 1500` (the default) with an empty `history`.
`size` must be 1–100 and `page` ≥0, else `400`.

```json
// GET /api/leaderboard?page=0&size=20
{ "content": [ {"rank":1,"handle":"alice","rating":1512} ], "page":0, "size":20, "totalElements":4 }
```

Ranks are **global, not page-local**, and equal ratings **share** a rank with the next rank skipping
(1, 2, 2, 4). One windowed query serves a page — do not reintroduce a per-row rank lookup.

> **ELO is applied by `identity`, not by the contest module.** `ContestRatingScheduler` polls for
> contests that have **ended**, had **at least one registrant**, and have **no `rating_history` rows**,
> then calls `RatingService.applyContestResults(contestId)` **per contest, in its own transaction**, so
> one bad contest cannot roll back or block the others. Scan interval:
> `praetor.rating.contest-scan-ms` (default 60000). The ADMIN endpoint above is the same operation on
> demand, for demos. Both are idempotent — a contest with history is a no-op, and
> `UNIQUE(user_id, contest_id)` on `rating_history` enforces that at the database level.
> Formula: multi-player ELO, K=24, per-contest delta clamped to ±48, ties score 0.5.
> `ContestService` has **no** dependency on rating — keep it that way.

### Solve stats (FR-25) — *not implemented yet*
| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/users/{handle}/stats` | any | below |
```json
{ "solved": 42, "attempted": 70, "accuracy": 0.60,
  "byVerdict": {"AC":42,"WA":18,"TLE":7,"RE":3} }
```
> **Optional (heatmap, not in the 21):** add `"heatmap": [ {"date":"2026-06-01","count":3}, ... ]` to the stats response.

### Rate-limit (FR-26)
Not an endpoint — a cooldown enforced **inside `SubmissionService.create`** (`SubmissionRateLimiter`).
Rule: at most **1 accepted submission per user per 10s**, set by
`praetor.rate-limit.submission-seconds` / `SUBMISSION_RATE_LIMIT_SECONDS` (`0` disables).

Exceeded →
```
429  Retry-After: 6
{ "error": "too many submissions — wait before submitting again", "status": 429, "retryAfterSec": 6 }
```

> **Only submissions the judge will actually run spend the window.** A rejected request — unknown
> slug (`404`), unsupported language (`400`) — costs the user nothing. That is why this is not a
> servlet filter: a filter runs before the handler and cannot tell an accepted submission from a
> rejected one, so the earlier `RateLimitFilter` charged the cooldown for 404s too and locked people
> out over a typo. The filter is deleted; do not reinstate one.

---

## Submissions & Judging (`submission`) — FR-4–11, FR-10, FR-27

> **Contest embargo applies to `POST /api/submissions` too** — `403` for a problem in a contest that
> has not ended, unless the caller is staff or a registered participant of a running contest. Checked
> **before** the rate-limit cooldown is spent, so a refusal costs the user nothing. Without this the
> statement gate would be pointless: the slug would still be submittable.

### Submissions
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/submissions` | USER | submit code; rate-limited (FR-26 filter) |
| GET  | `/api/submissions/{id}` | owner/ADMIN | full result incl. per-testcase |
| GET  | `/api/submissions?user=&problem=&contest=` | owner/staff | history list (FR-10) — **unbuilt** |
| POST | `/api/submissions/{id}/rejudge` | ADMIN | FR-27 |

> ⚠ **The FR-10 list is `owner/staff`, corrected from an earlier `any`.** A publicly readable
> `?user=` list re-exposes exactly what the standings freeze exists to hide: during a freeze the
> board withholds a late `AC`, but anyone could read that user's submission list and see it anyway.
> Whoever builds FR-10 must scope the query to the caller's own submissions unless the caller is
> staff. The row also must not carry `sourceCode` — `GET /api/submissions/{id}` is `owner/ADMIN`
> precisely so source stays private, and a list that included it would route around that.

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
Judging covers: sandboxed execution (FR-4), multi-language (FR-5), per-test-case verdict (FR-6), enforced limits (FR-7), async queue (FR-8), compile-error capture (FR-9), token/float checkers (FR-11 — `SPECIAL` is refused on write, see the problem module).

**Rejudge (FR-27) — FROZEN.** Re-enqueues an existing submission through the pipeline (same source/language, fresh verdict). ADMIN only. Re-uses the submission id; resets `status→QUEUED`, clears prior verdict/results, then judges again. If the submission belongs to a contest, judging completion triggers a standings **recompute** (not just a delta) so a flipped verdict (e.g. WA→AC) propagates to the board.
```json
// POST /api/submissions/{id}/rejudge   (ADMIN)   → 202 Accepted
{ "id": 99, "status": "QUEUED" }
// 403 if caller not ADMIN, 404 if submission id unknown
```

**Multi-language (FR-5) — FROZEN.** `language ∈ {CPP, PYTHON, JAVA}`. Per-language time/memory
multipliers are applied over the problem's `timeLimitMs`/`memLimitKb`, because limits are authored
C++-first and a slower runtime would otherwise be falsely TLE'd or MLE'd:

| Language | Source file | Time × | Memory × |
|---|---|---|---|
| `CPP` | `main.cpp` | 1.0 | 1.0 |
| `PYTHON` | `main.py` | 3.0 | 2.0 |
| `JAVA` | `Main.java` | 3.0 | 3.0 |

> **`JAVA` submissions must declare `public class Main`** — javac ties the class name to the file
> name, and the file is always written as `Main.java`. A mismatch compiles to a `CE` whose message
> names the problem, so it needs no special handling. The JVM runs with `-XX:+UseSerialGC` (the
> sandbox enforces `--pids-limit`, and a parallel-GC JVM can exhaust that budget before `main()`
> starts) and `-Xss64m` for recursion depth comparable to C++'s default stack.

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
| POST | `/api/contests` | ADMIN | create; time window + problem set (FR-17). `problems` may be empty **only** with `callsOpen:true` |
| GET  | `/api/contests` | any | list (meta only: `id,title,startsAt,endsAt,scoring,callsOpen`) |
| GET  | `/api/contests/{id}` | any | meta + problem slots + `registered` + `problemsVisible` + `callsOpen` |
| GET  | `/api/contests/{id}/standings` | any | snapshot; respects freeze (FR-18, FR-19, FR-21) |
| POST | `/api/contests/{id}/register` | USER | `{virtual:false}` (FR-20) |
| GET  | `/api/contests/eligible-problems` | staff | draft problems a contest may still use |
| POST | `/api/contests/{id}/calls` | ADMIN | `{open:bool}` — open/close the contest to setter proposals |
| POST | `/api/contests/{id}/proposals` | staff | `{problemId, note?}` → `201`; a setter offers a draft |
| GET  | `/api/contests/{id}/proposals` | staff | the admin's review queue for this contest |
| GET  | `/api/contests/my-proposals` | staff | everything the caller has offered, across contests |
| POST | `/api/contests/{id}/proposals/{pid}/accept` | ADMIN | `{label}` — puts the problem in the contest |
| POST | `/api/contests/{id}/proposals/{pid}/reject` | ADMIN | problem stays a draft, free for another contest |

**Contest detail payload — FROZEN.** Problem slots always carry `label` and `ord`, because the
standings board needs its columns even for a spectator, but `slug` and `title` are **`null` while
the embargo applies** — withheld server-side, so a hidden statement is not reachable from the page's
own payload. `problemsVisible` says which of the two shapes you got; `registered` is `false` for an
anonymous reader.

**Problem sourcing — FROZEN.** A contest is built from the eligible pool (drafts, never published,
unclaimed) or by opening a **call for problems**: setters propose, and the **admin decides**.
Accepting is what writes the `contest_problems` row, under a label the admin supplies — labels are
unique per contest (`409` on a clash). Eligibility is re-checked **at accept time as well as at
propose time**, since a draft can be published or claimed in between; letting an already-readable
statement into a contest is the one thing the rule exists to prevent. Rejecting records the
decision and leaves the problem a draft.

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

**ICPC scoring rule — FROZEN (FR-19):** rank by `solved` desc, then `penalty` asc. `penalty = Σ over solved problems (solvedAtMin + 20 × rejectedAttemptsBeforeAC)`. **CE does not count** as a rejected attempt. Unsolved problems contribute nothing.

> **`scoring='POINTS'` is rejected with `400` on contest creation.** The schema CHECK and the
> request pattern still accept the value so an existing row reads, but `StandingsCalculator`
> implements ICPC only — accepting a POINTS contest would score it by ICPC rules while calling
> itself POINTS, and a scoreboard that misrepresents its own rules is worse than a refused request.
> Same treatment as the unimplemented `SPECIAL` judge mode.

**Freeze rule — FROZEN (FR-21):** during the last `contests.freeze_min` minutes, standings changes are hidden from non-privileged viewers on **both** the snapshot and the live WS stream — the publisher is freeze-aware per recipient (contestants see the frozen board; ADMIN/PROBLEM_SETTER see live). A single unfiltered broadcast would leak post-freeze results and is not allowed.

> **Optional (FR-22, not in the 21):** `{virtual:true}` on register enables virtual participation / upsolving.
> **Optional (FR-23, not in the 21):** clarifications/announcements —
> `POST /api/contests/{id}/clarifications` (USER) `{problemId?, question}`,
> `POST /api/contests/{id}/announcements` (ADMIN) `{problemId?, answer, isPublic:true}`,
> `GET /api/contests/{id}/clarifications` (participant).

---

## Integration rules
- The engine reads problems/testcases **directly from the DB** (`JudgeProblem`, `JudgeTestCase` — its own `@Immutable` projections) — it does NOT call the `problem` module's controllers. If problem CRUD is broken, seed SQL fills the tables and judging still works.
- A module that needs another module's tables reads them through a **projection or a native query**, not by importing the owning module's entities. Duplicating a few lines of SQL beats coupling two slices (see `ProblemUsageRepository`, `StandingsRepository`, `RatedContestRepository`).
- Cross-module **service** calls go one way only: `identity` (rating) may read `contest` (standings); `contest` must not depend on `identity`'s rating. Never HTTP between in-process modules.
- All write endpoints validate role server-side (don't trust the frontend). The Angular `roleGuard` only hides UI.
- Each module owns a package-scoped `@RestControllerAdvice` at `HIGHEST_PRECEDENCE` that maps `ResponseStatusException` → `{error,status}`. Without it, identity's broad `RuntimeException → 400` advice swallows the real status (a 404 became a 400). Keep that advice per module rather than editing the shared one.
- Anonymous-readable paths are declared in per-module `SecurityFilterChain` beans with explicit `@Order`s: `ws` (1), `contest` reads (2), `identity` rating reads (3), `problem` reads (4), then the authenticated main chain. A management endpoint must **not** live under a prefix whose GETs are anonymous — it would arrive with no `Authentication` to check. A `permitAll` chain that needs to know *who* is calling must still `addFilterBefore` the JWT filter (optional auth), or `@AuthenticationPrincipal` is always `null` there.
- A rule enforced in more than one place is defined **once** and called from each (`ContestAccessService` for the contest embargo and problem eligibility). Two copies of a security rule drift, and the copy that drifts is the one nobody tested.
- Reading back rows you just wrote with a **native** query needs an explicit flush (`saveAllAndFlush`) — Hibernate does not flush for native queries, so the read returns nothing.
