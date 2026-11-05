package steamgifts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Deliberate delays between actions (bot protection). Don't shorten them casually. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Pause {

    /**
     * Sleeps for the given time. Interruption means "stop", so it aborts the run instead of
     * continuing: with the interrupt flag set every later pause would return instantly and the
     * bot would hammer the site without its deliberate delays.
     */
    public static void seconds(int secs) {
        try {
            TimeUnit.SECONDS.sleep(secs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pausing, stopping the run", e);
        }
    }

    /** Random whole number of seconds in [minSecs, maxSecs], to avoid a uniform timing fingerprint. */
    public static void randomSeconds(int minSecs, int maxSecs) {
        seconds(ThreadLocalRandom.current().nextInt(minSecs, maxSecs + 1));
    }
}
