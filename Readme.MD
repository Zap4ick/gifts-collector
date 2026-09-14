[![build](https://github.com/Zap4ick/gifts-collector/actions/workflows/maven.yml/badge.svg)](https://github.com/Zap4ick/gifts-collector/actions/workflows/maven.yml)
# Gifts collector

This is a simple gifts grabber from steam gifts site.

## Using

Log in to steam gifts site with your steam account, go to dev tools (F12) in browser and look for PHPSESSID cookie. Put it in config.properties file or env variable.

## Quick Start (one command)

Make sure `config.properties` has `local_browser_port=9222`, then just run:

```bash
./run.sh
```

The script will:
1. Launch Chrome with remote debugging on port 9222 (skipped if Chrome is already running).
2. Wait until Chrome is ready.
3. Run the app with `mvn exec:java`.

> 💡 The first time Chrome opens, log in to steamgifts.com. Your session is persisted in `~/.chrome-steamgifts`, so subsequent runs start straight away.

---

## Running the app

### CI / headless (GitHub Actions)

The app runs automatically via GitHub Actions. Cookie and `cf_clearance` values are read from environment variables / `config.properties`.

### Local — Attach to Chrome (recommended for local use)

This mode attaches to a Chrome browser you started yourself, bypassing Cloudflare captcha entirely. The app will prompt you if Chrome isn't running or you're not logged in.

**Step 1 — Set the config flag** (once):

```properties
# config.properties
local_browser_port=9222
```

**Step 2 — Start Chrome with remote debugging** (every run):

```bash
# macOS
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=~/.chrome-steamgifts
```

The terminal will appear to hang — that's normal. Chrome is running in it.

**Step 3 — Run the app:**

```bash
mvn exec:java
```

The app will open steamgifts.com in your Chrome window. If not logged in, it will pause and ask you to log in before continuing.

> 💡 `--user-data-dir=~/.chrome-steamgifts` persists your session across runs, so you only need to log in once (unless the session expires or your Mac reboots).

### Option A — Use your real Chrome profile (Linux/Windows only)

Chrome recognises the browser as genuine so the captcha is typically skipped entirely.

> ⚠️ **macOS note:** Likely to fail due to OS-level security restrictions. Use the **Local — Attach to Chrome** mode above instead.

Set `chrome_profile_dir` in `config.properties`:
- Linux: `/home/<you>/.config/google-chrome`
- Windows: `C:\Users\<you>\AppData\Local\Google\Chrome\User Data`

Then run `mvn exec:java`. Cookie injection is skipped — the browser already has your session.

## Built With

* [Selenide](https://selenide.org/) - Web Ui testing framework based on Selenium
