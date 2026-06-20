# OWASP API Security Mapping

| Control | Implemented by | OWASP API risk |
| --- | --- | --- |
| JWT required at gateway | `api-gateway` security config | API2: Broken Authentication |
| Route roles and scopes | `api-gateway` security config | API1: Broken Object Property Level Authorization, API5: Broken Function Level Authorization |
| Ownership checks | `account-service`, `payment-service` | API1: Broken Object Level Authorization |
| Redis rate limits | `api-gateway` | API4: Unrestricted Resource Consumption |
| Standard errors and no sensitive audit payloads | gateway and `audit-service` | API3: Broken Object Property Level Authorization |
| Immutable audit trail | `audit-service` | API10: Unsafe Consumption of APIs and forensic controls |
