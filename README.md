# Praetor — Mini Online Judge

Course project (BRAC University). Submit code → sandboxed Docker execution → per-testcase verdict → contest + live standings. Mini-Codeforces.

**Stack:** Spring Boot + PostgreSQL + Angular (TS) + Docker sandbox. Realtime via WebSocket/STOMP.
**Docs:** `docs/api-contracts.md` (endpoints) · `docs/CONVENTIONS.md` (build rules).

## Repo layout (target)

```
praetor/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/praetor/
│       │   │   ├── PraetorApplication.java
│       │   │   ├── common/
│       │   │   │   ├── config/
│       │   │   │   │   ├── CorsConfig.java
│       │   │   │   │   ├── JacksonConfig.java
│       │   │   │   │   └── OpenApiConfig.java
│       │   │   │   ├── security/
│       │   │   │   │   ├── SecurityConfig.java
│       │   │   │   │   ├── JwtService.java
│       │   │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   │   └── RateLimitFilter.java
│       │   │   │   ├── error/
│       │   │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   │   ├── ApiError.java
│       │   │   │   │   ├── NotFoundException.java
│       │   │   │   │   ├── ForbiddenException.java
│       │   │   │   │   └── RateLimitException.java
│       │   │   │   └── sandbox/
│       │   │   │       └── DockerExecUtil.java
│       │   │   ├── identity/
│       │   │   │   ├── entity/
│       │   │   │   │   ├── User.java
│       │   │   │   │   └── RatingHistory.java
│       │   │   │   ├── repository/
│       │   │   │   │   ├── UserRepository.java
│       │   │   │   │   └── RatingHistoryRepository.java
│       │   │   │   ├── service/
│       │   │   │   │   ├── AuthService.java
│       │   │   │   │   ├── UserService.java
│       │   │   │   │   ├── RatingService.java
│       │   │   │   │   └── StatsService.java
│       │   │   │   ├── controller/
│       │   │   │   │   ├── AuthController.java
│       │   │   │   │   ├── UserController.java
│       │   │   │   │   ├── RatingController.java
│       │   │   │   │   ├── LeaderboardController.java
│       │   │   │   │   └── StatsController.java
│       │   │   │   └── dto/
│       │   │   │       ├── RegisterRequest.java
│       │   │   │       ├── LoginRequest.java
│       │   │   │       ├── AuthResponse.java
│       │   │   │       ├── UserResponse.java
│       │   │   │       ├── RatingResponse.java
│       │   │   │       ├── LeaderboardEntryResponse.java
│       │   │   │       └── StatsResponse.java
│       │   │   ├── problem/
│       │   │   │   ├── entity/
│       │   │   │   │   ├── Problem.java
│       │   │   │   │   ├── TestCase.java
│       │   │   │   │   └── Tag.java
│       │   │   │   ├── repository/
│       │   │   │   │   ├── ProblemRepository.java
│       │   │   │   │   ├── TestCaseRepository.java
│       │   │   │   │   └── TagRepository.java
│       │   │   │   ├── service/
│       │   │   │   │   ├── ProblemService.java
│       │   │   │   │   ├── TestCaseService.java
│       │   │   │   │   └── TagService.java
│       │   │   │   ├── controller/
│       │   │   │   │   ├── ProblemController.java
│       │   │   │   │   ├── TestCaseController.java
│       │   │   │   │   └── TagController.java
│       │   │   │   └── dto/
│       │   │   │       ├── ProblemRequest.java
│       │   │   │       ├── ProblemResponse.java
│       │   │   │       ├── ProblemSummaryResponse.java
│       │   │   │       ├── SampleDto.java
│       │   │   │       └── TestCaseBulkRequest.java
│       │   │   ├── submission/
│       │   │   │   ├── entity/
│       │   │   │   │   ├── Submission.java
│       │   │   │   │   └── SubmissionResult.java
│       │   │   │   ├── repository/
│       │   │   │   │   ├── SubmissionRepository.java
│       │   │   │   │   └── SubmissionResultRepository.java
│       │   │   │   ├── service/
│       │   │   │   │   ├── SubmissionService.java
│       │   │   │   │   ├── JudgeService.java
│       │   │   │   │   ├── SandboxRunner.java
│       │   │   │   │   ├── LanguageConfig.java
│       │   │   │   │   ├── queue/
│       │   │   │   │   │   ├── SubmissionQueue.java
│       │   │   │   │   │   └── JudgeWorker.java
│       │   │   │   │   └── checker/
│       │   │   │   │       ├── Checker.java
│       │   │   │   │       ├── ExactChecker.java
│       │   │   │   │       ├── TokenChecker.java
│       │   │   │   │       ├── FloatChecker.java
│       │   │   │   │       └── SpecialChecker.java
│       │   │   │   ├── controller/
│       │   │   │   │   └── SubmissionController.java
│       │   │   │   └── dto/
│       │   │   │       ├── SubmitRequest.java
│       │   │   │       ├── SubmissionResponse.java
│       │   │   │       ├── SubmissionSummaryResponse.java
│       │   │   │       └── TestResultResponse.java
│       │   │   ├── contest/
│       │   │   │   ├── entity/
│       │   │   │   │   ├── Contest.java
│       │   │   │   │   ├── ContestProblem.java
│       │   │   │   │   └── ContestRegistration.java
│       │   │   │   ├── repository/
│       │   │   │   │   ├── ContestRepository.java
│       │   │   │   │   ├── ContestProblemRepository.java
│       │   │   │   │   └── ContestRegistrationRepository.java
│       │   │   │   ├── service/
│       │   │   │   │   ├── ContestService.java
│       │   │   │   │   ├── StandingsService.java
│       │   │   │   │   ├── ScoringService.java
│       │   │   │   │   └── RegistrationService.java
│       │   │   │   ├── controller/
│       │   │   │   │   ├── ContestController.java
│       │   │   │   │   ├── StandingsController.java
│       │   │   │   │   └── RegistrationController.java
│       │   │   │   └── dto/
│       │   │   │       ├── ContestRequest.java
│       │   │   │       ├── ContestResponse.java
│       │   │   │       ├── StandingsResponse.java
│       │   │   │       ├── StandingsRowResponse.java
│       │   │   │       └── RegistrationRequest.java
│       │   │   └── ws/
│       │   │       ├── WebSocketConfig.java
│       │   │       └── StandingsPublisher.java
│       │   └── resources/
│       │       └── application.yml
│       └── test/java/com/praetor/
│           ├── submission/
│           │   ├── JudgeServiceTest.java
│           │   └── SandboxRunnerTest.java
│           ├── contest/
│           │   ├── ScoringServiceTest.java
│           │   └── StandingsServiceTest.java
│           ├── problem/
│           │   ├── ProblemServiceTest.java
│           │   └── TestCaseServiceTest.java
│           └── identity/
│               ├── AuthServiceTest.java
│               └── RatingServiceTest.java
├── frontend/
│   ├── package.json
│   ├── angular.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── main.ts
│       ├── index.html
│       ├── styles.scss
│       ├── environments/
│       │   ├── environment.ts
│       │   └── environment.docker.ts
│       └── app/
│           ├── app.component.ts
│           ├── app.config.ts
│           ├── app.routes.ts
│           ├── core/
│           │   ├── guards/
│           │   │   ├── auth.guard.ts
│           │   │   └── role.guard.ts
│           │   ├── interceptors/
│           │   │   ├── jwt.interceptor.ts
│           │   │   └── error.interceptor.ts
│           │   └── services/
│           │       ├── auth.service.ts
│           │       └── token.service.ts
│           ├── shared/
│           │   ├── components/
│           │   │   ├── navbar/navbar.component.ts
│           │   │   ├── footer/footer.component.ts
│           │   │   ├── spinner/spinner.component.ts
│           │   │   ├── verdict-badge/verdict-badge.component.ts
│           │   │   └── pagination/pagination.component.ts
│           │   ├── toast/
│           │   │   ├── toast.service.ts
│           │   │   └── toast.component.ts
│           │   └── models/
│           │       ├── problem.model.ts
│           │       ├── submission.model.ts
│           │       ├── contest.model.ts
│           │       └── user.model.ts
│           └── features/
│               ├── auth/
│               │   ├── login/login.component.ts
│               │   └── register/register.component.ts
│               ├── problems/
│               │   ├── problem-list/problem-list.component.ts
│               │   ├── problem-detail/problem-detail.component.ts
│               │   ├── problem-form/problem-form.component.ts
│               │   ├── testcase-upload/testcase-upload.component.ts
│               │   └── problem.service.ts
│               ├── submissions/
│               │   ├── submit/submit.component.ts
│               │   ├── submission-list/submission-list.component.ts
│               │   ├── submission-detail/submission-detail.component.ts
│               │   └── submission.service.ts
│               ├── contests/
│               │   ├── contest-list/contest-list.component.ts
│               │   ├── contest-detail/contest-detail.component.ts
│               │   ├── contest-form/contest-form.component.ts
│               │   ├── standings/standings.component.ts
│               │   └── contest.service.ts
│               ├── profile/
│               │   ├── profile/profile.component.ts
│               │   ├── rating/rating.component.ts
│               │   ├── stats/stats.component.ts
│               │   ├── leaderboard/leaderboard.component.ts
│               │   └── user.service.ts
│               └── home/
│                   ├── landing/landing.component.ts
│                   ├── about/about.component.ts
│                   └── not-found/not-found.component.ts
├── db/
│   ├── schema.sql
│   └── seed.sql
├── docs/
│   ├── api-contracts.md
│   └── CONVENTIONS.md
├── docker-compose.yml
├── .env.example
└── README.md
```

