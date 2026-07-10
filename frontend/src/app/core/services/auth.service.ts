import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap, catchError, throwError } from 'rxjs';
import { TokenService } from './token.service';

const AUTH_API = '/api/auth/';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private tokenService = inject(TokenService);
  
  private isLoggedInSubject = new BehaviorSubject<boolean>(!!this.tokenService.getToken());
  isLoggedIn$ = this.isLoggedInSubject.asObservable();

  constructor() { }

  login(credentials: any): Observable<any> {
    return this.http.post(AUTH_API + 'login', credentials).pipe(
      tap((res: any) => {
        this.tokenService.setToken(res.token);
        this.tokenService.setUser(res.user);
        this.isLoggedInSubject.next(true);
      }),
      catchError(err => throwError(() => err))
    );
  }

  register(user: any): Observable<any> {
    return this.http.post(AUTH_API + 'register', user).pipe(
      tap((res: any) => {
        this.tokenService.setToken(res.token);
        this.tokenService.setUser(res.user);
        this.isLoggedInSubject.next(true);
      }),
      catchError(err => throwError(() => err))
    );
  }

  logout(): void {
    this.http.post(AUTH_API + 'logout', {}).subscribe({
      next: () => {},
      error: () => {}
    });
    this.tokenService.clear();
    this.isLoggedInSubject.next(false);
  }

  getMe(): Observable<any> {
    return this.http.get(AUTH_API + 'me').pipe(
      tap((user: any) => {
        this.tokenService.setUser(user);
      })
    );
  }
}
