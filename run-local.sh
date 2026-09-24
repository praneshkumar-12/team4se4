#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# Local Runner (remote Kafka, local everything else)
# ============================================================================
# Edit the values in this block for your machine/environment.
#
# IMPORTANT: if Java is installed in a different location, change ONLY JAVA_HOME.
# ============================================================================

JAVA_HOME="/c/Program Files/Java/jdk-21"

VM_HOST="CHANGE_ME_VM_HOST"
VM_USER="CHANGE_ME_VM_USER"
VM_PASSWORD="CHANGE_ME_VM_PASSWORD"
VM_SSH_PORT="22"
REMOTE_APP_DIR="/opt/team4se4-kafka"
KAFKA_VM_PORT="8081"

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="trade_db"
DB_USER="postgres"
DB_PASSWORD="postgres_dev_password"

JWT_SECRET="dev-only-change-me-this-secret-is-not-for-production-use"
JWT_ISSUER="auth-service"
LIQUIBASE_CONTEXTS="demo"

FAUXNANCE_BASE_URL="https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"
FAUXNANCE_API_KEY="replace-with-your-fauxnance-key"
POLL_INTERVAL_SECONDS="120"

TRADE_API_PORT="8085"
AUTH_SERVICE_PORT="3000"
RUN_AUTH_SERVICE="true"

LOG_DIR="logs"
STATE_DIR=".run-local"
PID_FILE="${STATE_DIR}/pids.env"
REMOTE_OVERRIDE_FILE="${STATE_DIR}/docker-compose.kafka-remote.override.yml"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${REPO_ROOT}"

OS_NAME="$(uname -s)"
REMOTE_COMPOSE_CMD=""
SSH_WRAPPER=()
SCP_WRAPPER=()

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

info() {
  echo "[run-local] $1"
}

missing_dependency() {
  local tool="$1"
  echo "Missing dependency: ${tool}" >&2
  echo "Please install ${tool} and rerun the script." >&2
}

