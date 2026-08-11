import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TokenService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'auth_user';

  constructor() { }

  public setToken(token: string): void {
    window.localStorage.removeItem(this.TOKEN_KEY);
    window.localStorage.setItem(this.TOKEN_KEY, token);
  }

  public getToken(): string | null {
    return window.localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Merges into the cached user instead of replacing it. Several endpoints describe the
   * same user with different subsets of fields (login/register return UserResponse,
   * /api/users/me adds rating), and a plain replace let the narrower payload drop fields
   * the shell and the standings self-row read back out of here.
   */
  public setUser(user: any): void {
    const merged = { ...(this.getUser() ?? {}), ...(user ?? {}) };
    window.localStorage.setItem(this.USER_KEY, JSON.stringify(merged));
  }

  public getUser(): any {
    const user = window.localStorage.getItem(this.USER_KEY);
    if (user) {
      return JSON.parse(user);
    }
    return null;
  }

  public clear(): void {
    window.localStorage.clear();
  }
}
