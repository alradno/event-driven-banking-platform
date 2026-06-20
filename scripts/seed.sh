#!/usr/bin/env sh
set -eu

echo "Seed data is created automatically by customer-service and account-service on startup."
echo "Demo users:"
echo "  alice/password CUSTOMER customer_id=11111111-1111-1111-1111-111111111111"
echo "  bob/password CUSTOMER customer_id=22222222-2222-2222-2222-222222222222"
echo "  auditor/password AUDITOR"
echo "  sre/password SRE"
echo "  admin/password ADMIN,SRE,AUDITOR"
echo "Demo account IDs:"
echo "  alice CHF source: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"
echo "  alice frozen EUR: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2"
echo "  bob CHF target:   bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1"
