package steamgifts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/** Interaction with the person at the console (local modes only). */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Console {

    /** Prints the message and blocks until Enter is pressed. */
    public static void waitForInput(String message) {
        System.out.println(message);
        System.out.print("Press Enter when ready... ");
        try {
            if (System.in.read() == -1) {
                // No interactive console (e.g. CI): waiting would spin forever
                throw new IllegalStateException("stdin is closed, cannot wait for user input. Message was: " + message.strip());
            }
            // drain rest of line
            while (System.in.available() > 0) {
                //noinspection ResultOfMethodCallIgnored
                System.in.read();
            }
        } catch (IOException e) {
            log.warn("Error reading input", e);
        }
    }
}
