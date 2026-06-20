# Runbook: Authentication Failure

1. Split 401 from 403 at the gateway.
2. Verify Keycloak realm import and issuer URI.
3. Inspect JWT roles, scopes, and `customer_id` claim.
4. Check rate limit counters before concluding identity failure.
5. Audit repeated access denied events by subject and path.