require_cmd() {
  local tool="$1"
  if ! command -v "${tool}" >/dev/null 2>&1; then
    missing_dependency "${tool}"
    if [[ "${tool}" == "sshpass" ]]; then
      print_sshpass_install_help >&2
    fi
    exit 1
  fi
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

validate_required_config() {
  [[ -n "${VM_HOST}" && "${VM_HOST}" != "CHANGE_ME_VM_HOST" ]] || fail "Set VM_HOST near the top of this script."
  [[ -n "${VM_USER}" && "${VM_USER}" != "CHANGE_ME_VM_USER" ]] || fail "Set VM_USER near the top of this script."
  [[ -n "${VM_PASSWORD}" && "${VM_PASSWORD}" != "CHANGE_ME_VM_PASSWORD" ]] || fail "Set VM_PASSWORD near the top of this script."

  if ! [[ "${KAFKA_VM_PORT}" =~ ^[0-9]+$ ]]; then
    fail "KAFKA_VM_PORT must be a number."
  fi
  if (( KAFKA_VM_PORT < 8081 || KAFKA_VM_PORT > 8100 )); then
    fail "KAFKA_VM_PORT must be within 8081-8100."
  fi
}

configure_java() {
  [[ -x "${JAVA_HOME}/bin/java" ]] || fail "JAVA_HOME is invalid: ${JAVA_HOME}. Update JAVA_HOME at the top of this script."
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
  if [[ "${RUN_AUTH_SERVICE}" != "true" ]]; then
    return
  fi

  local node_major
  node_major="$(node -p "process.versions.node.split('.')[0]")"
  if (( node_major < 20 )); then
    fail "Node.js 20+ is required for sprint-08-auth-service. Found Node ${node_major}."
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

  if [[ "${RUN_AUTH_SERVICE}" == "true" ]]; then
    require_cmd node
    require_cmd npm
    require_cmd liquibase
  fi

  check_maven_version
  check_node_version_if_needed
}

setup_ssh_wrappers() {
  local ssh_opts=(
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
    SSH_WRAPPER=(sshpass -e ssh "${ssh_opts[@]}")
    SCP_WRAPPER=(sshpass -e scp "${scp_opts[@]}")
  else
    SSH_WRAPPER=(ssh "${ssh_opts[@]}")
    SCP_WRAPPER=(scp "${scp_opts[@]}")
  fi
}

remote_ssh() {
  local cmd="$1"
  "${SSH_WRAPPER[@]}" "${VM_USER}@${VM_HOST}" "${cmd}"
}

remote_scp() {
  local src="$1"
  local dest="$2"
  "${SCP_WRAPPER[@]}" "${src}" "${VM_USER}@${VM_HOST}:${dest}"
}

verify_ssh_and_remote_docker() {
  info "Validating SSH connection to ${VM_USER}@${VM_HOST}:${VM_SSH_PORT}"
  remote_ssh "echo SSH_OK >/dev/null" || fail "SSH connection failed"

  info "Checking remote Docker availability"
  remote_ssh "docker info >/dev/null 2>&1" || fail "Remote Docker unavailable"

  REMOTE_COMPOSE_CMD="$(remote_ssh 'if docker compose version >/dev/null 2>&1; then echo "docker compose"; elif command -v docker-compose >/dev/null 2>&1; then echo "docker-compose"; else exit 7; fi')" || fail "Remote Docker Compose unavailable"
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
}

copy_kafka_files_to_vm() {
  info "Preparing remote Kafka workspace at ${REMOTE_APP_DIR}"
  remote_ssh "mkdir -p '${REMOTE_APP_DIR}/scripts'"

  remote_scp "${REPO_ROOT}/docker-compose.yml" "${REMOTE_APP_DIR}/docker-compose.yml"
  remote_scp "${REPO_ROOT}/scripts/kafka-init.sh" "${REMOTE_APP_DIR}/scripts/kafka-init.sh"
  remote_scp "${REMOTE_OVERRIDE_FILE}" "${REMOTE_APP_DIR}/docker-compose.kafka-remote.override.yml"

  remote_ssh "chmod +x '${REMOTE_APP_DIR}/scripts/kafka-init.sh'"
}

remote_compose() {
  local compose_args="$1"
  remote_ssh "cd '${REMOTE_APP_DIR}' && ${REMOTE_COMPOSE_CMD} -f docker-compose.yml -f docker-compose.kafka-remote.override.yml ${compose_args}"
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
    npm --prefix sprint-08-auth-service ci
    npm --prefix sprint-08-auth-service run build
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

load_pids() {
  TRADE_API_PID=""
  AUTH_SERVICE_PID=""
  EXECUTOR_PID=""

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
EOF
}

start_trade_api() {
  info "Starting trade-api on localhost:${TRADE_API_PORT}"
  mkdir -p "${LOG_DIR}"

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
  liquibase \
    --classpath="${driver_jar}" \
    --url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
    --username="${DB_USER}" \
    --password="${DB_PASSWORD}" \
    --changelog-file="${REPO_ROOT}/sprint-08-auth-service/db/changelog/db.changelog-master.xml" \
    --search-path="${REPO_ROOT}/sprint-08-auth-service/db/changelog" \
    update
}

start_auth_service() {
  if [[ "${RUN_AUTH_SERVICE}" != "true" ]]; then
    return
  fi

  info "Starting auth-service on localhost:${AUTH_SERVICE_PORT}"

  PORT="${AUTH_SERVICE_PORT}" \
  DB_HOST="${DB_HOST}" \
  DB_PORT="${DB_PORT}" \
  DB_NAME="${DB_NAME}" \
  DB_USER="${DB_USER}" \
  DB_PASSWORD="${DB_PASSWORD}" \
  JWT_SECRET="${JWT_SECRET}" \
  JWT_ISSUER="${JWT_ISSUER}" \
  nohup npm --prefix sprint-08-auth-service run start > "${LOG_DIR}/auth-service.log" 2>&1 &

  AUTH_SERVICE_PID=$!
  save_pids

  if ! wait_for_http_contains "http://localhost:${AUTH_SERVICE_PORT}/health" '"status":"up"' 60; then
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
  fi
}

stop_local_services() {
  load_pids

  stop_pid_if_running "executor" "${EXECUTOR_PID}"
  stop_pid_if_running "auth-service" "${AUTH_SERVICE_PID}"
  stop_pid_if_running "trade-api" "${TRADE_API_PID}"

  TRADE_API_PID=""
  AUTH_SERVICE_PID=""
  EXECUTOR_PID=""
  save_pids

  info "Local services stopped"
}

kafka_stop() {
  validate_required_config
  check_local_dependencies
  configure_java
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

  kafka_state="$(kafka_status)"
  db_state="$(db_status)"
  trade_state="$(service_status "${TRADE_API_PID}")"
  auth_state="$(service_status "${AUTH_SERVICE_PID}")"
  executor_state="$(service_status "${EXECUTOR_PID}")"

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
  echo "========================================"
  echo "Everything is ready."
  echo "========================================"
  echo
  echo "Stop local services: ./run-local.sh stop"
  echo "Stop remote Kafka:   ./run-local.sh kafka-stop"
  echo "Show status:         ./run-local.sh status"
}

start_all() {
  validate_required_config
  check_local_dependencies
  configure_java
  setup_ssh_wrappers

  verify_ssh_and_remote_docker
  create_remote_kafka_override
  copy_kafka_files_to_vm
  start_remote_kafka

  ensure_local_database
  build_projects

  stop_local_services
  start_trade_api
  run_auth_liquibase
  start_auth_service
  start_executor

  print_summary
}

show_status() {
  validate_required_config
  check_local_dependencies
  configure_java
  print_summary
}

restart_all() {
  stop_local_services
  start_all
}

usage() {
  cat <<EOF
Usage:
  ./run-local.sh              Start full local stack with remote Kafka
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
