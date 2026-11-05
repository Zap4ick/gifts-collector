package steamgifts;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.ex.UIAssertionError;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriverException;
import steamgifts.pages.CaptchaPage;
import steamgifts.pages.GamePage;
import steamgifts.pages.Giveaway;
import steamgifts.pages.ListPage;
import steamgifts.pages.SuspensionPage;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Walks the giveaway categories and enters every affordable giveaway. */
@Slf4j
public class Collector {

    // Deliberate dwell after opening a page, before the captcha check (bot protection; also lets
    // the challenge render). Only applies until a captcha has been passed once. Don't shorten.
    private static final int CAPTCHA_CHECK_PAUSE_SECS = 10;
    // Extra dwell on a rejected giveaway page so we don't bounce off it instantly (bot protection).
    // Matches GamePage's enter-button wait, which plays the same role when the button is missing.
    private static final int REJECTED_GIVEAWAY_PAUSE_SECS = 5;
    // Cheapest possible giveaway; with fewer points there is nothing left to enter
    private static final int MIN_GIVEAWAY_COST = 1;
    private static final Random RANDOM = new Random();

    private final Config config;
    private final BrowserSession session;
    private boolean captchaPassed = false;

    public Collector(Config config, BrowserSession session) {
        this.config = config;
        this.session = session;
    }

    public void run() {
        int pointsLeft = Integer.MAX_VALUE; // unknown until the first page is read
        for (String page : config.categoryPages()) {
            if (pointsLeft < MIN_GIVEAWAY_COST) {
                log.info("Out of points ({}), skipping remaining pages.", pointsLeft);
                break;
            }
            pointsLeft = drillPage(page);
        }
    }

    /** Enters what it can on one giveaway category and returns the points left afterwards. */
    private int drillPage(String page) {
        Selenide.open(page);

        if (!captchaPassed) {
            Utils.pause(CAPTCHA_CHECK_PAUSE_SECS);
            CaptchaPage captchaPage = new CaptchaPage();
            if (captchaPage.isOpen()) {
                if (config.runMode().isLocal()) {
                    Console.waitForInput("\nCaptcha detected! Please solve the captcha in the browser window.");
                } else {
                    captchaPage.passCaptcha();
                }
                captchaPassed = true;
            }
        }

        if (new SuspensionPage().isOpen()) {
            throw new RuntimeException("Seems like we are suspened :( Aborting mission!");
        }

        session.ensureLoggedIn();

        ListPage listPage = new ListPage();
        listPage.dismissOverlays();
        int minimumCostSeen = Integer.MAX_VALUE;

        // Giveaways already opened in this category; keyed by URL because row indexes shift
        // when an entered giveaway fades out and differ between pagination pages.
        Set<String> visited = new HashSet<>();
        do {
            int points = listPage.getPoints();
            if (points < MIN_GIVEAWAY_COST) {
                log.info("No more points, skipping.");
                break;
            }

            List<Giveaway> candidates = listPage.getGiveaways().stream()
                    .filter(g -> !g.pinned() && !visited.contains(g.url()))
                    .toList();
            if (candidates.isEmpty()) {
                log.info("No more giveaways on this page, skipping.");
                break;
            }

            int pageMinCost = candidates.stream().mapToInt(Giveaway::cost).min().orElse(Integer.MAX_VALUE);
            minimumCostSeen = Math.min(minimumCostSeen, pageMinCost);
            if (points < minimumCostSeen) {
                log.info("Points ({}) below minimum giveaway cost ({}) — stopping early.", points, minimumCostSeen);
                break;
            }
            enterAffordableGiveaways(listPage, visited);
        } while (listPage.clickNextIfPresent());
        // Read points again: the value from the loop is stale after giveaways have been entered
        int pointsLeft = listPage.getPoints();
        log.info("Points: {}, Games left: {}", pointsLeft, listPage.hasNotFadedGames());
        return pointsLeft;
    }

    private void enterAffordableGiveaways(ListPage listPage, Set<String> visited) {
        String listUrl = WebDriverRunner.url();
        Optional<Giveaway> next = findAffordable(listPage, visited);
        while (next.isPresent()) {
            Giveaway giveaway = next.get();
            visited.add(giveaway.url()); // never open the same giveaway twice, whatever happens on its page
            try {
                processGiveaway(giveaway);
            } catch (UIAssertionError | WebDriverException e) {
                // One broken giveaway page shouldn't abort the whole run. If the browser itself is
                // gone, reloading the list below fails and stops the run anyway.
                log.warn("Failed to process giveaway {} ({}), skipping it: {}", giveaway.name(), giveaway.url(), firstLine(e));
                log.debug("Full error:", e);
            }

            // Reload the list instead of navigating back: no stale cached page, entered rows are faded
            Selenide.open(listUrl);
            listPage.dismissOverlays();
            Utils.pause(4 + RANDOM.nextInt(6)); // random 4–9s to avoid uniform timing fingerprint
            next = findAffordable(listPage, visited);
        }
    }

    private void processGiveaway(Giveaway giveaway) {
        Selenide.open(giveaway.url());

        GamePage gamePage = new GamePage();
        if (gamePage.isError()) {
            log.info("Can't participate ({}): {}", gamePage.getErrorText(), giveaway.name());
            Utils.pause(REJECTED_GIVEAWAY_PAUSE_SECS);
        } else if (!gamePage.canEnter()) {
            String reason = gamePage.isEntered() ? "already entered" : "enter button is not present";
            log.info("Can't participate ({}): {}", reason, giveaway.name());
        } else {
            log.info("Entering giveaway for {} ({}P)", giveaway.name(), giveaway.cost());
            gamePage.enterGiveaway();
        }
    }

    /** Selenide/WebDriver messages are long multi-line dumps; the first line is enough for the log. */
    private static String firstLine(Throwable e) {
        return String.valueOf(e.getMessage()).lines().findFirst().orElse("");
    }

    private static Optional<Giveaway> findAffordable(ListPage listPage, Set<String> visited) {
        int points = listPage.getPoints();
        return listPage.getGiveaways().stream()
                .filter(g -> !g.pinned() && !visited.contains(g.url()) && g.cost() <= points)
                .findFirst();
    }
}
