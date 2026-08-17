#!/usr/bin/env bash
# Tags the release that was just approved by merging the "chore(release): publish vX.Y.Z" PR,
# then explicitly asks release.yml to run - see PROJECT_SPEC.md "cd.yml".
#
# A plain tag push authenticated with the default GITHUB_TOKEN would not do that on its own:
# GitHub deliberately refuses to let GITHUB_TOKEN-authenticated push events trigger further
# workflow runs (anti-recursion safeguard). workflow_dispatch and repository_dispatch are the two
# documented exceptions, so this fires a repository_dispatch instead of relying on the tag push
# itself - https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow
set -euo pipefail

TAG="$1" # e.g. v0.2.0

git tag -a "$TAG" -m "Release ${TAG}"
git push origin "$TAG"

gh api "repos/${GITHUB_REPOSITORY}/dispatches" \
  -f event_type=release \
  -f "client_payload[tag]=${TAG}"
