# ADR 0002: Keycloak for Local OIDC

## Status

Accepted

## Context

The platform needs realistic OAuth2/OIDC/JWT behavior without paid services or external identity dependencies.

## Decision

Use Keycloak in Docker Compose with an imported demo realm, roles, scopes, clients, and users. The gateway validates JWTs and enforces role/scope access before routing to internal services.

## Consequences

- Local demos use real JWTs and role claims.
- 401, 403, ownership isolation, and audit behavior can be tested.
- Realm import files must avoid secrets beyond demo-only credentials documented in `.env.example`.
