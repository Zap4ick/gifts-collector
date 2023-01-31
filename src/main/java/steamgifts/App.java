package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Cookie;
import steamgifts.pages.BaseForm;
import steamgifts.pages.GamePage;
import steamgifts.pages.ListPage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.IntStream;

@Slf4j
public class App {

    private static final String COOKIE_PROP_KEY = "cookie";
    private static final String COOKIE_FIELD_NAME = "PHPSESSID";
    private static final Properties PROPERTIES = new Properties();

    static {
        readProperties();
    }

    private static void readProperties() {
        try {
            PROPERTIES.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
            Optional.ofNullable(System.getenv(COOKIE_PROP_KEY)).ifPresent(value -> PROPERTIES.setProperty(COOKIE_PROP_KEY, value));
            if (PROPERTIES.getProperty(COOKIE_PROP_KEY).isEmpty()){
                throw new RuntimeException("No cookie has been read from props!");
            }

            Optional.ofNullable(System.getenv("TRAVIS")).ifPresent(value -> PROPERTIES.setProperty("ci", value));
            Optional.ofNullable(System.getenv("CI")).ifPresent(value -> PROPERTIES.setProperty("ci", value));
        } catch (IOException e) {
            log.warn("Properties not loaded:", e);
        }
    }

    private static final List<String> pages = new ArrayList<>() {{
        add(PROPERTIES.getProperty("site") + "giveaways/search?type=wishlist");
        add(PROPERTIES.getProperty("site") + "giveaways/search?type=recommended");
        add(PROPERTIES.getProperty("site") + "giveaways/search?dlc=true");
        add(PROPERTIES.getProperty("site"));
    }};

    public static void main(String[] args) {
        Configuration.browser = "chrome";
        Configuration.headless = true;
        //Configuration.browserSize = "1366x768";

        Selenide.open(PROPERTIES.getProperty("site"));
        WebDriverRunner.getWebDriver().manage().deleteCookieNamed(COOKIE_FIELD_NAME);
        WebDriverRunner.getWebDriver().manage().addCookie(new Cookie(COOKIE_FIELD_NAME, PROPERTIES.getProperty(COOKIE_PROP_KEY)));

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

        if (!new BaseForm().isLoggedIn()) {
            throw new RuntimeException("We are not logged in! Check cookie in props!");
        }

        ListPage listPage = new ListPage();
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
            if (!gamePage.isWon() && !gamePage.isMine()) {
                gamePage.enterGiveway();
            }
            else {
                log.info("Can't participate: {}", gamePage.getName());
                ignoredNums.add(numWeClick);
            }
            Selenide.back();
            Utils.pause(5);
            points = listPage.getPoints();
            numWeClick = listPage.getLinkNumberWithPointsWeCanHandle(points, ignoredNums);
        }
    }
    private static void optOutPinnedGames(ListPage listPage, List<Integer> ignoredNums) {
        int pinnedGamesNum = listPage.getPinnedGamesNum();
        IntStream.range(0, pinnedGamesNum).forEach(ignoredNums::add);
    }
}
