#!/usr/bin/env python3
"""Print what each Google Play track is currently serving, straight from the Publishing API.

Why this exists: the Play Console summary line renders as "最新版本：1.0.176 個國家/地區" —
the version and the country count run together with no separator, so "1.0.17" and "1.0.176"
are indistinguishable by any regex. Scraping it produced a wrong current-production version,
which matters because that value gates the downgrade check in play_release_production.sh.

Usage:
    python3 play_tracks.py              # all tracks, "track<TAB>versionName<TAB>status"
    python3 play_tracks.py production   # just that track's versionName, or exit 1

Reads credentials from play-service-account.json at the repo root.
"""
import base64
import json
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

REPO_ROOT = Path(__file__).resolve().parents[2]
KEY_PATH = REPO_ROOT / "play-service-account.json"
PACKAGE = "com.rootilabs.wmeCardiac2"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
TOKEN_URL = "https://oauth2.googleapis.com/token"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"


def _b64u(raw: bytes) -> bytes:
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def _make_jwt(claims: dict, private_key_pem: str) -> str:
    header = {"alg": "RS256", "typ": "JWT"}
    signing_input = (
        _b64u(json.dumps(header, separators=(",", ":")).encode())
        + b"."
        + _b64u(json.dumps(claims, separators=(",", ":")).encode())
    )
    key = serialization.load_pem_private_key(private_key_pem.encode(), password=None)
    sig = key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256())
    return (signing_input + b"." + _b64u(sig)).decode()


def _curl(args: list[str]) -> tuple[int, dict]:
    # This interpreter has no usable CA bundle on the release machine, so HTTP goes via curl.
    out = subprocess.run(
        ["curl", "-s", "-w", "\n%{http_code}", *args],
        capture_output=True, text=True, timeout=60,
    )
    body, _, code = out.stdout.rpartition("\n")
    try:
        parsed = json.loads(body) if body.strip() else {}
    except json.JSONDecodeError:
        parsed = {"_raw": body[:300]}
    return int(code or 0), parsed


def fetch_tracks() -> dict[str, dict]:
    if not KEY_PATH.exists():
        sys.exit(f"Error: {KEY_PATH} not found")
    sa = json.loads(KEY_PATH.read_text())

    now = int(time.time())
    assertion = _make_jwt(
        {"iss": sa["client_email"], "scope": SCOPE, "aud": TOKEN_URL,
         "iat": now, "exp": now + 3600},
        sa["private_key"],
    )
    status, tok = _curl([
        TOKEN_URL, "-X", "POST",
        "-H", "Content-Type: application/x-www-form-urlencoded",
        "--data", urllib.parse.urlencode({
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": assertion,
        }),
    ])
    if status != 200 or "access_token" not in tok:
        sys.exit(f"Error: token request failed ({status}): {tok}")
    access = tok["access_token"]

    def call(url, method="GET"):
        return _curl([url, "-X", method, "-H", f"Authorization: Bearer {access}",
                      "-H", "Content-Length: 0"])

    status, edit = call(f"{BASE}/edits", "POST")
    if status != 200:
        sys.exit(f"Error: cannot create edit ({status}): {edit}")
    eid = edit["id"]
    try:
        status, tracks = call(f"{BASE}/edits/{eid}/tracks")
        if status != 200:
            sys.exit(f"Error: tracks.list failed ({status}): {tracks}")
    finally:
        call(f"{BASE}/edits/{eid}", "DELETE")

    out = {}
    for t in tracks.get("tracks", []):
        live = [r for r in t.get("releases", []) if r.get("status") == "completed"]
        rel = live[0] if live else None
        out[t["track"]] = {
            "versionName": (rel or {}).get("name"),
            "versionCodes": (rel or {}).get("versionCodes", []),
            "status": (rel or {}).get("status"),
        }
    return out


def main() -> None:
    tracks = fetch_tracks()
    if len(sys.argv) > 1:
        want = sys.argv[1]
        info = tracks.get(want)
        if not info or not info["versionName"]:
            sys.exit(f"Error: track '{want}' has no completed release")
        print(info["versionName"])
        return
    for name, info in tracks.items():
        print(f"{name}\t{info['versionName'] or '-'}\t{info['status'] or '-'}")


if __name__ == "__main__":
    main()
