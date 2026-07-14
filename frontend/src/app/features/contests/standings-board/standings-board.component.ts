import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ContestProblem } from '../../../core/models/contest.model';
import { ProblemCell, Standings } from '../../../core/models/standings.model';

/**
 * Presentational ICPC standings board. Renders columns `# | Who | Solved | Penalty | <one per
 * problem>`, a freeze banner while `standings.frozen`, and the current user's row highlighted.
 * All data comes from inputs — the contest-detail page owns loading + the live WS stream.
 */
@Component({
  selector: 'app-standings-board',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './standings-board.component.html',
  styleUrls: ['./standings-board.component.scss']
})
export class StandingsBoardComponent {
  @Input() standings?: Standings;
  @Input() problems: ContestProblem[] = [];
  @Input() myHandle: string | null = null;

  /** Visual class for a problem cell by its state. */
  cellClass(cell: ProblemCell): string {
    if (cell.solvedAtMin != null) {
      return 'c-solved';
    }
    if (cell.frozen) {
      return 'c-frozen';
    }
    if (cell.attempts > 0) {
      return 'c-fail';
    }
    return 'c-none';
  }

  /** Main glyph for a cell: +/(+n) when solved, ? when frozen, −n when failed, · when untouched. */
  cellMain(cell: ProblemCell): string {
    if (cell.solvedAtMin != null) {
      return cell.attempts > 0 ? `+${cell.attempts}` : '+';
    }
    if (cell.frozen) {
      return '?';
    }
    if (cell.attempts > 0) {
      return `−${cell.attempts}`;
    }
    return '·';
  }

  /** Sub-line: solve time when solved, tries hidden when frozen, blank otherwise. */
  cellSub(cell: ProblemCell): string {
    if (cell.solvedAtMin != null) {
      return `${cell.solvedAtMin} min`;
    }
    if (cell.frozen) {
      return `${cell.attempts} tries`;
    }
    return '';
  }
}
