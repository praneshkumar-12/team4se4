# Kafka topics — SEC4-611

Source of truth for the values below: `contracts/kafka-topics.md` (copied into
this repo from the requirements repo as part of this ticket). This file is the
design justification the ticket asks for; if the two ever disagree, the
contract wins and this file is out of date.

## Topics

| Topic | Key | Partitions | Retention | Dead-letter topic |
|---|---|---|---|---|
| `orders` | `accountId` (string) | 3 | 7 days | `orders.DLT` |
| `trade-events` | `accountId` (string) | 3 | 30 days | `trade-events.DLT` |
| `market-data` | `symbol` | 6 | 1 day | `market-data.DLT` |

Dead-letter topics carry the same partition count and retention as their
parent. The naming convention for any DLT in this platform is always
`<topic>.DLT`.

## Envelope

Every message on all three topics carries the same five-field envelope plus a
`payload`:

| Field | Type | Notes |
|---|---|---|
| `eventId` | string, UUID | Idempotency key for consumers. |
| `eventType` | string | Discriminates the payload. |
| `eventTime` | RFC 3339 UTC | When the producer created the event. |
| `source` | string | `trade-api` \| `trade-executor` \| `market-poller`. |
| `schemaVersion` | int | Starts at 1, increments only on a breaking change. |

Implementation: `org.leap.events.EventEnvelope<T>` and `org.leap.events.Topics`
in `sprint-05-domain-engine`, shared by every producer and consumer in the
platform (the Trade REST API today, the Trade Executor once a teammate builds
it).

**Every consumer must ignore JSON fields it does not recognise.** That is what
lets an additive schema change (a new optional field) ship without an outage:
old consumers keep working because they were never told to reject fields they
don't understand. This repo's own producer (SEC4-613's `OrderEventPublisher`)
demonstrates the principle even though a producer doesn't strictly need it:
its Jackson `ObjectMapper` has `FAIL_ON_UNKNOWN_PROPERTIES` disabled.

## Why the key decides the partition, and the partition decides ordering

Kafka guarantees message order only *within* a partition — never across
partitions. The partition a message lands on is a hash of its key. So
choosing a key is really choosing the unit of ordering: two messages with the
same key always land on the same partition and are always delivered to a
consumer in the order they were produced; two messages with different keys
have no ordering relationship at all, even if one was produced a second
before the other.

**`orders` and `trade-events` are keyed by `accountId`, not `orderId`.**
Every order and every lifecycle event for one account must be processed in
the order they were submitted — if a sell for an account is processed before
the buy that funded it, the sell fails or the account goes negative depending
on how the bug is handled, either way it's wrong. Keying by `accountId` puts
every message for that account on the same partition, so per-account order is
guaranteed.

Keying by `orderId` instead would break that guarantee. Every order has a
distinct `orderId`, so two orders from the same account would (almost always)
hash to *different* partitions, with no ordering relationship between them.
A consumer group with more than one instance could then genuinely process
account 1's second order before its first, because they're sitting in two
different partitions being read by two different consumer instances with no
coordination between them. Keying by `orderId` looks more "unique" and
therefore more correct at a glance, but uniqueness is not what ordering
needs — grouping is.

**`market-data` is keyed by `symbol`** for the same reason at the instrument
level: a consumer must never see an older quote for `AAPL` arrive after a
newer one, and quotes for different symbols have no ordering relationship to
preserve, so they're free to spread across all 6 partitions and be consumed
in parallel.

## Why these partition counts

- **`orders` / `trade-events`: 3 partitions.** This sizes the topic to the
  number of Trade Executor instances the platform runs in this sprint — three
  is enough to demonstrate consumer-group rebalancing (an instance drops, its
  partitions move to the survivors) and enough to show that a fourth consumer
  in the same group would sit idle: a consumer group cannot usefully have
  more members than a topic has partitions, because a partition is assigned
  to exactly one consumer in the group at a time.
- **`market-data`: 6 partitions.** Reflects the higher message rate — every
  tradable symbol gets its own quote on every poll, so this topic carries
  materially more traffic per unit time than an order-driven topic, and more
  partitions gives more consumers something to do in parallel.

## Partition counts can go up but never down

Kafka lets a topic's partition count increase later but never decrease. That
matters here for a reason beyond "changing infrastructure is annoying":
**increasing partition count rehashes every key.** The mapping from key to
partition is `hash(key) % partitionCount`, so changing the denominator
changes where nearly every key lands. Concretely: if `orders` goes from 3
partitions to 4 next year, account 1's older messages stay on whichever
partition they were already written to, but its new messages very likely land
on a different partition than before. From that point on, account 1's order
history is split across two partitions with no ordering relationship between
the two halves — exactly the guarantee this design exists to protect. This is
why the counts in the table above were picked deliberately now, not left at a
default to be "tuned later."

## Local operation

`docker-compose.yml` adds a single-broker `kafka` service, KRaft mode (no
ZooKeeper — unnecessary for a modern single-node broker), with
`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`. Auto-creation is off deliberately:
left on, the first producer or consumer to touch an unknown topic name
silently creates a 1-partition, default-retention topic — exactly the wrong
shape for all three topics here, and wrong silently, which is worse than
failing loudly.

Because auto-creation is off, the topics must be created explicitly before
anything tries to use them. `scripts/kafka-init.sh` does that — see the
comments in that script for when to run it.