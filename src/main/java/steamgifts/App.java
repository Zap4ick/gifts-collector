package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.ex.UIAssertionError;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chromium.ChromiumDriver;
import steamgifts.pages.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class App {

    private static final String COOKIE_PROP_KEY = "cookie";
    private static final String COOKIE_FIELD_NAME = "PHPSESSID";
    private static final String CF_CLEARANCE_FIELD_NAME = "cf_clearance";
    private static final String CHROME_PROFILE_DIR_KEY = "chrome_profile_dir";
    private static final String LOCAL_BROWSER_PORT_KEY = "local_browser_port";
    private static final String LOCAL_CONFIG_FILE = "config.local.properties";
    private static final String CONFIG_RESOURCE = "config.properties";
    private static final String SITE_KEY = "site";
    private static final Properties PROPERTIES = new Properties();

    // Deliberate dwell after opening a page, before the captcha check (bot protection; also lets
    // the challenge render). Only applies until a captcha has been passed once. Don't shorten.
    private static final int CAPTCHA_CHECK_PAUSE_SECS = 10;
    private static boolean captchaPassed = false;
    private static final Random RANDOM = new Random();
    // Cheapest possible giveaway; with fewer points there is nothing left to enter
    private static final int MIN_GIVEAWAY_COST = 1;
    // Extra dwell on a rejected giveaway page so we don't bounce off it instantly (bot protection).
    // Matches GamePage's enter-button wait, which plays the same role when the button is missing.
    private static final int REJECTED_GIVEAWAY_PAUSE_SECS = 5;

    /**
     * Loads config: bundled config.properties, then the optional git-ignored local overrides,
     * then env vars. Fails fast with a clear message instead of running with an empty config.
     */
    private static void readProperties() {
        try (InputStream in = App.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(CONFIG_RESOURCE + " not found on the classpath");
            }
            PROPERTIES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + CONFIG_RESOURCE, e);
        }

        // Optional git-ignored overrides (secrets live here, not in the committed file)
        Path localConfig = Path.of(LOCAL_CONFIG_FILE);
        if (Files.isRegularFile(localConfig)) {
            try (Reader reader = Files.newBufferedReader(localConfig)) {
                PROPERTIES.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't read " + localConfig.toAbsolutePath(), e);
            }
            log.info("Loaded local overrides from {}", localConfig.toAbsolutePath());
        }

        overrideFromEnv(COOKIE_PROP_KEY, COOKIE_PROP_KEY);
        overrideFromEnv(CF_CLEARANCE_FIELD_NAME, CF_CLEARANCE_FIELD_NAME);
        overrideFromEnv("CI", "ci");
        overrideFromEnv("CHROME_PROFILE_DIR", CHROME_PROFILE_DIR_KEY);
        overrideFromEnv("LOCAL_BROWSER_PORT", LOCAL_BROWSER_PORT_KEY);
    }

    /** An env var that is set (even to an empty string, which clears the value) wins over config files. */
    private static void overrideFromEnv(String envName, String key) {
        Optional.ofNullable(System.getenv(envName)).ifPresent(value -> PROPERTIES.setProperty(key, value));
    }

    /** Site root from config, always with a trailing slash. */
    private static String siteUrl() {
        String site = PROPERTIES.getProperty(SITE_KEY, "").trim();
        if (site.isEmpty()) {
            throw new IllegalStateException("'" + SITE_KEY + "' is not set in " + CONFIG_RESOURCE + " / " + LOCAL_CONFIG_FILE);
        }
        return site.endsWith("/") ? site : site + "/";
    }

    private static List<String> categoryPages(String site) {
        return List.of(
                site + "giveaways/search?type=wishlist",
                site + "giveaways/search?type=recommended",
                site + "giveaways/search?dlc=true",
                site
        );
    }

    private static boolean isLocalMode() {
        String profileDir = PROPERTIES.getProperty(CHROME_PROFILE_DIR_KEY, "").trim();
        String localPort = PROPERTIES.getProperty(LOCAL_BROWSER_PORT_KEY, "").trim();
        return !profileDir.isEmpty() || !localPort.isEmpty();
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void waitForInput(String message) {
        System.out.println(message);
        System.out.print("Press Enter when ready... ");
        try {
            if (System.in.read() == -1) {
                // No interactive console (e.g. CI): waiting would spin forever
                throw new IllegalStateException("stdin is closed, cannot wait for user input. Message was: " + message.strip());
            }
            // drain rest of line
            while (System.in.available() > 0) {
                //noinspection ResultOfMethodCallIgnored
                System.in.read();
            }
        } catch (IOException e) {
            log.warn("Error reading input", e);
        }
    }

    public static void main(String[] args) {
        readProperties();
        String site = siteUrl();
        // CI mode injects the session cookie; check it before spending time on starting a browser
        if (!isLocalMode() && PROPERTIES.getProperty(COOKIE_PROP_KEY, "").isBlank()) {
            throw new IllegalStateException("No cookie has been read from props! Set env var '"
                    + COOKIE_PROP_KEY + "' or put it into " + LOCAL_CONFIG_FILE);
        }

        Configuration.browser = "chrome";
        Configuration.browserSize = "1366x768";
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
        Utils.disableChromeDriverBuildCheck();
        String chromeDebugUserDataDir = System.getProperty("user.home") + "/.chrome-steamgifts";

        String profileDir = PROPERTIES.getProperty(CHROME_PROFILE_DIR_KEY, "").trim();
        String localPort = PROPERTIES.getProperty(LOCAL_BROWSER_PORT_KEY, "").trim();

        if (!profileDir.isEmpty()) {
            // Option A: launch Chrome with the user's real profile — captcha likely skipped entirely
            log.info("Local mode: launching Chrome with real profile at '{}'", profileDir);
            Configuration.browserCapabilities = Utils.buildProfileOptions(profileDir);
            Selenide.open(site);
            Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
        } else if (!localPort.isEmpty()) {
            // Option B: attach to an already-running Chrome instance
            String debuggerAddress = "localhost:" + localPort;
            int port;
            try {
                port = Integer.parseInt(localPort);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("'" + LOCAL_BROWSER_PORT_KEY + "' must be a port number, got '" + localPort + "'");
            }

            // Wait until Chrome is listening on the debug port
            while (!isPortOpen("localhost", port)) {
                waitForInput("""
                        
                        Chrome is not running with remote debugging.
                        Please start it with:
                          /Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --remote-debugging-port=%d --user-data-dir=%s
                        """.formatted(port, chromeDebugUserDataDir));
            }

            log.info("Local mode: attaching to running Chrome at {}", debuggerAddress);
            Configuration.browserCapabilities = Utils.buildAttachOptions(debuggerAddress);
            Selenide.open(site);

            ensureLoggedIn();
            log.info("Logged in — starting giveaway collection.");
        } else {
            // Default (CI) mode: spawn a fresh Chrome with stealth options
            Configuration.browserCapabilities = Utils.buildStealthOptions();
            Selenide.open(site);
            Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
        }

        if (!isLocalMode()) {
            WebDriverRunner.getWebDriver().manage().deleteCookieNamed(COOKIE_FIELD_NAME);
            WebDriverRunner.getWebDriver().manage().addCookie(new Cookie(COOKIE_FIELD_NAME, PROPERTIES.getProperty(COOKIE_PROP_KEY)));

            // Inject cf_clearance so Cloudflare treats this session as already verified
            Optional.ofNullable(PROPERTIES.getProperty(CF_CLEARANCE_FIELD_NAME))
                    .filter(v -> !v.isBlank())
                    .ifPresent(v -> {
                        WebDriverRunner.getWebDriver().manage().deleteCookieNamed(CF_CLEARANCE_FIELD_NAME);
                        Cookie cfCookie = new Cookie.Builder(CF_CLEARANCE_FIELD_NAME, v)
                                .domain(".steamgifts.com")
                                .path("/")
                                .isSecure(true)
                                .build();
                        WebDriverRunner.getWebDriver().manage().addCookie(cfCookie);
                        log.info("cf_clearance cookie injected.");
                    });
        } else {
            log.info("Local mode: skipping cookie injection — using browser's existing session.");
        }

        int pointsLeft = Integer.MAX_VALUE; // unknown until the first page is read
        for (String page : categoryPages(site)) {
            if (pointsLeft < MIN_GIVEAWAY_COST) {
                log.info("Out of points ({}), skipping remaining pages.", pointsLeft);
                break;
            }
            pointsLeft = drillPage(page);
        }

        try {
            Selenide.closeWebDriver();
        } catch (WebDriverException e) {
            log.warn("Browser session already closed or disconnected, skipping driver close: {}", e.getMessage());
        }

        if (PROPERTIES.getProperty("ci") != null) {
            log.info("Goodbye");
        } else {
            waitForInput("Finishing. Press Enter.");
        }
    }

    /**
     * Local modes: asks the user to log in in the browser window until they are.
     * CI mode: nobody to ask, so a missing login means a bad cookie.
     */
    private static void ensureLoggedIn() {
        if (!isLocalMode()) {
            if (!new BaseForm().isLoggedIn()) {
                throw new RuntimeException("We are not logged in! Check cookie in props!");
            }
            return;
        }
        while (!new BaseForm().isLoggedIn()) {
            waitForInput("""
                    
                    Not logged in to steamgifts.com.
                    Please log in to https://www.steamgifts.com in the Chrome window.
                    """);
            Selenide.refresh();
        }
    }

    /** Enters what it can on one giveaway category and returns the points left afterwards. */
    private static int drillPage(String page) {
        Selenide.open(page);

        if (!captchaPassed) {
            Utils.pause(CAPTCHA_CHECK_PAUSE_SECS);
            CaptchaPage captchaPage = new CaptchaPage();
            if (captchaPage.isOpen()) {
                if (isLocalMode()) {
                    waitForInput("\nCaptcha detected! Please solve the captcha in the browser window.");
                } else {
                    captchaPage.passCaptcha();
                }
                captchaPassed = true;
            }
        }

        if (new SuspensionPage().isOpen()) {
            throw new RuntimeException("Seems like we are suspened :( Aborting mission!");
        }

        ensureLoggedIn();

        ListPage listPage = new ListPage();
        listPage.dismissOverlays();
        int minimumCostSeen = Integer.MAX_VALUE;

        // Giveaways already opened in this category; keyed by URL because row indexes shift
        // when an entered giveaway fades out and differ between pagination pages.
        Set<String> visited = new HashSet<>();
        do {
            int points = listPage.getPoints();
            if (points < MIN_GIVEAWAY_COST) {
                log.info("No more points, skipping.");
                break;
            }

            List<Giveaway> candidates = listPage.getGiveaways().stream()
                    .filter(g -> !g.pinned() && !visited.contains(g.url()))
                    .toList();
            if (candidates.isEmpty()) {
                log.info("No more giveaways on this page, skipping.");
                break;
            }

            int pageMinCost = candidates.stream().mapToInt(Giveaway::cost).min().orElse(Integer.MAX_VALUE);
            minimumCostSeen = Math.min(minimumCostSeen, pageMinCost);
            if (points < minimumCostSeen) {
                log.info("Points ({}) below minimum giveaway cost ({}) — stopping early.", points, minimumCostSeen);
                break;
            }
            enterAffordableGiveaways(listPage, visited);
        } while (listPage.clickNextIfPresent());
        // Read points again: the value from the loop is stale after giveaways have been entered
        int pointsLeft = listPage.getPoints();
        log.info("Points: {}, Games left: {}", pointsLeft, listPage.hasNotFadedGames());
        return pointsLeft;
    }

    private static void enterAffordableGiveaways(ListPage listPage, Set<String> visited) {
        String listUrl = WebDriverRunner.url();
        Optional<Giveaway> next = findAffordable(listPage, visited);
        while (next.isPresent()) {
            Giveaway giveaway = next.get();
            visited.add(giveaway.url()); // never open the same giveaway twice, whatever happens on its page
            try {
                processGiveaway(giveaway);
            } catch (UIAssertionError | WebDriverException e) {
                // One broken giveaway page shouldn't abort the whole run. If the browser itself is
                // gone, reloading the list below fails and stops the run anyway.
                log.warn("Failed to process giveaway {} ({}), skipping it: {}", giveaway.name(), giveaway.url(), firstLine(e));
                log.debug("Full error:", e);
            }

            // Reload the list instead of navigating back: no stale cached page, entered rows are faded
            Selenide.open(listUrl);
            listPage.dismissOverlays();
            Utils.pause(4 + RANDOM.nextInt(6)); // random 4–9s to avoid uniform timing fingerprint
            next = findAffordable(listPage, visited);
        }
    }

    private static void processGiveaway(Giveaway giveaway) {
        Selenide.open(giveaway.url());

        GamePage gamePage = new GamePage();
        if (gamePage.isError()) {
            log.info("Can't participate ({}): {}", gamePage.getErrorText(), giveaway.name());
            Utils.pause(REJECTED_GIVEAWAY_PAUSE_SECS);
        } else if (!gamePage.canEnter()) {
            String reason = gamePage.isEntered() ? "already entered" : "enter button is not present";
            log.info("Can't participate ({}): {}", reason, giveaway.name());
        } else {
            log.info("Entering giveaway for {} ({}P)", giveaway.name(), giveaway.cost());
            gamePage.enterGiveaway();
        }
    }

    /** Selenide/WebDriver messages are long multi-line dumps; the first line is enough for the log. */
    private static String firstLine(Throwable e) {
        return String.valueOf(e.getMessage()).lines().findFirst().orElse("");
    }

    private static Optional<Giveaway> findAffordable(ListPage listPage, Set<String> visited) {
        int points = listPage.getPoints();
        return listPage.getGiveaways().stream()
                .filter(g -> !g.pinned() && !visited.contains(g.url()) && g.cost() <= points)
                .findFirst();
    }
}
