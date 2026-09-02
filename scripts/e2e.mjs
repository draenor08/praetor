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

/**
 * Submits, waiting out the per-user cooldown if this user is still inside it. The script is
 * documented as safe to re-run back to back, which means the very first submission of a run can
 * legitimately land inside the window left by the previous run.
 */
async function submitting(token, body) {
  let res = await call('POST', '/api/submissions', { token, body });
  if (res.status === 429) {
    await sleep(((res.body?.retryAfterSec ?? COOLDOWN_SEC) + 1) * 1000);
    res = await call('POST', '/api/submissions', { token, body });
  }
  return res;
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
  const submitted = await submitting(alice, {
    problemSlug: slug,
    language: 'CPP',
    sourceCode: '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
  });
  check('submit → 202 QUEUED',
    submitted.status === 202 && submitted.body?.status === 'QUEUED', submitted);

  const judged = await waitForVerdict(submitted.body.id, alice);
  check('correct solution judged AC on every case',
    judged?.verdict === 'AC' && judged.results?.every((r) => r.verdict === 'AC'), judged);
  check('per-testcase rows never carry hidden input/expected',
    judged?.results?.every((r) => r.input == null || r.ord === undefined) !== false &&
    !JSON.stringify(judged?.results ?? []).includes('100 200'), judged?.results);

  section('Rejudge (FR-27)');
  // Rejudge is in-place: same submission id, results wiped, row back to QUEUED. Nothing else
  // executes this path — there is no unit test for it, and a browser pass cannot assert the reset.
  const rejudgeAsUser = await call('POST', `/api/submissions/${submitted.body.id}/rejudge`, { token: alice });
  check('contestant cannot rejudge → 403', rejudgeAsUser.status === 403, rejudgeAsUser);

  const rejudgeMissing = await call('POST', '/api/submissions/99999999/rejudge', { token: admin });
  check('rejudging a submission that does not exist → 404', rejudgeMissing.status === 404, rejudgeMissing);

  const rejudged = await call('POST', `/api/submissions/${submitted.body.id}/rejudge`, { token: admin });
  check('ADMIN rejudge → 202, same id, back to QUEUED',
    rejudged.status === 202 &&
    rejudged.body?.id === submitted.body.id &&
    rejudged.body?.status === 'QUEUED', rejudged);

  const rejudgedVerdict = await waitForVerdict(submitted.body.id, alice);
  check('the rejudged submission lands on the same verdict',
    rejudgedVerdict?.verdict === 'AC', { verdict: rejudgedVerdict?.verdict });
  check('rejudge rebuilt the per-testcase rows rather than doubling them',
    rejudgedVerdict?.results?.length === 3, rejudgedVerdict?.results?.length);

  section('Rate limit (cooldown)');
  // This section needs a clean window, and it used to get one by accident: locally, judging the
  // submission above took longer than the cooldown. On a CI runner judging finished in ~1s, the
  // accepted submission above was still inside the window, and the first submit here came back 429
  // with retryAfterSec 9. Wait the window out explicitly rather than depend on judging being slow.
  console.log(`  ...waiting ${COOLDOWN_SEC + 1}s to clear the cooldown from the judged submission`);
  await sleep((COOLDOWN_SEC + 1) * 1000);

  // Two calls back-to-back from here, no waiting between them — otherwise this measures nothing.
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
    !publicList.body?.content?.some((p) => p.slug === slug), publicList.body?.totalElements);

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

  section('Verdict matrix (FR-6, FR-7, FR-9)');
  // Until this section existed the judge was only ever proven on the happy path: every e2e
  // submission was AC. WA/TLE/MLE/RE each come from a DIFFERENT branch of VerdictEvaluator, and a
  // change to the sandbox can break one while leaving AC green. One seed user per verdict, so the
  // per-user cooldown never has to be slept off.
  const vSlug = `e2e-verdicts-${RUN}`;
  const madeV = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: vSlug, title: `Verdict Matrix ${RUN}`, statement: 'Print 1.',
      difficulty: 800,
      timeLimitMs: 1000,
      // 64 MB, comfortably under the 256 MB container cap, so MLE is decided by the MEASURED peak
      // RSS rule rather than by the container OOM-killing the process.
      memLimitKb: 65536,
      judgeMode: 'EXACT'
    }
  });
  check('verdict-matrix problem created → 201', madeV.status === 201, madeV);

  const vCases = await call('POST', `/api/problems/${vSlug}/testcases/bulk`, {
    token: setter,
    body: {
      mode: 'REPLACE',
      cases: [
        { ord: 1, kind: 'SAMPLE', input: '1\n', expected: '1\n', points: 0 },
        { ord: 2, kind: 'HIDDEN', input: '1\n', expected: '1\n', points: 0 }
      ]
    }
  });
  check('verdict-matrix test cases → 201', vCases.status === 201, vCases);

  const bob = await login('bob');
  const carol = await login('carol');
  const dan = await login('dan');
  const erin = await login('erin');

  async function verdictOf(token, sourceCode, language = 'CPP') {
    const res = await call('POST', '/api/submissions', {
      token, body: { problemSlug: vSlug, language, sourceCode }
    });
    if (res.status !== 202) {
      return { status: res.status, body: res.body };
    }
    return await waitForVerdict(res.body.id, token);
  }

  const wa = await verdictOf(bob, '#include <iostream>\nint main(){std::cout<<"2\\n";}');
  check('wrong output → WA', wa?.verdict === 'WA', { verdict: wa?.verdict });
  check('WA stops at the first failing case (FR-6: no rows past the break)',
    wa?.results?.length === 1 && wa.results[0].verdict === 'WA', wa?.results);

  const tle = await verdictOf(carol,
    '#include <iostream>\nint main(){volatile long x=0;while(1){x++;}}');
  check('never terminates → TLE (hard wall-clock kill)', tle?.verdict === 'TLE',
    { verdict: tle?.verdict, timeMs: tle?.timeMs });

  const mle = await verdictOf(dan,
    '#include <iostream>\n#include <cstdlib>\nint main(){size_t n=192ul*1024*1024;'
    + 'char*p=(char*)malloc(n);for(size_t i=0;i<n;i+=4096)p[i]=1;std::cout<<"1\\n";}');
  check('peak RSS over the problem limit → MLE', mle?.verdict === 'MLE',
    { verdict: mle?.verdict, memKb: mle?.memKb });

  const re = await verdictOf(erin, 'int main(){return 3;}');
  check('non-zero exit → RE', re?.verdict === 'RE', { verdict: re?.verdict });

  const ce = await verdictOf(admin, 'int main(){ this is not c++ }');
  check('does not compile → CE', ce?.verdict === 'CE', { verdict: ce?.verdict });
  check('CE carries the compiler output and no test-case rows (FR-9)',
    typeof ce?.compileLog === 'string' && ce.compileLog.length > 0 &&
    (ce?.results?.length ?? 0) === 0, { log: ce?.compileLog?.slice(0, 120), rows: ce?.results?.length });

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
  check('solve stats → 200', stats.status === 200, stats.body);

  check('stats carry the contract fields and nothing else',
    JSON.stringify(Object.keys(stats.body ?? {}).sort())
      === JSON.stringify(['accuracy', 'attempted', 'byVerdict', 'solved']),
    Object.keys(stats.body ?? {}));

  const tally = Object.values(stats.body?.byVerdict ?? {}).reduce((a, b) => a + b, 0);
  check('byVerdict sums to attempted (so nothing unjudged is counted)',
    tally === stats.body?.attempted, { tally, attempted: stats.body?.attempted });

  // The shipped version divided distinct problems solved by total submissions, mixing units.
  const accepted = stats.body?.byVerdict?.AC ?? 0;
  const expectedAccuracy = stats.body.attempted === 0
    ? 0
    : Math.round((accepted / stats.body.attempted) * 10_000) / 10_000;
  check('accuracy is accepted submissions over attempted, not problems over submissions',
    stats.body?.accuracy === expectedAccuracy,
    { accuracy: stats.body?.accuracy, expectedAccuracy, accepted, attempted: stats.body?.attempted });

  check('solved never exceeds accepted submissions',
    stats.body?.solved <= accepted, { solved: stats.body?.solved, accepted });

  const ghostStats = await call('GET', `/api/users/ghost-${RUN}/stats`, { token: alice });
  check('unknown handle → 404 (not a 500 from an unmapped exception)',
    ghostStats.status === 404, ghostStats);


  section('Solve stats do not leak a live contest (FR-25 x FR-18)');
  // The one check that actually proves the contest-end filter, because the filter lives in SQL and
  // every backend test mocks its repositories. A rising solved count during a freeze tells a rival
  // exactly what the standings board is withholding, so an aggregate is a side channel like any
  // other. A fresh problem is used rather than the one above, which by now has submissions and so
  // is no longer eligible for a contest.
  const freezeSlug = `e2e-freeze-${RUN}`;
  const freezeProblem = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: freezeSlug, title: 'E2E Freeze Sum', statement: 'Read two integers. Output their sum.',
      difficulty: 800, timeLimitMs: 2000, memLimitKb: 262144, judgeMode: 'EXACT',
      // draft = archived and unpublished. A contest may only use a problem that has never been
      // publicly visible, and published_at is stamped on creation otherwise, never cleared.
      draft: true
    }
  });
  check('fresh problem for the live contest → 201', freezeProblem.status === 201, freezeProblem);

  const freezeCases = await call('POST', `/api/problems/${freezeSlug}/testcases/bulk`, {
    token: setter,
    body: { mode: 'REPLACE', cases: [{ ord: 1, kind: 'SAMPLE', input: '2 3\n', expected: '5\n', points: 0 }] }
  });
  check('its test case → 201', freezeCases.status === 201, freezeCases);

  const before = await call('GET', '/api/users/alice/stats', { token: alice });
  check('baseline stats readable', before.status === 200, before);

  // Starts a minute ago, ends in an hour: live right now, so nothing in it may count yet.
  const liveContest = await call('POST', '/api/contests', {
    token: admin,
    body: {
      title: `E2E Live ${RUN}`,
      startsAt: new Date(Date.now() - 60_000).toISOString(),
      endsAt: new Date(Date.now() + 3_600_000).toISOString(),
      freezeMin: 0,
      scoring: 'ICPC',
      problems: [{ problemId: freezeProblem.body?.id, label: 'A', ord: 1 }]
    }
  });
  check('live contest created → 201', liveContest.status === 201, liveContest);

  const joined = await call('POST', `/api/contests/${liveContest.body?.id}/register`, {
    token: alice, body: { virtual: false }
  });
  check('alice registers → 201', joined.status === 201, joined);

  await sleep(COOLDOWN_SEC * 1000);
  const inContest = await call('POST', '/api/submissions', {
    token: alice,
    body: {
      problemSlug: freezeSlug, language: 'CPP',
      sourceCode: '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
    }
  });
  check('in-contest submission accepted → 202', inContest.status === 202, inContest);

  const contestVerdict = await waitForVerdict(inContest.body?.id, alice);
  check('it really was judged AC (so only the filter can be hiding it)',
    contestVerdict?.verdict === 'AC', { verdict: contestVerdict?.verdict });

  const after = await call('GET', '/api/users/alice/stats', { token: alice });
  check('attempted did NOT rise for a contest still running',
    after.body?.attempted === before.body?.attempted,
    { before: before.body?.attempted, after: after.body?.attempted });
  check('solved did NOT rise for a contest still running',
    after.body?.solved === before.body?.solved,
    { before: before.body?.solved, after: after.body?.solved });

  // The filter is specific to aggregates: your own history is yours to read in full, live or not.
  const ownRows = await call('GET', `/api/submissions?contest=${liveContest.body?.id}`, { token: alice });
  check('the same submission IS visible in the owner\'s own history',
    ownRows.status === 200 && (ownRows.body?.content?.length ?? 0) >= 1, ownRows.body);

  // ⚠ THE CHECK ABOVE IS ALSO THE CONTEST-ATTRIBUTION REGRESSION, so read a failure that way.
  // Nothing in this file sends a contestId any more — the server derives it from the caller's
  // registration and the contest window. This query filters on `s.contest_id = :contestId`, so a
  // submission recorded as practice cannot appear in it. That is exactly the bug this replaced:
  // the browser never sent the field, the service copied it straight from the request, and every
  // submission made in a live round through the UI scored nothing while every e2e check stayed
  // green because this script used to send it explicitly.
  check('it was attributed to the contest WITHOUT the client naming one (derived server-side)',
    (ownRows.body?.content ?? []).some((r) => r.id === inContest.body?.id),
    { submissionId: inContest.body?.id, rows: ownRows.body?.content?.map((r) => r.id) });

  section('Standings board over HTTP (FR-18, FR-19, FR-21)');
  // StandingsCalculator has a thorough unit suite, but nothing exercised
  // controller -> service -> native query, and the freeze has leaked three times in this project's
  // history. Reuses the live contest above, where alice already has one judged AC.
  const board = await call('GET', `/api/contests/${liveContest.body?.id}/standings`, { token: alice });
  check('standings → 200 with the board envelope',
    board.status === 200 && typeof board.body?.frozen === 'boolean' &&
    Array.isArray(board.body?.rows), board.body);
  check('no freeze window configured → not frozen', board.body?.frozen === false, board.body?.frozen);

  const aliceRow = (board.body?.rows ?? []).find((r) => r.handle === 'alice');
  check('the registered participant is on the board', !!aliceRow, board.body?.rows);
  check('her accepted problem counts as solved, with a cell per contest problem',
    aliceRow?.solved === 1 && aliceRow?.problems?.length === 1, aliceRow);
  check('the solved cell carries a minute mark and the first-solve flag (FR-19)',
    aliceRow?.problems?.[0]?.solvedAtMin != null && aliceRow.problems[0].firstSolve === true,
    aliceRow?.problems?.[0]);
  check('a clean first solve carries no penalty beyond its time (FR-19)',
    aliceRow?.penalty === aliceRow?.problems?.[0]?.solvedAtMin, {
      penalty: aliceRow?.penalty, at: aliceRow?.problems?.[0]?.solvedAtMin });

  const boardUnknown = await call('GET', '/api/contests/99999999/standings', { token: alice });
  check('standings for a contest that does not exist → 404', boardUnknown.status === 404, boardUnknown);

  // --- and now the same board under an ACTIVE freeze -----------------------------------------
  const frSlug = `e2e-frozen-${RUN}`;
  const frProblem = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: frSlug, title: 'E2E Frozen Sum', statement: 'Read two integers. Output their sum.',
      difficulty: 800, timeLimitMs: 2000, memLimitKb: 262144, judgeMode: 'EXACT', draft: true
    }
  });
  await call('POST', `/api/problems/${frSlug}/testcases/bulk`, {
    token: setter,
    body: { mode: 'REPLACE', cases: [{ ord: 1, kind: 'SAMPLE', input: '2 3\n', expected: '5\n', points: 0 }] }
  });
  // Started an hour ago, ends in an hour, freeze covers the last 90 minutes => frozen right now.
  const frozenContest = await call('POST', '/api/contests', {
    token: admin,
    body: {
      title: `E2E Frozen ${RUN}`,
      startsAt: new Date(Date.now() - 3_600_000).toISOString(),
      endsAt: new Date(Date.now() + 3_600_000).toISOString(),
      freezeMin: 90,
      scoring: 'ICPC',
      problems: [{ problemId: frProblem.body?.id, label: 'A', ord: 1 }]
    }
  });
  check('contest with an active freeze created → 201', frozenContest.status === 201, frozenContest);

  // bob, not alice: this is the last submission of the run, and leaving alice's cooldown spent
  // here is what stops the script being safe to run twice in a row.
  const frJoined = await call('POST', `/api/contests/${frozenContest.body?.id}/register`, {
    token: bob, body: { virtual: false }
  });
  check('bob registers for the frozen contest → 201', frJoined.status === 201, frJoined);

  const frSub = await submitting(bob, {
    problemSlug: frSlug, language: 'CPP',
    sourceCode: '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
  });
  check('in-freeze submission accepted → 202', frSub.status === 202, frSub);
  const frVerdict = await waitForVerdict(frSub.body?.id, bob);
  check('it really was judged AC (so only the freeze can be hiding it)',
    frVerdict?.verdict === 'AC', { verdict: frVerdict?.verdict });

  const frozenView = await call('GET', `/api/contests/${frozenContest.body?.id}/standings`, { token: bob });
  const frozenRow = (frozenView.body?.rows ?? []).find((r) => r.handle === 'bob');
  check('the board reports the freeze window as active (FR-21)',
    frozenView.body?.frozen === true, frozenView.body?.frozen);
  check('a contestant does NOT see the post-freeze solve',
    frozenRow?.solved === 0 && frozenRow?.problems?.[0]?.solvedAtMin == null, frozenRow);
  check('the hidden activity is flagged so the cell renders "?" rather than looking untouched',
    frozenRow?.problems?.[0]?.frozen === true, frozenRow?.problems?.[0]);

  const privilegedView = await call('GET', `/api/contests/${frozenContest.body?.id}/standings`, { token: admin });
  const privilegedRow = (privilegedView.body?.rows ?? []).find((r) => r.handle === 'bob');
  check('ADMIN sees through the freeze to the real board',
    privilegedRow?.solved === 1 && privilegedRow?.problems?.[0]?.solvedAtMin != null, privilegedRow);
  check('the window is still reported as frozen to the privileged viewer too',
    privilegedView.body?.frozen === true, privilegedView.body?.frozen);

  section('Contest calls and problem proposals');
  // The proposal workflow is a whole feature with no e2e coverage at all: nothing here had ever
  // been called, so a broken query would have reached the demo.
  const poolAsUser = await call('GET', '/api/contests/eligible-problems', { token: alice });
  check('a contestant cannot browse the problem pool → 403', poolAsUser.status === 403, poolAsUser);

  const pool = await call('GET', '/api/contests/eligible-problems', { token: setter });
  check('staff can browse the eligible problem pool → 200',
    pool.status === 200 && Array.isArray(pool.body), pool.body);

  // A brand-new draft problem, so it is genuinely eligible (never public, never used).
  const propSlug = `e2e-proposed-${RUN}`;
  const propProblem = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: propSlug, title: 'E2E Proposed', statement: 'x', difficulty: 900,
      timeLimitMs: 2000, memLimitKb: 262144, judgeMode: 'EXACT', draft: true
    }
  });
  check('draft problem for proposing → 201', propProblem.status === 201, propProblem);
  check('the eligible pool really is queried, and the new draft is in it',
    (await call('GET', '/api/contests/eligible-problems', { token: setter }))
      .body?.some((p) => p.slug === propSlug), propSlug);

  // A future contest, so accepting a proposal is legal (the live ones above have started). It has
  // no problems yet, which the API only permits when it is created open for proposals.
  const noProblemsClosed = await call('POST', '/api/contests', {
    token: admin,
    body: {
      title: `E2E Empty ${RUN}`,
      startsAt: new Date(Date.now() + 86_400_000).toISOString(),
      endsAt: new Date(Date.now() + 90_000_000).toISOString(),
      freezeMin: 0, scoring: 'ICPC', problems: []
    }
  });
  check('a contest with neither problems nor open calls → 400',
    noProblemsClosed.status === 400, noProblemsClosed);

  const callContest = await call('POST', '/api/contests', {
    token: admin,
    body: {
      title: `E2E Calls ${RUN}`,
      startsAt: new Date(Date.now() + 86_400_000).toISOString(),
      endsAt: new Date(Date.now() + 90_000_000).toISOString(),
      freezeMin: 0, scoring: 'ICPC', problems: [], callsOpen: true
    }
  });
  check('a problem-less contest created for proposals → 201', callContest.status === 201, callContest);
  const cid = callContest.body?.id;

  const closed = await call('POST', `/api/contests/${cid}/calls`, { token: admin, body: { open: false } });
  check('ADMIN closes calls → 200', closed.status === 200, closed);

  const proposeClosed = await call('POST', `/api/contests/${cid}/proposals`, {
    token: setter, body: { problemId: propProblem.body?.id, note: 'too early' }
  });
  check('proposing while calls are closed → 409', proposeClosed.status === 409, proposeClosed);

  const proposeAsUser = await call('POST', `/api/contests/${cid}/proposals`, {
    token: alice, body: { problemId: propProblem.body?.id, note: 'nope' }
  });
  check('a contestant cannot propose → 403', proposeAsUser.status === 403, proposeAsUser);

  const opened = await call('POST', `/api/contests/${cid}/calls`, { token: admin, body: { open: true } });
  check('ADMIN reopens calls → 200', opened.status === 200, opened);

  const proposed = await call('POST', `/api/contests/${cid}/proposals`, {
    token: setter, body: { problemId: propProblem.body?.id, note: 'nice warmup' }
  });
  check('setter proposes a problem → 201', proposed.status === 201, proposed);
  check('the proposal echoes the problem it names', proposed.body?.slug === propSlug, proposed.body);

  const dup = await call('POST', `/api/contests/${cid}/proposals`, {
    token: setter, body: { problemId: propProblem.body?.id, note: 'again' }
  });
  check('proposing the same problem twice → 409', dup.status === 409, dup);

  const mine = await call('GET', '/api/contests/my-proposals', { token: setter });
  check('the setter sees their own proposal in my-proposals',
    mine.status === 200 && mine.body?.some((x) => x.id === proposed.body?.id), mine.body);

  const listed = await call('GET', `/api/contests/${cid}/proposals`, { token: admin });
  check('ADMIN lists the contest proposals',
    listed.status === 200 && listed.body?.some((x) => x.id === proposed.body?.id), listed.body);

  const acceptedProposal = await call('POST', `/api/contests/${cid}/proposals/${proposed.body?.id}/accept`, {
    token: admin, body: { label: 'A' }
  });
  check('ADMIN accepts the proposal → 200', acceptedProposal.status === 200, acceptedProposal);
  check('the accepted problem is now on the contest',
    (await call('GET', `/api/contests/${cid}`, { token: admin }))
      .body?.problems?.some((p) => p.label === 'A'), null);

  section('Search, filter and tags (FR-14, FR-15)');
  // The list query is the most complex native SQL in the backend — CSV tag splitting, AND-tag
  // semantics, a literal-not-wildcard search — and every backend test mocks the repository, so
  // nothing but this section ever executes it.
  const tagDp = `e2e-dp-${RUN}`;
  const tagGraph = `e2e-graph-${RUN}`;
  const slugA = `e2e-find-alpha-${RUN}`;
  const slugB = `e2e-find-beta-${RUN}`;

  const madeA = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: slugA, title: `Zircon${RUN} Alpha`, statement: 'x', difficulty: 1200,
      tags: [tagDp, tagGraph]
    }
  });
  const madeB = await call('POST', '/api/problems', {
    token: setter,
    body: {
      slug: slugB, title: `Zircon${RUN} Beta`, statement: 'x', difficulty: 900,
      tags: [tagDp]
    }
  });
  check('two tagged problems created → 201',
    madeA.status === 201 && madeB.status === 201, { a: madeA.status, b: madeB.status });

  const vocab = await call('GET', '/api/tags');
  check('GET /api/tags is public and carries the new tags',
    vocab.status === 200 && vocab.body?.includes(tagDp) && vocab.body?.includes(tagGraph),
    vocab.body?.length);

  const slugsOf = (res) => (res.body?.content ?? []).map((x) => x.slug);

  const byText = await call('GET', `/api/problems?q=Zircon${RUN}%20Alpha`);
  check('q matches the title, and only the matching problem',
    slugsOf(byText).includes(slugA) && !slugsOf(byText).includes(slugB), slugsOf(byText));

  const bySlugText = await call('GET', `/api/problems?q=find-beta-${RUN}`);
  check('q matches the slug too', slugsOf(bySlugText).includes(slugB), slugsOf(bySlugText));

  const literal = await call('GET', '/api/problems?q=%25');
  check('a literal % in q is not a wildcard',
    literal.status === 200 && literal.body?.content?.length === 0, literal.body?.totalElements);

  const oneTag = await call('GET', `/api/problems?tags=${tagDp}`);
  check('one tag returns every problem carrying it',
    slugsOf(oneTag).includes(slugA) && slugsOf(oneTag).includes(slugB), slugsOf(oneTag));

  const bothTags = await call('GET', `/api/problems?tags=${tagDp}&tags=${tagGraph}`);
  check('two tags are AND, not OR',
    slugsOf(bothTags).includes(slugA) && !slugsOf(bothTags).includes(slugB), slugsOf(bothTags));

  const absentTag = await call('GET', `/api/problems?tags=${tagDp}&tags=e2e-absent-${RUN}`);
  check('a tag nothing carries empties the result, rather than erroring',
    absentTag.status === 200 && !slugsOf(absentTag).includes(slugA), absentTag.body?.totalElements);

  const minDiff = await call('GET', `/api/problems?minDifficulty=1000&q=Zircon${RUN}`);
  check('minDifficulty excludes the easier problem',
    slugsOf(minDiff).includes(slugA) && !slugsOf(minDiff).includes(slugB), slugsOf(minDiff));

  const maxDiff = await call('GET', `/api/problems?maxDifficulty=1000&q=Zircon${RUN}`);
  check('maxDifficulty excludes the harder problem',
    slugsOf(maxDiff).includes(slugB) && !slugsOf(maxDiff).includes(slugA), slugsOf(maxDiff));

  const band = await call('GET', `/api/problems?minDifficulty=1000&maxDifficulty=1100&q=Zircon${RUN}`);
  check('a band that spans neither returns neither',
    !slugsOf(band).includes(slugA) && !slugsOf(band).includes(slugB), slugsOf(band));

  const rowA = (oneTag.body?.content ?? []).find((x) => x.slug === slugA);
  check('list rows carry their tags (FR-14 list contract)',
    Array.isArray(rowA?.tags) && rowA.tags.includes(tagDp) && rowA.tags.includes(tagGraph), rowA);

  const unfiltered = await call('GET', '/api/problems');
  check('omitting every filter still returns the first page of the catalogue',
    unfiltered.status === 200 && slugsOf(unfiltered).includes(slugA), unfiltered.body?.totalElements);

  // --- paging (FR-15) ---------------------------------------------------------------------
  const pageOne = await call('GET', `/api/problems?q=Zircon${RUN}&page=0&size=1`);
  check('size is honoured and the envelope reports the true total',
    pageOne.body?.content?.length === 1 && pageOne.body?.totalElements === 2 &&
    pageOne.body?.page === 0 && pageOne.body?.size === 1, pageOne.body);

  const pageTwo = await call('GET', `/api/problems?q=Zircon${RUN}&page=1&size=1`);
  check('the second page is a different problem, not a repeat of the first',
    pageTwo.body?.content?.length === 1 &&
    pageTwo.body.content[0].slug !== pageOne.body.content[0].slug, pageTwo.body);

  const pastEnd = await call('GET', `/api/problems?q=Zircon${RUN}&page=9&size=1`);
  check('a page past the end is empty but still reports the total',
    pastEnd.status === 200 && pastEnd.body?.content?.length === 0 &&
    pastEnd.body?.totalElements === 2, pastEnd.body);

  const badListSize = await call('GET', '/api/problems?size=500');
  check('problem-list size above the cap → 400', badListSize.status === 400, badListSize);

  const badListPage = await call('GET', '/api/problems?page=-1');
  check('problem-list negative page → 400', badListPage.status === 400, badListPage);

  const commaTag = await call('POST', '/api/problems', {
    token: setter,
    body: { slug: `e2e-comma-${RUN}`, title: 'x', statement: 'x', tags: ['a,b'] }
  });
  check('a comma inside a tag is refused → 400 (it would split the CSV filter)',
    commaTag.status === 400, commaTag);

  // These two carry no submissions, so the delete guard lets them go.
  const delA = await call('DELETE', `/api/problems/${slugA}`, { token: admin });
  const delB = await call('DELETE', `/api/problems/${slugB}`, { token: admin });
  check('the search fixtures delete cleanly',
    delA.status === 204 && delB.status === 204, { a: delA.status, b: delB.status });

  section('Cleanup');
  // The problem now has submissions, so it cannot be hard-deleted — that is the guard working,
  // not a failure. It is left archived and out of the public list, named with this run's suffix.
  const restored = await call('POST', `/api/problems/${slug}/unarchive`, { token: setter });
  check('unarchive works (leaving the run artifact visible to staff only)',
    restored.status === 200, restored);
  const reArchived = await call('POST', `/api/problems/${slug}/archive`, { token: setter });
  check('re-archived so the public list stays clean', reArchived.status === 200, reArchived);

  // The verdict-matrix problem has submissions too, so it is archived rather than deleted.
  const vArchived = await call('POST', `/api/problems/${vSlug}/archive`, { token: setter });
  check('verdict-matrix problem archived out of the public list', vArchived.status === 200, vArchived);

  console.log(`\n${passed} checks passed.`);
  console.log(`Left behind: archived problem "${slug}" (it has submissions, so it cannot be deleted),`);
  console.log(`             plus draft problem "${freezeSlug}" and the live contest holding it —`);
  console.log('             that contest ends an hour from now and will publish the problem then.');
}

main().catch((err) => {
  console.error('\nE2E aborted:', err?.message ?? err);
  console.error('Is the stack up? docker compose up --build -d');
  process.exit(1);
});
