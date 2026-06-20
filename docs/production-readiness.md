# Production Readiness Notes

This portfolio system is intentionally local-first. Before production, the following would be required:

- Replace demo credentials with managed secret storage.
- Enforce mTLS or service mesh identity between internal services.
- Use managed PostgreSQL, Kafka, Redis, and Keycloak or equivalent hardened services.
- Add schema migrations with Flyway or Liquibase.
- Add Kafka schema registry or a formal compatibility verifier.
- Add real alert routing and on-call ownership.
- Add SAST, dependency, and image vulnerability gates with organization policies.
- Add backup, restore, DR, and load-test evidence.
