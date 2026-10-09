#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir/../.."
compose=(docker compose --env-file /dev/null -f compose.yaml)

postgres_client() {
  docker run --rm -i --network "$POSTGRES_NETWORK" --read-only --cap-drop ALL \
    --security-opt no-new-privileges:true -e PGPASSWORD -e SPRING_DATASOURCE_PASSWORD \
    -e PGCONNECT_TIMEOUT=5 -e PGOPTIONS postgres:17-bookworm \
    psql -X -w -h infra-postgres -v ON_ERROR_STOP=1 "$@"
}

prepare_database() {
  : "${PGPASSWORD:?Set GitHub secret POSTGRES_ADMIN_PASSWORD}"
  : "${SPRING_DATASOURCE_PASSWORD:?Set GitHub secret SPRING_DATASOURCE_PASSWORD}"
  : "${VK_GROUP_TOKEN:?Set GitHub secret VK_GROUP_TOKEN}"
  : "${VK_GROUP_ID:?Set GitHub variable VK_GROUP_ID}"
  : "${VK_MY_ID:?Set GitHub variable VK_MY_ID}"
  [[ "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]
  docker network inspect "$POSTGRES_NETWORK" > /dev/null
  "${compose[@]}" config --quiet
  # Avoid logging the password embedded in CREATE ROLE, for this session only.
  export PGOPTIONS="-c statement_timeout=10000 -c log_statement=none \
  -c log_min_error_statement=panic -c log_min_duration_statement=-1 \
  -c log_min_duration_sample=-1 -c log_transaction_sample_rate=0"
  if ! postgres_client -U postgres -d postgres < "$script_dir/init-monolit.sql" > /dev/null 2>&1; then
    echo "::error::Database initialization failed; check admin credentials, network and existing monolit role/database ownership or privileges."
    exit 1
  fi
}

check_database() {
  export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" PGOPTIONS='-c statement_timeout=5000'
  if ! postgres_client -U monolit -d monolit -c 'SELECT 1' > /dev/null 2>&1; then
    echo "::error::Database preflight failed; check network, database and service credentials."
    exit 1
  fi
}

build_image() {
  "${compose[@]}" build monolit
}

deploy_application() {
  previous_container=$("${compose[@]}" ps --all --quiet monolit)
  previous_image=none
  previous_ref=none
  if [[ -n "$previous_container" ]]; then
    previous_image=$(docker inspect --format '{{.Image}}' "$previous_container")
    previous_ref=$(docker inspect --format '{{.Config.Image}}' "$previous_container")
  fi
  printf 'Deploying `%s`; previous image `%s` (`%s`).\n' \
    "$DEPLOY_SHA" "$previous_image" "$previous_ref" >> "$GITHUB_STEP_SUMMARY"
  "${compose[@]}" up -d --no-build --wait --wait-timeout 60 monolit
  container=$("${compose[@]}" ps --all --quiet monolit)
  test -n "$container"
  expected_image=$(docker image inspect --format '{{.Id}}' "monolit:$DEPLOY_SHA")
  test "$(docker inspect --format '{{.Image}}' "$container")" = "$expected_image"
  printf 'Deployed `%s`; container is healthy. Manual VK smoke is still required.\n' \
    "$DEPLOY_SHA" >> "$GITHUB_STEP_SUMMARY"
}

case "${1:-}" in
  prepare) prepare_database ;;
  check-database) check_database ;;
  build) build_image ;;
  deploy) deploy_application ;;
  *) echo 'Usage: deploy.sh prepare|check-database|build|deploy' >&2; exit 2 ;;
esac
