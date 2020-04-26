package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$;

public class BaseForm {

    private SelenideElement sePoints = $(".nav__points");

    public int getPoints() {
        return Integer.parseInt(sePoints.getText());
    }
}
