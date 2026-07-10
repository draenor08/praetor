import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { TokenService } from '../services/token.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const tokenService = inject(TokenService);

  return next(req).pipe(
    catchError((err) => {
      if (err.status === 401) {
        tokenService.clear();
        router.navigate(['/login']);
      } else if (err.status === 403) {
        router.navigate(['/']); // Redirect to home if forbidden
      }
      return throwError(() => err);
    })
  );
};
