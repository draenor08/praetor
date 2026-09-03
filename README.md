# Praetor — Mini Online Judge

Course project (BRAC University). Submit code → sandboxed Docker execution → per-testcase verdict → contest + live standings. Mini-Codeforces.

**Stack:** Spring Boot + PostgreSQL + Angular (TS) + Docker sandbox. Realtime via WebSocket/STOMP.
**Docs:** `docs/api-contracts.md` (endpoints) · `docs/CONVENTIONS.md` (build rules).

## Repo layout

```
praetor/
├── .github/
│   ├── workflows/
│   │   └── ci.yml
│   ├── CODEOWNERS
│   └── pull_request_template.md
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/praetor/
│   │   │   │   ├── common/
│   │   │   │   │   ├── error/
│   │   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   │   ├── event/
│   │   │   │   │   │   └── ContestSubmissionJudgedEvent.java
│   │   │   │   │   ├── security/
│   │   │   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   │   │   ├── JwtProperties.java
│   │   │   │   │   │   ├── JwtService.java
│   │   │   │   │   │   └── SecurityConfig.java
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── contest/
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── ContestExecutorConfig.java
│   │   │   │   │   │   └── ContestWebSecurityConfig.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── ContestController.java
│   │   │   │   │   │   └── ContestExceptionHandler.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── AcceptProposalRequest.java
│   │   │   │   │   │   ├── CallsOpenRequest.java
│   │   │   │   │   │   ├── ContestProblemDto.java
│   │   │   │   │   │   ├── ContestProblemSlot.java
│   │   │   │   │   │   ├── ContestProblemSpec.java
│   │   │   │   │   │   ├── ContestResponse.java
│   │   │   │   │   │   ├── ContestSummary.java
│   │   │   │   │   │   ├── CreateContestRequest.java
│   │   │   │   │   │   ├── EligibleProblemDto.java
│   │   │   │   │   │   ├── ProblemCell.java
│   │   │   │   │   │   ├── ProposalDto.java
│   │   │   │   │   │   ├── ProposeRequest.java
│   │   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   │   ├── StandingsResponse.java
│   │   │   │   │   │   └── StandingsRow.java
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── Contest.java
│   │   │   │   │   │   ├── ContestProblem.java
│   │   │   │   │   │   ├── ContestProblemId.java
│   │   │   │   │   │   ├── ContestProblemProposal.java
│   │   │   │   │   │   ├── Registration.java
│   │   │   │   │   │   └── RegistrationId.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── ContestAccessRepository.java
│   │   │   │   │   │   ├── ContestProblemRepository.java
│   │   │   │   │   │   ├── ContestProblemRow.java
│   │   │   │   │   │   ├── ContestRepository.java
│   │   │   │   │   │   ├── EligibleProblemRow.java
│   │   │   │   │   │   ├── ProposalRepository.java
│   │   │   │   │   │   ├── ProposalRow.java
│   │   │   │   │   │   ├── RegistrationRepository.java
│   │   │   │   │   │   ├── StandingsRepository.java
│   │   │   │   │   │   └── StandingsSubmissionRow.java
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── ContestAccessService.java
│   │   │   │   │   │   ├── ContestService.java
│   │   │   │   │   │   └── ProposalService.java
│   │   │   │   │   ├── standings/
│   │   │   │   │   │   ├── PrivilegedSubscriberRegistry.java
│   │   │   │   │   │   ├── StandingsCalculator.java
│   │   │   │   │   │   ├── StandingsPublisher.java
│   │   │   │   │   │   ├── StandingsService.java
│   │   │   │   │   │   └── StandingsSubscribeListener.java
│   │   │   │   │   └── .gitkeep
│   │   │   │   ├── identity/
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── IdentityReadSecurityConfig.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── AuthController.java
│   │   │   │   │   │   ├── IdentityExceptionHandler.java
│   │   │   │   │   │   ├── ProfileController.java
│   │   │   │   │   │   ├── RatingController.java
│   │   │   │   │   │   └── UserController.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── AuthResponse.java
│   │   │   │   │   │   ├── CurrentUserResponse.java
│   │   │   │   │   │   ├── LeaderboardEntry.java
│   │   │   │   │   │   ├── LeaderboardResponse.java
│   │   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   │   ├── ProfileSolveStats.java
│   │   │   │   │   │   ├── RatingHistoryResponse.java
│   │   │   │   │   │   ├── RatingResponse.java
│   │   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   │   └── UserResponse.java
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── Rating.java
│   │   │   │   │   │   ├── RatingHistory.java
│   │   │   │   │   │   └── User.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── RatedContestRepository.java
│   │   │   │   │   │   ├── RatingHistoryRepository.java
│   │   │   │   │   │   ├── RatingRepository.java
│   │   │   │   │   │   └── UserRepository.java
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── AuthService.java
│   │   │   │   │   │   ├── ContestRatingScheduler.java
│   │   │   │   │   │   ├── EloCalculator.java
│   │   │   │   │   │   ├── ProfileService.java
│   │   │   │   │   │   ├── RatingService.java
│   │   │   │   │   │   └── UserService.java
│   │   │   │   │   └── .gitkeep
│   │   │   │   ├── problem/
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── ProblemWebSecurityConfig.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── ProblemController.java
│   │   │   │   │   │   ├── ProblemExceptionHandler.java
│   │   │   │   │   │   ├── SetterProblemController.java
│   │   │   │   │   │   ├── TagController.java
│   │   │   │   │   │   └── TestCaseController.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── BulkTestCaseRequest.java
│   │   │   │   │   │   ├── ManagedProblemResponse.java
│   │   │   │   │   │   ├── ProblemDetail.java
│   │   │   │   │   │   ├── ProblemRequest.java
│   │   │   │   │   │   ├── ProblemResponse.java
│   │   │   │   │   │   ├── ProblemSummary.java
│   │   │   │   │   │   ├── ProblemUsageResponse.java
│   │   │   │   │   │   ├── SampleDto.java
│   │   │   │   │   │   ├── TestCaseItem.java
│   │   │   │   │   │   └── TestCaseResponse.java
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── Problem.java
│   │   │   │   │   │   ├── ProblemView.java
│   │   │   │   │   │   └── TestCase.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── ProblemRepository.java
│   │   │   │   │   │   ├── ProblemTagRepository.java
│   │   │   │   │   │   ├── ProblemUsageRepository.java
│   │   │   │   │   │   ├── ProblemViewRepository.java
│   │   │   │   │   │   └── TestCaseRepository.java
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── ContestProblemPublisher.java
│   │   │   │   │   │   ├── ProblemAuthz.java
│   │   │   │   │   │   ├── ProblemReadService.java
│   │   │   │   │   │   ├── ProblemService.java
│   │   │   │   │   │   └── TestCaseService.java
│   │   │   │   │   └── .gitkeep
│   │   │   │   ├── submission/
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── AsyncConfig.java
│   │   │   │   │   │   ├── JudgeConfigLogger.java
│   │   │   │   │   │   └── JudgeProperties.java
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── SubmissionController.java
│   │   │   │   │   │   └── SubmissionExceptionHandler.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── ResultResponse.java
│   │   │   │   │   │   ├── SubmissionCreatedResponse.java
│   │   │   │   │   │   ├── SubmissionPage.java
│   │   │   │   │   │   ├── SubmissionResponse.java
│   │   │   │   │   │   ├── SubmissionStatusEvent.java
│   │   │   │   │   │   ├── SubmissionSummary.java
│   │   │   │   │   │   └── SubmitRequest.java
│   │   │   │   │   ├── engine/
│   │   │   │   │   │   ├── checker/
│   │   │   │   │   │   │   ├── Checker.java
│   │   │   │   │   │   │   ├── Checkers.java
│   │   │   │   │   │   │   ├── ExactChecker.java
│   │   │   │   │   │   │   ├── FloatChecker.java
│   │   │   │   │   │   │   └── TokenChecker.java
│   │   │   │   │   │   ├── CompileResult.java
│   │   │   │   │   │   ├── DockerExecUtil.java
│   │   │   │   │   │   ├── DockerSandboxRunner.java
│   │   │   │   │   │   ├── JudgeReaper.java
│   │   │   │   │   │   ├── JudgeService.java
│   │   │   │   │   │   ├── Language.java
│   │   │   │   │   │   ├── RunLimits.java
│   │   │   │   │   │   ├── RunResult.java
│   │   │   │   │   │   ├── SandboxException.java
│   │   │   │   │   │   ├── SandboxRunner.java
│   │   │   │   │   │   ├── StubSandboxRunner.java
│   │   │   │   │   │   └── VerdictEvaluator.java
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── JudgeProblem.java
│   │   │   │   │   │   ├── JudgeTestCase.java
│   │   │   │   │   │   ├── Submission.java
│   │   │   │   │   │   └── SubmissionResult.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── JudgeProblemRepository.java
│   │   │   │   │   │   ├── JudgeTestCaseRepository.java
│   │   │   │   │   │   ├── ResultView.java
│   │   │   │   │   │   ├── RevealView.java
│   │   │   │   │   │   ├── SubmissionRepository.java
│   │   │   │   │   │   ├── SubmissionResultRepository.java
│   │   │   │   │   │   └── VerdictCountView.java
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── SubmissionRateLimitedException.java
│   │   │   │   │   │   ├── SubmissionRateLimiter.java
│   │   │   │   │   │   └── SubmissionService.java
│   │   │   │   │   ├── .gitkeep
│   │   │   │   │   ├── SubmissionStatus.java
│   │   │   │   │   └── Verdict.java
│   │   │   │   ├── ws/
│   │   │   │   │   ├── .gitkeep
│   │   │   │   │   ├── StompAuthChannelInterceptor.java
│   │   │   │   │   ├── SubmissionSubscribeListener.java
│   │   │   │   │   ├── WsConfig.java
│   │   │   │   │   └── WsSecurityConfig.java
│   │   │   │   └── PraetorApplication.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/java/com/praetor/
│   │       ├── common/
│   │       │   ├── error/
│   │       │   │   └── GlobalExceptionHandlerTest.java
│   │       │   └── security/
│   │       │       ├── JwtPropertiesTest.java
│   │       │       └── JwtServiceTest.java
│   │       ├── contest/
│   │       │   ├── service/
│   │       │   │   ├── ContestAccessServiceTest.java
│   │       │   │   ├── ContestServiceTest.java
│   │       │   │   └── ProposalServiceTest.java
│   │       │   └── standings/
│   │       │       ├── PrivilegedSubscriberRegistryTest.java
│   │       │       ├── StandingsCalculatorTest.java
│   │       │       └── StandingsPublisherTest.java
│   │       ├── identity/
│   │       │   ├── controller/
│   │       │   │   └── UserControllerTest.java
│   │       │   └── service/
│   │       │       ├── ContestRatingSchedulerTest.java
│   │       │       ├── EloCalculatorTest.java
│   │       │       ├── ProfileServiceTest.java
│   │       │       └── RatingServiceTest.java
│   │       ├── problem/service/
│   │       │   ├── ProblemServiceTest.java
│   │       │   └── TestCaseServiceTest.java
│   │       ├── submission/
│   │       │   ├── config/
│   │       │   │   ├── AsyncConfigTest.java
│   │       │   │   └── JudgePropertiesTest.java
│   │       │   ├── engine/
│   │       │   │   ├── checker/
│   │       │   │   │   └── CheckerTest.java
│   │       │   │   ├── DockerSandboxRunnerTest.java
│   │       │   │   ├── LanguageTest.java
│   │       │   │   └── VerdictEvaluatorTest.java
│   │       │   └── service/
│   │       │       ├── SubmissionRateLimiterTest.java
│   │       │       └── SubmissionServiceTest.java
│   │       ├── ws/
│   │       │   └── StompAuthChannelInterceptorTest.java
│   │       └── .gitkeep
│   ├── Dockerfile
│   └── pom.xml
├── db/
│   ├── schema.sql
│   └── seed.sql
├── demo/
│   └── solutions/            # an AC solution per problem per language + verify.mjs
│       ├── <problem-slug>/   # main.cpp · main.py · Main.java
│       ├── README.md
│       └── verify.mjs
├── docs/
│   ├── CONVENTIONS.docx
│   ├── CONVENTIONS.md
│   └── api-contracts.md
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/
│   │   │   │   ├── guards/
│   │   │   │   │   ├── auth.guard.ts
│   │   │   │   │   └── role.guard.ts
│   │   │   │   ├── interceptors/
│   │   │   │   │   ├── error.interceptor.ts
│   │   │   │   │   └── jwt.interceptor.ts
│   │   │   │   ├── models/
│   │   │   │   │   ├── contest.model.ts
│   │   │   │   │   ├── problem.model.ts
│   │   │   │   │   ├── profile.model.ts
│   │   │   │   │   ├── rating.model.ts
│   │   │   │   │   ├── standings.model.ts
│   │   │   │   │   └── submission.model.ts
│   │   │   │   └── services/
│   │   │   │       ├── api.service.ts
│   │   │   │       ├── auth.service.ts
│   │   │   │       ├── token.service.ts
│   │   │   │       └── ws.service.ts
│   │   │   ├── features/
│   │   │   │   ├── about/
│   │   │   │   │   └── about.component.ts
│   │   │   │   ├── auth/
│   │   │   │   │   ├── login/
│   │   │   │   │   │   └── login.component.ts
│   │   │   │   │   └── register/
│   │   │   │   │       └── register.component.ts
│   │   │   │   ├── contests/
│   │   │   │   │   ├── contest-create/
│   │   │   │   │   │   └── contest-create.component.ts
│   │   │   │   │   ├── contest-detail/
│   │   │   │   │   │   └── contest-detail.component.ts
│   │   │   │   │   ├── contest-list/
│   │   │   │   │   │   └── contest-list.component.ts
│   │   │   │   │   ├── contest-proposals/
│   │   │   │   │   │   └── contest-proposals.component.ts
│   │   │   │   │   ├── contest-standings/
│   │   │   │   │   │   └── contest-standings.component.ts
│   │   │   │   │   ├── standings-board/
│   │   │   │   │   │   └── standings-board.component.ts
│   │   │   │   │   ├── standings-list/
│   │   │   │   │   │   └── standings-list.component.ts
│   │   │   │   │   └── standings-live/
│   │   │   │   │       └── standings-live.component.ts
│   │   │   │   ├── landing/
│   │   │   │   │   └── landing.component.ts
│   │   │   │   ├── leaderboard/
│   │   │   │   │   └── leaderboard.component.ts
│   │   │   │   ├── not-found/
│   │   │   │   │   └── not-found.component.ts
│   │   │   │   ├── problems/
│   │   │   │   │   ├── problem-detail/
│   │   │   │   │   │   └── problem-detail.component.ts
│   │   │   │   │   └── problem-list/
│   │   │   │   │       └── problem-list.component.ts
│   │   │   │   ├── profile/
│   │   │   │   │   └── profile.component.ts
│   │   │   │   ├── setter/
│   │   │   │   │   ├── contest-calls/
│   │   │   │   │   │   └── contest-calls.component.ts
│   │   │   │   │   ├── problem-editor/
│   │   │   │   │   │   └── problem-editor.component.ts
│   │   │   │   │   ├── problem-manage/
│   │   │   │   │   │   └── problem-manage.component.ts
│   │   │   │   │   └── test-case-editor/
│   │   │   │   │       └── test-case-editor.component.ts
│   │   │   │   └── submissions/
│   │   │   │       ├── submission-detail/
│   │   │   │       │   └── submission-detail.component.ts
│   │   │   │       └── submissions-list/
│   │   │   │           └── submissions-list.component.ts
│   │   │   ├── layout/shell/
│   │   │   │   └── shell.component.ts
│   │   │   ├── shared/
│   │   │   │   ├── components/
│   │   │   │   │   ├── coming-soon/
│   │   │   │   │   │   └── coming-soon.component.ts
│   │   │   │   │   ├── countdown/
│   │   │   │   │   │   └── countdown.component.ts
│   │   │   │   │   ├── navbar/
│   │   │   │   │   │   └── navbar.component.ts
│   │   │   │   │   └── rich-text/
│   │   │   │   │       └── rich-text.component.ts
│   │   │   │   ├── markdown/
│   │   │   │   │   └── markdown.ts
│   │   │   │   ├── toast/
│   │   │   │   │   ├── toast.component.ts
│   │   │   │   │   └── toast.service.ts
│   │   │   │   └── contest-clock.ts
│   │   │   ├── app.component.ts
│   │   │   ├── app.config.ts
│   │   │   └── app.routes.ts
│   │   ├── environments/
│   │   │   ├── environment.docker.ts
│   │   │   └── environment.ts
│   │   ├── index.html
│   │   ├── main.ts
│   │   └── styles.scss
│   ├── Dockerfile
│   ├── angular.json
│   ├── nginx.conf
│   ├── package-lock.json
│   ├── package.json
│   ├── tsconfig.app.json
│   └── tsconfig.json
├── judge/
│   └── Dockerfile
├── scripts/
│   ├── browser-pass-setup.sh
│   ├── check-mark-dirty.mjs
│   └── e2e.mjs
├── .env
├── .env.example
├── .gitignore
├── README.md
└── docker-compose.yml
```

