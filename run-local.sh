#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# Local Runner (remote Kafka, local everything else)
# ============================================================================
# Secrets and machine-specific values live in run-local.env (git-ignored), not
# in this script. First time: cp run-local.env.example run-local.env, then
# fill it in. Anything listed in LOCAL_ENV_KEYS below can be overridden there.
#
# JAVA_HOME below is just a hint: if it doesn't point at a valid Java 21
# install, the script auto-detects one (and installs Temurin 21 via
# Chocolatey on Windows if none is found). Set it explicitly if you want to
# pin a specific JDK.
# ============================================================================

JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"

# Required, supplied by run-local.env:
VM_HOST=""
VM_USER=""
VM_PASSWORD=""
DB_PASSWORD=""
JWT_SECRET=""
FAUXNANCE_API_KEY=""

VM_SSH_PORT="22"
REMOTE_APP_DIR="/opt/team4se4-kafka"
KAFKA_VM_PORT="8081"

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="trade_db"
DB_USER="postgres"

JWT_ISSUER="auth-service"
LIQUIBASE_CONTEXTS="demo"

FAUXNANCE_BASE_URL="https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"
POLL_INTERVAL_SECONDS="120"

TRADE_API_PORT="8085"
AUTH_SERVICE_PORT="3000"
RUN_AUTH_SERVICE="true"

FRONTEND_PORT="4200"
RUN_FRONTEND="true"

LOG_DIR="logs"
STATE_DIR=".run-local"
PID_FILE="${STATE_DIR}/pids.env"
REMOTE_OVERRIDE_FILE="${STATE_DIR}/docker-compose.kafka-remote.override.yml"
REMOTE_ENV_FILE="${STATE_DIR}/docker-compose.kafka-remote.env"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${REPO_ROOT}"

OS_NAME="$(uname -s)"
REMOTE_COMPOSE_FLAVOR=""
SSH_WRAPPER=()
SCP_WRAPPER=()

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

info() {
  echo "[run-local] $1"
}

LOCAL_ENV_FILE="${REPO_ROOT}/run-local.env"
LOCAL_ENV_KEYS=(
  VM_HOST VM_USER VM_PASSWORD VM_SSH_PORT KAFKA_VM_PORT
  DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
  JWT_SECRET JWT_ISSUER
  FAUXNANCE_BASE_URL FAUXNANCE_API_KEY POLL_INTERVAL_SECONDS
  JAVA_HOME RUN_AUTH_SERVICE RUN_FRONTEND
)

# Reads KEY=VALUE lines from run-local.env. Parsed, not `source`d: passwords
# containing !, $ or spaces are taken literally instead of being executed by
# the shell. Errors name the offending key, never its value.
load_local_env() {
  [[ -f "${LOCAL_ENV_FILE}" ]] || return 0

  local double_quoted='^"(.*)"$'
  local single_quoted="^'(.*)'\$"
  local line key value allowed known

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line//[[:space:]]/}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" == *=* ]] || fail "run-local.env has a line that is not KEY=VALUE."

    key="${line%%=*}"
    key="${key//[[:space:]]/}"
    value="${line#*=}"
    if [[ "${value}" =~ ${double_quoted} || "${value}" =~ ${single_quoted} ]]; then
      value="${BASH_REMATCH[1]}"
    fi

    allowed=false
    for known in "${LOCAL_ENV_KEYS[@]}"; do
      [[ "${known}" == "${key}" ]] && allowed=true
    done
    [[ "${allowed}" == true ]] || fail "Unknown setting '${key}' in run-local.env."

    printf -v "${key}" '%s' "${value}"
  done < "${LOCAL_ENV_FILE}"
}

load_local_env

missing_dependency() {
  local tool="$1"
  echo "Missing dependency: ${tool}" >&2
  echo "Please install ${tool} and rerun the script." >&2
}

require_cmd() {
  local tool="$1"
  if ! command -v "${tool}" >/dev/null 2>&1; then
    auto_install_dependency "${tool}"
  fi

  if ! command -v "${tool}" >/dev/null 2>&1; then
    missing_dependency "${tool}"
    if [[ "${tool}" == "sshpass" ]]; then
      print_sshpass_install_help >&2
    fi
    exit 1
  fi
}

# Best-effort auto-install for the small set of narrow-purpose CLI tools this
# script needs that aren't typically part of a dev machine's base toolchain.
# Only attempted on Windows, and only when a package manager is present;
# otherwise falls through to the normal missing-dependency message.
auto_install_dependency() {
  local tool="$1"

  case "${OS_NAME}" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *) return 0 ;;
  esac

  case "${tool}" in
    sshpass)
      if command -v winget >/dev/null 2>&1; then
        info "sshpass not found; installing via winget (xhcoding.sshpass-win32)"
        winget install --id xhcoding.sshpass-win32 -e --accept-package-agreements --accept-source-agreements || true
      fi
      ;;
    liquibase)
      if command -v choco >/dev/null 2>&1; then
        info "liquibase not found; installing via Chocolatey"
        choco install liquibase -y || true
      fi
      ;;
    *)
      return 0
      ;;
  esac

  hash -r 2>/dev/null || true
}

