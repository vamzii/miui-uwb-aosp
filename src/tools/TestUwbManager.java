import android.content.Context;
import android.os.Looper;
import java.lang.reflect.Method;

public class TestUwbManager {
    public static void main(String[] args) {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method systemMain = activityThread.getMethod("systemMain");
            Object at = systemMain.invoke(null);
            
            Method getSystemContext = activityThread.getMethod("getSystemContext");
            Context ctx = (Context) getSystemContext.invoke(at);
            
            Object uwbManager = ctx.getSystemService("uwb");
            System.out.println("UwbManager service from Context: " + uwbManager);
            if (uwbManager != null) {
                Method isUwbEnabled = uwbManager.getClass().getMethod("isUwbEnabled");
                System.out.println("isUwbEnabled(): " + isUwbEnabled.invoke(uwbManager));

                try {
                    Method getChipInfos = uwbManager.getClass().getMethod("getChipInfos");
                    System.out.println("getChipInfos(): " + getChipInfos.invoke(uwbManager));
                } catch (Exception e) {
                    System.err.println("getChipInfos error: " + e.getCause());
                }

                try {
                    Method getSpecificationInfo = uwbManager.getClass().getMethod("getSpecificationInfo");
                    System.out.println("getSpecificationInfo(): " + getSpecificationInfo.invoke(uwbManager));
                } catch (Exception e) {
                    System.err.println("getSpecificationInfo error: " + e.getCause());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
