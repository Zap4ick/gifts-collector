package steamgifts;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App {

    public static void main(String[] args) {
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
}
