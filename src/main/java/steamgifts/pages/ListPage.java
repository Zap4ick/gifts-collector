package steamgifts.pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

@Slf4j
public class ListPage extends BaseForm {

    private static final String NOT_FADED_ROW =
            "//div[contains(@class,'giveaway__row-inner-wrap') and not(contains(@class,'is-faded'))]";

    private final ElementsCollection seNotFadedRows = $$x(NOT_FADED_ROW);

    private final SelenideElement seNextPage = $x("//a[./span[text()='Next']]");

    public boolean hasNotFadedGames() {
        return !seNotFadedRows.isEmpty();
    }

    /**
     * Reads all not-faded giveaway rows in a single pass. Link, cost and pinned flag are taken
     * from the same row, so they can't get out of sync.
     */
    public List<Giveaway> getGiveaways() {
        List<Giveaway> giveaways = new ArrayList<>();
        for (SelenideElement row : seNotFadedRows) {
            SelenideElement link = row.$x(".//a[contains(@class,'giveaway_image_thumbnail')]");
            SelenideElement costLabel = row.$x(".//h2/span[@class='giveaway__heading__thin'][last()]");
            if (!link.exists() || !costLabel.isDisplayed()) {
                continue;
            }
            SelenideElement nameLink = row.$x(".//a[contains(@class,'giveaway__heading__name')]");
            String name = nameLink.exists() ? nameLink.text() : link.getAttribute("href");
            String costText = costLabel.getText();
            OptionalInt cost = parseCost(costText);
            if (cost.isEmpty()) {
                log.warn("Can't parse cost '{}' of giveaway '{}', skipping it", costText, name);
                continue;
            }
            boolean pinned = row.$x("ancestor::*[contains(@class,'pinned-giveaways')]").exists();
            giveaways.add(new Giveaway(link.getAttribute("href"), name, cost.getAsInt(), pinned));
        }
        return giveaways;
    }

    /** Parses a cost label like "(10P)". */
    static OptionalInt parseCost(String label) {
        String digits = StringUtils.substringBetween(label, "(", "P)");
        if (digits == null || !StringUtils.isNumeric(digits)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(digits));
    }

    public boolean clickNextIfPresent() {
        if (seNextPage.isDisplayed()) {
            seNextPage.click();
            return true;
        }
        return false;
    }
}
