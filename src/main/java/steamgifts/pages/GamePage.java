package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$x;

@Slf4j
public class GamePage extends BaseForm {

    private final SelenideElement seError = $(".sidebar__error");
    private final SelenideElement seWon = $x("//div[contains(@class,'sidebar__error')][contains(text(),'Won')]");
    private final SelenideElement seNotEnoughPoint = $(".fa-exclamation-circle");
    private final SelenideElement seEnter = $(".sidebar__entry-insert");
    private final SelenideElement seRemoveEntry = $(".sidebar__entry-delete");
    private final SelenideElement seGameName = $(".featured__heading__medium");

    public void enterGiveaway() {
        log.info("Entering giveaway for {}", seGameName.getText());
        seEnter.click(ClickOptions.usingJavaScript());
        seRemoveEntry.should(Condition.appear, Duration.of(20, ChronoUnit.SECONDS)); //todo: take from params
    }

    public boolean isError() {
        return seError.isDisplayed();
    }

    public boolean isWon() {
       return seWon.isDisplayed();
    }

    public boolean isNotEnoughPoint() {
        return seNotEnoughPoint.isDisplayed();
    }

    public boolean isEntered() {
        seEnter.should(Condition.appear, Duration.of(5, ChronoUnit.SECONDS));
        return !seEnter.isDisplayed();
    }

    public String getName() {
        return seGameName.text();
    }
}
