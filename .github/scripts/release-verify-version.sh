#!/usr/bin/env bash
# Refuses to publish if the pushed tag and gradle.properties disagree, so a mistagged commit
# can never reach Modrinth - see PROJECT_SPEC.md "release.yml".
set -euo pipefail

TAG="$1" # e.g. v0.2.0
TAG_VERSION="${TAG#v}"

GRADLE_VERSION="$(grep -E '^version=' gradle.properties | cut -d= -f2)"

if [[ "$TAG_VERSION" != "$GRADLE_VERSION" ]]; then
  echo "::error::Tag ${TAG} does not match gradle.properties version (${GRADLE_VERSION})."
  exit 1
fi

echo "Tag ${TAG} matches gradle.properties version ${GRADLE_VERSION}."
