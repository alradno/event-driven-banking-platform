#!/usr/bin/env sh
set -eu

fail() {
  echo "CI scan failed: $*" >&2
  exit 1
}

scan_pattern() {
  label="$1"
  pattern="$2"
  shift 2
  set +e
  git grep -I -n -E -e "$pattern" -- . ':!target/**' ':!*.class' ':!scripts/ci-scan.sh' "$@"
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then
    fail "$label found"
  fi
  if [ "$status" -ne 1 ]; then
    fail "$label scan errored"
  fi
}

forbidden_paths="$(git ls-files | grep -E '(^|/)(\.DS_Store|\.env|id_rsa|id_dsa|id_ecdsa|id_ed25519)$|(\.pem|\.key|\.p12|\.pfx)$|(^|/)(__pycache__|target|node_modules|\.venv)/' || true)"
if [ -n "$forbidden_paths" ]; then
  printf '%s\n' "$forbidden_paths" >&2
  fail "forbidden generated, secret, or build artifact paths are tracked"
fi

scan_pattern "known Bitbucket or Sonar token pattern" 'BBDC-|squ_[[:alnum:]_]+'
scan_pattern "generic private key or access token pattern" '-----BEGIN ((RSA|DSA|EC|OPENSSH) )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|ghp_[[:alnum:]_]{30,}'
scan_pattern "hard-coded password or secret assignment pattern" 'password[[:space:]]*[:=][[:space:]]*["'\''][^"'\'']{8,}["'\'']|secret[[:space:]]*[:=][[:space:]]*["'\''][^"'\'']{8,}["'\'']' ':!docs/**' ':!README.md' ':!STATUS.md'

echo "CI scan passed: no forbidden tracked artifacts or secret patterns found."
