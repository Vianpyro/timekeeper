#!/usr/bin/env bash
# Tags the release that was just approved by merging the "chore(release): publish vX.Y.Z" PR.
# Pushing the tag is what triggers release.yml - see PROJECT_SPEC.md "cd.yml".
set -euo pipefail

TAG="$1" # e.g. v0.2.0

git tag -a "$TAG" -m "Release ${TAG}"
git push origin "$TAG"
