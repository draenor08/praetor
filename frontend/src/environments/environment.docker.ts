// Used by the `docker` build configuration. Same relative paths — nginx in the
// frontend container proxies /api and /ws to the backend service.
export const environment = {
  production: true,
  apiUrl: '/api',
  wsUrl: '/ws',
};
