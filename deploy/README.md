# Deploying the backend

CI builds the server image on every push to `master`, pushes it to
`ghcr.io/thesircororo/youtubecounter-backend`, then SSHes into the host and runs
`deploy.sh`, which pulls the new tag, restarts the container, and **rolls back to
the previous tag if the app does not answer on `/static/` within 90 seconds**.

Images are tagged `sha-<short commit>` (immutable, this is what gets deployed) and
`latest`.

## Where the secrets live

| Secret | Stored in | Seen by CI? |
| --- | --- | --- |
| `GOOGLE_CLIENT_SECRET`, `GOOGLE_CLIENT_ID` | `/opt/youtubecounter/.env` on the host, mode `600` | **No** |
| SSH deploy key, host, user | GitHub Actions secrets | Yes, only in the `deploy` job |
| ghcr.io pull credentials | `~/.docker/config.json` on the host (only if the package is private) | No |

The Google credentials never enter this repository, the image, GitHub Actions, or
any command line — the container reads them from `.env` at startup. CI can restart
the app but cannot read its secrets.

## One-time host setup

```sh
# As root on the deployment host
install -d -o deploy -g deploy -m 750 /opt/youtubecounter
```

Then, as the `deploy` user (must be in the `docker` group):

```sh
cd /opt/youtubecounter
# Copy docker-compose.yml, deploy.sh and .env.example out of this repository
chmod +x deploy.sh
cp .env.example .env
chmod 600 .env
$EDITOR .env          # fill in GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET, set HOST_PORT

# If the ghcr package is private, log in once with a PAT scoped to read:packages
printf '%s' "$GHCR_PAT" | docker login ghcr.io -u <github-user> --password-stdin

./deploy.sh latest    # first deploy
```

The container publishes **only on `127.0.0.1:$HOST_PORT`**. Point the existing
reverse proxy for `youtubecounter.cororo.ru` at that port; it stays unreachable
from the internet otherwise.

## GitHub setup

Create a keypair dedicated to deployments — it is not a login key, do not reuse a
personal one:

```sh
ssh-keygen -t ed25519 -f ~/.ssh/youtubecounter_deploy -C "github-actions deploy" -N ""
```

Append the **public** key to `/home/deploy/.ssh/authorized_keys` on the host, with
a forced command so a leaked key can do nothing but deploy:

```
command="/opt/youtubecounter/deploy.sh",restrict ssh-ed25519 AAAA... github-actions deploy
```

`restrict` disables port forwarding, agent forwarding, and PTY allocation.
`deploy.sh` picks the requested tag out of `SSH_ORIGINAL_COMMAND` and refuses
anything that is not a bare image tag, so the key cannot be used to run arbitrary
commands even though CI passes an argument.

Then add these to **Settings → Secrets and variables → Actions** (repository
secrets, or scoped to the `production` environment if you want required reviewers
on deploys):

| Name | Value |
| --- | --- |
| `DEPLOY_SSH_KEY` | contents of the **private** key `~/.ssh/youtubecounter_deploy` |
| `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -p 22 <host>` |
| `DEPLOY_HOST` | the host's address |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_PORT` | optional, defaults to `22` |

`DEPLOY_KNOWN_HOSTS` is what makes `StrictHostKeyChecking=yes` meaningful — without
it the runner would trust whatever answers on that address.

## Everyday use

- **Deploy**: push to `master`.
- **Roll back / redeploy a specific build**: Actions → *Build and deploy backend* →
  *Run workflow*, and give it a tag such as `sha-1a2b3c4`. That path skips the
  build entirely and only redeploys.
- **On the host**: `./deploy.sh sha-1a2b3c4`, `docker compose logs -f backend`,
  `docker compose ps`.

`docker compose up` on its own is deliberately rejected — the image tag is not
guessed, it comes from `.env`, which `deploy.sh` maintains.

## Things worth knowing

- Anyone in the `docker` group on the host can read the secrets out of the running
  container (`docker inspect`). That is inherent to `env_file`; keep group
  membership to the `deploy` user.
- Pull requests, including ones from forks, build the image but never log in to the
  registry and never reach the `deploy` job, so a PR cannot touch production.
- If a Google client secret is ever exposed, rotate it in the Google Cloud console
  (APIs & Services → Credentials), update `.env`, and `./deploy.sh <current tag>`.
  Rotating it invalidates every stored refresh token, so all users re-authenticate.
