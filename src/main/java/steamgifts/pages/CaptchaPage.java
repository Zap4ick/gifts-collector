package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.interactions.Actions;
import steamgifts.Utils;

import java.time.Duration;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.refresh;

@Slf4j
public class CaptchaPage extends BaseForm {

    private static final Duration CAPTCHA_TIMEOUT = Duration.ofSeconds(20);
    private static final int[] ATTEMPT_WAITS_SECS = {5, 10, 15, 20};

    private final SelenideElement seCaptcha = $x("//div[@id][@style='display: grid;']");

    public boolean isOpen() {
        Utils.pause(10);
        return seCaptcha.is(exist);
    }

    public void passCaptcha() {
        for (int i = 0; i < ATTEMPT_WAITS_SECS.length; i++) {
            log.info("Captcha attempt {}/{}, waiting {}s...", i + 1, ATTEMPT_WAITS_SECS.length, ATTEMPT_WAITS_SECS[i]);
            Utils.pause(ATTEMPT_WAITS_SECS[i]);
            if (!seCaptcha.is(exist)) { log.info("Captcha resolved on attempt {}", i + 1); return; }
            new Actions(WebDriverRunner.getWebDriver())
                    .moveToElement(seCaptcha.getWrappedElement(), -300, 0)
                    .pause(Duration.ofMillis(500 + (long) (Math.random() * 500)))
                    .click()
                    .perform();
            Utils.pause(10);
            if (!seCaptcha.is(exist)) { log.info("Captcha passed on attempt {}", i + 1); Utils.pause(3); return; }
            if (i < ATTEMPT_WAITS_SECS.length - 1) { log.info("Retrying, refreshing page..."); refresh(); Utils.pause(5); }
        }
        seCaptcha.shouldNot(exist, CAPTCHA_TIMEOUT);
    }
}
