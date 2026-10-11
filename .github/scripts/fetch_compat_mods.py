#!/usr/bin/env python3
"""
CI helper: downloads optional compatibility mods (and their required dependencies) for NeoForge 1.21.1
from Modrinth into a mods folder, so the smoke test can run with them installed.

usage: fetch_compat_mods.py <mods_dir> <slug> [<slug> ...]
Mods that cannot be found are reported and skipped.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
LOADERS = json.dumps(["neoforge"])
GAME = json.dumps(["1.21.1"])
HEADERS = {"User-Agent": "M4ytenn/barotrauma-oceans-ci (github actions)"}


def get(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def latest_version(project):
    q = urllib.parse.urlencode({"loaders": LOADERS, "game_versions": GAME})
    versions = get(f"{API}/project/{project}/version?{q}")
    return versions[0] if versions else None


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    todo = list(sys.argv[2:])
    done = set()
    while todo:
        project = todo.pop(0)
        if project in done:
            continue
        done.add(project)
        try:
            v = latest_version(project)
        except Exception as e:  # noqa: BLE001
            print(f"[compat] {project}: lookup failed ({e})")
            continue
        if v is None:
            print(f"[compat] {project}: no NeoForge 1.21.1 version")
            continue
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        path = os.path.join(out, f["filename"])
        urllib.request.urlretrieve(f["url"], path)
        print(f"[compat] {project}: {v['version_number']} -> {f['filename']}")
        for dep in v.get("dependencies", []):
            if dep.get("dependency_type") == "required" and dep.get("project_id"):
                todo.append(dep["project_id"])


if __name__ == "__main__":
    main()
