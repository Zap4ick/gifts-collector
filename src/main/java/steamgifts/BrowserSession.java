package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chromium.ChromiumDriver;
import steamgifts.pages.BaseForm;

import java.io.IOException;
import java.net.Socket;

/** Starts (or attaches to) the browser for the configured {@link RunMode} and gets it logged in. */
@Slf4j
public class BrowserSession {

    private static final String SESSION_COOKIE_NAME = "PHPSESSID";
    private static final String CF_CLEARANCE_COOKIE_NAME = "cf_clearance";

    private final Config config;

    public BrowserSession(Config config) {
        this.config = config;
    }

    public void start() {
        Configuration.browser = "chrome";
        Configuration.browserSize = "1366x768";
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
        Utils.disableChromeDriverBuildCheck();

        switch (config.runMode()) {
            case PROFILE -> startWithProfile();
            case ATTACH -> attachToRunningChrome();
            case CI -> startFresh();
        }

        if (config.runMode() == RunMode.CI) {
            injectCookies();
        } else {
            log.info("Local mode: skipping cookie injection — using browser's existing session.");
        }
    }

    /** Option A: launch Chrome with the user's real profile — captcha likely skipped entirely. */
    private void startWithProfile() {
        String profileDir = config.chromeProfileDir();
        log.info("Local mode: launching Chrome with real profile at '{}'", profileDir);
        Configuration.browserCapabilities = Utils.buildProfileOptions(profileDir);
        Selenide.open(config.site());
        Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
    }

    /** Option B: attach to an already-running Chrome instance. */
    private void attachToRunningChrome() {
        int port = config.localBrowserPort();
        String debuggerAddress = "localhost:" + port;
        String chromeDebugUserDataDir = System.getProperty("user.home") + "/.chrome-steamgifts";

        // Wait until Chrome is listening on the debug port
        while (!isPortOpen("localhost", port)) {
            Console.waitForInput("""

                    Chrome is not running with remote debugging.
                    Please start it with:
                      /Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --remote-debugging-port=%d --user-data-dir=%s
                    """.formatted(port, chromeDebugUserDataDir));
        }

        log.info("Local mode: attaching to running Chrome at {}", debuggerAddress);
        Configuration.browserCapabilities = Utils.buildAttachOptions(debuggerAddress);
        Selenide.open(config.site());

        ensureLoggedIn();
        log.info("Logged in — starting giveaway collection.");
    }

    /** Default (CI) mode: spawn a fresh Chrome with stealth options. */
    private void startFresh() {
        Configuration.browserCapabilities = Utils.buildStealthOptions();
        Selenide.open(config.site());
        Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
    }

    private void injectCookies() {
        WebDriverRunner.getWebDriver().manage().deleteCookieNamed(SESSION_COOKIE_NAME);
        WebDriverRunner.getWebDriver().manage().addCookie(new Cookie(SESSION_COOKIE_NAME, config.cookie()));

        // Inject cf_clearance so Cloudflare treats this session as already verified
        config.cfClearance().ifPresent(v -> {
            WebDriverRunner.getWebDriver().manage().deleteCookieNamed(CF_CLEARANCE_COOKIE_NAME);
            Cookie cfCookie = new Cookie.Builder(CF_CLEARANCE_COOKIE_NAME, v)
                    .domain(".steamgifts.com")
                    .path("/")
                    .isSecure(true)
                    .build();
            WebDriverRunner.getWebDriver().manage().addCookie(cfCookie);
            log.info("cf_clearance cookie injected.");
        });
    }

    /**
     * Local modes: asks the user to log in in the browser window until they are.
     * CI mode: nobody to ask, so a missing login means a bad cookie.
     */
    public void ensureLoggedIn() {
        if (!config.runMode().isLocal()) {
            if (!new BaseForm().isLoggedIn()) {
                throw new RuntimeException("We are not logged in! Check cookie in props!");
            }
            return;
        }
        while (!new BaseForm().isLoggedIn()) {
            Console.waitForInput("""

                    Not logged in to steamgifts.com.
                    Please log in to https://www.steamgifts.com in the Chrome window.
                    """);
            Selenide.refresh();
        }
    }

    public void close() {
        try {
            Selenide.closeWebDriver();
        } catch (WebDriverException e) {
            log.warn("Browser session already closed or disconnected, skipping driver close: {}", e.getMessage());
        }
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket ignored = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
