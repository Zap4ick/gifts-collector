package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.SelenideElement;
import steamgifts.Utils;

import java.time.Duration;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.$;

public class CaptchaPage extends BaseForm {

    private static final Duration CAPTCHA_TIMEOUT = Duration.ofSeconds(20);

    private final SelenideElement seCaptcha = $("#mIyT8");

    public boolean isOpen() {
        Utils.pause(5);
        return seCaptcha.is(exist);
    }

    public void passCaptcha() {
        seCaptcha.click(ClickOptions.withOffset(-300, 0).force());
        Utils.pause(10);
        seCaptcha.shouldNot(exist, CAPTCHA_TIMEOUT);
    }
}
