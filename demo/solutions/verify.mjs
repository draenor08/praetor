#!/usr/bin/env node
//
// PRAETOR — verify every demo solution against the seeded test cases.
//
// These files exist so the demo never involves writing code live. That is only worth anything if
// they are known to pass, so this compiles and runs all of them inside the REAL judge image
// (praetor-judge:latest) with the real compile/run commands, and applies the same AC/WA rules the
// backend does.
//
//   node demo/solutions/verify.mjs              # everything
//   node demo/solutions/verify.mjs a-plus-b      # one problem
//
// Deliberately does NOT submit through the API: submissions would land in the database and perturb
// the seed shape that praetor-browser-check.md asserts throughout (solve stats, dashboard tiles,
// standings). This exercises the same toolchain without writing a row.
//
// Needs: the stack up (for the test cases, read from Postgres) and the judge image built on the
// host daemon — docker build -t praetor-judge:latest judge/

import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, copyFileSync, existsSync, rmSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';

const HERE = dirname(new URL(import.meta.url).pathname);
const DB = process.env.DB ?? 'praetor-db-1';
const IMAGE = process.env.JUDGE_IMAGE ?? 'praetor-judge:latest';
const only = process.argv.slice(2);

// Mirrors backend .../submission/engine/Language.java — that file is the source of truth. The
// multipliers are why a Java solution is not expected to fit a C++-shaped time limit.
const LANGUAGES = {
  CPP: {
    file: 'main.cpp',
    compile: ['g++', '-O2', '-std=gnu++17', '-o', 'prog', 'main.cpp'],
    run: ['./prog'],
    timeMultiplier: 1.0
  },
  PYTHON: {
    file: 'main.py',
    compile: ['python3', '-m', 'py_compile', 'main.py'],
    run: ['python3', 'main.py'],
    timeMultiplier: 3.0
  },
  JAVA: {
    file: 'Main.java',
    compile: ['javac', '-encoding', 'UTF-8', 'Main.java'],
    run: ['java', '-XX:+UseSerialGC', '-Xss64m', '-cp', '.', 'Main'],
    timeMultiplier: 3.0
  }
};

// --- checkers: same semantics as backend .../engine/checker/*.java ------------------------------

const tokenize = (s) => {
  const t = (s ?? '').trim();
  return t === '' ? [] : t.split(/\s+/);
};

const exactNormalize = (s) => {
  const lines = (s ?? '').split('\n');
  let end = lines.length;
  while (end > 0 && lines[end - 1].trim() === '') end--;
  return lines
    .slice(0, end)
    .map((l) => l.replace(/\s+$/, ''))
    .join('\n');
};

const checkers = {
  EXACT: (actual, expected) => exactNormalize(actual) === exactNormalize(expected),
  TOKEN: (actual, expected) => {
    const a = tokenize(actual);
    const e = tokenize(expected);
    return a.length === e.length && a.every((x, i) => x === e[i]);
  },
  FLOAT: (actual, expected, eps) => {
    const a = tokenize(actual);
    const e = tokenize(expected);
    if (a.length !== e.length) return false;
    return a.every((x, i) => {
      const ea = Number(e[i]);
      const aa = Number(x);
      // Non-numeric tokens compare exactly, matching FloatChecker's null-parse branch.
      if (!Number.isFinite(ea) || !Number.isFinite(aa)) return x === e[i];
      const diff = Math.abs(aa - ea);
      return diff <= eps || diff <= eps * Math.abs(ea);
    });
  }
};

// --- test data ---------------------------------------------------------------------------------

const sql = `
select coalesce(json_agg(x order by x->>'slug'), '[]'::json) from (
  select json_build_object(
    'slug', p.slug,
    'judgeMode', p.judge_mode,
    'eps', p.float_eps,
    'timeLimitMs', p.time_limit_ms,
    'tests', (
      select coalesce(json_agg(json_build_object('ord', t.ord, 'input', t.input, 'expected', t.expected)
                               order by t.ord), '[]'::json)
      from test_cases t where t.problem_id = p.id
    )
  ) as x
  from problems p
) s;`;

let problems;
try {
  const raw = execFileSync('docker', ['exec', '-i', DB, 'psql', '-U', 'praetor', '-d', 'praetor', '-tAc', sql], {
    encoding: 'utf8'
  });
  problems = JSON.parse(raw);
} catch (err) {
  console.error(`Could not read test cases from ${DB}. Is the stack up? (docker compose up -d)`);
  console.error(String(err.message ?? err).split('\n')[0]);
  process.exit(2);
}

