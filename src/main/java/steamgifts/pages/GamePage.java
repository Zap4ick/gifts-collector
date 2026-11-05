package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.UIAssertionError;

import java.time.Duration;

import static com.codeborne.selenide.Selenide.$;

public class GamePage extends BaseForm {

    /** The "Enter Giveaway" button may render with a delay. */
    private static final Duration ENTER_BUTTON_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ENTRY_CONFIRM_TIMEOUT = Duration.ofSeconds(20);

    private final SelenideElement seError = $(".sidebar__error");
    private final SelenideElement seEnter = $(".sidebar__entry-insert");
    private final SelenideElement seRemoveEntry = $(".sidebar__entry-delete");

    public void enterGiveaway() {
        seEnter.click(ClickOptions.usingJavaScript());
        seRemoveEntry.should(Condition.appear, ENTRY_CONFIRM_TIMEOUT);
    }

    /** Sidebar shows why the giveaway can't be entered (not enough points, won, exists in account, ...). */
    public boolean isError() {
        return seError.isDisplayed();
    }

    public String getErrorText() {
        return seError.text().strip();
    }

    /** Waits briefly for the "Enter Giveaway" button. */
    public boolean canEnter() {
        try {
            seEnter.should(Condition.appear, ENTER_BUTTON_TIMEOUT);
            return true;
        } catch (UIAssertionError e) {
            return false;
        }
    }

    /** "Remove Entry" button is shown, i.e. we've already entered this giveaway. */
    public boolean isEntered() {
        return seRemoveEntry.isDisplayed();
    }
}
