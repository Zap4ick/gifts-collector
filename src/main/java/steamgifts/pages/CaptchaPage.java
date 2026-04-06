package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.SelenideElement;

import java.time.Duration;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.$;

public class CaptchaPage extends BaseForm {

    private static final Duration CAPTCHA_TIMEOUT = Duration.ofSeconds(20);

    private final SelenideElement seCaptcha = $("#mIyT8");

    public boolean isOpen() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return seCaptcha.is(exist);
    }

    public void passCaptcha() {
        seCaptcha.click(ClickOptions.withOffset(-300, 0).force());
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        seCaptcha.shouldNot(exist, CAPTCHA_TIMEOUT);
    }
}
