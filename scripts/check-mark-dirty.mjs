#!/usr/bin/env node
//
// PRAETOR — guard the OnPush change-detection convention.
//
// Every component in this app is `ChangeDetectionStrategy.OnPush`. Assigning a field from inside a
// `subscribe` callback is not one of the things that marks an OnPush component dirty, so a screen
// can silently stop updating while still compiling and still passing every unit test. The
// convention is therefore one `markDirty(this.cdr)` operator (core/rx/mark-dirty.ts) piped through
// every subscription — written that way specifically so a missing one can be found mechanically,
// which is what this script does.
//
//   node scripts/check-mark-dirty.mjs
//
// Exits non-zero and names file:line for any `.subscribe(` in an OnPush file whose statement does
// not pipe markDirty(). Three components legitimately call markForCheck() directly, because their
// state moves outside any stream: countdown (setInterval), rich-text (lazy KaTeX import),
// problem-detail (cooldown timer + Monaco load). Those are listed, not failed.

import { readFileSync } from 'node:fs';
import { readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const SRC = join(ROOT, 'frontend/src/app');

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (entry.name.endsWith('.ts')) out.push(full);
  }
  return out;
}

const onPush = [];
for (const file of walk(SRC).sort()) {
  const text = readFileSync(file, 'utf8');
  if (text.includes('ChangeDetectionStrategy.OnPush')) onPush.push({ file, text });
}

let subscriptions = 0;
const missing = [];
const explicit = [];

for (const { file, text } of onPush) {
  const direct = text.split('markForCheck(').length - 1;
  if (direct > 0) explicit.push({ file, direct });

  for (const match of text.matchAll(/\.subscribe\s*\(/g)) {
    subscriptions += 1;
    const at = match.index;
    // The statement this subscribe terminates: everything back to the previous statement boundary.
    const boundary = Math.max(
      text.lastIndexOf(';', at),
      text.lastIndexOf('{', at),
      text.lastIndexOf('}', at)
    );
    const statement = text.slice(boundary + 1, at);
    if (!statement.includes('markDirty(')) {
      missing.push({
        file,
        line: text.slice(0, at).split('\n').length,
        statement: statement.replace(/\s+/g, ' ').trim().slice(-110)
      });
    }
  }
}

console.log(`OnPush components : ${onPush.length}`);
console.log(`.subscribe( calls : ${subscriptions}`);
console.log(`missing markDirty : ${missing.length}`);

for (const { file, line, statement } of missing) {
  console.log(`\n  ${relative(ROOT, file)}:${line}`);
  console.log(`      …${statement}`);
}

if (explicit.length) {
  console.log('\nExplicit markForCheck (state moves outside a stream — expected, not a failure):');
  for (const { file, direct } of explicit) {
    console.log(`  ${relative(ROOT, file)} x${direct}`);
  }
}

if (missing.length) {
  console.log(
    '\nA subscription without markDirty() will render once and then go stale. ' +
      'Pipe markDirty(this.cdr) through it, or call markForCheck() and say why here.'
  );
  process.exit(1);
}
