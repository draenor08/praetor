-- PRAETOR — seed data. Run AFTER schema.sql.
-- Purpose: fresh DB instantly judgeable WITHOUT any teammate UI (insulation layer).
-- Demo survives broken CRUD: engine reads these rows straight from DB.
-- psql -U praetor -d praetor -f db/seed.sql
--
-- Password hashes below are bcrypt of 'password' — DEV ONLY. Replace in prod.

BEGIN;

-- Users: 1 admin, 1 setter, 2 coders --------------------------------------
INSERT INTO users (full_name, username, email, password, role) VALUES
  ('Admin User', 'draenor08', 'admin@praetor.local',  '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Vf9bxQ5jR0jX8mJ0qQ8mJ0qQ8mO', 'ADMIN'),
  ('Setter User', 'setter01',  'setter@praetor.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Vf9bxQ5jR0jX8mJ0qQ8mJ0qQ8mO', 'PROBLEM_SETTER'),
  ('Alice Coder', 'alice',     'alice@praetor.local',  '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Vf9bxQ5jR0jX8mJ0qQ8mJ0qQ8mO', 'USER'),
  ('Bob Coder', 'bob',       'bob@praetor.local',    '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Vf9bxQ5jR0jX8mJ0qQ8mJ0qQ8mO', 'USER');

-- Tags --------------------------------------------------------------------
INSERT INTO tags (name) VALUES ('math'), ('implementation'), ('greedy'), ('strings');

-- Problem 1: A+B (EXACT) --------------------------------------------------
INSERT INTO problems (slug, title, statement, constraints, difficulty, time_limit_ms, mem_limit_kb, judge_mode, created_by)
VALUES ('a-plus-b', 'A + B',
        'Read two integers a and b on one line. Output their sum.',
        '-10^9 <= a, b <= 10^9', 800, 1000, 262144, 'EXACT',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='a-plus-b'), id FROM tags WHERE name IN ('math','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='a-plus-b'), 1, 'SAMPLE', '2 3',           '5'),
  ((SELECT id FROM problems WHERE slug='a-plus-b'), 2, 'HIDDEN', '100 200',       '300'),
  ((SELECT id FROM problems WHERE slug='a-plus-b'), 3, 'HIDDEN', '-5 5',          '0'),
  ((SELECT id FROM problems WHERE slug='a-plus-b'), 4, 'HIDDEN', '1000000000 1000000000', '2000000000');

-- Problem 2: Reverse String (EXACT) ---------------------------------------
INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('reverse-string', 'Reverse String',
        'Read a single line string s. Output it reversed.',
        '1 <= |s| <= 1000, printable ascii, no spaces', 900, 'EXACT',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='reverse-string'), id FROM tags WHERE name IN ('strings','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='reverse-string'), 1, 'SAMPLE', 'hello', 'olleh'),
  ((SELECT id FROM problems WHERE slug='reverse-string'), 2, 'HIDDEN', 'praetor', 'rotearp'),
  ((SELECT id FROM problems WHERE slug='reverse-string'), 3, 'HIDDEN', 'a', 'a');

-- Problem 3: Circle Area (FLOAT — special judge, eps tolerance) ------------
INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, float_eps, created_by)
VALUES ('circle-area', 'Circle Area',
        'Read integer radius r. Output the area of the circle (pi r^2).',
        '1 <= r <= 1000', 1000, 'FLOAT', 1e-6,
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='circle-area'), id FROM tags WHERE name='math';

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='circle-area'), 1, 'SAMPLE', '1', '3.14159265'),
  ((SELECT id FROM problems WHERE slug='circle-area'), 2, 'HIDDEN', '2', '12.56637061'),
  ((SELECT id FROM problems WHERE slug='circle-area'), 3, 'HIDDEN', '10', '314.15926536');

-- A live contest with the 3 problems --------------------------------------
INSERT INTO contests (title, starts_at, ends_at, freeze_min, scoring)
VALUES ('Praetor Demo Round 1', now() - interval '10 minutes', now() + interval '2 hours', 15, 'ICPC');

INSERT INTO contest_problems (contest_id, problem_id, label, ord) VALUES
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='a-plus-b'),       'A', 1),
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='reverse-string'), 'B', 2),
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='circle-area'),    'C', 3);

INSERT INTO registrations (contest_id, user_id) VALUES
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM users WHERE username='alice')),
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM users WHERE username='bob'));

-- Seed ratings (TM2 domain, but seeded so leaderboard not empty) -----------
INSERT INTO ratings (user_id, value) VALUES
  ((SELECT id FROM users WHERE username='alice'), 1500),
  ((SELECT id FROM users WHERE username='bob'),   1500);

COMMIT;

-- Verify quickly:
--   SELECT slug, judge_mode, (SELECT count(*) FROM test_cases t WHERE t.problem_id=p.id) AS tc
--   FROM problems p;