> Each Angular component is a folder holding its `.ts` / `.html` / `.scss`; only the `.ts` is shown above. Generated from the working tree — build output (`target/`, `dist/`, `node_modules/`) is omitted.

## Feature → files (the 21 committed features)

Baseline auth (register/login/roles/profile) is required but **not** counted. Optional features (FR-22 virtual, FR-23 clarifications, and the activity heatmap layered on FR-25) are out of this tree until pulled in. **FR-16 editorial is Optional but built** — note that FR-25 itself is a counted feature; only the heatmap on top of it is optional.

| FR | Feature | Primary backend | Primary frontend |
|---|---|---|---|
| FR-4 | Submit → sandbox → verdict | `SubmissionController`, `SubmissionService`, `engine/JudgeService`, `engine/SandboxRunner` | `problem-detail.component` |
| FR-5 | Multi-language judging (C++ / Python / Java) | `engine/Language`, `engine/DockerSandboxRunner` | `problem-detail.component` |
| FR-6 | Per-test-case verdict | `entity/SubmissionResult`, `engine/VerdictEvaluator`, `engine/checker/*` | `submission-detail.component` |
| FR-7 | Time / memory / process limits | `engine/DockerSandboxRunner`, `engine/DockerExecUtil`, `engine/RunLimits`, `config/JudgeProperties` | — |
| FR-8 | Async queue + worker pool | `submission/config/AsyncConfig` (`judgeExecutor`), `JudgeService.enqueue`, `engine/JudgeReaper` | — |
| FR-9 | Compile-error capture | `engine/JudgeService`, `DockerSandboxRunner.compile` | `submission-detail.component` |
| FR-11 | Token / float checkers (`SPECIAL` refused on write) | `engine/checker/TokenChecker`, `engine/checker/FloatChecker` | — |
| FR-18 | Live standings (WebSocket) | `standings/StandingsService`, `standings/StandingsPublisher`, `ws/WsConfig` | `standings-live.component`, `standings-board.component` |
| FR-19 | ICPC scoring + penalty | `standings/StandingsCalculator` | `standings-board.component` |
| FR-21 | Standings freeze | `standings/StandingsCalculator`, `standings/PrivilegedSubscriberRegistry` | `standings-board.component` |
| FR-27 | Admin rejudge + recompute | `SubmissionController.rejudge`, `SubmissionService.rejudge` | `submission-detail.component` |
| FR-12 | Problem create / edit / delete | `ProblemController`, `SetterProblemController`, `ProblemService` | `problem-editor.component`, `problem-manage.component` |
| FR-13 | Bulk test-case upload | `TestCaseController`, `TestCaseService` | `test-case-editor.component` |
| FR-17 | Create contest | `ContestController`, `ContestService` | `contest-create.component` |
| FR-24 | Rating (ELO) + global rank | `RatingService`, `EloCalculator`, `entity/RatingHistory` | `profile.component`, `leaderboard.component` |
| FR-26 | Submission rate-limit | `SubmissionRateLimiter` (inside `SubmissionService.create`) | cooldown on `problem-detail.component` |
| FR-10 | Submission history + code viewer | `SubmissionController`, `SubmissionRepository.findHistoryPage` | `submissions-list.component`, `submission-detail.component` |
| FR-14 | Difficulty + tags | `TagController`, `ProblemTagRepository` | `problem-editor.component`, `problem-list.component` |
| FR-15 | Search / filter problems | `ProblemViewRepository.search` | `problem-list.component` |
| FR-16 | Editorial, gated on solving it | `ProblemReadService.editorialFor` | `problem-detail.component` |
| FR-20 | Contest registration | `ContestController`, `ContestService`, `RegistrationRepository` | `contest-detail.component` |
| FR-25 | Per-user solve statistics | `ProfileController`, `ProfileService`, `SubmissionRepository.tallyVerdictsForUser` | `profile.component`, `landing.component` |