print_sshpass_install_help() {
  case "${OS_NAME}" in
    MINGW*|MSYS*|CYGWIN*)
      echo
      echo "sshpass install hints (Windows Git Bash):"
      echo "  Chocolatey: choco install sshpass"
      ;;
    Darwin*)
      echo
      echo "sshpass install hints (macOS):"
      echo "  Homebrew: brew install hudochenkov/sshpass/sshpass"
      ;;
    *)
      echo
      echo "sshpass install hints (Linux):"
      echo "  Debian/Ubuntu: sudo apt-get install -y sshpass"
      echo "  RHEL/Fedora:   sudo dnf install -y sshpass"
      ;;
  esac

  echo
  echo "If you prefer key-based SSH, clear VM_PASSWORD and use your SSH key setup."
}

require_setting() {
  local name="$1"
  local value="${!name}"
  [[ -n "${value}" && "${value}" != CHANGE_ME* ]] \
    || fail "Set ${name} in run-local.env (first time: cp run-local.env.example run-local.env)."
}

validate_required_config() {
  local name
  for name in VM_HOST VM_USER VM_PASSWORD DB_PASSWORD JWT_SECRET FAUXNANCE_API_KEY; do
    require_setting "${name}"
  done

  if ! [[ "${KAFKA_VM_PORT}" =~ ^[0-9]+$ ]]; then
    fail "KAFKA_VM_PORT must be a number."
  fi
  if (( KAFKA_VM_PORT < 8081 || KAFKA_VM_PORT > 8100 )); then
    fail "KAFKA_VM_PORT must be within 8081-8100."
  fi
}

# Searches common Windows JDK install roots for a Java 21 install and prints
# its home directory. Used to self-heal when the JAVA_HOME hint above doesn't
# match this machine (e.g. a different JDK vendor/patch version).
find_java21_home() {
  local roots=(
    "/c/Program Files/Eclipse Adoptium"
    "/c/Program Files/Java"
    "/c/Program Files/Microsoft"
    "/c/Program Files/Zulu"
    "/c/Program Files/AdoptOpenJDK"
    "/c/Program Files/BellSoft"
  )
  local root dir major
  for root in "${roots[@]}"; do
    [[ -d "${root}" ]] || continue
    while IFS= read -r dir; do
      [[ -x "${dir}/bin/java" && -x "${dir}/bin/javac" ]] || continue
      major="$("${dir}/bin/java" -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
      if [[ "${major}" == "21" ]]; then
        printf '%s\n' "${dir}"
        return 0
      fi
    done < <(find "${root}" -maxdepth 1 -type d -iname '*21*' 2>/dev/null)
  done
  return 1
}

install_temurin21_if_possible() {
  case "${OS_NAME}" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *) return 0 ;;
  esac

  if command -v choco >/dev/null 2>&1; then
    info "No Java 21 install found; installing Temurin 21 via Chocolatey (this may take a few minutes)"
    choco install temurin21 -y || info "Automatic Temurin 21 install failed; install a Java 21 JDK manually and rerun."
  fi
}

