import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ContestProblem } from '../../../core/models/contest.model';
import { ProblemCell, Standings, StandingsRow } from '../../../core/models/standings.model';

/**
 * Presentational ICPC standings board. Renders columns `# | Who | Solved | Penalty | <one per
 * problem>`, a freeze banner while `standings.frozen`, and the current user's row highlighted.
 * All data comes from inputs — the contest-detail page owns loading + the live WS stream.
 *
 * <p>OnPush: every input is replaced wholesale rather than mutated — {@link StandingsLiveComponent}
 * assigns a fresh board object on each WS frame — so reference checks see every update. Without it
 * the default checker re-ran `cellClass`/`cellMain`/`cellSub` for every cell of every row on each
 * cycle, and a contest board is the one screen where those multiply.
 */
@Component({
  selector: 'app-standings-board',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './standings-board.component.html',
  styleUrls: ['./standings-board.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StandingsBoardComponent {
  @Input() standings?: Standings;
  @Input() problems: ContestProblem[] = [];
  @Input() myHandle: string | null = null;

  /** Rows are keyed by handle: a rank change reorders the board without rebuilding every row. */
  trackRow(_index: number, row: StandingsRow): string {
    return row.handle;
  }

  /** Problem columns are keyed by their contest label (A, B, C...), which is unique per contest. */
  trackProblem(_index: number, problem: ContestProblem): string {
    return problem.label;
  }

  /** Cells have no identity of their own; their column position is their identity. */
  trackCell(index: number): number {
    return index;
  }

  /**
   * Visual class for a problem cell by its state. A first solve is a MODIFIER on the solved state,
   * not a state of its own — the cell still has to read as accepted first.
   */
  cellClass(cell: ProblemCell): string {
    if (cell.solvedAtMin != null) {
      return cell.firstSolve ? 'c-solved c-first' : 'c-solved';
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
