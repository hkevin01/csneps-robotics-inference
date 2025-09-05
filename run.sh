#!/usr/bin/env bash
set -euo pipefail

# Project settings
PROJECT_NAME="csri"
COMPOSE_FILE="docker/compose.yaml"
: "${COMPOSE_PROFILES:=}"  # e.g., export COMPOSE_PROFILES=gpu

# Colors
RED="$(tput setaf 1 || true)"
GRN="$(tput setaf 2 || true)"
YLW="$(tput setaf 3 || true)"
RST="$(tput sgr0 || true)"

# Helper to print section headers
section() {
  echo -e "\n${GRN}==>${RST} $1"
}

warn() {
  echo -e "${YLW}[warn]${RST} $1"
}

error() {
  echo -e "${RED}[error]${RST} $1"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "Missing required command: $1"
    exit 1
  fi
}

cleanup_previous() {
  section "Cleaning up previous Docker runs (project: ${PROJECT_NAME})"
  # Stop and remove containers for this project (if any)
  if docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" ps >/dev/null 2>&1; then
    docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" down --remove-orphans || true
  fi

  # Remove dangling containers with same label if any were left
  dangling_ctrs=$(docker ps -a --filter "label=com.docker.compose.project=${PROJECT_NAME}" -q || true)
  if [ -n "${dangling_ctrs}" ]; then
    warn "Removing leftover containers: ${dangling_ctrs}"
    docker rm -f ${dangling_ctrs} || true
  fi

  # Remove network if still present
  net_name="${PROJECT_NAME}_default"
  if docker network inspect "${net_name}" >/dev/null 2>&1; then
    warn "Removing leftover network: ${net_name}"
    docker network rm "${net_name}" || true
  fi

  # Optionally remove old images (uncomment if desired)
  # old_images=$(docker images --filter "label=com.docker.compose.project=${PROJECT_NAME}" -q || true)
  # if [ -n "${old_images}" ]; then
  #   warn "Removing leftover images"
  #   docker rmi -f ${old_images} || true
  # fi
}

build_images() {
  section "Building all Docker images"
  local build_args=()
  if [ -n "${COMPOSE_PROFILES}" ]; then
    build_args+=(--profile "${COMPOSE_PROFILES}")
  fi
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "${build_args[@]}" build --no-cache
}

wait_healthy() {
  local svc="$1" timeout="${2:-120}"
  section "Waiting for service healthy: ${svc} (timeout ${timeout}s)"
  local start=$(date +%s)
  while true; do
    if docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" ps --format json 2>/dev/null | \
      jq -r ".[] | select(.Name==\"${svc}\") | .Health" 2>/dev/null | grep -q "healthy"; then
      echo "${svc} is healthy."
      break
    fi
    sleep 2
    local now=$(date +%s)
    if (( now - start > timeout )); then
      warn "${svc} not healthy within ${timeout}s; continuing..."
      break
    fi
  done
}

bring_up() {
  section "Starting services"
  local up_args=("--remove-orphans")
  if [ -n "${COMPOSE_PROFILES}" ]; then
    up_args+=(--profile "${COMPOSE_PROFILES}")
  fi
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d "${up_args[@]}"

  section "Health checks (comprehensive)"

  # Wait for core services to be healthy
  wait_healthy "csri-csneps-core" 180
  wait_healthy "csri-kg-service" 180

  # Optional GPU node
  if [ "${COMPOSE_PROFILES}" = "gpu" ]; then
    wait_healthy "csri-perception-gpu" 240 || true
  fi

  # Additional endpoint verification
  section "Verifying service endpoints"

  # Wait for csneps-core
  for i in {1..30}; do
    if curl -fsS "http://localhost:3000/health" >/dev/null 2>&1; then
      echo "csneps-core /health endpoint is responding."
      break
    fi
    sleep 1
    [ $i -eq 30 ] && warn "csneps-core health endpoint not detected; continuing..."
  done

  # Wait for Java service
  for i in {1..30}; do
    if curl -fsS "http://localhost:8081/actuator/health" >/dev/null 2>&1; then
      echo "csri-kg-service actuator health is responding."
      break
    fi
    sleep 1
    [ $i -eq 30 ] && warn "csri-kg-service health endpoint not detected; continuing..."
  done

  # Test core endpoints functionality
  section "Testing core API endpoints"

  # Test /why endpoint
  if curl -fsS "http://localhost:3000/why?term=ping" >/dev/null 2>&1; then
    echo "✓ CSNePS /why endpoint is functional"
  else
    warn "CSNePS /why endpoint not responding"
  fi

  # Test /assert endpoint
  if curl -fsS -X POST "http://localhost:3000/assert" \
    -H "Content-Type: application/json" \
    -d '{"subject":"Robot:R1","predicate":"operatesIn","object":"Area:A-12","confidence":0.5}' \
    >/dev/null 2>&1; then
    echo "✓ CSNePS /assert endpoint is functional"
  else
    warn "CSNePS /assert endpoint not responding"
  fi

  # Test /query endpoint
  if curl -fsS "http://localhost:3000/query?pattern=Entity(?x)" >/dev/null 2>&1; then
    echo "✓ CSNePS /query endpoint is functional"
  else
    warn "CSNePS /query endpoint not responding"
  fi

  # Web UI optional
  if lsof -i :8080 >/dev/null 2>&1; then
    echo "✓ webui detected on :8080"
  fi
}

tail_logs() {
  section "Tailing logs (Ctrl+C to stop)"
  set +e
  local log_args=()
  if [ -n "${COMPOSE_PROFILES}" ]; then
    log_args+=(--profile "${COMPOSE_PROFILES}")
  fi
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "${log_args[@]}" logs -f
  set -e
}

shutdown() {
  section "Shutting down services"
  local down_args=("--remove-orphans")
  if [ -n "${COMPOSE_PROFILES}" ]; then
    down_args+=(--profile "${COMPOSE_PROFILES}")
  fi
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" down "${down_args[@]}"
}

# Trap Ctrl+C for graceful shutdown
trap_ctrlc() {
  trap 'echo; warn "Interrupt received, shutting down..."; shutdown; exit 0' INT TERM
}

main() {
  require_cmd docker
  if ! docker compose version >/dev/null 2>&1; then
    error "Docker Compose V2 is required (docker compose ...). Please upgrade Docker Desktop/Engine."
    exit 1
  fi
  require_cmd curl
  require_cmd lsof
  require_cmd jq

  if [ ! -f "${COMPOSE_FILE}" ]; then
    error "Compose file not found: ${COMPOSE_FILE}"
    exit 1
  fi

  section "Starting ${PROJECT_NAME} stack"
  if [ -n "${COMPOSE_PROFILES}" ]; then
    echo "Using profile: ${COMPOSE_PROFILES}"
  fi

  trap_ctrlc
  cleanup_previous
  build_images
  bring_up
  tail_logs
  shutdown
}

main "$@"
