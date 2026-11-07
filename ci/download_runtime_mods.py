#!/usr/bin/env python3
"""Fetch checksum-pinned test dependencies on GitHub-hosted CI, never a local PC.

These dependencies are used only by the disposable test server and are not bundled
in the replacement mod or uploaded as release artifacts.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ALLOWED_HOSTS = {"edge.forgecdn.net", "mediafilez.forgecdn.net", "media.forgecdn.net"}


def require_ci() -> None:
    workspace = os.environ.get("GITHUB_WORKSPACE")
    if os.environ.get("GITHUB_ACTIONS") != "true" or not workspace:
        raise RuntimeError("Runtime downloads are restricted to GitHub Actions; do not run tests on the user's PC")
    if not ROOT.is_relative_to(Path(workspace).resolve()):
        raise RuntimeError("Project is outside the GitHub Actions workspace")


def valid_url(value: str) -> None:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS or parsed.username or parsed.password:
        raise ValueError("Runtime dependencies must use approved public HTTPS CurseForge CDN URLs")


def checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    require_ci()
    manifest = json.loads((ROOT / "ci/runtime-mods.json").read_text(encoding="utf-8-sig"))
    if not isinstance(manifest, list) or not 1 <= len(manifest) <= 32:
        raise ValueError("Invalid dependency manifest")
    destination = ROOT / "run/mods"
    if not destination.resolve().is_relative_to(ROOT):
        raise RuntimeError("Test mods directory points outside the isolated project")
    destination.mkdir(parents=True, exist_ok=True)
    expected_names = set()
    for entry in manifest:
        name = entry["file"]
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+\-]*\.jar", name) or name in expected_names:
            raise ValueError("Unsafe or duplicate dependency filename")
        if not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]) or not 0 < entry["bytes"] < 500_000_000:
            raise ValueError("Invalid dependency checksum or byte count")
        valid_url(entry["url"])
        expected_names.add(name)
    extras = {p.name for p in destination.glob("*.jar")} - expected_names
    if extras:
        raise RuntimeError("Unexpected test mods: " + ", ".join(sorted(extras)))

    verified = []
    for entry in manifest:
        name = entry["file"]
        target = destination / name
        if target.is_file() and target.stat().st_size == entry["bytes"] and checksum(target) == entry["sha256"]:
            print("Verified cached dependency:", name, flush=True)
        else:
            temporary = destination / (name + ".download")
            for attempt in range(3):
                try:
                    request = urllib.request.Request(entry["url"], headers={"User-Agent": "UnyieldingDungeon-GitHub-CI/1.0"})
                    digest = hashlib.sha256()
                    length = 0
                    with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                        valid_url(response.geturl())
                        while chunk := response.read(1024 * 1024):
                            length += len(chunk)
                            if length > entry["bytes"]:
                                raise ValueError("Dependency exceeded pinned byte count: " + name)
                            digest.update(chunk)
                            output.write(chunk)
                    if length != entry["bytes"] or digest.hexdigest() != entry["sha256"]:
                        raise ValueError("Dependency integrity check failed: " + name)
                    os.replace(temporary, target)
                    print("Downloaded and verified:", name, flush=True)
                    break
                except Exception:
                    temporary.unlink(missing_ok=True)
                    if attempt == 2:
                        raise
                    time.sleep(2 ** (attempt + 1))
        verified.append({"file": name, "bytes": entry["bytes"], "sha256": entry["sha256"], "url": entry["url"]})

    evidence = ROOT / "test-evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "runtime-dependencies.json").write_text(json.dumps({"status": "PASS", "verified": verified}, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {len(verified)} isolated server dependencies; no game launched.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("Runtime dependency download failed:", exc, file=sys.stderr)
        raise SystemExit(1)
