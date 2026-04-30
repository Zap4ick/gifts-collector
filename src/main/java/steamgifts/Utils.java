package steamgifts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

    public static void pause(int secs) {
        try {
            TimeUnit.SECONDS.sleep(secs);
        } catch (InterruptedException e) {
            log.warn("Exception in thread sleeping: ", e);
        }
    }

    /**
     * Builds ChromeOptions configured to look as human-like as possible.
     */
    public static ChromeOptions buildStealthOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--headless=new");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.56 Safari/537.36");
        options.addArguments("--lang=en-US,en;q=0.9");
        options.addArguments("--window-size=1366,768");
        options.addArguments("--disable-infobars");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("intl.accept_languages", "en-US,en");
        options.setExperimentalOption("prefs", prefs);

        return options;
    }

    /**
     * Injects a stealth JS script via CDP that runs before any page script,
     * overriding properties commonly used by bot-detection fingerprinting.
     */
    public static void injectStealthScript(ChromiumDriver driver) {
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", """
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                window.chrome = { runtime: {} };
                Object.defineProperty(navigator, 'plugins',            { get: () => [1, 2, 3, 4, 5] });
                Object.defineProperty(navigator, 'languages',          { get: () => ['en-US', 'en'] });
                Object.defineProperty(navigator, 'platform',           { get: () => 'Win32' });
                Object.defineProperty(navigator, 'hardwareConcurrency',{ get: () => 8 });
                Object.defineProperty(navigator, 'deviceMemory',       { get: () => 8 });
                Object.defineProperty(screen, 'width',      { get: () => 1366 });
                Object.defineProperty(screen, 'height',     { get: () => 768 });
                Object.defineProperty(screen, 'colorDepth', { get: () => 24 });
                Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });
                const getParameter = WebGLRenderingContext.prototype.getParameter;
                WebGLRenderingContext.prototype.getParameter = function(parameter) {
                    if (parameter === 37445) return 'Intel Inc.';
                    if (parameter === 37446) return 'Intel Iris OpenGL Engine';
                    return getParameter.call(this, parameter);
                };
                """));
    }
}
