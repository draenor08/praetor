-- PRAETOR — seed data. Run AFTER schema.sql.
-- Purpose: fresh DB instantly judgeable WITHOUT any teammate UI (insulation layer).
-- Demo survives broken CRUD: engine reads these rows straight from DB.
-- psql -U praetor -d praetor -f db/seed.sql
--
-- Password hashes below are bcrypt of 'password' — DEV ONLY. Replace in prod.

BEGIN;

-- Users: 1 admin, 1 setter, 2 coders --------------------------------------
-- Real bcrypt(cost 10) hashes of 'password', generated with the app's own
-- BCryptPasswordEncoder. The previous values were hand-typed placeholders that no encoder
-- ever produced, so every seeded account failed login — including setter01, which is the
-- only account that can author problems.
INSERT INTO users (full_name, username, email, password, role) VALUES
  ('Admin User', 'draenor08', 'admin@praetor.local',  '$2a$10$hzSiBJck5JwpT1KZf6p1ZOu6y5azOvxg9DkN6MAm3vZCgpZOu2R6W', 'ADMIN'),
  ('Setter User', 'setter01',  'setter@praetor.local', '$2a$10$ThvytFJuKV1zPlGHW33RwOMvmTueSQ2aHrRGGMKBOsZRMutu2.G22', 'PROBLEM_SETTER'),
  ('Alice Coder', 'alice',     'alice@praetor.local',  '$2a$10$B5Snx3okF5ynOo5vII8V0eVGOgDmOvXUv5RMKSv5X7gYYDqkKrwrS', 'USER'),
  ('Bob Coder', 'bob',       'bob@praetor.local',    '$2a$10$VMY379g6O67Opp7W3A6uOuHePRcUX0N2WH5MHIIFD48ySPrrFUhL6', 'USER');

-- A second setter, and a field of contestants so the leaderboard and standings have shape.
-- The hashes are the four above, reused in rotation: bcrypt verifies a password against whatever
-- salt is embedded in the stored hash, so every account below also logs in with 'password'.
-- Reused rather than freshly generated because generating one requires running the encoder.
INSERT INTO users (full_name, username, email, password, role) VALUES
  ('Nadia Rahman', 'setter02', 'nadia@praetor.local',  '$2a$10$ThvytFJuKV1zPlGHW33RwOMvmTueSQ2aHrRGGMKBOsZRMutu2.G22', 'PROBLEM_SETTER'),
  ('Carol Dey', 'carol',       'carol@praetor.local',  '$2a$10$B5Snx3okF5ynOo5vII8V0eVGOgDmOvXUv5RMKSv5X7gYYDqkKrwrS', 'USER'),
  ('Dan Iqbal', 'dan',         'dan@praetor.local',    '$2a$10$VMY379g6O67Opp7W3A6uOuHePRcUX0N2WH5MHIIFD48ySPrrFUhL6', 'USER'),
  ('Erin Haque', 'erin',       'erin@praetor.local',   '$2a$10$B5Snx3okF5ynOo5vII8V0eVGOgDmOvXUv5RMKSv5X7gYYDqkKrwrS', 'USER'),
  ('Farid Ahmed', 'farid',     'farid@praetor.local',  '$2a$10$VMY379g6O67Opp7W3A6uOuHePRcUX0N2WH5MHIIFD48ySPrrFUhL6', 'USER'),
  ('Gita Sen', 'gita',         'gita@praetor.local',   '$2a$10$B5Snx3okF5ynOo5vII8V0eVGOgDmOvXUv5RMKSv5X7gYYDqkKrwrS', 'USER'),
  ('Hasan Ali', 'hasan',       'hasan@praetor.local',  '$2a$10$VMY379g6O67Opp7W3A6uOuHePRcUX0N2WH5MHIIFD48ySPrrFUhL6', 'USER'),
  ('Imran Kabir', 'imran',     'imran@praetor.local',  '$2a$10$B5Snx3okF5ynOo5vII8V0eVGOgDmOvXUv5RMKSv5X7gYYDqkKrwrS', 'USER');

-- Tags --------------------------------------------------------------------
INSERT INTO tags (name) VALUES
  ('math'), ('implementation'), ('greedy'), ('strings'),
  ('sorting'), ('number theory'), ('brute force'), ('geometry');

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

