#!/usr/bin/env bash
# Regenerates this release's changelog section straight from Git history (the same way cd.yml
# generated it for the release PR), into RELEASE_NOTES.md, for the GitHub release body and the
# Modrinth changelog - see PROJECT_SPEC.md "release.yml".
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=lib-changelog.sh
source "${SCRIPT_DIR}/lib-changelog.sh"

TAG="$1" # e.g. v0.2.0
VERSION="${TAG#v}"

PREVIOUS_TAG="$(git tag -l 'v*' --sort=-v:refname | grep -v -x "$TAG" | head -n1)"

changelog_section "$PREVIOUS_TAG" "$TAG" "$VERSION" >RELEASE_NOTES.md