const bySlug = new Map(problems.map((p) => [p.slug, p]));

// --- runner ------------------------------------------------------------------------------------

function verify(slug, langName) {
  const lang = LANGUAGES[langName];
  const source = join(HERE, slug, lang.file);
  if (!existsSync(source)) return null;

  const problem = bySlug.get(slug);
  const work = mkdtempSync(join(tmpdir(), `praetor-verify-${slug}-`));
  let container = null;

  try {
    copyFileSync(source, join(work, lang.file));

    // One container per solution, kept alive with docker exec per test case — the same shape the
    // judge itself uses since #51, and for the same reason: a spawn costs ~890ms, an exec ~60ms.
    container = execFileSync(
      'docker',
      ['run', '-d', '--rm', '--network', 'none', '-v', `${work}:/work`, '-w', '/work', IMAGE, 'sleep', 'infinity'],
      { encoding: 'utf8' }
    ).trim();

    const compiled = spawnSync('docker', ['exec', container, ...lang.compile], { encoding: 'utf8' });
    if (compiled.status !== 0) {
      return { verdict: 'CE', detail: (compiled.stderr || compiled.stdout || '').trim().split('\n')[0] };
    }

    const budgetMs = problem.timeLimitMs * lang.timeMultiplier;
    let slowestMs = 0;

    for (const test of problem.tests) {
      const started = Date.now();
      const run = spawnSync('docker', ['exec', '-i', container, ...lang.run], {
        input: test.input.endsWith('\n') ? test.input : `${test.input}\n`,
        encoding: 'utf8',
        timeout: Math.max(10000, budgetMs * 10)
      });
      const elapsed = Date.now() - started;
      slowestMs = Math.max(slowestMs, elapsed);

      if (run.status !== 0) {
        return {
          verdict: 'RE',
          detail: `test ${test.ord}: exit ${run.status}, ${(run.stderr || '').trim().split('\n')[0]}`
        };
      }

      const eps = problem.eps ?? 1e-6;
      const ok = checkers[problem.judgeMode](run.stdout, test.expected, eps);
      if (!ok) {
        return {
          verdict: 'WA',
          detail: `test ${test.ord}: expected ${JSON.stringify(test.expected)}, got ${JSON.stringify(
            (run.stdout ?? '').trim()
          )}`
        };
      }
    }

    return { verdict: 'AC', cases: problem.tests.length, slowestMs, budgetMs };
  } finally {
    if (container) spawnSync('docker', ['rm', '-f', container], { stdio: 'ignore' });
    rmSync(work, { recursive: true, force: true });
  }
}

// --- report ------------------------------------------------------------------------------------

const slugs = readdirSync(HERE, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => d.name)
  .filter((s) => only.length === 0 || only.includes(s))
  .sort();

if (slugs.length === 0) {
  console.error(only.length ? `No solution directory for: ${only.join(', ')}` : 'No solution directories found.');
  process.exit(2);
}

let failures = 0;
let untestable = 0;
let passes = 0;

for (const slug of slugs) {
  const problem = bySlug.get(slug);
  if (!problem) {
    console.log(`${slug}\n  ?? not in the database — reseed, or the slug was renamed`);
    untestable++;
    continue;
  }

  if (problem.tests.length === 0) {
    console.log(`${slug}  [${problem.judgeMode}]\n  -- 0 test cases seeded: unjudgeable by design, nothing to verify`);
    untestable++;
    continue;
  }

  const parts = [];
  for (const langName of Object.keys(LANGUAGES)) {
    const result = verify(slug, langName);
    if (!result) continue;

    if (result.verdict === 'AC') {
      const margin = Math.round((result.slowestMs / result.budgetMs) * 100);
      parts.push(`${langName} AC (${result.cases} cases, slowest ${result.slowestMs}ms of ${result.budgetMs}ms budget${margin > 60 ? ' ← TIGHT' : ''})`);
      passes++;
    } else {
      parts.push(`${langName} ${result.verdict} — ${result.detail}`);
      failures++;
    }
  }
  console.log(`${slug}  [${problem.judgeMode}]`);
  for (const p of parts) console.log(`  ${p.startsWith(' ') ? p : `${p.split(' ')[0].padEnd(7)}${p.slice(p.split(' ')[0].length + 1)}`}`);
}

console.log(`\n${passes} accepted · ${failures} failed · ${untestable} not verifiable`);
if (failures > 0) {
  console.log('A failing solution here would fail in the demo too — fix it before 6 Sept.');
  process.exit(1);
}