> Each Angular component is a folder holding its `.ts` / `.html` / `.scss` / `.spec.ts`; only the `.ts` is shown above. `db/` and `docs/` exist today; everything under `backend/` and `frontend/` is the scaffold target.

## Feature → files (the 21 committed features)

Baseline auth (register/login/roles/profile) is required but **not** counted. Optional features (FR-16 editorial, FR-22 virtual, FR-23 clarifications, FR-25 heatmap) are out of this tree until pulled in.

| FR | Feature | Primary backend | Primary frontend |
|---|---|---|---|
| FR-4 | Submit → sandbox → verdict | `SubmissionController`, `JudgeService`, `SandboxRunner` | `submit.component` |
| FR-5 | Multi-language judging | `LanguageConfig`, `SandboxRunner` | `submit.component` |
| FR-6 | Per-test-case verdict | `SubmissionResult`, `checker/*` | `submission-detail.component` |
| FR-7 | Time / memory / process limits | `SandboxRunner`, `DockerExecUtil` | — |
| FR-8 | Async queue + worker pool | `queue/SubmissionQueue`, `queue/JudgeWorker` | — |
| FR-9 | Compile-error capture | `JudgeService` | `submission-detail.component` |
| FR-11 | Special / float judge | `checker/FloatChecker`, `checker/SpecialChecker` | — |
| FR-18 | Live standings (WebSocket) | `StandingsService`, `ws/StandingsPublisher` | `standings.component` |
| FR-19 | ICPC scoring + penalty | `ScoringService` | `standings.component` |
| FR-21 | Standings freeze | `StandingsService` | `standings.component` |
| FR-27 | Admin rejudge + recompute | `SubmissionController`, `JudgeService` | `submission-detail.component` |
| FR-12 | Problem create / edit / delete | `ProblemController`, `ProblemService` | `problem-form.component` |
| FR-13 | Bulk test-case upload | `TestCaseController`, `TestCaseService` | `testcase-upload.component` |
| FR-17 | Create contest | `ContestController`, `ContestService` | `contest-form.component` |
| FR-24 | Rating (ELO) + global rank | `RatingService`, `RatingHistory` | `rating.component`, `leaderboard.component` |
| FR-26 | Submission rate-limit | `RateLimitFilter` | — |
| FR-10 | Submission history + code viewer | `SubmissionController`, `SubmissionService` | `submission-list.component`, `submission-detail.component` |
| FR-14 | Difficulty + tags | `TagController`, `TagService` | `problem-form.component`, `problem-list.component` |
| FR-15 | Search / filter problems | `ProblemService` | `problem-list.component` |
| FR-20 | Contest registration | `RegistrationController`, `RegistrationService` | `contest-detail.component` |
| FR-25 | Per-user solve statistics | `StatsController`, `StatsService` | `stats.component` |