configure_java() {
  if [[ ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
    local detected
    if detected="$(find_java21_home)"; then
      info "Configured JAVA_HOME is invalid; using auto-detected Java 21 at ${detected}"
      JAVA_HOME="${detected}"
    fi
  fi

  if [[ ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
    install_temurin21_if_possible
    local detected
    if detected="$(find_java21_home)"; then
      JAVA_HOME="${detected}"
    fi
  fi

  [[ -x "${JAVA_HOME}/bin/java" ]] || fail "JAVA_HOME is invalid: ${JAVA_HOME}. Install a Java 21 JDK (e.g. 'choco install temurin21') or update JAVA_HOME at the top of this script."
  [[ -x "${JAVA_HOME}/bin/javac" ]] || fail "JAVA_HOME is missing javac: ${JAVA_HOME}. Update JAVA_HOME at the top of this script."

  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"

  local java_line
  local javac_line
  local java_major
  local javac_major
  java_line="$(java -version 2>&1 | head -n1)"
  javac_line="$(javac -version 2>&1 | head -n1)"

  java_major="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  javac_major="$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)"

  info "java -version => ${java_line}"
  info "javac -version => ${javac_line}"

  [[ "${java_major}" == "21" ]] || fail "Java check failed: expected Java 21, got '${java_line}'."
  [[ "${javac_major}" == "21" ]] || fail "Java check failed: expected javac 21, got '${javac_line}'."
}

check_maven_version() {
  local mvn_ver
  mvn_ver="$(mvn -v 2>/dev/null | awk '/Apache Maven/ {print $3; exit}')"
  [[ -n "${mvn_ver}" ]] || fail "Unable to read Maven version."

  local mvn_major mvn_minor
  mvn_major="${mvn_ver%%.*}"
  mvn_minor="$(echo "${mvn_ver}" | cut -d. -f2)"

  if (( mvn_major < 3 )) || (( mvn_major == 3 && mvn_minor < 9 )); then
    fail "Maven 3.9+ is required. Found ${mvn_ver}."
  fi
}

check_node_version_if_needed() {
  if [[ "${RUN_AUTH_SERVICE}" != "true" && "${RUN_FRONTEND}" != "true" ]]; then
    return
  fi

  local node_major
  node_major="$(node -p "process.versions.node.split('.')[0]")"
  if (( node_major < 20 )); then
    fail "Node.js 20+ is required for sprint-08-auth-service/sprint-09-trading-ui. Found Node ${node_major}."
  fi
}

check_local_dependencies() {
  require_cmd git
  require_cmd mvn
  require_cmd ssh
  require_cmd scp
  require_cmd curl
  require_cmd psql

  if [[ -n "${VM_PASSWORD}" ]]; then
    require_cmd sshpass
  fi

  if [[ "${RUN_AUTH_SERVICE}" == "true" || "${RUN_FRONTEND}" == "true" ]]; then
    require_cmd node
    require_cmd npm
  fi

  if [[ "${RUN_AUTH_SERVICE}" == "true" ]]; then
    require_cmd liquibase
  fi

  check_maven_version
  check_node_version_if_needed
}

setup_ssh_wrappers() {
  local ssh_opts=(
    -T
    -p "${VM_SSH_PORT}"
    -o StrictHostKeyChecking=accept-new
    -o UserKnownHostsFile="${HOME}/.ssh/known_hosts"
    -o LogLevel=ERROR
  )

  local scp_opts=(
    -P "${VM_SSH_PORT}"
    -o StrictHostKeyChecking=accept-new
    -o UserKnownHostsFile="${HOME}/.ssh/known_hosts"
    -o LogLevel=ERROR
  )

  if [[ -n "${VM_PASSWORD}" ]]; then
    export SSHPASS="${VM_PASSWORD}"
    # Disable pubkey auth. Without this, ssh tries any default identity file
    # (e.g. ~/.ssh/id_ed25519) first; if it's passphrase-protected, ssh
    # prompts locally for the passphrase — a prompt sshpass can't answer
    # (it only auto-fills a remote "password:"/keyboard-interactive prompt)
    # — and hangs forever with no TTY to type into. (Don't also restrict
    # PreferredAuthentications to just "password": some servers only expose
    # this login via keyboard-interactive, and forcing "password" breaks it.)
    ssh_opts+=(-o PubkeyAuthentication=no)
    scp_opts+=(-o PubkeyAuthentication=no)
    SSH_WRAPPER=(sshpass -e ssh "${ssh_opts[@]}")
    SCP_WRAPPER=(sshpass -e scp "${scp_opts[@]}")
  else
    SSH_WRAPPER=(ssh "${ssh_opts[@]}")
    SCP_WRAPPER=(scp "${scp_opts[@]}")
  fi
}

remote_ssh() {
  local cmd="${1:-}"
  [[ -n "${cmd}" ]] || fail "Internal error: remote_ssh called without a command"

  # Feed the command over stdin (via a here-string) rather than encoding it
  # into a quoted ssh argument: on Windows, sshpass/ssh argument re-quoting
  # corrupts complex commands containing nested quotes (e.g. embedded double
  # quotes).
  #
  # Also wrap in a timeout with retries: the Windows sshpass port (there is
  # no real `sshpass` on Windows) is intermittently flaky and can hang the
  # whole handshake indefinitely for no discernible reason — observed
  # anywhere from ~1 in 4 calls up to several in a row against this VM. A
  # genuine command failure (e.g. `docker info` returning non-zero because
  # Docker really is down) returns immediately with its own exit code and is
  # not retried; only an actual timeout (exit 124) is.
  local attempts=5
  local attempt rc
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    # Use a clean non-login shell so noisy profile scripts on the VM cannot
    # pollute command output or emit control sequences.
    timeout 25 "${SSH_WRAPPER[@]}" "${VM_USER}@${VM_HOST}" "bash --noprofile --norc -s" <<< "${cmd}"
    rc=$?
    if [[ "${rc}" -ne 124 ]]; then
      return "${rc}"
    fi
    info "Remote SSH command timed out (attempt ${attempt}/${attempts}, likely a flaky sshpass hang) — retrying" >&2
  done
  return 124
}

remote_scp() {
  local src="$1"
  local dest="$2"

  [[ -f "${src}" ]] || fail "Missing local file for remote copy: ${src}"

  # Stream files over SSH instead of relying on scp protocol. This avoids
  # protocol corruption when a remote shell profile writes unexpected output.
  local eof_marker="RUN_LOCAL_FILE_EOF_$(date +%s)_$$"

  # Same flaky-sshpass timeout/retry treatment as remote_ssh.
  local attempts=5
  local attempt rc
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    {
      printf "cat > %q <<'%s'\n" "${dest}" "${eof_marker}"
      cat "${src}"
      printf "\n%s\n" "${eof_marker}"
    } | timeout 25 "${SSH_WRAPPER[@]}" "${VM_USER}@${VM_HOST}" "bash --noprofile --norc -s"
    rc=$?
    if [[ "${rc}" -ne 124 ]]; then
      return "${rc}"
    fi
    info "Remote copy timed out (attempt ${attempt}/${attempts}, likely a flaky sshpass hang) — retrying" >&2
  done
  return 124
}

verify_ssh_and_remote_docker() {
  info "Validating SSH connection to ${VM_USER}@${VM_HOST}:${VM_SSH_PORT}"
  remote_ssh "echo SSH_OK >/dev/null" || fail "SSH connection failed"

  info "Checking remote Docker availability"
  remote_ssh "docker info >/dev/null 2>&1" || fail "Remote Docker unavailable"

  local detected_compose
  detected_compose="$(remote_ssh 'if docker compose version >/dev/null 2>&1; then echo docker_compose_plugin; elif command -v docker-compose >/dev/null 2>&1; then echo docker_compose_legacy; else exit 7; fi')" || fail "Remote Docker Compose unavailable"

  # Defensive cleanup: Windows ssh/terminal hops can inject CR/LF bytes.
  detected_compose="${detected_compose//$'\r'/}"
  detected_compose="${detected_compose//$'\n'/}"
  detected_compose="$(printf '%s' "${detected_compose}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"

  if [[ "${detected_compose}" == *"docker_compose_plugin"* ]]; then
    REMOTE_COMPOSE_FLAVOR="docker_compose_plugin"
  elif [[ "${detected_compose}" == *"docker_compose_legacy"* ]]; then
    REMOTE_COMPOSE_FLAVOR="docker_compose_legacy"
  else
    fail "Remote Docker Compose detection returned an unexpected value: '${detected_compose}'."
  fi
}

create_remote_kafka_override() {
  mkdir -p "${STATE_DIR}"
  cat > "${REMOTE_OVERRIDE_FILE}" <<EOF
services:
  kafka:
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://${VM_HOST}:${KAFKA_VM_PORT}
    ports:
      - "${KAFKA_VM_PORT}:9092"
EOF

  # docker-compose.yml requires JWT_SECRET (it's a hard `${JWT_SECRET:?...}`
  # on the trade-api/auth-service blocks) and validates env interpolation for
  # the whole file even when we only start the kafka service. Ship a .env
  # alongside it on the VM so `up -d kafka` doesn't fail on unrelated vars.
  cat > "${REMOTE_ENV_FILE}" <<EOF
JWT_SECRET=${JWT_SECRET}
EOF
}

copy_kafka_files_to_vm() {
  info "Preparing remote Kafka workspace at ${REMOTE_APP_DIR}"
  # REMOTE_APP_DIR commonly lives under a root-owned path (e.g. /opt) that
  # VM_USER can't write to directly; use sudo to create it and hand it over.
  remote_ssh "mkdir -p '${REMOTE_APP_DIR}/scripts' 2>/dev/null || { sudo mkdir -p '${REMOTE_APP_DIR}/scripts' && sudo chown -R \"\$(id -u)\":\"\$(id -g)\" '${REMOTE_APP_DIR}'; }"

  remote_scp "${REPO_ROOT}/docker-compose.yml" "${REMOTE_APP_DIR}/docker-compose.yml"
  remote_scp "${REPO_ROOT}/scripts/kafka-init.sh" "${REMOTE_APP_DIR}/scripts/kafka-init.sh"
  remote_scp "${REMOTE_OVERRIDE_FILE}" "${REMOTE_APP_DIR}/docker-compose.kafka-remote.override.yml"
  remote_scp "${REMOTE_ENV_FILE}" "${REMOTE_APP_DIR}/.env"

  remote_ssh "chmod +x '${REMOTE_APP_DIR}/scripts/kafka-init.sh'"
}

remote_compose() {
  local compose_args="$1"
  local compose_cmd
  case "${REMOTE_COMPOSE_FLAVOR}" in
    docker_compose_plugin)
      compose_cmd="docker compose"
      ;;
    docker_compose_legacy)
      compose_cmd="docker-compose"
      ;;
    *)
      fail "Internal error: remote compose flavor is not set."
      ;;
  esac

  remote_ssh "cd '${REMOTE_APP_DIR}' && ${compose_cmd} -f docker-compose.yml -f docker-compose.kafka-remote.override.yml ${compose_args}"
}

