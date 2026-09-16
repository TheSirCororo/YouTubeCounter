# Deploying the backend

The service runs under [Dokploy](https://dokploy.com) (project `services`, compose
service `youtubecounter`). **This directory is the source of truth**: Dokploy pulls
`deploy/docker-compose.yml` straight from this repository, so the deployed stack is
whatever master says it is, reviewed like any other change.

CI builds the server image on every push to `master`, pushes it to
`ghcr.io/thesircororo/youtubecounter-backend` as `sha-<short commit>` and `latest`,
then **POSTs to a single Dokploy webhook** — that is the entire deploy step. Dokploy
re-reads this repository, pulls the new `latest` image and recreates the container.

There is no deploy script and no SSH: the host is not touched by CI at all.

## Where the secrets live

| Secret | Stored in | Seen by CI? |
| --- | --- | --- |
| `GOOGLE_CLIENT_SECRET`, `GOOGLE_CLIENT_ID` | Dokploy environment variables for the service (Dokploy's own PostgreSQL) | **No** |
| `DOKPLOY_DEPLOY_WEBHOOK` | GitHub Actions secret | Yes, only in the `deploy` job |
| ghcr.io pull credentials | `~/.docker/config.json` on the host (only if the package is private) | No |

The Google credentials never enter this repository, the image, GitHub Actions, or
any command line — the container receives them as environment variables at startup.
CI can trigger a deploy but cannot read the app's secrets.

**The webhook URL is the whole secret.** It looks like

```
https://youtubecounter.cororo.ru/<unguessable-prefix>/deploy/<service-token>
```

and that one path is the only part of Dokploy reachable from the internet — the
panel and its API stay on the host's loopback interface. Anyone holding the URL can
redeploy this service (and nothing else), so treat it like a deploy key: keep it in
GitHub secrets, pass it to shell steps via `env:`, never echo it.

## One-time host setup

In Dokploy: create a project, add a service of type **Compose**, paste
`docker-compose.yml` from this directory into it, and fill its environment with the
keys from `.env.example` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `HOST_PORT`,
`APP_IMAGE`, `APP_TAG`).

Compose, not Application: Dokploy runs Compose services with plain `docker compose`,
which is what makes the `exec` tmpfs below possible. A Swarm Application cannot set
it at all, and the container would die at startup.

Add the domain(s) to the service with **HTTPS disabled** — TLS is terminated by the
host's nginx, which holds the certificates. Traefik is not exposed on 443 at all.

Point the service's source at this repository (branch `master`, compose path
`deploy/docker-compose.yml`) so Dokploy deploys what is in git rather than a copy
pasted into its database.

Nothing needs to be installed on the host: there is no deploy script and no deploy
user. The reverse proxy in front of Dokploy needs one extra location, which is what
CI calls:

```nginx
location ~ ^/<unguessable-prefix>/deploy/([A-Za-z0-9_-]+)$ {
    proxy_pass http://127.0.0.1:3000/api/deploy/compose/$1;
    proxy_method POST;
    proxy_set_header Content-Length 0;
}
```

The container publishes **only on `127.0.0.1:$HOST_PORT`** — that port exists purely
so monitoring can probe the container directly, bypassing the proxy chain. Public
traffic goes nginx → Traefik → container.

## GitHub setup

One secret, in **Settings → Secrets and variables → Actions** (repository secrets,
or scoped to the `production` environment if you want required reviewers on
deploys):

| Name | Value |
| --- | --- |
| `DOKPLOY_DEPLOY_WEBHOOK` | the full webhook URL shown above |

The old SSH deployment secrets (`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`,
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_PORT`) are no longer used and should be
deleted, together with the `deploy` user's key in `authorized_keys` on the host —
an unused credential is only a liability.

## Everyday use

- **Deploy**: push to `master`. CI builds, pushes `latest`, pokes Dokploy.
- **Redeploy the current build**: Actions → *Build and deploy backend* →
  *Run workflow*, or the Deploy button in Dokploy.
- **Roll back to an older build**: in Dokploy, set the service's `APP_TAG` to the
  wanted `sha-<short>` and deploy. CI only ever ships `latest`, so rolling back is
  deliberately a human decision made in one place. Put `APP_TAG` back to `latest`
  afterwards, otherwise the next push builds an image nobody deploys.
- **Logs and environment**: the Dokploy UI, or `docker logs -f
  youtubecounter-backend` on the host.

Do not run `docker compose up` by hand in `/opt/youtubecounter`. The pre-Dokploy
stack is kept there as `docker-compose.yml.pre-dokploy` **renamed on purpose**: if
it were started alongside Dokploy's stack, the two would fight over the container
name and over port 9080.

## The token database

Refresh tokens live in SQLite at `/data/tokens.db` inside the container, on the
`youtubecounter_tokens` docker volume. Before this existed they were in memory, so
every deploy signed all users out; now a redeploy leaves sessions intact.

Treat the volume as secret material — it holds usable refresh tokens (access tokens
are stored only as hashes). To back it up:

```sh
docker run --rm -v youtubecounter_tokens:/data -v "$PWD":/backup busybox \
    cp /data/tokens.db /backup/tokens-$(date +%F).db
```

`docker compose down -v` deletes it and signs everyone out; plain `down` does not.
The volume is declared `external: true` in the compose file, so Dokploy neither
creates nor removes it — that is what let the service move into Dokploy without
signing anyone out.

## Things worth knowing

- Anyone in the `docker` group on the host can read the secrets out of the running
  container (`docker inspect`). That is inherent to passing them as environment
  variables; keep group membership to the `deploy` user.
- **`pull_policy: always` in the compose file is load-bearing.** Dokploy deploys a
  compose service with `docker compose up -d --build` and **no separate `pull`**.
  With a floating tag like `latest`, docker would happily reuse the image it already
  has locally, so the webhook would redeploy the *previous* build and report success.
  Remove that line and deploys silently stop shipping new code.
- **"Deployment queued" is not "deployed".** Dokploy's API answers immediately and
  deploys asynchronously. Anything that triggers a deploy and then health-checks the
  service will be answered by the *old, still running* container. If you ever script
  around this webhook, wait for the deployment to leave `running` (tRPC
  `deployment.allByCompose`) and compare the tag actually running with the one you
  wanted.
- Dokploy is driven over tRPC at `/api/trpc/<procedure>` with an `x-api-key` header.
  Calling a procedure with an empty body makes it reply with its own validation
  schema, which is the easiest way to discover the expected fields. Note that API
  keys are rate limited by default (better-auth) — a key that suddenly returns 401
  for everything is usually throttled, not expired.
- Pull requests, including ones from forks, build the image but never log in to the
  registry and never reach the `deploy` job, so a PR cannot touch production.
- If a Google client secret is ever exposed, rotate it in the Google Cloud console
  (APIs & Services → Credentials), update the variable in Dokploy, and press
  Deploy there.
  Applying it restarts the container, which drops the in-memory refresh-token map,
  so every user re-authenticates regardless.
