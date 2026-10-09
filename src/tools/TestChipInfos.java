import android.os.IBinder;
import java.lang.reflect.Method;

public class TestChipInfos {
    public static void main(String[] args) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "uwb");
            Class<?> stubClass = Class.forName("android.uwb.IUwbAdapter$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object aospAdapter = asInterface.invoke(null, binder);
            Method getChipInfos = aospAdapter.getClass().getMethod("getChipInfos");
            Object res = getChipInfos.invoke(aospAdapter);
            System.out.println("getChipInfos(): " + res);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
