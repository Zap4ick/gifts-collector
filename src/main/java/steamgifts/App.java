package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.chromium.ChromiumDriver;
import steamgifts.pages.*;

import java.io.IOException;
import java.io.Reader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

@Slf4j
public class App {

    private static final String COOKIE_PROP_KEY = "cookie";
    private static final String COOKIE_FIELD_NAME = "PHPSESSID";
    private static final String CF_CLEARANCE_FIELD_NAME = "cf_clearance";
    private static final String CHROME_PROFILE_DIR_KEY = "chrome_profile_dir";
    private static final String LOCAL_BROWSER_PORT_KEY = "local_browser_port";
    private static final String LOCAL_CONFIG_FILE = "config.local.properties";
    private static final Properties PROPERTIES = new Properties();

    private static final String CAPTCHA_PASSED_KEY = "CAPTCHA_PASSED";
    private static final Random RANDOM = new Random();
    // Cheapest possible giveaway; with fewer points there is nothing left to enter
    private static final int MIN_GIVEAWAY_COST = 1;

    static {
        readProperties();
    }

    private static void readProperties() {
        try {
            PROPERTIES.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
            // Optional git-ignored overrides (secrets live here, not in the committed file)
            Path localConfig = Path.of(LOCAL_CONFIG_FILE);
            if (Files.isRegularFile(localConfig)) {
                try (Reader reader = Files.newBufferedReader(localConfig)) {
                    PROPERTIES.load(reader);
                }
                log.info("Loaded local overrides from {}", localConfig.toAbsolutePath());
            }
            Optional.ofNullable(System.getenv(COOKIE_PROP_KEY)).ifPresent(value -> PROPERTIES.setProperty(COOKIE_PROP_KEY, value));
            Optional.ofNullable(System.getenv(CF_CLEARANCE_FIELD_NAME)).ifPresent(value -> PROPERTIES.setProperty(CF_CLEARANCE_FIELD_NAME, value));

            Optional.ofNullable(System.getenv("CI")).ifPresent(value -> PROPERTIES.setProperty("ci", value));
            Optional.ofNullable(System.getenv("CHROME_PROFILE_DIR")).ifPresent(value -> PROPERTIES.setProperty(CHROME_PROFILE_DIR_KEY, value));
            Optional.ofNullable(System.getenv("LOCAL_BROWSER_PORT")).ifPresent(value -> PROPERTIES.setProperty(LOCAL_BROWSER_PORT_KEY, value));
        } catch (IOException e) {
            log.warn("Properties not loaded:", e);
        }
    }

    private static final List<String> pages = Arrays.asList(
            PROPERTIES.getProperty("site") + "giveaways/search?type=wishlist",
            PROPERTIES.getProperty("site") + "giveaways/search?type=recommended",
            PROPERTIES.getProperty("site") + "giveaways/search?dlc=true",
            PROPERTIES.getProperty("site")
    );

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
            Selenide.open(PROPERTIES.getProperty("site"));
            Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
        } else if (!localPort.isEmpty()) {
            // Option B: attach to an already-running Chrome instance
            String debuggerAddress = "localhost:" + localPort;
            int port = Integer.parseInt(localPort);

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
            Selenide.open(PROPERTIES.getProperty("site"));

            // Wait until user is logged in
            while (!new BaseForm().isLoggedIn()) {
                waitForInput("""
                        
                        Not logged in to steamgifts.com.
                        Please log in to https://www.steamgifts.com in the Chrome window.
                        """);
                Selenide.refresh();
            }
            log.info("Logged in — starting giveaway collection.");
        } else {
            // Default (CI) mode: spawn a fresh Chrome with stealth options
            Configuration.browserCapabilities = Utils.buildStealthOptions();
            Selenide.open(PROPERTIES.getProperty("site"));
            Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());
        }

        if (!isLocalMode()) {
            if (PROPERTIES.getProperty(COOKIE_PROP_KEY, "").isBlank()) {
                throw new RuntimeException("No cookie has been read from props! Set env var '"
                        + COOKIE_PROP_KEY + "' or put it into " + LOCAL_CONFIG_FILE);
            }
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
        for (String page : pages) {
            if (pointsLeft < MIN_GIVEAWAY_COST) {
                log.info("Out of points ({}), skipping remaining pages.", pointsLeft);
                break;
            }
            pointsLeft = drillPage(page);
        }

        try {
            Selenide.closeWebDriver();
        } catch (org.openqa.selenium.WebDriverException e) {
            log.warn("Browser session already closed or disconnected, skipping driver close: {}", e.getMessage());
        }

        Optional.ofNullable(PROPERTIES.getProperty("ci")).ifPresentOrElse(prop -> Logger.getGlobal().info("Goodbye"),
                ThrowingRunnable.unchecked(() -> waitForInput("Finishing. Press Enter.")));
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws IOException;

        static Runnable unchecked(ThrowingRunnable f) {
            return () -> {
                try {
                    f.run();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    /** Enters what it can on one giveaway category and returns the points left afterwards. */
    private static int drillPage(String page) {
        Selenide.open(page);

        if (System.getProperty(CAPTCHA_PASSED_KEY) == null) {
            CaptchaPage captchaPage = new CaptchaPage();
            if (captchaPage.isOpen()) {
                if (isLocalMode()) {
                    waitForInput("\nCaptcha detected! Please solve the captcha in the browser window.");
                } else {
                    captchaPage.passCaptcha();
                }
                System.setProperty(CAPTCHA_PASSED_KEY, "true");
            }
        }

        if (new SuspensionPage().isOpen()) {
            throw new RuntimeException("Seems like we are suspened :( Aborting mission!");
        }

        if (isLocalMode()) {
            while (!new BaseForm().isLoggedIn()) {
                waitForInput("""
                        
                        Not logged in to steamgifts.com.
                        Please log in to https://www.steamgifts.com in the Chrome window.
                        """);
                Selenide.refresh();
            }
        } else if (!new BaseForm().isLoggedIn()) {
            throw new RuntimeException("We are not logged in! Check cookie in props!");
        }

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
            Selenide.open(giveaway.url());

            GamePage gamePage = new GamePage();
            if (gamePage.isError()) {
                log.info("Error encountered on game page: {}", gamePage.getName());
                String reason = gamePage.isWon() ? "already won" : gamePage.isEntered() ? "already entered" : "not enough points";
                log.info("Can't participate ({}): {}", reason, gamePage.getName());
            } else if (gamePage.isEntered()) { // no error but no button either
                log.info("Can't participate ({}): {}", gamePage.getName(), "button is not present");
            } else {
                gamePage.enterGiveaway();
            }

            // Reload the list instead of navigating back: no stale cached page, entered rows are faded
            Selenide.open(listUrl);
            listPage.dismissOverlays();
            Utils.pause(4 + RANDOM.nextInt(6)); // random 4–9s to avoid uniform timing fingerprint
            next = findAffordable(listPage, visited);
        }
    }

    private static Optional<Giveaway> findAffordable(ListPage listPage, Set<String> visited) {
        int points = listPage.getPoints();
        return listPage.getGiveaways().stream()
                .filter(g -> !g.pinned() && !visited.contains(g.url()) && g.cost() <= points)
                .findFirst();
    }
}
