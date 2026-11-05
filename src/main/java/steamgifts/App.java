package steamgifts;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

@Slf4j
public class App {

    public static void main(String[] args) {
        configureLogging();
        Config config = Config.load();

        BrowserSession session = new BrowserSession(config);
        session.start();
        new Collector(config, session).run();
        session.close();

        if (config.isCi()) {
            log.info("Goodbye");
        } else {
            Console.waitForInput("Finishing. Press Enter.");
        }
    }

    /** Applies logging.properties (one-line format, UTF-8) to java.util.logging, the slf4j backend. */
    private static void configureLogging() {
        try (InputStream in = App.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Can't load logging.properties, using default logging: " + e);
        }
    }
}
