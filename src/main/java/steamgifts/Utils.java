package steamgifts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

    public static void pause(int secs) {
        try {
            TimeUnit.SECONDS.sleep(secs);
        } catch (InterruptedException e) {
            log.warn("Exception in thread sleeping: ", e);
        }
    }

    /**
     * Builds ChromeOptions that attach to an already-running Chrome instance
     * via Chrome DevTools Protocol (CDP) remote debugging.
     *
     * Start Chrome first with:
     *   google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-debug
     */
    public static ChromeOptions buildAttachOptions(String debuggerAddress) {
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", debuggerAddress);
        return options;
    }

    /**
     * Builds ChromeOptions that launch Chrome with the user's real profile.
     * Cloudflare sees a genuine browser fingerprint and typically skips the captcha.
     * Chrome must NOT already be running with this profile when the app starts.
     * Stealth flags are intentionally omitted — the real profile is already trusted.
     */
    public static ChromeOptions buildProfileOptions(String profileDir) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profileDir);
        options.addArguments("--profile-directory=Default");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-infobars");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-extensions");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    /**
     * Builds ChromeOptions configured to look as human-like as possible.
     */
    public static ChromeOptions buildStealthOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.56 Safari/537.36");
        options.addArguments("--lang=en-US,en;q=0.9");
        options.addArguments("--window-size=1366,768");
        options.addArguments("--disable-infobars");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        // Disable site isolation so CDP stealth script also runs inside cross-origin iframes (e.g. Turnstile)
        options.addArguments("--disable-features=IsolateOrigins,site-per-process");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("intl.accept_languages", "en-US,en");
        options.setExperimentalOption("prefs", prefs);

        return options;
    }

    /**
     * Injects a stealth JS script via CDP that runs before any page script,
     * overriding properties commonly used by bot-detection fingerprinting.
     */
    public static void injectStealthScript(ChromiumDriver driver) {
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", """
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                window.chrome = {
                    runtime: {},
                    loadTimes: function() {},
                    csi: function() {},
                    app: {}
                };
                Object.defineProperty(navigator, 'plugins',            { get: () => [1, 2, 3, 4, 5] });
                Object.defineProperty(navigator, 'mimeTypes',          { get: () => [1, 2, 3] });
                Object.defineProperty(navigator, 'languages',          { get: () => ['en-US', 'en'] });
                Object.defineProperty(navigator, 'platform',           { get: () => 'Win32' });
                Object.defineProperty(navigator, 'hardwareConcurrency',{ get: () => 8 });
                Object.defineProperty(navigator, 'deviceMemory',       { get: () => 8 });
                Object.defineProperty(screen, 'width',      { get: () => 1366 });
                Object.defineProperty(screen, 'height',     { get: () => 768 });
                Object.defineProperty(screen, 'colorDepth', { get: () => 24 });
                Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });
                Object.defineProperty(window, 'outerWidth',  { get: () => 1366 });
                Object.defineProperty(window, 'outerHeight', { get: () => 768 });
                const origQuery = window.navigator.permissions.query.bind(navigator.permissions);
                window.navigator.permissions.query = (parameters) =>
                    parameters.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : origQuery(parameters);
                const getParameter = WebGLRenderingContext.prototype.getParameter;
                WebGLRenderingContext.prototype.getParameter = function(parameter) {
                    if (parameter === 37445) return 'Intel Inc.';
                    if (parameter === 37446) return 'Intel Iris OpenGL Engine';
                    return getParameter.call(this, parameter);
                };
                """));
    }
}