-- Problem 4: Sort Three (TOKEN — whitespace-insensitive) --------------------
INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('sort-three', 'Sort Three',
        'Read three integers. Output them in non-decreasing order (any whitespace between them).',
        '-1e9 <= each <= 1e9', 900, 'TOKEN',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='sort-three'), id FROM tags WHERE name='implementation';

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='sort-three'), 1, 'SAMPLE', '3 1 2',   '1 2 3'),
  ((SELECT id FROM problems WHERE slug='sort-three'), 2, 'HIDDEN', '5 5 5',   '5 5 5'),
  ((SELECT id FROM problems WHERE slug='sort-three'), 3, 'HIDDEN', '9 -1 0',  '-1 0 9');

-- Problems 5-12: the free practice set ------------------------------------
-- Deliberately more than the contests consume. A contest embargoes its problems until it ends
-- (they vanish from the public list), so without a surplus the Problems page would look empty
-- to a contestant for as long as a round is running.

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('count-vowels', 'Count Vowels',
        'Read a single line string s. Output how many of its characters are vowels (a, e, i, o, u).',
        '1 <= |s| <= 1000, lowercase letters only', 800, 'EXACT',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='count-vowels'), id FROM tags WHERE name IN ('strings','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='count-vowels'), 1, 'SAMPLE', 'praetor',    '3'),
  ((SELECT id FROM problems WHERE slug='count-vowels'), 2, 'HIDDEN', 'rhythm',     '0'),
  ((SELECT id FROM problems WHERE slug='count-vowels'), 3, 'HIDDEN', 'aeiou',      '5');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('max-of-three', 'Max Of Three',
        'Read three integers on one line. Output the largest of them.',
        '-10^9 <= each <= 10^9', 800, 'EXACT',
        (SELECT id FROM users WHERE username='setter02'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='max-of-three'), id FROM tags WHERE name IN ('implementation','brute force');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='max-of-three'), 1, 'SAMPLE', '3 9 4',        '9'),
  ((SELECT id FROM problems WHERE slug='max-of-three'), 2, 'HIDDEN', '-5 -2 -9',     '-2'),
  ((SELECT id FROM problems WHERE slug='max-of-three'), 3, 'HIDDEN', '7 7 7',        '7');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('is-prime', 'Is Prime',
        'Read an integer n. Output YES if n is prime, otherwise NO.',
        '2 <= n <= 10^9', 1100, 'EXACT',
        (SELECT id FROM users WHERE username='setter02'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='is-prime'), id FROM tags WHERE name IN ('math','number theory');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='is-prime'), 1, 'SAMPLE', '7',          'YES'),
  ((SELECT id FROM problems WHERE slug='is-prime'), 2, 'HIDDEN', '1000000007', 'YES'),
  ((SELECT id FROM problems WHERE slug='is-prime'), 3, 'HIDDEN', '999999999',  'NO');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('sum-to-n', 'Sum To N',
        'Read an integer n. Output the sum of all integers from 1 to n.',
        '1 <= n <= 10^9 (the answer may exceed 32 bits)', 900, 'EXACT',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='sum-to-n'), id FROM tags WHERE name IN ('math','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='sum-to-n'), 1, 'SAMPLE', '5',          '15'),
  ((SELECT id FROM problems WHERE slug='sum-to-n'), 2, 'HIDDEN', '1',          '1'),
  ((SELECT id FROM problems WHERE slug='sum-to-n'), 3, 'HIDDEN', '1000000',    '500000500000');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('sort-array', 'Sort Array',
        'Read n, then n integers. Output them in non-decreasing order, separated by spaces.',
        '1 <= n <= 1000, -10^9 <= each <= 10^9', 1000, 'TOKEN',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='sort-array'), id FROM tags WHERE name IN ('sorting','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='sort-array'), 1, 'SAMPLE', '4
4 1 3 2',            '1 2 3 4'),
  ((SELECT id FROM problems WHERE slug='sort-array'), 2, 'HIDDEN', '1
42',                  '42'),
  ((SELECT id FROM problems WHERE slug='sort-array'), 3, 'HIDDEN', '5
-3 0 -3 9 1',         '-3 -3 0 1 9');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('coin-change-greedy', 'Coin Change (Greedy)',
        'Read an amount n. Using coins of 25, 10, 5 and 1, output the smallest number of coins that make n.',
        '1 <= n <= 10^6', 1200, 'EXACT',
        (SELECT id FROM users WHERE username='setter02'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='coin-change-greedy'), id FROM tags WHERE name IN ('greedy','math');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='coin-change-greedy'), 1, 'SAMPLE', '41',   '4'),
  ((SELECT id FROM problems WHERE slug='coin-change-greedy'), 2, 'HIDDEN', '99',   '9'),
  ((SELECT id FROM problems WHERE slug='coin-change-greedy'), 3, 'HIDDEN', '1',    '1');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, float_eps, created_by)
VALUES ('triangle-area', 'Triangle Area',
        'Read the base b and height h of a triangle. Output its area.',
        '1 <= b, h <= 10^4', 1000, 'FLOAT', 1e-6,
        (SELECT id FROM users WHERE username='setter02'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='triangle-area'), id FROM tags WHERE name IN ('geometry','math');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='triangle-area'), 1, 'SAMPLE', '4 5',    '10.00000000'),
  ((SELECT id FROM problems WHERE slug='triangle-area'), 2, 'HIDDEN', '7 3',    '10.50000000'),
  ((SELECT id FROM problems WHERE slug='triangle-area'), 3, 'HIDDEN', '10000 10000', '50000000.00000000');

