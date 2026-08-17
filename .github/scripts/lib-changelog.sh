#!/usr/bin/env bash
# Shared helpers for turning Conventional Commit history into a version bump decision and a
# changelog section. Sourced (not executed) by cd-propose-release.sh and
# release-generate-notes.sh so both workflows agree on exactly the same rules - see
# PROJECT_SPEC.md "cd.yml" and "release.yml".
#
# Commit messages are read as NUL-free records separated by \x1e, with \x1f between the hash,
# subject, and body of each one, to survive multi-line commit bodies without ambiguity.

# changelog_bump <from-tag-or-empty> <to-ref>
# Prints "major", "minor", "patch" or "none" for the commits in the given range.
changelog_bump() {
  local from="$1" to="$2" range
  range=$([[ -n "$from" ]] && echo "${from}..${to}" || echo "$to")

  # Kept in a variable rather than inlined: an unescaped ")" inside a [^)] character class
  # directly in a [[ =~ PATTERN ]] confuses bash's own conditional-expression parser.
  local header_pattern='^([a-z]+)(\([^)]+\))?(!)?:\ .+$'

  local bump="none"
  while IFS=$'\x1f' read -r -d $'\x1e' _hash subject body; do
    # git appends its own "\n" after every %x1e-terminated record, which lands as a leading
    # newline on the *next* record's first field since IFS above no longer treats "\n" as
    # whitespace to strip.
    _hash="${_hash#$'\n'}"
    [[ -z "$subject" ]] && continue

    local type=""
    if [[ "$subject" =~ $header_pattern ]]; then
      type="${BASH_REMATCH[1]}"
      if [[ -n "${BASH_REMATCH[3]}" ]]; then
        bump="major"
        continue
      fi
    fi
    if [[ "$body" == *"BREAKING CHANGE:"* ]]; then
      bump="major"
      continue
    fi
    if [[ "$type" == "feat" && "$bump" != "major" ]]; then
      bump="minor"
    fi
    if [[ ("$type" == "fix" || "$type" == "perf") && "$bump" != "major" && "$bump" != "minor" ]]; then
      bump="patch"
    fi
  done < <(git log "$range" --format='%H%x1f%s%x1f%b%x1e')

  echo "$bump"
}

# changelog_section <from-tag-or-empty> <to-ref> <version>
# Prints a "## [version] - date" markdown section grouping commits in the given range by
# Conventional Commit type.
changelog_section() {
  local from="$1" to="$2" version="$3" range
  range=$([[ -n "$from" ]] && echo "${from}..${to}" || echo "$to")

  local header_pattern='^([a-z]+)(\(([^)]+)\))?(!)?:\ (.+)$'

  local features=() fixes=() perf=() other=()
  while IFS=$'\x1f' read -r -d $'\x1e' hash subject body; do
    # See the matching comment in changelog_bump: strip the leading "\n" git glues onto the
    # start of every record after the first.
    hash="${hash#$'\n'}"
    [[ -z "$subject" ]] && continue

    local type="" scope="" desc="$subject" breaking="false"
    if [[ "$subject" =~ $header_pattern ]]; then
      type="${BASH_REMATCH[1]}"
      scope="${BASH_REMATCH[3]}"
      desc="${BASH_REMATCH[5]}"
      [[ -n "${BASH_REMATCH[4]}" ]] && breaking="true"
    fi
    [[ "$body" == *"BREAKING CHANGE:"* ]] && breaking="true"

    local entry="- ${desc} (${hash:0:7})"
    [[ -n "$scope" ]] && entry="- **${scope}:** ${desc} (${hash:0:7})"
    [[ "$breaking" == "true" ]] && entry="${entry} (BREAKING CHANGE)"

    case "$type" in
      feat) features+=("$entry") ;;
      fix) fixes+=("$entry") ;;
      perf) perf+=("$entry") ;;
      "") ;;
      *) other+=("$entry") ;;
    esac
  done < <(git log "$range" --format='%H%x1f%s%x1f%b%x1e')

  echo "## [${version}] - $(date -u +%Y-%m-%d)"
  echo
  if [[ ${#features[@]} -gt 0 ]]; then
    echo "### Features"
    printf '%s\n' "${features[@]}"
    echo
  fi
  if [[ ${#fixes[@]} -gt 0 ]]; then
    echo "### Bug Fixes"
    printf '%s\n' "${fixes[@]}"
    echo
  fi
  if [[ ${#perf[@]} -gt 0 ]]; then
    echo "### Performance"
    printf '%s\n' "${perf[@]}"
    echo
  fi
  if [[ ${#other[@]} -gt 0 ]]; then
    echo "### Other Changes"
    printf '%s\n' "${other[@]}"
    echo
  fi
}
