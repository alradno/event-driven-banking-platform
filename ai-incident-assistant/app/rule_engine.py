from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class Evidence:
    source: str
    message: str
    service: str | None = None
    trace_id: str | None = None
    metric: str | None = None
    value: float | None = None


@dataclass(frozen=True)
class IncidentInput:
    logs: list[dict[str, Any]] = field(default_factory=list)
    metrics: dict[str, float] = field(default_factory=dict)
    traces: list[str] = field(default_factory=list)
    kafka: dict[str, float] = field(default_factory=dict)
    runbooks: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class IncidentReport:
    severity: str
    summary: str
    affected_services: list[str]
    likely_causes: list[str]
    confidence: float
    evidence: list[Evidence]
    trace_ids: list[str]
    checks: list[str]
    runbooks: list[str]


def analyze_incident(data: IncidentInput) -> IncidentReport:
    evidence: list[Evidence] = []
    causes: list[str] = []
    services: set[str] = set()
    checks: list[str] = []
    severity = "low"

    payment_error_rate = data.metrics.get("payment.error_rate", 0)
    if payment_error_rate >= 0.2:
        evidence.append(Evidence(
            source="metric",
            service="payment-service",
            metric="payment.error_rate",
            value=payment_error_rate,
            message=f"payment.error_rate is {payment_error_rate:.2f}",
        ))
        causes.append("payment workflow failures are elevated")
        services.add("payment-service")
        checks.append("Inspect payment-service logs for rejection and failure reasons.")
        severity = "high"

    stuck_payments = data.metrics.get("payment.stuck.count", 0)
    if stuck_payments > 0:
        evidence.append(Evidence(
            source="metric",
            service="payment-service",
            metric="payment.stuck.count",
            value=stuck_payments,
            message=f"{int(stuck_payments)} payments appear stuck outside a terminal state",
        ))
        causes.append("payments may be stuck between validation and completion")
        services.add("payment-service")
        checks.append("Query payments in CREATED, VALIDATED, or PROCESSING for age and correlation IDs.")
        severity = "high"

    kafka_lag = data.kafka.get("max_lag", 0)
    if kafka_lag >= 100:
        evidence.append(Evidence(
            source="kafka",
            service="notification-service",
            metric="kafka.max_lag",
            value=kafka_lag,
            message=f"Kafka consumer lag reached {int(kafka_lag)} messages",
        ))
        causes.append("consumer lag may delay notifications or audit visibility")
        services.add("notification-service")
        checks.append("Check consumer group lag and broker health before restarting consumers.")
        severity = "high" if severity == "high" else "medium"

    dlq_count = data.kafka.get("dlq_count", 0)
    if dlq_count > 0:
        evidence.append(Evidence(
            source="kafka",
            service="notification-service",
            metric="kafka.dlq_count",
            value=dlq_count,
            message=f"DLQ contains {int(dlq_count)} messages",
        ))
        causes.append("notification failures are being dead-lettered")
        services.add("notification-service")
        checks.append("Read DLQ payloads and replay only after the failure switch or consumer bug is fixed.")
        severity = "high"

    auth_failures = data.metrics.get("gateway.auth_failures", 0)
    if auth_failures >= 10:
        evidence.append(Evidence(
            source="metric",
            service="api-gateway",
            metric="gateway.auth_failures",
            value=auth_failures,
            message=f"gateway auth failures reached {int(auth_failures)}",
        ))
        causes.append("authentication or authorization failures spiked at the gateway")
        services.add("api-gateway")
        checks.append("Compare 401 versus 403 rates and verify Keycloak availability.")
        severity = "medium" if severity == "low" else severity

    for log in data.logs:
        message = str(log.get("message", ""))
        service = str(log.get("service", "") or "unknown")
        trace_id = log.get("traceId") or log.get("trace_id")
        if any(token in message.lower() for token in ["database", "connection refused", "timeout"]):
            evidence.append(Evidence(
                source="log",
                service=service,
                trace_id=str(trace_id) if trace_id else None,
                message=message[:300],
            ))
            causes.append("database connectivity problems are visible in logs")
            services.add(service)
            checks.append("Check database readiness, pool saturation, and recent restarts.")
            severity = "high"

    unique_causes = _ordered_unique(causes)
    unique_checks = _ordered_unique(checks)
    trace_ids = _ordered_unique([trace for trace in data.traces if trace] + [
        item.trace_id for item in evidence if item.trace_id
    ])

    if not evidence:
        return IncidentReport(
            severity="low",
            summary="No supported incident cause found in the supplied evidence.",
            affected_services=[],
            likely_causes=[],
            confidence=0.2,
            evidence=[],
            trace_ids=trace_ids,
            checks=["Collect gateway errors, payment metrics, Kafka lag, DLQ counts, and trace IDs."],
            runbooks=data.runbooks,
        )

    return IncidentReport(
        severity=severity,
        summary=_summary(severity, services, unique_causes),
        affected_services=sorted(services),
        likely_causes=unique_causes,
        confidence=min(0.95, 0.45 + len(evidence) * 0.12),
        evidence=evidence,
        trace_ids=trace_ids,
        checks=unique_checks,
        runbooks=data.runbooks,
    )


def _summary(severity: str, services: set[str], causes: list[str]) -> str:
    service_text = ", ".join(sorted(services)) if services else "unknown services"
    cause_text = "; ".join(causes[:2]) if causes else "no likely cause"
    return f"{severity.upper()} incident affecting {service_text}: {cause_text}."


def _ordered_unique(items: list[Any]) -> list[Any]:
    seen: set[Any] = set()
    output: list[Any] = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            output.append(item)
    return output
