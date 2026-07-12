# Praetor — Teammate Onboarding

Mini online judge (course project). Submit code → sandboxed Docker run → per-testcase verdict → contest + live standings. Stack: **Spring Boot + PostgreSQL + Angular + Docker**.

This file gets you from a fresh clone to a running app, then points you at your work.

---

## 0. Prerequisites

- **Docker** + **Docker Compose** (v2, i.e. `docker compose`, not `docker-compose`)
- **Git** + a GitHub account (ask Sazid to add you as a collaborator — send him your GitHub handle)
- That's it for running. You do **not** need Java/Node/Postgres installed locally — everything runs in containers.

---

## 1. First run (do this once)

```bash
git clone https://github.com/draenor08/praetor.git
cd praetor

# REQUIRED: create your local env file. .env is gitignored, so a fresh
# clone has none. Compose won't start without it.
cp .env.example .env

# build + start postgres + backend + frontend
docker compose up --build
```

First boot takes a few minutes (downloads images, builds backend jar + frontend). When you see the backend log `Started PraetorApplication`, it's up.

**Open:** http://localhost:4200 (the app)
**Health check:** http://localhost:8080/api/health → should return `{"status":"UP"}`

Stop it: `Ctrl-C`, or `docker compose down` from another terminal.

### Ports (change in `.env` if they clash with your other projects)
| Service | URL | `.env` var |
|---|---|---|
| Frontend (the app) | http://localhost:4200 | `FRONTEND_PORT` |
| Backend API | http://localhost:8080 | `BACKEND_PORT` |
| Postgres | localhost:5432 | `POSTGRES_PORT` |

---

## 2. What's here today vs. what you build

The repo **boots** but implements almost no features yet — it's a skeleton.

- **Real code so far:** `PraetorApplication.java` + `common/HealthController.java` only.
- **Your module packages are empty** (`.gitkeep` placeholder files). You fill them.
- The big file tree in `README.md` is the **target** — where files *should* land, not what exists.

### Where your slice lives

**Backend** (Java, under `backend/src/main/java/com/praetor/`):
| You | Package(s) |
|---|---|
| Sazid | `submission/`, `contest/`, `ws/`, `common/` + integration/shell |
| Yeasir | `identity/`, `problem/` (problems + contest-setup + rating/auth) |
| Mahir | submission history/tags/search/registration/stats slices |

**Frontend** (Angular): each person builds their own components under `frontend/src/app/features/`. They plug into the shared shell (`core/`, `shared/`) Sazid scaffolds.

> Exact FR-number → file mapping is in `README.md` ("Feature → files" table). Build to that.

---

## 3. Rules — read before writing code

- **`docs/api-contracts.md`** — the FROZEN REST/WS contract. Endpoints, request/response shapes. Build to these exactly so slices integrate without rework. Don't change a contract without telling the team.
- **`docs/CONVENTIONS.md`** — layering (controller→service→repository), naming (DB `snake_case`, JSON `camelCase`), MVC rules, testing, definition-of-done.

**Insulation rule:** the judging engine reads problems/testcases straight from the DB, not through your controllers. So even if someone's CRUD is half-done, seeded data keeps the demo working. Don't break this — keep repositories as the source of truth.

---

## 4. Git workflow

```bash
git checkout -b feat/<short-name>     # branch per feature, off main
# ... work ...
git add -A
git commit -m "feat: <what you did>"  # Conventional Commits: feat/fix/chore/docs/test
git push -u origin feat/<short-name>
# open a Pull Request on GitHub → someone reviews → merge to main
```

- Commit types: `feat` (feature), `fix` (bug), `chore` (build/config), `docs`, `test`.
- **Never commit `.env`** (it's gitignored — keep it that way). Only `.env.example` is tracked.
- Pull `main` before starting: `git checkout main && git pull`.

---

## 5. Database notes (important gotchas)

- Postgres runs **only in the container** — nothing installed on your machine. Data lives in a Docker volume (`praetor_pgdata`).
- **Schema + seed run ONCE**, on the very first boot (empty volume). Editing `db/schema.sql` or `db/seed.sql` afterward does **nothing** until you reset:
  ```bash
  docker compose down -v      # -v wipes the volume
  docker compose up --build   # re-runs schema + seed fresh
  ```
- Seed data: 4 users, 3 problems, 1 live contest. **Dev login:** any seeded handle (e.g. `draenor08`, `alice`) + password `password`.
  - ⚠️ Login may be broken until the placeholder bcrypt hashes in `seed.sql` are replaced — check with Sazid.

---

## 6. Common problems

| Symptom | Fix |
|---|---|
| `docker compose up` errors on missing variables | You skipped `cp .env.example .env`. Do step 1. |
| Port already in use (5432/8080/4200) | Change the port in `.env`, `docker compose up` again. |
| Changed `seed.sql` but data unchanged | Seed only runs on empty volume → `docker compose down -v && docker compose up`. |
| `failed to add veth pair` (Arch/Linux after a kernel update) | Reboot into the current kernel, then retry. |
| Backend can't reach DB | Wait — backend waits for the DB healthcheck; first boot is slow. |

---

**Questions → ask Sazid.** Send him your GitHub handle so he can add you to the repo.
