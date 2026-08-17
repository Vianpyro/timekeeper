#!/usr/bin/env bash
# Decides the next version from Conventional Commit history since the last tag, and opens or
# updates the single release PR that a human merges to actually ship it. Never publishes
# anything itself - see PROJECT_SPEC.md "cd.yml".
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=lib-changelog.sh
source "${SCRIPT_DIR}/lib-changelog.sh"

RELEASE_BRANCH="release/next"
LAST_TAG="$(git tag -l 'v*' --sort=-v:refname | head -n1)"
PREV_VERSION="${LAST_TAG#v}"
[[ -z "$LAST_TAG" ]] && PREV_VERSION="0.0.0"

BUMP="$(changelog_bump "$LAST_TAG" HEAD)"
if [[ "$BUMP" == "none" ]]; then
  echo "No release-worthy commits since ${LAST_TAG:-the beginning of history}; nothing to propose."
  exit 0
fi

IFS=. read -r MAJOR MINOR PATCH <<<"$PREV_VERSION"
case "$BUMP" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac
NEXT_VERSION="${MAJOR}.${MINOR}.${PATCH}"
TAG="v${NEXT_VERSION}"

SECTION_FILE="$(mktemp)"
changelog_section "$LAST_TAG" HEAD "$NEXT_VERSION" >"$SECTION_FILE"

git checkout -B "$RELEASE_BRANCH"

sed -i -E "s/^version=.*/version=${NEXT_VERSION}/" gradle.properties

if [[ ! -f CHANGELOG.md ]]; then
  printf '# Changelog\n\nAll notable changes to this project are documented in this file.\nSections below are generated automatically by cd.yml from Conventional Commits.\n\n' >CHANGELOG.md
fi

CHANGELOG_TMP="$(mktemp)"
awk -v sectionfile="$SECTION_FILE" '
  BEGIN { while ((getline line < sectionfile) > 0) section = section line "\n" }
  /^## \[/ && !inserted { printf "%s", section; inserted = 1 }
  { print }
  END { if (!inserted) printf "%s", section }
' CHANGELOG.md >"$CHANGELOG_TMP"
mv "$CHANGELOG_TMP" CHANGELOG.md

git add gradle.properties CHANGELOG.md
git commit -m "chore(release): publish ${TAG}"
git push --force origin "$RELEASE_BRANCH"

EXISTING_PR="$(gh pr list --base main --head "$RELEASE_BRANCH" --state open --json number --jq '.[0].number // empty')"
if [[ -n "$EXISTING_PR" ]]; then
  gh pr edit "$EXISTING_PR" --title "chore(release): publish ${TAG}" --body-file "$SECTION_FILE"
  echo "Updated release PR #${EXISTING_PR} -> ${TAG}."
else
  gh pr create --base main --head "$RELEASE_BRANCH" --title "chore(release): publish ${TAG}" --body-file "$SECTION_FILE"
fi
