package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.chromium.ChromiumDriver;
import steamgifts.pages.*;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Slf4j
public class App {

    private static final String COOKIE_PROP_KEY = "cookie";
    private static final String COOKIE_FIELD_NAME = "PHPSESSID";
    private static final String CF_CLEARANCE_FIELD_NAME = "cf_clearance";
    private static final String CHROME_PROFILE_DIR_KEY = "chrome_profile_dir";
    private static final String LOCAL_BROWSER_PORT_KEY = "local_browser_port";
    private static final Properties PROPERTIES = new Properties();

    private static final String CAPTCHA_PASSED_KEY = "CAPTCHA_PASSED";
    private static final Random RANDOM = new Random();

    static {
        readProperties();
    }

    private static void readProperties() {
        try {
            PROPERTIES.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
            Optional.ofNullable(System.getenv(COOKIE_PROP_KEY)).ifPresent(value -> PROPERTIES.setProperty(COOKIE_PROP_KEY, value));
            if (PROPERTIES.getProperty(COOKIE_PROP_KEY).isEmpty()) {
                throw new RuntimeException("No cookie has been read from props!");
            }
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
            //noinspection ResultOfMethodCallIgnored
            // drain rest of line
            do System.in.read();
            while (System.in.available() > 0);
        } catch (IOException e) {
            log.warn("Error reading input", e);
        }
    }

    public static void main(String[] args) {
        Configuration.browser = "chrome";
        Configuration.browserSize = "1366x768";
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
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

        AtomicInteger pointsLeft = new AtomicInteger(300);
        pages.forEach((page) -> pointsLeft.set(drillPage(page, pointsLeft.get())));

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

    private static int drillPage(String page, Integer pointsleft) {
        if (pointsleft < 5) {
            return pointsleft;
        }

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
        listPage.consentIfPresent();
        listPage.closeBannerIfPresent();
        listPage.closeModalWinIfPresent();
        int points;
        int minimumCostSeen = Integer.MAX_VALUE;

        List<Integer> ignoredNums = new ArrayList<>();
        do {
            points = listPage.getPoints();
            int linksCount = listPage.getLinksCount();

            if (points == 0) {
                log.info("No more points, skipping.");
                break;
            }

            if (linksCount == 0) {
                log.info("No more links on this page, skipping.");
                break;
            }
            optOutPinnedGames(listPage, ignoredNums);
            int pageMinCost = listPage.getMinimumPointsCost(ignoredNums);
            minimumCostSeen = Math.min(minimumCostSeen, pageMinCost);
            if (minimumCostSeen < Integer.MAX_VALUE && points < minimumCostSeen) {
                log.info("Points ({}) below minimum giveaway cost ({}) — stopping early.", points, minimumCostSeen);
                break;
            }
            clickThrough(ignoredNums, listPage, points);
        } while (listPage.clickNextIfPresent());
        log.info("Points: {}, Games left: {}", listPage.getPoints(), listPage.hasNotFadedGames());
        return points;
    }

    private static void clickThrough(List<Integer> ignoredNums, ListPage listPage, int points) {
        Integer numWeClick = listPage.getLinkNumberWithPointsWeCanHandle(points, ignoredNums);
        while (numWeClick != null && points > 0) {
            listPage.openNotFadedGameByNumber(numWeClick);
            GamePage gamePage = new GamePage();
            if (gamePage.isError()) {
                log.info("Error encountered on game page: {}", gamePage.getName());
                ignoredNums.add(numWeClick);
                String reason = gamePage.isWon() ? "already won" : gamePage.isEntered() ? "already entered" : "not enough points";
                log.info("Can't participate ({}): {}", reason, gamePage.getName());
            } else {
                gamePage.enterGiveaway();
            }
            Selenide.back();
            // Selenide.refresh(); // hotfix for cache_err
            Utils.pause(4 + RANDOM.nextInt(6)); // random 4–9s to avoid uniform timing fingerprint
            points = listPage.getPoints();
            numWeClick = listPage.getLinkNumberWithPointsWeCanHandle(points, ignoredNums);
        }
    }

    private static void optOutPinnedGames(ListPage listPage, List<Integer> ignoredNums) {
        listPage.getPinnedGameIndices().stream()
                .filter(i -> !ignoredNums.contains(i))
                .forEach(ignoredNums::add);
    }
}
