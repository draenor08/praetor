import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { ProblemFilter, ProblemSummary } from '../../../core/models/problem.model';

/** Difficulty presets, so the common case is one click instead of typing two numbers. */
interface DifficultyBand {
  label: string;
  min: number | null;
  max: number | null;
}

/**
 * The problem list with search, difficulty and tag filters (FR-15).
 *
 * <p>Filtering is done by the server, not by narrowing an already-fetched array: the list the server
 * returns is the embargo-filtered one, and re-filtering a client-side copy would work only for as
 * long as the whole list fits in one response.
 */
@Component({
  selector: 'app-problem-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './problem-list.component.html',
  styleUrls: ['./problem-list.component.scss']
})
export class ProblemListComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);

  readonly bands: DifficultyBand[] = [
    { label: 'Any', min: null, max: null },
    { label: '≤ 1000', min: null, max: 1000 },
    { label: '1000 – 1600', min: 1000, max: 1600 },
    { label: '1600 – 2200', min: 1600, max: 2200 },
    { label: '2200 +', min: 2200, max: null }
  ];

  problems: ProblemSummary[] = [];
  allTags: string[] = [];
  loading = true;
  error = '';

  search = '';
  band: DifficultyBand = this.bands[0];
  selectedTags: string[] = [];

  /** Typing pushes here; the debounce keeps one keystroke from being one request. */
  private readonly changes = new Subject<void>();

  ngOnInit(): void {
    this.changes
      .pipe(
        debounceTime(250),
        // switchMap so a slow earlier response cannot land after a newer one and show stale rows.
        switchMap(() => this.api.getProblems(this.currentFilter()))
      )
      .subscribe({
        next: (list) => {
          this.problems = list;
          this.loading = false;
          this.error = '';
        },
        error: () => {
          this.error = 'Could not load problems.';
          this.loading = false;
        }
      });

    // Cosmetic: without the vocabulary the tag filter simply has no options to offer.
    this.api.getTags().subscribe({
      next: (tags) => (this.allTags = tags),
      error: () => undefined
    });

    this.reload();
  }

  ngOnDestroy(): void {
    this.changes.complete();
  }

  reload(): void {
    this.loading = true;
    this.changes.next();
  }

  toggleTag(tag: string): void {
    this.selectedTags = this.selectedTags.includes(tag)
      ? this.selectedTags.filter((t) => t !== tag)
      : [...this.selectedTags, tag];
    this.reload();
  }

  isTagSelected(tag: string): boolean {
    return this.selectedTags.includes(tag);
  }

  clear(): void {
    this.search = '';
    this.band = this.bands[0];
    this.selectedTags = [];
    this.reload();
  }

  get filtering(): boolean {
    return !!this.search.trim() || this.selectedTags.length > 0 || this.band !== this.bands[0];
  }

  private currentFilter(): ProblemFilter {
    return {
      q: this.search,
      minDifficulty: this.band.min,
      maxDifficulty: this.band.max,
      tags: this.selectedTags
    };
  }
}
