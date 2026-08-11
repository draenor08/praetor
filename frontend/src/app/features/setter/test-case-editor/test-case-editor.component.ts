import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TestCaseRow } from '../../../core/models/problem.model';

/** Delimiters for the paste box. Documented in the template — no hidden syntax. */
const CASE_SEPARATOR = /^==+$/m;
const IO_SEPARATOR = /^--+$/m;

/**
 * Test cases for one problem.
 *
 * <p>Two save paths, kept explicit rather than hidden behind a mode dropdown, because they differ
 * in how destructive they are:
 * <ul>
 *   <li><b>Replace all</b> — the table becomes the complete set. This is the only way to edit an
 *       existing case (the API has no per-case update), and it deletes the old rows, so it asks
 *       for confirmation and warns that earlier verdicts were judged against what is being
 *       discarded.</li>
 *   <li><b>Append new rows</b> — sends only the rows added in this session and leaves saved cases
 *       untouched.</li>
 * </ul>
 *
 * <p>While a contest using the problem is running the backend refuses both, so the UI disables
 * them and says why instead of letting the setter discover it through a 409.
 */
@Component({
  selector: 'app-test-case-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './test-case-editor.component.html',
  styleUrls: ['./test-case-editor.component.scss']
})
export class TestCaseEditorComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  slug = '';
  rows: TestCaseRow[] = [];

  loading = true;
  saving = false;
  loadError = '';
  saveError = '';
  notice = '';
  rowErrors: string[] = [];

  frozen = false;
  confirmingReplace = false;

  showPaste = false;
  pasteText = '';

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug')!;

    this.api.getTestCases(this.slug).subscribe({
      next: (cases) => {
        this.rows = cases.map((c) => ({ ...c }));
        this.loading = false;
      },
      error: (err) => {
        this.loadError = err?.error?.error ?? 'Could not load test cases.';
        this.loading = false;
      }
    });

    this.api.getProblemUsage(this.slug).subscribe({
      next: (usage) => (this.frozen = usage.inLiveContest),
      error: () => (this.frozen = false)
    });
  }

  get sampleCount(): number {
    return this.rows.filter((r) => r.kind === 'SAMPLE').length;
  }

  get newRows(): TestCaseRow[] {
    return this.rows.filter((r) => r.id === undefined);
  }

  addRow(kind: 'SAMPLE' | 'HIDDEN'): void {
    const nextOrd = this.rows.reduce((max, r) => Math.max(max, r.ord), 0) + 1;
    this.rows.push({ ord: nextOrd, kind, input: '', expected: '', points: 0 });
    this.notice = '';
  }

  removeRow(index: number): void {
    this.rows.splice(index, 1);
    this.confirmingReplace = false;
  }

  /** Makes ord 1..n in the table's current order — the judge runs cases in ord order. */
  renumber(): void {
    this.rows.forEach((row, i) => (row.ord = i + 1));
    this.notice = 'Renumbered 1…' + this.rows.length + '. Not saved yet.';
  }

  // --- paste box ----------------------------------------------------------

  get pastePreview(): { count: number; error: string | null } {
    if (!this.pasteText.trim()) {
      return { count: 0, error: null };
    }
    const blocks = this.pasteText.split(CASE_SEPARATOR).filter((b) => b.trim().length > 0);
    for (const block of blocks) {
      if (!IO_SEPARATOR.test(block)) {
        return { count: 0, error: 'Every case needs a line of -- between input and expected.' };
      }
    }
    return { count: blocks.length, error: null };
  }

  appendPasted(): void {
    const preview = this.pastePreview;
    if (preview.error || preview.count === 0) {
      return;
    }
    let nextOrd = this.rows.reduce((max, r) => Math.max(max, r.ord), 0) + 1;

    for (const block of this.pasteText.split(CASE_SEPARATOR).filter((b) => b.trim().length > 0)) {
      const [input, expected] = block.split(IO_SEPARATOR);
      this.rows.push({
        ord: nextOrd++,
        kind: 'HIDDEN',
        input: (input ?? '').replace(/^\n+|\n+$/g, ''),
        expected: (expected ?? '').replace(/^\n+|\n+$/g, ''),
        points: 0
      });
    }

    this.notice = `Added ${preview.count} case(s) as HIDDEN. Review, then save.`;
    this.pasteText = '';
    this.showPaste = false;
  }

  // --- saving -------------------------------------------------------------

  /** Mirrors TestCaseService.validateRequest so the setter sees problems before sending. */
  private validate(cases: TestCaseRow[]): boolean {
    const errors: string[] = [];
    if (cases.length === 0) {
      errors.push('Add at least one test case.');
    }

    const seen = new Set<number>();
    for (const row of cases) {
      const label = `Case ${row.ord}`;
      if (!row.ord || row.ord < 1) {
        errors.push(`${label}: order must be 1 or greater.`);
      } else if (seen.has(row.ord)) {
        errors.push(`${label}: duplicate order — each case needs a distinct number.`);
      } else {
        seen.add(row.ord);
      }
      if (row.kind !== 'SAMPLE' && row.kind !== 'HIDDEN') {
        errors.push(`${label}: kind must be SAMPLE or HIDDEN.`);
      }
      if (row.points === null || row.points === undefined || row.points < 0) {
        errors.push(`${label}: points cannot be negative.`);
      }
    }

    this.rowErrors = errors;
    return errors.length === 0;
  }

  askReplace(): void {
    this.saveError = '';
    if (!this.validate(this.rows)) {
      return;
    }
    this.confirmingReplace = true;
  }

  cancelReplace(): void {
    this.confirmingReplace = false;
  }

  replaceAll(): void {
    this.confirmingReplace = false;
    this.send('REPLACE', this.rows);
  }

  appendNew(): void {
    this.saveError = '';
    const additions = this.newRows;
    if (!this.validate(this.rows) || additions.length === 0) {
      return;
    }
    this.send('APPEND', additions);
  }

  private send(mode: 'APPEND' | 'REPLACE', cases: TestCaseRow[]): void {
    this.saving = true;
    this.saveError = '';

    this.api.saveTestCases(this.slug, { mode, cases }).subscribe({
      next: (saved) => {
        this.rows = saved.map((c) => ({ ...c }));
        this.saving = false;
        this.notice = mode === 'REPLACE'
          ? `Saved — ${saved.length} test case(s) now define this problem.`
          : `Appended ${cases.length} case(s).`;
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.error ?? err?.error?.message ?? 'Could not save test cases.';
      }
    });
  }
}