## Run
```bash
cp .env.example .env              # host ports, JWT/DB config (git-ignored)
docker build -t praetor-judge:latest judge/   # one-time: the sandbox image (not a compose service)
docker compose up --build         # postgres + backend + frontend
# schema + seed auto-applied by postgres initdb on the first (empty) volume
# browse http://localhost:4200   (API proxied at /api, WebSocket at /ws)
```
Rebuild the judge image after any change under `judge/`. If the DB schema changes, reset the volume
once with `docker compose down -v` before the next `up` (ddl-auto=none — it won't self-migrate).

Seed loads 4 problems, 1 live contest, 4 users. **Seed user password hashes are placeholders — the
seed users can't log in yet; register a fresh account for local dev** (auth landmine, tracked).

## Insulation rule (why seed matters)
Engine reads problems/testcases **straight from the DB** (`ProblemRepository`, `TestCaseRepository`), not through another module's controllers. Broken CRUD → seed still fills the tables → judging + demo survive.

## Team & modules
| Module | Owner |
|---|---|
| Judging engine, contest core & integration | Sazid |
| Problems, contest setup & ratings | Yeasir |
| Discovery, insights & client experience | Mahir |

Each member builds their own Angular slices under `features/`; they plug into the shared shell (`core/`, `shared/`) that Sazid scaffolds.

## Optional final phase
Kafka (submission queue → consumer group) → Kubernetes (Minikube, scale judge-workers). Feature-flagged / separate branch. Core demos without them.
