package steamgifts.pages;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import lombok.extern.slf4j.Slf4j;

import static com.codeborne.selenide.Selenide.$;

@Slf4j
public class GamePage extends BaseForm {

    private SelenideElement seWon = $(".sidebar__error");
    private SelenideElement seEnter = $(".sidebar__entry-insert");
    private SelenideElement seRemoveEntry = $(".sidebar__entry-delete");
    private SelenideElement seGameName = $(".featured__heading__medium");

    public GamePage enterGiveway() {
        log.info("Entering giveway for " + seGameName.getText());
        seEnter.click();
        seRemoveEntry.waitUntil(Condition.appear, 20000); //todo: take from params
        return this;
    }

    public boolean isWon() {
        return seWon.isDisplayed();
    }

    public String getName() {
        return seGameName.text();
    }
}
