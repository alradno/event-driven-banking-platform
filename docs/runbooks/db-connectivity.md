# Runbook: Database Connectivity

1. Check PostgreSQL readiness and recent restarts.
2. Confirm each service points to its own database.
3. Inspect connection pool exhaustion and authentication failures.
4. Verify schema migration or `ddl-auto` startup logs.
5. Restart dependent services only after the database is healthy.
