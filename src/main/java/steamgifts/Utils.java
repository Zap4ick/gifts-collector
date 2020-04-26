package steamgifts;

public class Utils {

    public static void pause(int secs){
        try {
            Thread.sleep(secs*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
