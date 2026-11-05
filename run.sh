#!/usr/bin/env bash
# One-command local run in "attach" mode:
#   1. start Chrome with remote debugging (skipped if something already listens on the port)
#   2. wait until Chrome is ready
#   3. build and run the app with `mvn compile exec:java`, attached to that Chrome
#
# Port: LOCAL_BROWSER_PORT env var, else local_browser_port from config.local.properties, else 9222.
# Chrome binary: CHROME_BIN env var, else auto-detected (macOS / Linux).
set -euo pipefail

cd "$(dirname "$0")"

LOCAL_CONFIG="config.local.properties"
PROFILE_DIR="$HOME/.chrome-steamgifts"   # keeps the steamgifts login between runs

port="${LOCAL_BROWSER_PORT:-}"
if [[ -z "$port" && -f "$LOCAL_CONFIG" ]]; then
    port="$(sed -n 's/^[[:space:]]*local_browser_port[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p' "$LOCAL_CONFIG" | tail -n 1)"
fi
port="${port:-9222}"
export LOCAL_BROWSER_PORT="$port"   # make the app attach to this Chrome

find_chrome() {
    if [[ -n "${CHROME_BIN:-}" ]]; then
        echo "$CHROME_BIN"
        return
    fi
    local mac="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    if [[ -x "$mac" ]]; then
        echo "$mac"
        return
    fi
    local candidate
    for candidate in google-chrome google-chrome-stable chromium chromium-browser; do
        if command -v "$candidate" >/dev/null 2>&1; then
            command -v "$candidate"
            return
        fi
    done
    echo "Chrome not found. Install it or set CHROME_BIN=/path/to/chrome" >&2
    exit 1
}

chrome_ready() {
    # --noproxy: a configured http_proxy must not swallow this local request
    curl -s --noproxy '*' -o /dev/null "http://127.0.0.1:$port/json/version"
}

if chrome_ready; then
    echo "Chrome is already listening on port $port, reusing it."
else
    chrome="$(find_chrome)"
    echo "Starting Chrome on debug port $port (profile: $PROFILE_DIR)..."
    nohup "$chrome" --remote-debugging-port="$port" --user-data-dir="$PROFILE_DIR" >/dev/null 2>&1 &

    for _ in $(seq 1 30); do
        chrome_ready && break
        sleep 1
    done
    if ! chrome_ready; then
        echo "Chrome did not open debug port $port within 30s." >&2
        exit 1
    fi
fi

# compile first: exec:java alone runs whatever is in target/ (stale or missing classes)
exec mvn -q compile exec:java
