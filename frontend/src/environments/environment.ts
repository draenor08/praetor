// API is reached through a relative path so the same build works in dev and in
// the nginx container (nginx proxies /api and /ws to the backend). No host/port
// baked in. environment.docker.ts mirrors this for the `docker` build config.
export const environment = {
  production: false,
  apiUrl: '/api',
  wsUrl: '/ws',
};
