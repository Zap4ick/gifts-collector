package steamgifts;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

    public static void pause(int secs) {
        try {
            Thread.sleep(secs * 1000);
        } catch (InterruptedException e) {
            log.warn("Exception in thread sleeping: ", e);
        }
    }
}