wait_for_remote_kafka() {
  local attempts=40
  local sleep_seconds=3

  for ((i=1; i<=attempts; i++)); do
    if remote_compose "exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 >/dev/null 2>&1"; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done

  return 1
}

check_tcp_reachable_once() {
  local host="$1"
  local port="$2"

  if command -v nc >/dev/null 2>&1; then
    nc -z "${host}" "${port}" >/dev/null 2>&1
    return $?
  fi

  if command -v timeout >/dev/null 2>&1; then
    timeout 3 bash -c "cat < /dev/null > /dev/tcp/${host}/${port}" >/dev/null 2>&1
    return $?
  fi

  bash -c "cat < /dev/null > /dev/tcp/${host}/${port}" >/dev/null 2>&1
}

wait_for_tcp_reachable() {
  local host="$1"
  local port="$2"
  local attempts=20

  for ((i=1; i<=attempts; i++)); do
    if check_tcp_reachable_once "${host}" "${port}"; then
      return 0
    fi
    sleep 2
  done

  return 1
}

start_remote_kafka() {
  info "Starting Kafka on remote VM"
  remote_compose "up -d kafka" || fail "Kafka failed to start"

  info "Waiting for remote Kafka broker readiness"
  if ! wait_for_remote_kafka; then
    remote_compose "ps kafka" || true
    remote_compose "logs --tail=200 kafka" || true
    fail "Kafka failed to start"
  fi

  info "Creating required Kafka topics on remote VM"
  remote_ssh "cd '${REMOTE_APP_DIR}' && bash scripts/kafka-init.sh" || fail "Kafka topic creation failed"

  info "Verifying Kafka port reachability from local machine (${VM_HOST}:${KAFKA_VM_PORT})"
  if ! wait_for_tcp_reachable "${VM_HOST}" "${KAFKA_VM_PORT}"; then
    fail "Kafka port unreachable"
  fi
}