INSERT INTO problems (slug, title, statement, constraints, difficulty, judge_mode, created_by)
VALUES ('palindrome-check', 'Palindrome Check',
        'Read a single line string s. Output YES if it reads the same forwards and backwards, else NO.',
        '1 <= |s| <= 1000, lowercase letters only', 900, 'EXACT',
        (SELECT id FROM users WHERE username='setter01'));

INSERT INTO problem_tags (problem_id, tag_id)
SELECT (SELECT id FROM problems WHERE slug='palindrome-check'), id FROM tags WHERE name IN ('strings','implementation');

INSERT INTO test_cases (problem_id, ord, kind, input, expected) VALUES
  ((SELECT id FROM problems WHERE slug='palindrome-check'), 1, 'SAMPLE', 'level',  'YES'),
  ((SELECT id FROM problems WHERE slug='palindrome-check'), 2, 'HIDDEN', 'praetor','NO'),
  ((SELECT id FROM problems WHERE slug='palindrome-check'), 3, 'HIDDEN', 'a',      'YES');

-- A live contest with the 3 problems --------------------------------------
INSERT INTO contests (title, starts_at, ends_at, freeze_min, scoring)
VALUES ('Praetor Demo Round 1', now() - interval '10 minutes', now() + interval '2 hours', 15, 'ICPC');

INSERT INTO contest_problems (contest_id, problem_id, label, ord) VALUES
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='a-plus-b'),       'A', 1),
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='reverse-string'), 'B', 2),
  ((SELECT id FROM contests WHERE title='Praetor Demo Round 1'), (SELECT id FROM problems WHERE slug='circle-area'),    'C', 3);

INSERT INTO registrations (contest_id, user_id)
SELECT (SELECT id FROM contests WHERE title='Praetor Demo Round 1'), id
FROM users WHERE username IN ('alice','bob','carol','dan','erin');

-- An ENDED contest, with a scoreboard already on it -----------------------
-- Its problems are drawn from the practice set: a contest embargoes its problems only until it
-- ends, and this one is three days old, so all three are back in the public list. Gives the
-- standings page something real to show, and the ended-contest path something to demo.
INSERT INTO contests (title, starts_at, ends_at, freeze_min, scoring)
VALUES ('Praetor Warm-up Round 0', now() - interval '3 days', now() - interval '3 days' + interval '2 hours', 15, 'ICPC');

INSERT INTO contest_problems (contest_id, problem_id, label, ord) VALUES
  ((SELECT id FROM contests WHERE title='Praetor Warm-up Round 0'), (SELECT id FROM problems WHERE slug='count-vowels'), 'A', 1),
  ((SELECT id FROM contests WHERE title='Praetor Warm-up Round 0'), (SELECT id FROM problems WHERE slug='max-of-three'), 'B', 2),
  ((SELECT id FROM contests WHERE title='Praetor Warm-up Round 0'), (SELECT id FROM problems WHERE slug='is-prime'),     'C', 3);

INSERT INTO registrations (contest_id, user_id)
SELECT (SELECT id FROM contests WHERE title='Praetor Warm-up Round 0'), id
FROM users WHERE username IN ('alice','bob','carol','dan','erin','farid');

-- Judged submissions for that round. The standings calculator folds these: penalty is
-- solve-minute + 20 per rejected attempt before the AC, and only submissions inside the window
-- count — so every created_at is written as an offset from the contest's own start.
INSERT INTO submissions (user_id, problem_id, contest_id, language, source_code, status, verdict, time_ms, mem_kb, created_at)
SELECT u.id, p.id, c.id, 'CPP', '// seeded', 'DONE', v.verdict, v.time_ms, 2048,
       c.starts_at + (v.at_min || ' minutes')::interval
