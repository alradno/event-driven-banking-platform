# Runbook: Kafka Lag

1. Check broker health and consumer group lag.
2. Identify whether lag is isolated to audit or notification consumers.
3. Inspect retry and DLQ topics.
4. Disable demo failure switches before replay.
5. Scale consumers only after ruling out poison messages.
