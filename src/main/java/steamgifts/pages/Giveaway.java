package steamgifts.pages;

/**
 * One not-faded giveaway row on a list page.
 *
 * @param url    absolute giveaway URL, used as a stable identity (indexes shift when rows fade)
 * @param name   game name, for logging
 * @param cost   entry cost in points
 * @param pinned whether the row is in the pinned block at the top of the page
 */
public record Giveaway(String url, String name, int cost, boolean pinned) {
}