FROM (VALUES
        ('alice', 'count-vowels', 'WA',  5,  9),
        ('alice', 'count-vowels', 'AC', 12, 12),
        ('alice', 'max-of-three', 'AC', 18, 35),
        ('alice', 'is-prime',     'AC', 41, 78),
        ('bob',   'count-vowels', 'AC', 11, 20),
        ('bob',   'max-of-three', 'AC', 15, 50),
        ('bob',   'is-prime',     'WA', 33, 95),
        ('carol', 'count-vowels', 'AC',  9,  8),
        ('carol', 'is-prime',     'WA', 30, 40),
        ('carol', 'is-prime',     'WA', 31, 52),
        ('carol', 'is-prime',     'AC', 38, 65),
        ('dan',   'count-vowels', 'AC', 14, 40),
        ('erin',  'count-vowels', 'AC', 10, 15),
        ('erin',  'max-of-three', 'AC', 16, 55),
        ('farid', 'count-vowels', 'WA',  8, 22),
        ('farid', 'count-vowels', 'WA',  8, 47)
     ) AS v(username, slug, verdict, time_ms, at_min)
JOIN users u ON u.username = v.username
JOIN problems p ON p.slug = v.slug
CROSS JOIN (SELECT id, starts_at FROM contests WHERE title='Praetor Warm-up Round 0') c;

-- An UPCOMING contest -----------------------------------------------------
-- Registration is open, but its problems stay withheld until it starts — and stay out of the
-- public list until it ends. Useful for demonstrating the register prompt on a contest that has
-- not begun.
INSERT INTO contests (title, starts_at, ends_at, freeze_min, scoring)
VALUES ('Praetor Round 2', now() + interval '2 days', now() + interval '2 days' + interval '3 hours', 30, 'ICPC');

INSERT INTO contest_problems (contest_id, problem_id, label, ord) VALUES
  ((SELECT id FROM contests WHERE title='Praetor Round 2'), (SELECT id FROM problems WHERE slug='sort-array'),         'A', 1),
  ((SELECT id FROM contests WHERE title='Praetor Round 2'), (SELECT id FROM problems WHERE slug='coin-change-greedy'), 'B', 2),
  ((SELECT id FROM contests WHERE title='Praetor Round 2'), (SELECT id FROM problems WHERE slug='triangle-area'),      'C', 3);

INSERT INTO registrations (contest_id, user_id)
SELECT (SELECT id FROM contests WHERE title='Praetor Round 2'), id
FROM users WHERE username IN ('alice','carol');

-- Seed ratings (TM2 domain, but seeded so leaderboard not empty) -----------
-- All four seeded accounts, not just the two contestants: registration creates a ratings
-- row, but seeding never did, so staff accounts showed a default rating with no row behind it.
-- Spread rather than flat: a leaderboard where every row reads 1500 shows nothing about ranking,
-- ties, or the highlighted self-row.
INSERT INTO ratings (user_id, value) VALUES
  ((SELECT id FROM users WHERE username='draenor08'), 1500),
  ((SELECT id FROM users WHERE username='setter01'),  1500),
  ((SELECT id FROM users WHERE username='setter02'),  1500),
  ((SELECT id FROM users WHERE username='alice'),     1687),
  ((SELECT id FROM users WHERE username='bob'),       1542),
  ((SELECT id FROM users WHERE username='carol'),     1603),
  ((SELECT id FROM users WHERE username='dan'),       1421),
  ((SELECT id FROM users WHERE username='erin'),      1542),
  ((SELECT id FROM users WHERE username='farid'),     1358),
  ((SELECT id FROM users WHERE username='gita'),      1290),
  ((SELECT id FROM users WHERE username='hasan'),     1476),
  ((SELECT id FROM users WHERE username='imran'),     1500);

-- Rating history from the warm-up round, so the profile chart has a line to draw. The deltas
-- agree with that round's scoreboard: the three-solver gains most, the no-solver loses.
INSERT INTO rating_history (user_id, contest_id, rating_before, rating_after, created_at)
SELECT u.id, c.id, h.before, h.after, c.ends_at
FROM (VALUES
        ('alice', 1500, 1687),
        ('bob',   1500, 1542),
        ('carol', 1500, 1603),
        ('dan',   1500, 1421),
        ('erin',  1500, 1542),
        ('farid', 1500, 1358)
     ) AS h(username, before, after)
JOIN users u ON u.username = h.username
CROSS JOIN (SELECT id, ends_at FROM contests WHERE title='Praetor Warm-up Round 0') c;

COMMIT;

-- Verify quickly:
--   SELECT slug, judge_mode, (SELECT count(*) FROM test_cases t WHERE t.problem_id=p.id) AS tc
--   FROM problems p;
