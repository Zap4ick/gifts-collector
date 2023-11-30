package steamgifts.pages;

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.SelenideElement;

import java.util.Objects;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$x;

public class BaseForm {

    private final SelenideElement sePoints = $(".nav__points");
    private final SelenideElement seLogin = $(".nav__sits");
    private final SelenideElement seBanner = $x("//div[contains(@class,'banner_banner')]");
    private final SelenideElement seModalWin = $x("//div[contains(@class,'popup--gift-received')]");

    private final SelenideElement btnCloseBanner = $x("//button[contains(@class,'banner_save')]");
    private final SelenideElement btnCloseModalWin = $x("//div[contains(@class,'popup--gift-received')]//span[@class='b-close']");

    public int getPoints() {
        return Integer.parseInt(sePoints.getText());
    }

    public boolean isLoggedIn() {
        return !seLogin.isDisplayed();
    }

    public boolean isModalWinPresent() {
        return seModalWin.isDisplayed();
    }

    public void closeBannerIfPresent() {
        if (isBannerPresent()) {
            btnCloseBanner.click();
        }
    }

    public void closeModalWinIfPresent() {
        if (isModalWinPresent()) {
            btnCloseModalWin.click(ClickOptions.usingJavaScript());
        }
    }

    public boolean isBannerPresent() {
        return seBanner.isDisplayed() && Objects.requireNonNull(seBanner.getAttribute("style")).contains("bottom: 0px;");
    }
}
