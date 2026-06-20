from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from .rule_engine import IncidentInput, analyze_incident

app = FastAPI(
    title="AI Incident Assistant",
    version="0.1.0",
    description="Evidence-based banking incident assistant that works without paid API keys.",
)


class IncidentRequest(BaseModel):
    logs: list[dict] = Field(default_factory=list)
    metrics: dict[str, float] = Field(default_factory=dict)
    traces: list[str] = Field(default_factory=list)
    kafka: dict[str, float] = Field(default_factory=dict)
    runbooks: list[str] = Field(default_factory=list)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/incidents/analyze")
def analyze(request: IncidentRequest) -> dict:
    report = analyze_incident(IncidentInput(
        logs=request.logs,
        metrics=request.metrics,
        traces=request.traces,
        kafka=request.kafka,
        runbooks=request.runbooks,
    ))
    return {
        "severity": report.severity,
        "summary": report.summary,
        "affectedServices": report.affected_services,
        "likelyCauses": report.likely_causes,
        "confidence": report.confidence,
        "evidence": [item.__dict__ for item in report.evidence],
        "traceIds": report.trace_ids,
        "checks": report.checks,
        "runbooks": report.runbooks,
    }
