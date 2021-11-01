package steamgifts.pages;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static com.codeborne.selenide.Selenide.$;

@Slf4j
public class GamePage extends BaseForm {

    private final SelenideElement seWon = $(".sidebar__error");
    private final SelenideElement seEnter = $(".sidebar__entry-insert");
    private final SelenideElement seRemoveEntry = $(".sidebar__entry-delete");
    private final SelenideElement seGameName = $(".featured__heading__medium");

    public GamePage enterGiveway() {
        log.info("Entering giveway for " + seGameName.getText());
        seEnter.click();
        seRemoveEntry.should(Condition.appear, Duration.of(20, ChronoUnit.SECONDS)); //todo: take from params
        return this;
    }

    public boolean isWon() {
        return seWon.isDisplayed();
    }

    public String getName() {
        return seGameName.text();
    }
}
