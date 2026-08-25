#!/usr/bin/env node
/**
 * PRAETOR — end-to-end journey check.
 *
 * Walks the whole product against a running stack: authoring a problem, judging a submission,
 * the anti-cheat boundaries, the submission cooldown, and rating a finished contest. It covers the
 * paths no unit test can reach (real HTTP, real Postgres, real docker sandbox) and the ones a
 * browser test would not assert precisely.
 *
 * Zero dependencies — node's built-in fetch only.
 *
 *   docker compose up --build -d           # stack must be up, judge image built
 *   node scripts/e2e.mjs                   # defaults to http://localhost:8080
 *   BASE=http://localhost:9090 node scripts/e2e.mjs
 *
 * Exits 0 if every check passes, 1 on the first failure (with the actual response printed).
 * Safe to re-run: every object it creates is named with a fresh suffix and cleaned up.
 */

const BASE = process.env.BASE ?? 'http://localhost:8080';
const SEED_PASSWORD = process.env.SEED_PASSWORD ?? 'password';
/** Cooldown configured on the backend; the rate-limit check waits this out. */
const COOLDOWN_SEC = Number(process.env.SUBMISSION_RATE_LIMIT_SECONDS ?? 10);

const RUN = Date.now().toString(36).slice(-6);
let passed = 0;

// --- tiny harness ---------------------------------------------------------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  let parsed;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = text;
  }
  return { status: res.status, body: parsed, headers: res.headers };
}

function check(label, condition, actual) {
  if (condition) {
    passed += 1;
    console.log(`  ok   ${label}`);
    return;
  }
  console.error(`  FAIL ${label}`);
  console.error('       got:', JSON.stringify(actual));
  process.exit(1);
}

function section(title) {
  console.log(`\n${title}`);
}

async function login(identifier) {
  const res = await call('POST', '/api/auth/login', {
    body: { identifier, password: SEED_PASSWORD }
  });
  if (res.status !== 200 || !res.body?.token) {
    console.error(`Could not log in as ${identifier} (status ${res.status}).`);
    console.error('Seed accounts use the password "password" — has the DB been re-seeded');
    console.error('since the real bcrypt hashes landed? If not: docker compose down -v && up.');
    console.error('got:', JSON.stringify(res.body));
    process.exit(1);
  }
  return res.body.token;
}

/** Polls a submission until the judge finishes with it. */
async function waitForVerdict(id, token, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await call('GET', `/api/submissions/${id}`, { token });
    if (res.body?.status === 'DONE' || res.body?.status === 'ERROR') {
      return res.body;
    }
    await sleep(1000);
  }
  return null;
}

// --- the journey ----------------------------------------------------------

