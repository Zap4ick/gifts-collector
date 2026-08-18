package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

public class ListPage extends BaseForm {

    private static final String NOT_FADED_ROW =
            "//div[contains(@class,'giveaway__row-inner-wrap') and not(contains(@class,'is-faded'))]";

    private final ElementsCollection seNotFadedLink = $$x(
            NOT_FADED_ROW + "//a[contains(@class,'giveaway_image_thumbnail')]");
    private final ElementsCollection seNotFadedPoints = $$x(
            NOT_FADED_ROW + "//h2/span[@class='giveaway__heading__thin'][last()][text()]");

    private final SelenideElement seNextPage = $x("//a[./span[text()='Next']]");

    public void openNotFadedGameByNumber(int i) {
        seNotFadedLink.get(i).click(ClickOptions.usingJavaScript());
    }

    public boolean hasNotFadedGames() {
        return !seNotFadedLink.isEmpty();
    }

    public Integer getLinkNumberWithPointsWeCanHandle(int points, List<Integer> ignored) {
        for (int i = 0; i < getLinksCount(); i++) {
            if (seNotFadedPoints.get(i).isDisplayed()) {
                int pointsOfI = Integer.parseInt(StringUtils.substringBetween(seNotFadedPoints.get(i).getText(), "(", "P)"));
                if (pointsOfI <= points && !ignored.contains(i)) {
                    return i;
                }
            }
        }
        return null;
    }

    public int getLinksCount() {
        return seNotFadedPoints.size();
    }

    public int getMinimumPointsCost(List<Integer> ignored) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < getLinksCount(); i++) {
            if (ignored.contains(i)) continue;
            if (seNotFadedPoints.get(i).isDisplayed()) {
                int cost = Integer.parseInt(StringUtils.substringBetween(seNotFadedPoints.get(i).getText(), "(", "P)"));
                if (cost < min) min = cost;
            }
        }
        return min;
    }

    public List<Integer> getPinnedGameIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < seNotFadedLink.size(); i++) {
            if (seNotFadedLink.get(i).$x("ancestor::*[contains(@class,'pinned-giveaways')]").exists()) {
                indices.add(i);
            }
        }
        return indices;
    }

    public boolean clickNextIfPresent() {
        if (seNextPage.isDisplayed()) {
            seNextPage.click();
            return true;
        }
        return false;
    }
}
