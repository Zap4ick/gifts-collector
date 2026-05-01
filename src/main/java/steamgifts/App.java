package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.chromium.ChromiumDriver;
import steamgifts.pages.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.IntStream;

@Slf4j
public class App {

    private static final String COOKIE_PROP_KEY = "cookie";
    private static final String COOKIE_FIELD_NAME = "PHPSESSID";
    private static final String CF_CLEARANCE_FIELD_NAME = "cf_clearance";
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
            // Allow overriding cf_clearance via environment variable (CI secret)
            Optional.ofNullable(System.getenv(CF_CLEARANCE_FIELD_NAME)).ifPresent(value -> PROPERTIES.setProperty(CF_CLEARANCE_FIELD_NAME, value));

            Optional.ofNullable(System.getenv("CI")).ifPresent(value -> PROPERTIES.setProperty("ci", value));
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

    public static void main(String[] args) {
        Configuration.browser = "chrome";
        Configuration.browserSize = "1366x768";
        Configuration.browserCapabilities = Utils.buildStealthOptions();

        Selenide.open(PROPERTIES.getProperty("site"));

        Utils.injectStealthScript((ChromiumDriver) WebDriverRunner.getWebDriver());

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

        pages.forEach(App::drillPage);

        Optional.ofNullable(PROPERTIES.getProperty("ci")).ifPresentOrElse(prop -> Logger.getGlobal().info("Goodbye"),
                ThrowingRunnable.unchecked(System.in::read));

        Selenide.closeWebDriver();
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

    private static void drillPage(String page) {
        List<Integer> ignoredNums = new ArrayList<>();
        Selenide.open(page);

        if (System.getProperty(CAPTCHA_PASSED_KEY) == null && new CaptchaPage().isOpen()) {
            new CaptchaPage().passCaptcha();
            System.setProperty(CAPTCHA_PASSED_KEY, "true");
        }

        if (new SuspensionPage().isOpen()) {
            throw new RuntimeException("Seems like we are suspened :( Aborting mission!");
        }

        if (!new BaseForm().isLoggedIn()) {
            throw new RuntimeException("We are not logged in! Check cookie in props!");
        }

        ListPage listPage = new ListPage();
        listPage.consentIfPresent();
        listPage.closeBannerIfPresent();
        listPage.closeModalWinIfPresent();
        do {
            int points = listPage.getPoints();
            if (points == 0) {
                break;
            }
            optOutPinnedGames(listPage, ignoredNums);
            clickThrough(ignoredNums, listPage, points);
        } while (listPage.clickNextIfPresent());
        log.info("Points: {}, Games left: {}", listPage.getPoints(), listPage.hasNotFadedGames());
    }

    private static void clickThrough(List<Integer> ignoredNums, ListPage listPage, int points) {
        Integer numWeClick = listPage.getLinkNumberWithPointsWeCanHandle(points, ignoredNums);
        while (numWeClick != null && points > 0) {
            listPage.openNotFadedGameByNumber(numWeClick);
            GamePage gamePage = new GamePage();
            if (gamePage.isWon() || gamePage.isMine()) {
                log.info("Can't participate: {}", gamePage.getName());
                ignoredNums.add(numWeClick);
            } else {
                gamePage.enterGiveaway();
            }
            Selenide.back();
            Selenide.refresh(); // hotfix for cache_err
            Utils.pause(4 + RANDOM.nextInt(6)); // random 4–9s to avoid uniform timing fingerprint
            points = listPage.getPoints();
            numWeClick = listPage.getLinkNumberWithPointsWeCanHandle(points, ignoredNums);
        }
    }

    private static void optOutPinnedGames(ListPage listPage, List<Integer> ignoredNums) {
        int pinnedGamesNum = listPage.getPinnedGamesNum();
        IntStream.range(0, pinnedGamesNum).forEach(ignoredNums::add);
    }
}
