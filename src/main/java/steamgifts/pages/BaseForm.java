package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$;

public class BaseForm {

    private SelenideElement sePoints = $(".nav__points");
    private SelenideElement seLogin = $(".nav__sits");

    public int getPoints() {
        return Integer.parseInt(sePoints.getText());
    }

    public boolean isLoggedIn(){
        return !seLogin.isDisplayed();
    }
}
