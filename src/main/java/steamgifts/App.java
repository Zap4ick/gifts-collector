package steamgifts;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.Cookie;
import steamgifts.pages.GamePage;
import steamgifts.pages.ListPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class App {

    private static final List<String> pages = new ArrayList<String>() {{
        add("https://www.steamgifts.com/giveaways/search?type=wishlist");
        add("https://www.steamgifts.com/giveaways/search?type=recommended");
        add("https://www.steamgifts.com");
    }};

    public static void main(String[] args) throws IOException {
        Configuration.browser = "chrome";
        Configuration.headless = true;

        Selenide.open("https://www.steamgifts.com/");
        WebDriverRunner.getWebDriver().manage().deleteCookieNamed("PHPSESSID");
        WebDriverRunner.getWebDriver().manage().addCookie(new Cookie("PHPSESSID", readCookie()));

        pages.forEach(App::drillPage);

        System.in.read();
        Selenide.closeWebDriver();
    }

    private static void drillPage(String page) {
        List<Integer> ignoredNums = new ArrayList<>();
        Selenide.open(page);
        ListPage listPage = new ListPage();
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
            if (!gamePage.isWon()) {
                gamePage.enterGiveway();
            } else {
                log.info("Already won: {}", gamePage.getName());
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

    private static String readCookie() {
        File file = new File("steamGiftsCookie.txt");
        try {
            return FileUtils.readFileToString(file, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
