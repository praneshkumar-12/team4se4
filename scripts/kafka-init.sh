#!/usr/bin/env bash
#
# Creates the three Kafka topics this platform needs, plus their dead-letter
# topics, with the partition counts and retention the contract fixes
# (contracts/kafka-topics.md, justified in design/kafka.md).
#
# WHEN TO RUN THIS
#   Once, after `docker-compose up -d kafka` and before starting `trade-api`
#   or `executor`. Auto-creation is switched off on the broker
#   (KAFKA_AUTO_CREATE_TOPICS_ENABLE=false), so nothing creates these topics
#   on your behalf — a service that starts before this script has run will
#   fail to publish/consume rather than silently get a 1-partition topic.
#
# WHO RUNS IT
#   Whoever brings the local stack up. It is safe to re-run: every command
#   uses --if-not-exists, so running this against a broker that already has
#   the topics is a no-op.
#
# USAGE
#   ./scripts/kafka-init.sh
#
set -euo pipefail

SERVICE="kafka"
BOOTSTRAP_SERVER="kafka:29092"

if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
else
    echo "Error: neither 'docker compose' nor 'docker-compose' is available." >&2
    exit 1
fi

compose_exec() {
    "${COMPOSE_CMD[@]}" exec -T "$@"
}

create_topic() {
    local topic="$1"
    local partitions="$2"
    local retention_ms="$3"

    echo "Creating topic '${topic}' (partitions=${partitions}, retention.ms=${retention_ms})..."
    compose_exec "${SERVICE}" /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "${BOOTSTRAP_SERVER}" \
        --create --if-not-exists \
        --topic "${topic}" \
        --partitions "${partitions}" \
        --replication-factor 1 \
        --config "retention.ms=${retention_ms}"
}

# Topic                 Partitions  Retention (ms)
create_topic "orders"          3   604800000     # 7 days
create_topic "orders.DLT"      3   604800000

create_topic "trade-events"    3   2592000000    # 30 days
create_topic "trade-events.DLT" 3  2592000000

create_topic "market-data"     6   86400000      # 1 day
create_topic "market-data.DLT" 6   86400000

echo "Done. Listing topics:"
compose_exec "${SERVICE}" /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list