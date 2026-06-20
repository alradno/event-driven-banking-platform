import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.rule_engine import IncidentInput, analyze_incident


class RuleEngineTest(unittest.TestCase):
    def test_no_cause_without_evidence(self):
        report = analyze_incident(IncidentInput())

        self.assertEqual(report.severity, "low")
        self.assertEqual(report.likely_causes, [])
        self.assertEqual(report.evidence, [])

    def test_cites_dlq_and_payment_metrics(self):
        report = analyze_incident(IncidentInput(
            metrics={"payment.error_rate": 0.35},
            kafka={"dlq_count": 3},
            traces=["trace-123"],
            runbooks=["runbooks/payment-failure.md", "runbooks/kafka-lag.md"],
        ))

        self.assertEqual(report.severity, "high")
        self.assertIn("payment-service", report.affected_services)
        self.assertIn("notification-service", report.affected_services)
        self.assertTrue(report.evidence)
        self.assertIn("trace-123", report.trace_ids)


if __name__ == "__main__":
    unittest.main()
