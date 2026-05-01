package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import org.openqa.selenium.interactions.Actions;
import steamgifts.Utils;

import java.time.Duration;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.$x;

public class CaptchaPage extends BaseForm {

    private static final Duration CAPTCHA_TIMEOUT = Duration.ofSeconds(20);

    private final SelenideElement seCaptcha = $x("//div[@id][@style='display: grid;']");

    public boolean isOpen() {
        Utils.pause(10);
        return seCaptcha.is(exist);
    }

    public void passCaptcha() {
        // Use native mouse hover + click to appear human to the captcha widget
        new Actions(WebDriverRunner.getWebDriver())
                .moveToElement(seCaptcha.getWrappedElement(), -300, 0)
                .pause(Duration.ofMillis(500 + (long) (Math.random() * 500)))
                .click()
                .perform();
        Utils.pause(10);
        seCaptcha.shouldNot(exist, CAPTCHA_TIMEOUT);
        Utils.pause(5);
    }
}
