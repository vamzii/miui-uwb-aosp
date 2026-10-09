import android.os.IBinder;
import java.lang.reflect.Method;

public class TestAospUwb {
    public static void main(String[] args) {
        try {
            System.out.println("Testing AOSP UWB Interface...");
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "uwb");
            System.out.println("Service 'uwb' binder: " + binder);
            
            Class<?> stubClass = Class.forName("android.uwb.IUwbAdapter$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object aospAdapter = asInterface.invoke(null, binder);
            System.out.println("AOSP IUwbAdapter proxy: " + aospAdapter);
            
            Method getAdapterState = aospAdapter.getClass().getMethod("getAdapterState");
            int state = (Integer) getAdapterState.invoke(aospAdapter);
            System.out.println("getAdapterState(): " + state + " (2 = STATE_ENABLED_INACTIVE)");
            
            Method getChipId = aospAdapter.getClass().getMethod("getDefaultChipId");
            String chipId = (String) getChipId.invoke(aospAdapter);
            System.out.println("getDefaultChipId(): " + chipId);
            
            Method getSpec = aospAdapter.getClass().getMethod("getSpecificationInfo", String.class);
            Object spec = getSpec.invoke(aospAdapter, "SR100T");
            System.out.println("getSpecificationInfo('SR100T'): " + spec);
            System.out.println("SUCCESS! AOSP UWB EMULATION IS FULLY WORKING ON MIUI!");
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
