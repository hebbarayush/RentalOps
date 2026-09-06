# Deploying RentalOps

RentalOps ships as two artifacts: a **Spring Boot API** (a Docker image) and a **static React
bundle**. It needs a **PostgreSQL** database, and *optionally* **Redis**.

- [Run the whole stack locally in Docker](#run-the-whole-stack-locally-in-docker)
- [Option A — Render blueprint (one repo, one click)](#option-a--render-blueprint)
- [Option B — Render API + Netlify/Vercel frontend (manual)](#option-b--render-api--netlify-frontend-manual)
- [Option C — Fly.io + Neon](#option-c--flyio--neon)
- [The `prod` profile & config validation](#the-prod-profile--config-validation)
- [The CORS / API-URL handshake](#the-cors--api-url-handshake)
- [Post-deploy checklist](#post-deploy-checklist)
- [Adding Redis](#adding-redis)
- [Cost & cold starts](#cost--cold-starts)

---

## Run the whole stack locally in Docker

Proves the containers work before you deploy anything.

```bash
docker compose -f docker-compose.full.yml up --build
```

- Frontend → <http://localhost:8081> (nginx serves the SPA and proxies `/api` to the backend)
- Backend  → <http://localhost:8082> (Swagger at `/swagger-ui/index.html`)
- Postgres on `:5433`, Redis on `:6380` (non-standard host ports so they don't clash with local installs)

Sign in with `manager@rentalops.dev` / `password123`. `Ctrl-C` then
`docker compose -f docker-compose.full.yml down -v` to tear it down.

This runs the **default** Spring profile with explicit non-dev values. Real deployment uses the
`prod` profile — see [below](#the-prod-profile--config-validation).

---

## Option A — Render blueprint

The repo has a [`render.yaml`](../render.yaml) that provisions all three pieces (API + static
site + Postgres).

1. Push the repo to GitHub.
2. [Render dashboard](https://dashboard.render.com) → **New** → **Blueprint** → select the repo.
   Render reads `render.yaml` and creates `rentalops-db`, `rentalops-api`, `rentalops-web`.
3. It will ask you to fill the two `sync: false` values. On the first pass just put placeholders
   (`https://placeholder`) — you'll fix them in step 5.
4. Deploy. `JWT_SECRET` is generated automatically; the DB URL/credentials are injected from
   `rentalops-db`.
5. Once both services are live, do the [CORS / API-URL handshake](#the-cors--api-url-handshake):
   - `rentalops-api` → Environment → set `CORS_ALLOWED_ORIGINS` to the `rentalops-web` URL.
   - `rentalops-web` → Environment → set `VITE_API_BASE_URL` to the `rentalops-api` URL.
   - Manually redeploy both (the static site must rebuild to bake in the new URL).
6. Run the [post-deploy checklist](#post-deploy-checklist).

---

## Option B — Render API + Netlify frontend (manual)

Use this if you want the frontend on Netlify/Vercel/Cloudflare Pages instead of Render.

### 1. Database — Render Postgres

Render → **New** → **PostgreSQL** (free plan). Copy the **Internal Database URL** (a
`postgresql://...` string) and the host / user / password.

### 2. Backend — Render Web Service (Docker)

Render → **New** → **Web Service** → connect the repo →

| Setting | Value |
|---|---|
| Runtime | Docker |
| Dockerfile path | `backend/Dockerfile` |
| Docker build context | `backend` |
| Health check path | `/actuator/health` |

Environment variables:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:5432/<db>` (note the `jdbc:` prefix — Render's raw string omits it) |
| `DB_USERNAME` / `DB_PASSWORD` | from the database page |
| `JWT_SECRET` | `openssl rand -hex 32` |
| `CORS_ALLOWED_ORIGINS` | your Netlify URL (fill after step 3) |
| `JOBS_AUTORUN` | `true` |

Deploy. Watch the logs for `Production config validated` then `Started RentalOpsApplication`.
If it refuses to start, the log names exactly which variable is wrong.

### 3. Frontend — Netlify

Netlify → **Add new site** → import the repo →

| Setting | Value |
|---|---|
| Base directory | `frontend` |
| Build command | `npm run build` |
| Publish directory | `frontend/dist` |
| Environment variable | `VITE_API_BASE_URL` = your Render backend URL (e.g. `https://rentalops-api.onrender.com`) |

Add a `frontend/public/_redirects` file containing `/*  /index.html  200` for SPA routing
(Netlify), or a `vercel.json` rewrite for Vercel.

### 4. Close the loop

Set the backend's `CORS_ALLOWED_ORIGINS` to the Netlify URL, redeploy the backend.

---

## Option C — Fly.io + Neon

- **DB:** [Neon](https://neon.tech) free Postgres → copy the connection string, prefix with `jdbc:`.
- **Backend:** `fly launch --dockerfile backend/Dockerfile` (no deploy yet). In the generated
  `fly.toml` set `[http_service] internal_port = 8080` and
  `[[http_service.checks]] path = "/actuator/health"`. Then
  `fly secrets set SPRING_PROFILES_ACTIVE=prod DB_URL=... DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=$(openssl rand -hex 32) CORS_ALLOWED_ORIGINS=https://<frontend>` and `fly deploy`.
- **Frontend:** same as Option B step 3, or `fly deploy` a second app from `frontend/Dockerfile`
  with `--build-arg VITE_API_BASE_URL=https://<backend>`.

---

## The `prod` profile & config validation

`SPRING_PROFILES_ACTIVE=prod` (the Dockerfile sets it by default) activates
`application-prod.yml`, which has **no local fallbacks**. Before any bean is created,
`ProdConfigValidator` (an `EnvironmentPostProcessor`) checks:

| Check | Fails startup if… |
|---|---|
| `JWT_SECRET` | missing, still the dev placeholder, or shorter than 32 chars |
| `CORS_ALLOWED_ORIGINS` | missing, or contains `localhost` / `127.0.0.1` |
| `DB_URL` | missing |
| `REDIS_ENABLED=true` + `SPRING_CACHE_TYPE≠redis` | *(warning only)* |

A bad config produces one aggregated error naming every problem — the app never comes up
half-configured. `application-prod.yml` also sets `server.forward-headers-strategy: framework`
(correct scheme behind a load balancer) and `server.shutdown: graceful`.

Flyway runs the migrations (`V1`–`V6`) on first boot. The `DataSeeder` seeds the three demo
accounts + a sample portfolio **only if the `users` table is empty**, so a fresh managed DB
comes up demo-ready and later restarts don't re-seed.

---

## The CORS / API-URL handshake

There's a deploy-order dependency:

```
backend needs   CORS_ALLOWED_ORIGINS = <frontend URL>     (runtime env var)
frontend needs  VITE_API_BASE_URL    = <backend URL>       (BUILD-time — baked into the bundle)
```

So:

1. Deploy the backend with a placeholder `CORS_ALLOWED_ORIGINS`. Note its URL.
2. Deploy/build the frontend with `VITE_API_BASE_URL` = the backend URL. Note its URL.
3. Update the backend's `CORS_ALLOWED_ORIGINS` = the frontend URL. Redeploy the backend
   (env-var change, no rebuild needed).

Changing `VITE_API_BASE_URL` later **requires a frontend rebuild** — it's compile-time, not
runtime.

The Docker/compose path sidesteps this entirely: the frontend is built with an empty
`VITE_API_BASE_URL` and nginx proxies `/api` to the backend, so same-origin, no CORS.

---

## Post-deploy checklist

```bash
API=https://your-backend.example.com
WEB=https://your-frontend.example.com

curl -s $API/actuator/health                       # {"status":"UP"}
curl -s -X POST $API/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"manager@rentalops.dev","password":"password123"}'   # → a token

curl -s -o /dev/null -w '%{http_code}\n' $WEB       # 200
curl -s -o /dev/null -w '%{http_code}\n' $WEB/leases  # 200 (SPA fallback works)
```

Then in a browser: open `$WEB`, sign in, confirm the dashboard loads with real numbers and the
browser console shows no CORS errors. Check `$API/swagger-ui/index.html`.

**Change the demo passwords** (or disable the demo accounts via the admin console) before
sharing the link publicly.

---

## Adding Redis

Optional — the app runs fine without it. Add it when you run more than one backend instance and
want a shared login-attempt counter + shared dashboard cache.

- **Render:** add a **Key Value** instance, then set on the API service:
  `REDIS_ENABLED=true`, `REDIS_HOST=<host>`, `REDIS_PORT=<port>`, `SPRING_CACHE_TYPE=redis`.
  Also flip `management.health.redis.enabled` (it's tied to `REDIS_ENABLED`).
- **Fly.io:** `fly redis create`, then the same four secrets.
- **Upstash** (serverless Redis) works too — use the host/port, not the REST URL.

`management.endpoints` only exposes `health` and `info` in prod.

---

## Cost & cold starts

| Piece | Free option | Caveat |
|---|---|---|
| Backend | Render free web service | Sleeps after 15 min idle; ~30 s cold start on the next request. Fly.io free tier stays warm longer. |
| Postgres | Render free (90 days), Neon free (no expiry) | Small connection limits; fine for a demo. |
| Frontend | Netlify / Vercel / Cloudflare Pages / Render static | Effectively always free for this size. |

For a portfolio link that's occasionally clicked, the backend cold start is the only real
annoyance — mention it in your README ("first request may take ~30s while the free-tier backend
wakes up") or move the backend to a $7/mo always-on instance.
