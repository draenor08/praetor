import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-problems',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './problems.component.html',
  styleUrls: ['./problems.component.scss']
})
export class ProblemsComponent {
  searchTerm = '';
  selectedDifficulty = 'All';
  problems = [
    { title: 'Two Sum', difficulty: 'Easy', tags: ['Array', 'Hash Map'] },
    { title: 'Longest Substring Without Repeating Characters', difficulty: 'Medium', tags: ['String', 'Sliding Window'] },
    { title: 'Median of Two Sorted Arrays', difficulty: 'Hard', tags: ['Array', 'Binary Search'] },
    { title: 'Valid Parentheses', difficulty: 'Easy', tags: ['Stack', 'String'] },
    { title: 'Merge Intervals', difficulty: 'Medium', tags: ['Array', 'Sorting'] }
  ];

  get filteredProblems() {
    const term = this.searchTerm.toLowerCase();
    return this.problems.filter((problem) => {
      const matchesSearch = !term ||
        problem.title.toLowerCase().includes(term) ||
        problem.tags.some((tag) => tag.toLowerCase().includes(term));
      const matchesDifficulty = this.selectedDifficulty === 'All' || problem.difficulty === this.selectedDifficulty;
      return matchesSearch && matchesDifficulty;
    });
  }
}
