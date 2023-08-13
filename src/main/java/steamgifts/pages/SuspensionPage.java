package steamgifts.pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class SuspensionPage extends BaseForm {

    private final SelenideElement seSuspensionHeader = $x("//div[@class='page__heading'][.//a[text()='Suspensions']]");

    public boolean isOpen() {
        return seSuspensionHeader.isDisplayed();
    }
}
