import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss']
})
export class LandingComponent {
  features = [
    {
      title: 'Adaptive practice',
      description: 'Work through curated challenges that match your current skill level and growth goals.'
    },
    {
      title: 'Live contest flow',
      description: 'Join timed rounds, follow your standing, and review feedback right after each run.'
    },
    {
      title: 'Clear progress insights',
      description: 'Track your streaks, submissions, and problem history in a focused performance view.'
    }
  ];
}
