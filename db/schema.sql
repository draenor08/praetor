-- PRAETOR — Mini Online Judge — PostgreSQL schema (migration V1)
-- Owner: lead. Engine reads problems/testcases straight from these tables (insulation).
-- Apply: psql -U praetor -d praetor -f db/schema.sql   (or Flyway V1__init.sql)

BEGIN;

-- ---------------------------------------------------------------------------
-- Identity (baseline — NOT counted as a feature)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,
    full_name    VARCHAR(100) NOT NULL,
    username     VARCHAR(40)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER'
                 CHECK (role IN ('USER','PROBLEM_SETTER','ADMIN')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Problem domain (TM1 owns CRUD; lead owns schema + seed)
-- ---------------------------------------------------------------------------
CREATE TABLE problems (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(80)  NOT NULL UNIQUE,      -- url-friendly id, e.g. 'a-plus-b'
    title         VARCHAR(200) NOT NULL,
    statement     TEXT         NOT NULL,             -- markdown
    constraints   TEXT,
    difficulty    INTEGER      NOT NULL DEFAULT 800  -- CF-style rating
                  CHECK (difficulty BETWEEN 0 AND 4000),
    time_limit_ms INTEGER      NOT NULL DEFAULT 1000,
    mem_limit_kb  INTEGER      NOT NULL DEFAULT 262144,  -- 256 MB
    -- judging mode: exact diff vs custom/special judge (float tolerance, multiple answers)
    judge_mode    VARCHAR(20)  NOT NULL DEFAULT 'EXACT'
                  CHECK (judge_mode IN ('EXACT','TOKEN','FLOAT','SPECIAL')),
    float_eps     DOUBLE PRECISION,                  -- used when judge_mode='FLOAT'
    checker_code  TEXT,                              -- used when judge_mode='SPECIAL'
    editorial     TEXT,                              -- feat 13, markdown, nullable
    created_by    BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE problem_tags (
    problem_id BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tags(id)     ON DELETE CASCADE,
    PRIMARY KEY (problem_id, tag_id)
);

CREATE TABLE test_cases (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT  NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    ord        INTEGER NOT NULL,                      -- run order
    kind       VARCHAR(10) NOT NULL DEFAULT 'HIDDEN'
               CHECK (kind IN ('SAMPLE','HIDDEN')),   -- SAMPLE shown to user
    input      TEXT NOT NULL,
    expected   TEXT NOT NULL,
    points     INTEGER NOT NULL DEFAULT 0,            -- for partial scoring (stretch)
    UNIQUE (problem_id, ord)
);
CREATE INDEX idx_testcases_problem ON test_cases(problem_id);

-- ---------------------------------------------------------------------------
-- Contests (lead)
-- ---------------------------------------------------------------------------
CREATE TABLE contests (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    freeze_min  INTEGER      NOT NULL DEFAULT 0,      -- freeze standings last N min (feat 18)
    scoring     VARCHAR(20)  NOT NULL DEFAULT 'ICPC'
                CHECK (scoring IN ('ICPC','POINTS')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);

CREATE TABLE contest_problems (
    contest_id BIGINT  NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    problem_id BIGINT  NOT NULL REFERENCES problems(id),
    label      CHAR(2) NOT NULL,                      -- 'A','B',... shown in contest
    ord        INTEGER NOT NULL,
    PRIMARY KEY (contest_id, problem_id),
    UNIQUE (contest_id, label)
);

CREATE TABLE registrations (
    contest_id   BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    is_virtual   BOOLEAN     NOT NULL DEFAULT FALSE,  -- virtual/upsolve (feat 19)
    virtual_start TIMESTAMPTZ,                        -- when virtual participant started
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (contest_id, user_id)
);

-- ---------------------------------------------------------------------------
-- Submissions + per-testcase results (lead — engine writes here)
-- ---------------------------------------------------------------------------
CREATE TABLE submissions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    problem_id  BIGINT NOT NULL REFERENCES problems(id),
    contest_id  BIGINT REFERENCES contests(id),       -- null = practice submission
    language    VARCHAR(20) NOT NULL                  -- 'CPP','PYTHON','JAVA'
                CHECK (language IN ('CPP','PYTHON','JAVA')),
    source_code TEXT   NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'QUEUED' -- lifecycle (feat 5)
                CHECK (status IN ('QUEUED','JUDGING','DONE','ERROR')),
    verdict     VARCHAR(10)                           -- overall: AC/WA/TLE/MLE/RE/CE (feat 3)
                CHECK (verdict IN ('AC','WA','TLE','MLE','RE','CE','PE')),
    time_ms     INTEGER,                              -- max over testcases
    mem_kb      INTEGER,
    compile_log TEXT,                                 -- feat 6
    score       INTEGER NOT NULL DEFAULT 0,           -- partial scoring (stretch)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sub_user     ON submissions(user_id, created_at DESC);
CREATE INDEX idx_sub_problem  ON submissions(problem_id);
CREATE INDEX idx_sub_contest  ON submissions(contest_id, created_at);
-- rate-limit support (feat 24): query recent submissions per user fast
CREATE INDEX idx_sub_user_recent ON submissions(user_id, created_at);

CREATE TABLE submission_results (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    test_case_id  BIGINT NOT NULL REFERENCES test_cases(id),
    verdict       VARCHAR(10) NOT NULL
                  CHECK (verdict IN ('AC','WA','TLE','MLE','RE','CE','PE')),
    time_ms       INTEGER,
    mem_kb        INTEGER,
    -- feat 3d: program stdout of the first failing case, for the practice-mode reveal only.
    -- Truncated (~4KB) at capture; null on AC rows and when there is no per-test output (CE).
    actual_output TEXT,
    UNIQUE (submission_id, test_case_id)
);

-- ---------------------------------------------------------------------------
-- Rating / ELO (TM2)
-- ---------------------------------------------------------------------------
CREATE TABLE ratings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    value   INTEGER NOT NULL DEFAULT 1500
);

CREATE TABLE rating_history (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    contest_id    BIGINT NOT NULL REFERENCES contests(id),
    rating_before INTEGER NOT NULL,
    rating_after  INTEGER NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rating_hist_user ON rating_history(user_id, created_at);

-- ---------------------------------------------------------------------------
-- Clarifications / announcements (lead — WebSocket, feat 20)
-- ---------------------------------------------------------------------------
CREATE TABLE clarifications (
    id           BIGSERIAL PRIMARY KEY,
    contest_id   BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    problem_id   BIGINT REFERENCES problems(id),       -- null = general announcement
    asked_by     BIGINT REFERENCES users(id),          -- null = admin announcement
    question     TEXT,
    answer       TEXT,
    is_public    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_clar_contest ON clarifications(contest_id, created_at);

COMMIT;