postgres_admin_query() {
  local sql="$1"
  PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d postgres \
    -v ON_ERROR_STOP=1 \
    -Atqc "${sql}"
}

ensure_local_database() {
  info "Validating local PostgreSQL availability"
  if ! postgres_admin_query "SELECT 1" >/dev/null 2>&1; then
    fail "Database unavailable"
  fi

  local exists
  exists="$(postgres_admin_query "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'")"
  if [[ "${exists}" != "1" ]]; then
    info "Creating database ${DB_NAME}"
    postgres_admin_query "CREATE DATABASE \"${DB_NAME}\""
  fi

  if ! PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -Atqc "SELECT 1" >/dev/null 2>&1; then
    fail "Database unavailable"
  fi
}

build_projects() {
  info "Building sprint-05-domain-engine"
  mvn -f sprint-05-domain-engine/pom.xml -DskipTests install

  info "Building sprint-06-trade-api"
  mvn -f sprint-06-trade-api/pom.xml -DskipTests clean package

  info "Building executor"
  mvn -f executor/pom.xml -DskipTests clean package

  if [[ "${RUN_AUTH_SERVICE}" == "true" ]]; then
    info "Installing and building sprint-08-auth-service"

    local npm_attempts=3
    local npm_attempt
    for ((npm_attempt=1; npm_attempt<=npm_attempts; npm_attempt++)); do
      if npm --prefix sprint-08-auth-service ci; then
        break
      fi

      if (( npm_attempt < npm_attempts )); then
        info "auth-service npm ci failed (attempt ${npm_attempt}/${npm_attempts}); retrying after cleanup"
        rm -rf sprint-08-auth-service/node_modules/argon2/prebuilds/win32-x64 2>/dev/null || true
        rm -rf sprint-08-auth-service/node_modules/.package-lock 2>/dev/null || true
        sleep 2
      else
        fail "auth-service dependency install failed (npm ci). Ensure no process is locking sprint-08-auth-service/node_modules and retry."
      fi
    done

    npm --prefix sprint-08-auth-service run build
  fi

  if [[ "${RUN_FRONTEND}" == "true" ]]; then
    info "Installing sprint-09-trading-ui"

    local npm_attempts=3
    local npm_attempt
    for ((npm_attempt=1; npm_attempt<=npm_attempts; npm_attempt++)); do
      if npm --prefix sprint-09-trading-ui ci; then
        break
      fi

      if (( npm_attempt < npm_attempts )); then
        info "trading-ui npm ci failed (attempt ${npm_attempt}/${npm_attempts}); retrying after cleanup"
        rm -rf sprint-09-trading-ui/node_modules/.package-lock 2>/dev/null || true
        sleep 2
      else
        fail "trading-ui dependency install failed (npm ci). Ensure no process is locking sprint-09-trading-ui/node_modules and retry."
      fi
    done
  fi
}

wait_for_http_contains() {
  local url="$1"
  local expected_fragment="$2"
  local attempts="$3"

  for ((i=1; i<=attempts; i++)); do
    local body
    body="$(curl -fsS "${url}" 2>/dev/null || true)"
    if [[ -n "${body}" ]] && echo "${body}" | grep -q "${expected_fragment}"; then
      return 0
    fi
    sleep 2
  done

  return 1
}

wait_for_http_ok() {
  local url="$1"
  local attempts="$2"

  for ((i=1; i<=attempts; i++)); do
    if curl -fsS -o /dev/null "${url}" 2>/dev/null; then
      return 0
    fi
    sleep 3
  done

  return 1
}

find_listening_pids() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | sort -u
    return
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -ano 2>/dev/null \
      | tr -d '\r' \
      | awk -v port=":${port}" '$1 == "TCP" && $2 ~ port "$" && $4 == "LISTENING" {print $5}' \
      | sort -u
    return
  fi
}

stop_pid_cross_platform() {
  local pid="$1"

  kill "${pid}" >/dev/null 2>&1 || true
  sleep 1

  if command -v taskkill >/dev/null 2>&1; then
    taskkill //PID "${pid}" //T //F >/dev/null 2>&1 || true
  fi
}