## Run
```bash
cp .env.example .env              # host ports, JWT/DB config (git-ignored)
# JWT_SECRET is REQUIRED and has no default — the backend refuses to start without it:
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
docker build -t praetor-judge:latest judge/   # one-time: the sandbox image (not a compose service)
docker compose up --build         # postgres + backend + frontend
# schema + seed auto-applied by postgres initdb on the first (empty) volume
# browse http://localhost:4200   (API proxied at /api, WebSocket at /ws)
```
Rebuild the judge image after any change under `judge/`. If the DB schema changes, reset the volume
once with `docker compose down -v` before the next `up` (ddl-auto=none — it won't self-migrate).

The sandbox runs **one container per submission**, not one per test case: `compile` stands the
container up and every phase after it is a `docker exec` into it, which is where the judge's ~4×
speedup comes from. The container is destroyed when the submission finishes, so nothing leaks
between submissions. If that path is ever suspect, the old one-container-per-case behaviour is one
env var away:

```bash
SANDBOX_REUSE_CONTAINER=false     # backend env; default true (application.yml: judge.sandbox.reuse-container)
```

`JWT_SECRET` must be at least 32 characters (HS256 needs a 256-bit key). Miss it or make it short
and the backend stops at startup naming the property, rather than failing later on the first login.
It does not need to match your teammates' — each stack signs and verifies its own tokens. Changing
it invalidates every issued token, so everyone logs in again.

Seed loads **16 problems**, **3 contests** and **12 users**. Every seed account logs in with the
password `password` (dev only):

| accounts | role |
|---|---|
| `draenor08` | ADMIN — the only account that can create a contest or decide a problem proposal |
| `setter01`, `setter02` | PROBLEM_SETTER — authoring and test cases |
| `alice bob carol dan erin farid gita hasan imran` | USER |

Of the 16 problems, **12 are published** and **4 are drafts** (`grid-walk`, `two-sums`,
`bracket-balance`, `median-stream`) — a contest can only claim a draft, so those are the pool the
contest form offers. `median-stream` deliberately has **zero test cases**, to show the guard.

The three contests are seeded *relative to `now()`*, so a fresh volume always has one of each state:
`Praetor Warm-up Round 0` ended 3 days ago (real scoreboard), `Praetor Demo Round 1` is running
(2-hour window, 15-minute freeze), `Praetor Round 2` starts in 2 days. A volume seeded on an
**earlier day** therefore has a "live" contest that has already ended — `scripts/browser-pass-setup.sh`
re-dates the window without dropping data.

## End-to-end check
```bash
node scripts/e2e.mjs              # needs the stack up; zero dependencies, node's fetch only
BASE=http://localhost:9090 node scripts/e2e.mjs
```
Walks the whole journey against the running stack — author a problem, upload test cases, submit real
C++ through the Docker sandbox and wait for AC, then the anti-cheat boundaries, the submission
cooldown, the delete guard, archive, and contest rating. Exits non-zero on the first failure and
prints the actual response. Safe to re-run: everything it creates is suffixed per run. It leaves one
archived problem behind on purpose — it has submissions by then, so the delete guard (correctly)
refuses to remove it.

Two more checks, neither of which needs the stack:

```bash
node scripts/check-mark-dirty.mjs   # every OnPush subscribe pipes markDirty() — see CONVENTIONS 4.3
node demo/solutions/verify.mjs      # every demo solution still gets AC (needs the stack + judge image)
```

**CI** (`.github/workflows/ci.yml`) runs three jobs on every push and PR: backend unit tests
(`mvn test`), the frontend build in its shipped `docker` configuration, and the e2e journey — the
only job that executes a repository `@Query`, since every backend test mocks its repositories. The
two scripts above are **not** in CI yet and are run by hand.

## Demo walkthrough
1. Log in as `setter01` → **Manage** in the rail (staff only) → **New problem**.
2. Fill the form (`FLOAT` asks for a tolerance, `SPECIAL` for checker code — same rules the
   backend enforces), save, and it lands on the test-case editor.
3. Add a SAMPLE and a couple of HIDDEN cases → **Save all (replace)**.
4. Log in as `alice` → **Problems** → open it → submit code → watch the verdict arrive live over
   the WebSocket, per test case.
5. A wrong answer on a *practice* submission reveals the first failing case; the same wrong answer
   inside a live contest reveals nothing.
6. `POST /api/ratings/apply/{contestId}` as ADMIN rates a finished contest immediately instead of
   waiting for the 60s scheduler — then **Leaderboard** and **Profile** show ratings and history.

## Insulation rule (why seed matters)
Engine reads problems/testcases **straight from the DB** (via its own `@Immutable` `JudgeProblem` / `JudgeTestCase` projections), not through another module's controllers. Broken CRUD → seed still fills the tables → judging + demo survive. A module needing another's tables reads them through a projection or native query rather than importing the owning module's entities — see `docs/api-contracts.md` → Integration rules.

## Team & modules
| Module | Owner |
|---|---|
| Judging engine, contest core & integration | Sazid |
| Problems, contest setup & ratings | Yeasir |
| Discovery, insights & client experience | Mahir |

Each member builds their own Angular slices under `features/`; they plug into the shared shell (`core/`, `shared/`) that Sazid scaffolds.

## Optional final phase
Kafka (submission queue → consumer group) → Kubernetes (Minikube, scale judge-workers). Feature-flagged / separate branch. Core demos without them.
