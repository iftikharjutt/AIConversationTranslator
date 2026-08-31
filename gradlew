#!/bin/sh

# Lightweight Gradle wrapper bootstrapper for CI environments.
# Downloads the pinned Gradle distribution on first use, then delegates to it.

set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="8.10.2"
DIST_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}"
GRADLE_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$GRADLE_ZIP" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --retry-delay 2 -o "$GRADLE_ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$GRADLE_ZIP" "$DIST_URL"
    else
      echo "ERROR: curl or wget is required to bootstrap Gradle." >&2
      exit 1
    fi
  fi
  echo "Installing Gradle ${GRADLE_VERSION}..."
  TMP_DIR="$DIST_DIR/.tmp"
  rm -rf "$TMP_DIR"
  mkdir -p "$TMP_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$GRADLE_ZIP" -d "$TMP_DIR"
  else
    echo "ERROR: unzip is required to bootstrap Gradle." >&2
    exit 1
  fi
  rm -rf "$GRADLE_HOME"
  mv "$TMP_DIR/gradle-${GRADLE_VERSION}" "$GRADLE_HOME"
  rm -rf "$TMP_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" -p "$APP_HOME" "$@"
