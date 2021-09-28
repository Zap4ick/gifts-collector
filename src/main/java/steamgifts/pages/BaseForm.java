package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;

import java.util.Objects;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$x;

public class BaseForm {

    private final SelenideElement sePoints = $(".nav__points");
    private final SelenideElement seLogin = $(".nav__sits");
    private final SelenideElement seBanner = $x("//div[contains(@class,'banner_banner')]");

    private final SelenideElement btnCloseBanner = $x("//button[contains(@class,'banner_save')]");

    public int getPoints() {
        return Integer.parseInt(sePoints.getText());
    }

    public boolean isLoggedIn() {
        return !seLogin.isDisplayed();
    }

    public void closeBannerIfPresent() {
        if (isBannerPresent()) {
            btnCloseBanner.click();
        }
    }

    public boolean isBannerPresent() {
        return seBanner.isDisplayed() && Objects.requireNonNull(seBanner.getAttribute("style")).contains("bottom: 0px;");
    }
}
