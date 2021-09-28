package steamgifts.pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static com.codeborne.selenide.Selenide.*;

public class ListPage extends BaseForm {

    private final ElementsCollection seNotFadedLink = $$("div:not(.is-faded) > a.giveaway_image_thumbnail, div:not(.is-faded) > a.giveaway_image_thumbnail_missing");
    private final ElementsCollection sePinnedGames = $$(".pinned-giveaways__outer-wrap div:not(.is-faded) > a.giveaway_image_thumbnail, div:not(.is-faded) > a.giveaway_image_thumbnail_missing");
    private final ElementsCollection seNotFadedPoints = $$x("//div[@class='giveaway__row-inner-wrap']//h2/span[@class='giveaway__heading__thin'][last()][text()]");

    private final SelenideElement seNextPage = $x("//a[./span[text()='Next']]");

    public void openNotFadedGameByNumber(int i) {
        seNotFadedLink.get(i).click();
    }

    public boolean hasNotFadedGames() {
        return !seNotFadedLink.isEmpty();
    }

    public Integer getLinkNumberWithPointsWeCanHandle(int points, List<Integer> ignored) {
        for (int i = 0; i < seNotFadedPoints.size(); i++) {
            if (seNotFadedPoints.get(i).isDisplayed()) {
                int pointsOfI = Integer.parseInt(StringUtils.substringBetween(seNotFadedPoints.get(i).getText(), "(", "P)"));
                if (pointsOfI <= points && !ignored.contains(i)) {
                    return i;
                }
            }
        }
        return null;
    }

    public boolean clickNextIfPresent() {
        if (seNextPage.isDisplayed()) {
            seNextPage.click();
            return true;
        }
        return false;
    }

    public int getPinnedGamesNum() {
        return sePinnedGames.size();
    }
}