kill_port_listeners() {
  local port="$1"
  local pids pid

  pids="$(find_listening_pids "${port}" || true)"
  [[ -n "${pids}" ]] || return 0

  while IFS= read -r pid; do
    [[ -n "${pid}" ]] || continue
    stop_pid_cross_platform "${pid}"
  done <<< "${pids}"
}

ensure_port_free() {
  local service_name="$1"
  local port="$2"

  find_listening_pids "${port}" | grep -q . || return 0

  info "Port ${port} is already in use; stopping existing listener(s) before starting ${service_name}"
  kill_port_listeners "${port}"
}

# `$!` after a backgrounded command (what FRONTEND_PID etc. are) is a Git
# Bash/MSYS-level PID. MSYS's own `kill` understands it, but the real
# process actually holding the port - the far end of a nohup/npm/mvn
# wrapper chain - has a separate native Windows PID that only `taskkill`
# can see, and `taskkill //PID` on the MSYS PID targets nothing (or, by
# sheer coincidence, some unrelated real process with that same number).
# Finding the port's real listener via `netstat` (`find_listening_pids`,
# already proven out by `ensure_port_free` above) and killing *that* is
# the one approach confirmed to actually free the port.
stop_port_listeners() {
  local service_name="$1"
  local port="$2"

  find_listening_pids "${port}" | grep -q . || return 0

  info "Stopping ${service_name} (port ${port})"
  kill_port_listeners "${port}"
}

