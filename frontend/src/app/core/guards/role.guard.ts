import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { TokenService } from '../services/token.service';

export const roleGuard: CanActivateFn = (route, state) => {
  const tokenService = inject(TokenService);
  const router = inject(Router);
  const user = tokenService.getUser();

  if (user) {
    const expectedRoles = route.data['roles'] as Array<string>;
    if (!expectedRoles || expectedRoles.includes(user.role)) {
      return true;
    }
    // Redirect to home if not authorized
    router.navigate(['/']);
    return false;
  }

  router.navigate(['/login']);
  return false;
};
