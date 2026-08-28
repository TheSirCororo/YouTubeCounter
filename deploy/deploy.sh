#!/usr/bin/env bash
#
# Pulls an image tag and restarts the backend, rolling back if it does not come
# up healthy. Runs on the deployment host: either invoked by CI over SSH, or by
# hand as `./deploy.sh <tag>`.
#
# It deliberately never reads the secrets out of .env - only HOST_PORT and the
# image coordinates - so a secret can never end up in this script's output.
set -euo pipefail

DEPLOY_DIR=${DEPLOY_DIR:-/opt/youtubecounter}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-90}
HEALTH_PATH=${HEALTH_PATH:-/static/}

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] error: %s\n' "$*" >&2; exit 1; }

# --- Work out which tag was requested ----------------------------------------
# When the SSH key is locked to a forced command (see README.md), the arguments
# CI sent arrive in SSH_ORIGINAL_COMMAND rather than as real arguments.
requested=${1:-}
if [[ -z $requested && -n ${SSH_ORIGINAL_COMMAND:-} ]]; then
    read -ra ssh_argv <<<"$SSH_ORIGINAL_COMMAND"
    ((${#ssh_argv[@]} > 1)) && requested=${ssh_argv[-1]}
fi
requested=${requested:-latest}

# Anything the caller sends is untrusted: a tag, and nothing that could turn into
# a different registry, a path, or a second shell word.
if [[ ! $requested =~ ^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$ ]]; then
    die "refusing to deploy: '$requested' is not a valid image tag"
fi

cd "$DEPLOY_DIR" || die "deployment directory $DEPLOY_DIR not found"
[[ -f docker-compose.yml ]] || die "no docker-compose.yml in $DEPLOY_DIR"
[[ -f .env ]] || die "no .env in $DEPLOY_DIR - copy .env.example and fill it in"
docker compose version >/dev/null 2>&1 || die "docker compose v2 is required"

# --- Read the few non-secret settings we need --------------------------------
# Plain grep rather than `source`, so the secrets in .env are never evaluated by
# this shell and cannot leak into a child process or an error message.
env_get() {
    local value
    value=$(grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-) || true
    printf '%s' "${value:-$2}"
}

host_port=$(env_get HOST_PORT 8080)
app_image=$(env_get APP_IMAGE ghcr.io/thesircororo/youtubecounter-backend)
previous_tag=$(env_get APP_TAG '')

[[ $host_port =~ ^[0-9]{1,5}$ ]] || die "HOST_PORT in .env is not a port number"

set_tag() {
    local tag=$1 tmp
    tmp=$(mktemp "$DEPLOY_DIR/.env.XXXXXX")
    chmod --reference=.env "$tmp" 2>/dev/null || chmod 600 "$tmp"
    if grep -qE '^APP_TAG=' .env; then
        sed "s|^APP_TAG=.*|APP_TAG=$tag|" .env >"$tmp"
    else
        cat .env >"$tmp"
        printf 'APP_TAG=%s\n' "$tag" >>"$tmp"
    fi
    mv "$tmp" .env
}

healthy() {
    # stderr is dropped: a refused connection is the normal state while the JVM boots.
    curl -fsS --max-time 5 -o /dev/null "http://127.0.0.1:$host_port$HEALTH_PATH" 2>/dev/null
}

wait_for_health() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT))
    while ((SECONDS < deadline)); do
        if healthy; then return 0; fi
        sleep 3
    done
    return 1
}

# --- Deploy ------------------------------------------------------------------
log "deploying $app_image:$requested (currently ${previous_tag:-none})"

APP_TAG=$requested docker compose pull backend

set_tag "$requested"
docker compose up -d --remove-orphans

if wait_for_health; then
    log "healthy on 127.0.0.1:$host_port$HEALTH_PATH"
else
    log "did not become healthy within ${HEALTH_TIMEOUT}s - last 40 log lines:"
    docker compose logs --no-color --tail 40 backend || true

    if [[ -n $previous_tag && $previous_tag != "$requested" ]]; then
        log "rolling back to $previous_tag"
        set_tag "$previous_tag"
        docker compose up -d --remove-orphans
        if wait_for_health; then
            log "rollback to $previous_tag is healthy"
        else
            log "rollback to $previous_tag is ALSO unhealthy - the host needs attention"
        fi
    else
        log "no previous tag recorded, leaving the failed container in place"
    fi
    die "deploy of $requested failed"
fi

# --- Tidy up old images of this repository only ------------------------------
# Scoped to $app_image so unrelated images on the host are never touched.
docker image ls --format '{{.Repository}}:{{.Tag}} {{.ID}}' \
    | awk -v repo="$app_image" '$1 ~ "^"repo":" {print $1}' \
    | grep -vE ":(${requested}|${previous_tag:-__none__}|latest)$" \
    | xargs -r -n1 docker image rm >/dev/null 2>&1 || true

log "deployed $app_image:$requested"