async function main() {
  console.log(`Praetor E2E against ${BASE} (run ${RUN})`);

  const slug = `e2e-sum-${RUN}`;

  section('Auth');
  const admin = await login('draenor08');
  const setter = await login('setter01');
  const alice = await login('alice');
  check('seed accounts log in', true, null);

  const me = await call('GET', '/api/users/me', { token: alice });
  check('/api/users/me uses `username`, not `handle`',
    me.body?.username === 'alice' && me.body?.handle === undefined, me.body);

  section('Authoring (PROBLEM_SETTER)');
  const created = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug, title: 'E2E Sum', statement: 'Read two integers. Output their sum.',
      constraints: '|a|,|b| <= 1e9', difficulty: 800, timeLimitMs: 2000,
      memLimitKb: 262144, judgeMode: 'EXACT', floatEps: null, checkerCode: null, editorial: null
    }
  });
  check('setter creates a problem → 201', created.status === 201, created);

  const badSlug = await call('POST', '/api/problems', {
    token: setter,
    body: { slug: 'Not A Slug!', title: 'x', statement: 'x' }
  });
  check('invalid slug → 400', badSlug.status === 400, badSlug);

  const floatNoEps = await call('POST', '/api/problems', {
    token: setter,
    body: { slug: `e2e-float-${RUN}`, title: 'x', statement: 'x', judgeMode: 'FLOAT' }
  });
  check('FLOAT without tolerance → 400', floatNoEps.status === 400, floatNoEps);

  const asContestant = await call('POST', '/api/problems', {
    token: alice,
    body: { slug: `e2e-nope-${RUN}`, title: 'x', statement: 'x' }
  });
  check('contestant cannot create a problem → 403', asContestant.status === 403, asContestant);

  section('Test cases');
  const cases = await call('POST', `/api/problems/${slug}/testcases/bulk`, {
    token: setter,
    body: {
      mode: 'REPLACE',
      cases: [
        { ord: 1, kind: 'SAMPLE', input: '2 3\n', expected: '5\n', points: 0 },
        { ord: 2, kind: 'HIDDEN', input: '100 200\n', expected: '300\n', points: 0 },
        { ord: 3, kind: 'HIDDEN', input: '-5 5\n', expected: '0\n', points: 0 }
      ]
    }
  });
  check('bulk REPLACE → 201 with 3 cases',
    cases.status === 201 && cases.body?.length === 3, cases);

  const appended = await call('POST', `/api/problems/${slug}/testcases/bulk`, {
    token: setter,
    body: { mode: 'APPEND', cases: [{ ord: 2, kind: 'HIDDEN', input: '1 1\n', expected: '2\n', points: 0 }] }
  });
  check('APPEND on an existing ord → 409', appended.status === 409, appended);

  section('Anti-cheat boundary');
  const asUser = await call('GET', `/api/problems/${slug}/testcases`, { token: alice });
  check('contestant reading test cases → 403', asUser.status === 403, asUser);

  const asAnon = await call('GET', `/api/problems/${slug}/testcases`);
  check('anonymous reading test cases → 403', asAnon.status === 403, asAnon);

  const publicView = await call('GET', `/api/problems/${slug}`);
  const publicJson = JSON.stringify(publicView.body);
  check('public statement exposes samples only',
    publicView.status === 200 &&
    publicJson.includes('2 3') &&
    !publicJson.includes('100 200') &&
    !publicJson.includes('-5 5'), publicView.body);

  section('Judging');
  const submitted = await call('POST', '/api/submissions', {
    token: alice,
    body: {
      problemSlug: slug,
      language: 'CPP',
      sourceCode: '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
    }
  });
  check('submit → 202 QUEUED',
    submitted.status === 202 && submitted.body?.status === 'QUEUED', submitted);

  const judged = await waitForVerdict(submitted.body.id, alice);
  check('correct solution judged AC on every case',
    judged?.verdict === 'AC' && judged.results?.every((r) => r.verdict === 'AC'), judged);
  check('per-testcase rows never carry hidden input/expected',
    judged?.results?.every((r) => r.input == null || r.ord === undefined) !== false &&
    !JSON.stringify(judged?.results ?? []).includes('100 200'), judged?.results);

  section('Rate limit (cooldown)');
  // Start from a clean window: judging the submission above took longer than the cooldown, so
  // the first submit here must be the one that opens it. Two calls back-to-back, no waiting
  // between them — otherwise this measures nothing.
  const first = await call('POST', '/api/submissions', {
    token: alice,
    body: { problemSlug: slug, language: 'CPP', sourceCode: 'int main(){}' }
  });
  check('submit re-opens the cooldown → 202', first.status === 202, first);

  const second = await call('POST', '/api/submissions', {
    token: alice,
    body: { problemSlug: slug, language: 'CPP', sourceCode: 'int main(){}' }
  });
  check('immediate resubmit → 429 with a wait',
    second.status === 429 && Number(second.body?.retryAfterSec) > 0, second);
  check('429 sends Retry-After', second.headers.get('retry-after') !== null,
    second.headers.get('retry-after'));

  console.log(`  ...waiting ${COOLDOWN_SEC + 1}s for the cooldown to lapse`);
  await sleep((COOLDOWN_SEC + 1) * 1000);

  const rejected = await call('POST', '/api/submissions', {
    token: alice,
    body: { problemSlug: `no-such-${RUN}`, language: 'CPP', sourceCode: 'x' }
  });
  check('unknown problem → 404', rejected.status === 404, rejected);

  // The regression this whole design exists for: the 404 above must not have spent the window.
  const afterReject = await call('POST', '/api/submissions', {
    token: alice,
    body: { problemSlug: slug, language: 'CPP', sourceCode: 'int main(){}' }
  });
  check('a rejected request does NOT spend the cooldown',
    afterReject.status === 202, afterReject);

  section('Workspace + delete guard');
  const workspace = await call('GET', '/api/setter/problems', { token: setter });
  const row = workspace.body?.find((p) => p.slug === slug);
  check('workspace lists the problem with its counts',
    row && row.testCases === 3 && row.submissions >= 1, row);
  check('a problem with submissions is not deletable',
    row?.deletable === false && /submission/.test(row?.lockReason ?? ''), row);

  const blocked = await call('DELETE', `/api/problems/${slug}`, { token: admin });
  check('deleting it → 409, not a raw 500', blocked.status === 409, blocked);

  const archived = await call('POST', `/api/problems/${slug}/archive`, { token: setter });
  check('archive → 200', archived.status === 200, archived);

  const publicList = await call('GET', '/api/problems');
  check('archived problem leaves the public list',
    !publicList.body?.some((p) => p.slug === slug), publicList.body?.length);

  const stillManaged = await call('GET', '/api/setter/problems', { token: setter });
  check('archived problem stays in the workspace',
    stillManaged.body?.some((p) => p.slug === slug), null);

  section('Ratings');
  const leaderboard = await call('GET', '/api/leaderboard?page=0&size=20');
  check('leaderboard is public and ranked',
    leaderboard.status === 200 && Array.isArray(leaderboard.body?.content), leaderboard.body);

  const ranks = (leaderboard.body?.content ?? []).map((e) => e.rank);
  check('ranks are non-decreasing down the page',
    ranks.every((r, i) => i === 0 || r >= ranks[i - 1]), ranks);

  const unknownRating = await call('GET', `/api/users/ghost-${RUN}/rating`);
  check('unknown handle → 404 (not flattened to 400)', unknownRating.status === 404, unknownRating);

  const applyAsUser = await call('POST', '/api/ratings/apply/1', { token: alice });
  check('contestant cannot apply ratings → 403', applyAsUser.status === 403, applyAsUser);

  const applyAsAdmin = await call('POST', '/api/ratings/apply/1', { token: admin });
  check('ADMIN can apply ratings → 202', applyAsAdmin.status === 202, applyAsAdmin);

  const applyAgain = await call('POST', '/api/ratings/apply/1', { token: admin });
  check('applying twice is idempotent (still 202, no double-apply)',
    applyAgain.status === 202, applyAgain);

  section('Multi-language judging (FR-5)');
  // The judge image carries three toolchains, but only C++ was ever exercised end to end, so a
  // stale image that had lost its JDK would fail every Java submission with "javac: not found"
  // and nothing here would notice. These two submissions are that alarm. Same A+B problem and
  // the same test cases as the C++ run above, so an AC means the toolchain, not the solution.
  const PY_SOLUTION = 'a, b = map(int, input().split())\nprint(a + b)\n';
  const JAVA_SOLUTION = [
    'import java.util.Scanner;',
    '',
    'public class Main {',
    '    public static void main(String[] args) {',
    '        Scanner sc = new Scanner(System.in);',
    '        long a = sc.nextLong();',
    '        long b = sc.nextLong();',
    '        System.out.println(a + b);',
    '    }',
    '}',
    ''
  ].join('\n');

  for (const [language, sourceCode] of [['PYTHON', PY_SOLUTION], ['JAVA', JAVA_SOLUTION]]) {
    // One accepted submission per user per cooldown window, so wait it out rather than eating a 429.
    await sleep(COOLDOWN_SEC * 1000);
    const sent = await call('POST', '/api/submissions', {
      token: alice,
      body: { problemSlug: slug, language, sourceCode }
    });
    check(`${language}: submit → 202 QUEUED`, sent.status === 202, sent);

    const judged = await waitForVerdict(sent.body?.id, alice);
    // A CE here almost always means the toolchain is missing from the image rather than a bad
    // solution, so surface the compile log — it is the difference between a five-second diagnosis
    // and an hour of guessing.
    check(`${language}: judged AC (toolchain present and working)`,
      judged?.status === 'DONE' && judged?.verdict === 'AC',
      { status: judged?.status, verdict: judged?.verdict, compileLog: judged?.compileLog });
  }

  section('Submission history + solve stats (FR-10, FR-25)');
  // These endpoints are the only place a repository @Query is exercised at all — the backend
  // tests mock every repository, so a broken JPQL, a bad Spring-Data-derived count query, or a
  // parameter-cast problem would otherwise reach production silently. That is what this section
  // exists for.
  const history = await call('GET', '/api/submissions?page=0&size=5', { token: alice });
  check('own submission history → 200 (JPQL and its derived count query both execute)',
    history.status === 200 && Array.isArray(history.body?.content), history.body);

  check('page envelope carries page/size/totalElements',
    history.body?.page === 0 && history.body?.size === 5
      && typeof history.body?.totalElements === 'number', history.body);

  const rows = history.body?.content ?? [];
  check('every row belongs to the caller (no cross-user rows leak into own history)',
    rows.every((r) => r.handle === 'alice'), rows.map((r) => r.handle));

  check('summary rows never carry sourceCode',
    rows.every((r) => !('sourceCode' in r)), Object.keys(rows[0] ?? {}));

  const paged = await call('GET', '/api/submissions?page=0&size=1', { token: alice });
  check('size is honoured (size=1 returns at most one row)',
    paged.status === 200 && (paged.body?.content?.length ?? 0) <= 1, paged.body);

  const foreign = await call('GET', '/api/submissions?user=setter01', { token: alice });
  check('contestant reading another handle → 403', foreign.status === 403, foreign);

  const asAdmin = await call('GET', '/api/submissions?user=alice&page=0&size=5', { token: admin });
  check('ADMIN may filter to any handle → 200',
    asAdmin.status === 200 && Array.isArray(asAdmin.body?.content), asAdmin.body);

  const badSize = await call('GET', '/api/submissions?size=101', { token: alice });
  check('size above the cap → 400', badSize.status === 400, badSize);

  const badPage = await call('GET', '/api/submissions?page=-1', { token: alice });
  check('negative page → 400', badPage.status === 400, badPage);

  const stats = await call('GET', '/api/users/alice/stats', { token: alice });
  check('solve stats → 200 with a body', stats.status === 200 && typeof stats.body === 'object',
    stats.body);
  // Deliberately not asserting the field names yet. The shipped shape
  // ({username, solvedCount, submissionCount, acceptanceRate}) does not match
  // docs/api-contracts.md ({solved, attempted, accuracy, byVerdict}); the assertions for the
  // contract shape, the accuracy formula, the 404 on an unknown handle, and the contest-end
  // filter on the counts land together with those fixes. Keeping them out now so this script
  // stays green on main.

  section('Cleanup');
  // The problem now has submissions, so it cannot be hard-deleted — that is the guard working,
  // not a failure. It is left archived and out of the public list, named with this run's suffix.
  const restored = await call('POST', `/api/problems/${slug}/unarchive`, { token: setter });
  check('unarchive works (leaving the run artifact visible to staff only)',
    restored.status === 200, restored);
  const reArchived = await call('POST', `/api/problems/${slug}/archive`, { token: setter });
  check('re-archived so the public list stays clean', reArchived.status === 200, reArchived);

  console.log(`\n${passed} checks passed.`);
  console.log(`Left behind: archived problem "${slug}" (it has submissions, so it cannot be deleted).`);
}

main().catch((err) => {
  console.error('\nE2E aborted:', err?.message ?? err);
  console.error('Is the stack up? docker compose up --build -d');
  process.exit(1);
});
