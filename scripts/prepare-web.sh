#!/usr/bin/env bash
set -euo pipefail

# Pin the upstream release so the APK always contains the same Rust/WASM engine.
VERSION="${WORDCRAFT_VERSION:-v0.4.0}"
REPO="storytold/wordcraft"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/wordcraft"
LICENSES="$ROOT/app/src/main/assets/licenses"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT

if ! command -v gh >/dev/null; then
  echo "GitHub CLI (gh) is required, or build with the included GitHub Actions workflow." >&2
  exit 1
fi
if ! command -v unzip >/dev/null; then
  echo "unzip is required" >&2
  exit 1
fi

mkdir -p "$TEMP/release" "$TEMP/site" "$DEST" "$LICENSES"

echo "Downloading $REPO $VERSION web/WASM release..."
gh release download "$VERSION" --repo "$REPO" --pattern 'wordcraft-web-*.zip' --dir "$TEMP/release"
ZIP="$(find "$TEMP/release" -maxdepth 1 -name 'wordcraft-web-*.zip' -type f | head -n 1)"
if [[ -z "$ZIP" ]]; then
  echo "No WordCraft web release ZIP was found." >&2
  exit 1
fi

# Validate the release archive against the upstream checksum list when supplied.
if gh release download "$VERSION" --repo "$REPO" --pattern 'SHA256SUMS.txt' --dir "$TEMP/release" 2>/dev/null; then
  if [[ -f "$TEMP/release/SHA256SUMS.txt" ]]; then
    ( cd "$TEMP/release" && sha256sum --check --ignore-missing SHA256SUMS.txt )
  fi
fi

unzip -q "$ZIP" -d "$TEMP/site"
INDEX="$(find "$TEMP/site" -type f -name index.html | head -n 1)"
if [[ -z "$INDEX" ]]; then
  echo "The downloaded archive has no index.html" >&2
  exit 1
fi
SITE_ROOT="$(dirname "$INDEX")"
if ! find "$SITE_ROOT" -type f -name '*.wasm' -print -quit | grep -q .; then
  echo "The downloaded archive has no .wasm module" >&2
  exit 1
fi

find "$DEST" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -a "$SITE_ROOT"/. "$DEST"/

TAG="$VERSION"
for FILE in LICENSE-MIT LICENSE-APACHE NOTICE ATTRIBUTION.md; do
  curl --fail --silent --show-error --location \
    "https://raw.githubusercontent.com/$REPO/$TAG/$FILE" \
    --output "$LICENSES/$FILE"
done

echo "Web files installed in $DEST"
echo "Ready: gradle assembleDebug"