load_pids() {
  TRADE_API_PID=""
  AUTH_SERVICE_PID=""
  EXECUTOR_PID=""
  FRONTEND_PID=""

  if [[ -f "${PID_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${PID_FILE}"
  fi
}

save_pids() {
  mkdir -p "${STATE_DIR}"
  cat > "${PID_FILE}" <<EOF
TRADE_API_PID=${TRADE_API_PID:-}
AUTH_SERVICE_PID=${AUTH_SERVICE_PID:-}
EXECUTOR_PID=${EXECUTOR_PID:-}
FRONTEND_PID=${FRONTEND_PID:-}
EOF
}

start_trade_api() {
  info "Starting trade-api on localhost:${TRADE_API_PORT}"
  mkdir -p "${LOG_DIR}"
  ensure_port_free "trade-api" "${TRADE_API_PORT}"

  DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  DB_USER="${DB_USER}" \
  DB_PASSWORD="${DB_PASSWORD}" \
  JWT_SECRET="${JWT_SECRET}" \
  JWT_ISSUER="${JWT_ISSUER}" \
  LIQUIBASE_CONTEXTS="${LIQUIBASE_CONTEXTS}" \
  KAFKA_BOOTSTRAP_SERVERS="${VM_HOST}:${KAFKA_VM_PORT}" \
  SERVER_PORT="${TRADE_API_PORT}" \
  nohup mvn -f sprint-06-trade-api/pom.xml spring-boot:run > "${LOG_DIR}/trade-api.log" 2>&1 &

  TRADE_API_PID=$!
  save_pids

  if ! wait_for_http_contains "http://localhost:${TRADE_API_PORT}/actuator/health" '"status":"UP"' 90; then
    tail -n 120 "${LOG_DIR}/trade-api.log" || true
    fail "Service failed to start: trade-api"
  fi

  if ! kill -0 "${TRADE_API_PID}" >/dev/null 2>&1; then
    tail -n 120 "${LOG_DIR}/trade-api.log" || true
    fail "Service failed to start: trade-api"
  fi

  info "Started trade-api on port ${TRADE_API_PORT}"
  info "PID: ${TRADE_API_PID}"
}

run_auth_liquibase() {
  if [[ "${RUN_AUTH_SERVICE}" != "true" ]]; then
    return
  fi

  local driver_jar
  driver_jar="$(ls "${HOME}"/.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar 2>/dev/null | sort | tail -n1 || true)"
  [[ -n "${driver_jar}" ]] || fail "PostgreSQL JDBC driver jar not found in Maven repository for Liquibase"

  info "Running auth-service Liquibase migrations"
  # On Windows, liquibase.bat internally shells out to `find /i "version"` to
  # parse `java -version`. MSYS's GNU find normally shadows Windows'
  # find.exe on PATH and doesn't understand /i, which breaks the launcher
  # with "find: '/i': No such file or directory". Put System32 first so the
  # real find.exe is picked up for this call.
  # --changelog-file must be relative to --search-path: passing the full
  # absolute path for both (as older Liquibase releases tolerated) makes
  # 5.x try to resolve the absolute path *within* the search path and fail
  # with "was not found in the configured search path".
  PATH="/c/Windows/System32:${PATH}" \
  liquibase \
    --classpath="${driver_jar}" \
    --url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
    --username="${DB_USER}" \
    --password="${DB_PASSWORD}" \
    --changelog-file="db.changelog-master.xml" \
    --search-path="${REPO_ROOT}/sprint-08-auth-service/db/changelog" \
    update
}

start_auth_service() {
  if [[ "${RUN_AUTH_SERVICE}" != "true" ]]; then
    return
  fi

  info "Starting auth-service on localhost:${AUTH_SERVICE_PORT}"
  ensure_port_free "auth-service" "${AUTH_SERVICE_PORT}"

  PORT="${AUTH_SERVICE_PORT}" \
  DB_HOST="${DB_HOST}" \
  DB_PORT="${DB_PORT}" \
  DB_NAME="${DB_NAME}" \
  DB_USER="${DB_USER}" \
  DB_PASSWORD="${DB_PASSWORD}" \
  JWT_SECRET="${JWT_SECRET}" \
  JWT_ISSUER="${JWT_ISSUER}" \
  nohup node sprint-08-auth-service/dist/main.js > "${LOG_DIR}/auth-service.log" 2>&1 &

  AUTH_SERVICE_PID=$!
  save_pids

  if ! wait_for_http_contains "http://localhost:${AUTH_SERVICE_PORT}/health" '"status":"up"' 60; then
    tail -n 120 "${LOG_DIR}/auth-service.log" || true
    fail "Service failed to start: auth-service"
  fi

  if ! kill -0 "${AUTH_SERVICE_PID}" >/dev/null 2>&1; then
    tail -n 120 "${LOG_DIR}/auth-service.log" || true
    fail "Service failed to start: auth-service"
  fi

  info "Started auth-service on port ${AUTH_SERVICE_PORT}"
  info "PID: ${AUTH_SERVICE_PID}"
}

start_executor() {
  info "Starting executor"

  DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  DB_USER="${DB_USER}" \
  DB_PASSWORD="${DB_PASSWORD}" \
  KAFKA_BOOTSTRAP_SERVERS="${VM_HOST}:${KAFKA_VM_PORT}" \
  FAUXNANCE_BASE_URL="${FAUXNANCE_BASE_URL}" \
  FAUXNANCE_API_KEY="${FAUXNANCE_API_KEY}" \
  POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS}" \
  nohup mvn -f executor/pom.xml -DskipTests exec:java -Dexec.mainClass=org.leap.executor.Main > "${LOG_DIR}/executor.log" 2>&1 &

  EXECUTOR_PID=$!
  save_pids

  sleep 8
  if ! kill -0 "${EXECUTOR_PID}" >/dev/null 2>&1; then
    tail -n 120 "${LOG_DIR}/executor.log" || true
    fail "Service failed to start: executor"
  fi

  info "Started executor"
  info "PID: ${EXECUTOR_PID}"
}

start_frontend() {
  if [[ "${RUN_FRONTEND}" != "true" ]]; then
    return
  fi

  info "Starting frontend (ng serve) on localhost:${FRONTEND_PORT}"
  mkdir -p "${LOG_DIR}"
  ensure_port_free "frontend" "${FRONTEND_PORT}"

  # `proxy.conf.json` (sprint-09-trading-ui/proxy.conf.json) forwards
  # /auth-api -> localhost:${AUTH_SERVICE_PORT} and /trade-api ->
  # localhost:${TRADE_API_PORT}; it hardcodes those hosts, so this only
  # lines up with the backends this script just started when their ports
  # are left at the defaults.
  nohup npm --prefix sprint-09-trading-ui run start -- --port "${FRONTEND_PORT}" \
    > "${LOG_DIR}/frontend.log" 2>&1 &

  FRONTEND_PID=$!
  save_pids

  if ! wait_for_http_ok "http://localhost:${FRONTEND_PORT}/" 60; then
    tail -n 120 "${LOG_DIR}/frontend.log" || true
    fail "Service failed to start: frontend"
  fi

  if ! kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
    tail -n 120 "${LOG_DIR}/frontend.log" || true
    fail "Service failed to start: frontend"
  fi

  info "Started frontend on port ${FRONTEND_PORT}"
  info "PID: ${FRONTEND_PID}"
}

is_pid_running() {
  local pid="$1"
  [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1
}

stop_pid_if_running() {
  local service_name="$1"
  local pid="$2"

  if is_pid_running "${pid}"; then
    info "Stopping ${service_name} (PID ${pid})"
    kill "${pid}" >/dev/null 2>&1 || true
    sleep 2
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
    # `kill` only signals the tracked PID itself (the shell/npm wrapper
    # nohup handed back) - on Windows that wrapper's actual child process
    # (the JVM, or here node/ng serve) survives it and keeps the port
    # bound. taskkill //T kills the whole process tree.
    if command -v taskkill >/dev/null 2>&1; then
      taskkill //PID "${pid}" //T //F >/dev/null 2>&1 || true
    fi
  fi
}

stop_local_services() {
  load_pids

  stop_pid_if_running "frontend" "${FRONTEND_PID}"
  stop_port_listeners "frontend" "${FRONTEND_PORT}"
  stop_pid_if_running "executor" "${EXECUTOR_PID}"
  stop_pid_if_running "auth-service" "${AUTH_SERVICE_PID}"
  stop_pid_if_running "trade-api" "${TRADE_API_PID}"

  TRADE_API_PID=""
  AUTH_SERVICE_PID=""
  EXECUTOR_PID=""
  FRONTEND_PID=""
  save_pids

  info "Local services stopped"
}

kafka_stop() {
  validate_required_config
  configure_java
  check_local_dependencies
  setup_ssh_wrappers
  verify_ssh_and_remote_docker

  info "Stopping remote Kafka"
  remote_compose "stop kafka" || true
}

status_line() {
  local label="$1"
  local value="$2"
  printf "%-20s %s\n" "${label}:" "${value}"
}

service_status() {
  local pid="$1"
  if is_pid_running "${pid}"; then
    echo "RUNNING"
  else
    echo "STOPPED"
  fi
}

db_status() {
  if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Atqc "SELECT 1" >/dev/null 2>&1; then
    echo "RUNNING"
  else
    echo "STOPPED"
  fi
}

kafka_status() {
  setup_ssh_wrappers
  if ! remote_ssh "echo 1 >/dev/null" >/dev/null 2>&1; then
    echo "UNREACHABLE"
    return
  fi

  if remote_ssh "cd '${REMOTE_APP_DIR}' && if docker compose version >/dev/null 2>&1; then docker compose -f docker-compose.yml -f docker-compose.kafka-remote.override.yml exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 >/dev/null 2>&1; elif command -v docker-compose >/dev/null 2>&1; then docker-compose -f docker-compose.yml -f docker-compose.kafka-remote.override.yml exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 >/dev/null 2>&1; else exit 1; fi"; then
    echo "RUNNING"
  else
    echo "STOPPED"
  fi
}

print_summary() {
  load_pids

  local kafka_state
  local db_state
  local trade_state
  local auth_state
  local executor_state
  local frontend_state

  kafka_state="$(kafka_status)"
  db_state="$(db_status)"
  trade_state="$(service_status "${TRADE_API_PID}")"
  auth_state="$(service_status "${AUTH_SERVICE_PID}")"
  executor_state="$(service_status "${EXECUTOR_PID}")"
  frontend_state="$(service_status "${FRONTEND_PID}")"

  echo "========================================"
  echo " LOCAL TRADE PLATFORM"
  echo "========================================"
  echo
  echo "Kafka:"
  status_line "  VM" "${VM_HOST}"
  status_line "  Port" "${KAFKA_VM_PORT}"
  status_line "  Status" "${kafka_state}"
  echo
  echo "Database:"
  status_line "  Host" "${DB_HOST}"
  status_line "  Port" "${DB_PORT}"
  status_line "  Status" "${db_state}"
  echo
  echo "Trade API:"
  status_line "  Port" "${TRADE_API_PORT}"
  status_line "  Status" "${trade_state}"
  echo
  if [[ "${RUN_AUTH_SERVICE}" == "true" ]]; then
    echo "Auth Service:"
    status_line "  Port" "${AUTH_SERVICE_PORT}"
    status_line "  Status" "${auth_state}"
    echo
  fi
  echo "Executor:"
  status_line "  Status" "${executor_state}"
  echo
  if [[ "${RUN_FRONTEND}" == "true" ]]; then
    echo "Frontend:"
    status_line "  Port" "${FRONTEND_PORT}"
    status_line "  Status" "${frontend_state}"
    echo
  fi
  echo "========================================"
  echo "Everything is ready."
  echo "========================================"
  echo
  if [[ "${RUN_FRONTEND}" == "true" ]]; then
    echo "Open the app:        http://localhost:${FRONTEND_PORT}"
  fi
  echo "Stop local services: ./run-local.sh stop"
  echo "Stop remote Kafka:   ./run-local.sh kafka-stop"
  echo "Show status:         ./run-local.sh status"
}

start_all() {
  validate_required_config
  configure_java
  check_local_dependencies
  setup_ssh_wrappers

  verify_ssh_and_remote_docker
  create_remote_kafka_override
  copy_kafka_files_to_vm
  start_remote_kafka

  ensure_local_database
  stop_local_services
  build_projects

  start_trade_api
  run_auth_liquibase
  start_auth_service
  start_executor
  start_frontend

  print_summary
}

show_status() {
  validate_required_config
  configure_java
  check_local_dependencies
  print_summary
}

restart_all() {
  stop_local_services
  start_all
}

usage() {
  cat <<EOF
Usage:
  ./run-local.sh              Start full local stack (incl. frontend) with remote Kafka
  ./run-local.sh start        Same as default
  ./run-local.sh stop         Stop local services started by this script
  ./run-local.sh kafka-stop   Stop Kafka on the remote VM
  ./run-local.sh status       Show Kafka/DB/service status
  ./run-local.sh restart      Restart local services and ensure Kafka is running
EOF
}

main() {
  local command="${1:-start}"

  case "${command}" in
    start)
      start_all
      ;;
    stop)
      stop_local_services
      ;;
    kafka-stop)
      kafka_stop
      ;;
    status)
      show_status
      ;;
    restart)
      restart_all
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      usage
      fail "Unknown command: ${command}"
      ;;
  esac
}

main "$@"
