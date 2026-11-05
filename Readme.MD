[![build](https://github.com/Zap4ick/gifts-collector/actions/workflows/maven.yml/badge.svg)](https://github.com/Zap4ick/gifts-collector/actions/workflows/maven.yml)
# Gifts collector

This is a simple gifts grabber from steam gifts site.

## Using

The local modes below use your own logged-in browser and need no cookie. The cookie is only needed for the CI mode (a fresh browser, see below).

Log in to steam gifts site with your steam account, go to dev tools (F12) in browser and look for PHPSESSID cookie. Pass it via the `cookie` env variable, or put it into a `config.local.properties` file in the working directory (git-ignored, overrides `src/main/resources/config.properties`):

```properties
cookie=<PHPSESSID value>
cf_clearance=<optional>
```

> ⚠️ Never put real cookie values into `src/main/resources/config.properties` — that file is committed.

## Quick Start (one command, macOS / Linux)

```bash
./run.sh
```

The script will:
1. Launch Chrome with remote debugging on port 9222 (skipped if something already listens on that port).
2. Wait until Chrome is ready.
3. Build and run the app with `mvn compile exec:java`, attached to that Chrome.

The port is taken from the `LOCAL_BROWSER_PORT` env var, else from `local_browser_port` in `config.local.properties`, else 9222. Chrome is auto-detected; set `CHROME_BIN=/path/to/chrome` if it isn't found.

> 💡 The first time Chrome opens, log in to steamgifts.com. Your session is persisted in `~/.chrome-steamgifts`, so subsequent runs start straight away.

---

## Running the app

### CI / headless (GitHub Actions)

The app runs automatically via GitHub Actions. Cookie and `cf_clearance` values are read from environment variables (repository secrets `COOKIE` / `CF_CLEARANCE`).

### Local — Attach to Chrome (recommended for local use)

This mode attaches to a Chrome browser you started yourself, bypassing Cloudflare captcha entirely. The app will prompt you if Chrome isn't running or you're not logged in.

**Step 1 — Set the config flag** (once) in `config.local.properties` (or `LOCAL_BROWSER_PORT` env var). Keep it out of the committed `config.properties`, otherwise CI would try to attach to a local browser:

```properties
# config.local.properties
local_browser_port=9222
```

**Step 2 — Start Chrome with remote debugging** (every run):

```bash
# macOS
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=~/.chrome-steamgifts
```

```bash
# Linux
google-chrome --remote-debugging-port=9222 --user-data-dir=~/.chrome-steamgifts
```

The terminal will appear to hang — that's normal. Chrome is running in it.

**Step 3 — Run the app:**

```bash
mvn compile exec:java
```

The app will open steamgifts.com in your Chrome window. If not logged in, it will pause and ask you to log in before continuing.

> 💡 `--user-data-dir=~/.chrome-steamgifts` persists your session across runs, so you only need to log in once (unless the session expires).

### Option A — Use your real Chrome profile (Linux/Windows only)

Chrome recognises the browser as genuine so the captcha is typically skipped entirely.

> ⚠️ **macOS note:** Likely to fail due to OS-level security restrictions. Use the **Local — Attach to Chrome** mode above instead.

Set `chrome_profile_dir` in `config.local.properties` (or `CHROME_PROFILE_DIR` env var):
- Linux: `/home/<you>/.config/google-chrome`
- Windows: `C:\Users\<you>\AppData\Local\Google\Chrome\User Data`

Then run `mvn compile exec:java`. Cookie injection is skipped — the browser already has your session.

## Built With

* [Selenide](https://selenide.org/) - Web Ui testing framework based on Selenium
