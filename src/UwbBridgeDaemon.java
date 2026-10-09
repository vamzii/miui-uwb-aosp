import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;

public class UwbBridgeDaemon {
    public static final String DESCRIPTOR_AOSP = "android.uwb.IUwbAdapter";
    public static final String DESCRIPTOR_NXP = "com.nxp.uwb.IUwbAdapter";
    public static final String DESCRIPTOR_CB = "android.uwb.IUwbAdapterStateCallbacks";

    // AOSP IUwbAdapter transaction codes
    public static final int TRANSACTION_registerAdapterStateCallbacks = 1;
    public static final int TRANSACTION_registerVendorExtensionCallback = 2;
    public static final int TRANSACTION_unregisterVendorExtensionCallback = 3;
    public static final int TRANSACTION_unregisterAdapterStateCallbacks = 4;
    public static final int TRANSACTION_registerOemExtensionCallback = 5;
    public static final int TRANSACTION_unregisterOemExtensionCallback = 6;
    public static final int TRANSACTION_getTimestampResolutionNanos = 7;
    public static final int TRANSACTION_getSpecificationInfo = 8;
    public static final int TRANSACTION_openRanging = 9;
    public static final int TRANSACTION_startRanging = 10;
    public static final int TRANSACTION_reconfigureRanging = 11;
    public static final int TRANSACTION_stopRanging = 12;
    public static final int TRANSACTION_closeRanging = 13;
    public static final int TRANSACTION_addControlee = 14;
    public static final int TRANSACTION_removeControlee = 15;
    public static final int TRANSACTION_pause = 16;
    public static final int TRANSACTION_resume = 17;
    public static final int TRANSACTION_sendData = 18;
    public static final int TRANSACTION_setEnabled = 19;
    public static final int TRANSACTION_getAdapterState = 20;
    public static final int TRANSACTION_getChipInfos = 21;
    public static final int TRANSACTION_getChipIds = 22;
    public static final int TRANSACTION_getDefaultChipId = 23;
    public static final int TRANSACTION_addServiceProfile = 24;
    public static final int TRANSACTION_removeServiceProfile = 25;
    public static final int TRANSACTION_getAllServiceProfiles = 26;
    public static final int TRANSACTION_getAdfProvisioningAuthorities = 27;
    public static final int TRANSACTION_getAdfCertificateAndInfo = 28;
    public static final int TRANSACTION_provisionProfileAdfByScript = 29;
    public static final int TRANSACTION_removeProfileAdf = 30;
    public static final int TRANSACTION_updatePose = 31;
    public static final int TRANSACTION_sendVendorUciMessage = 32;
    public static final int TRANSACTION_updateRangingRoundsDtTag = 33;
    public static final int TRANSACTION_getUwbActivityEnergyInfoAsync = 34;
    public static final int TRANSACTION_queryMaxDataSizeBytes = 35;

    // AOSP Adapter States
    public static final int STATE_DISABLED = 1;
    public static final int STATE_ENABLED_INACTIVE = 2;
    public static final int STATE_ENABLED_ACTIVE = 3;

    private static IBinder sNxpBinder;
    private static Object sNxpAdapter;

    private static PersistableBundle createChipBundle() {
        PersistableBundle b = new PersistableBundle();
        b.putString("chip_id", "SR100T");
        b.putString("chip_name", "NXP SR100T");
        b.putBoolean("fira_support", true);
        b.putBoolean("has_fira_support", true);
        b.putBoolean("has_ccc_support", true);
        b.putBoolean("aoa_support", true);
        b.putIntArray("supported_channels", new int[]{5, 9});
        b.putIntArray("channels", new int[]{5, 9});
        return b;
    }

    private static PersistableBundle createSpecBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean("fira_support", true);
        bundle.putBoolean("has_fira_support", true);
        bundle.putBoolean("has_ccc_support", true);
        bundle.putBoolean("aoa_support", true);
        bundle.putBoolean("has_aoa_support", true);
        bundle.putIntArray("channels", new int[]{5, 9});
        bundle.putIntArray("supported_channels", new int[]{5, 9});
        bundle.putIntArray("supported_fira_versions", new int[]{1, 2});
        bundle.putIntArray("fira_versions", new int[]{1, 2});
        bundle.putInt("fira_version", 1);
        return bundle;
    }

    public static void main(String[] args) {
        System.out.println("[UwbBridge] Initializing AOSP UWB Bridge Daemon v1.1 for MIX 4...");
        try {
            Looper.prepare();

            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            Method addService = smClass.getMethod("addService", String.class, IBinder.class);

            // 1. Connect to NXP UWB Service
            sNxpBinder = (IBinder) getService.invoke(null, "uwb");
            if (sNxpBinder == null) {
                System.err.println("[UwbBridge] Error: NXP 'uwb' service not found!");
                return;
            }
            System.out.println("[UwbBridge] Connected to NXP UWB Binder: " + sNxpBinder);

            Class<?> stubClass = Class.forName("com.nxp.uwb.IUwbAdapter$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            sNxpAdapter = asInterface.invoke(null, sNxpBinder);
            System.out.println("[UwbBridge] NXP IUwbAdapter instance: " + sNxpAdapter);

            // Enable chip
            try {
                Method enableMethod = sNxpAdapter.getClass().getMethod("enable", IBinder.class);
                Object res = enableMethod.invoke(sNxpAdapter, new Binder());
                System.out.println("[UwbBridge] NXP Chip enable(): " + res);
            } catch (Throwable t) {
                System.err.println("[UwbBridge] Warning enabling chip: " + t);
            }

            // 2. Create AOSP Bridge Binder with attached interface
            Binder aospBridge = new Binder() {
                {
                    attachInterface(null, DESCRIPTOR_AOSP);
                }

                @Override
                public String getInterfaceDescriptor() {
                    return DESCRIPTOR_AOSP;
                }

                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    try {
                        if (code == IBinder.INTERFACE_TRANSACTION) {
                            if (reply != null) {
                                reply.writeString(DESCRIPTOR_AOSP);
                            }
                            return true;
                        }

                        switch (code) {
                            case TRANSACTION_getAdapterState: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    int state = isNxpEnabled() ? STATE_ENABLED_INACTIVE : STATE_DISABLED;
                                    reply.writeInt(state);
                                    System.out.println("[UwbBridge] getAdapterState() -> " + state);
                                }
                                return true;
                            }

                            case TRANSACTION_setEnabled: {
                                int enabled = 1;
                                try {
                                    data.setDataPosition(0);
                                    data.enforceInterface(DESCRIPTOR_AOSP);
                                    enabled = data.readInt();
                                } catch (Exception e) {}
                                System.out.println("[UwbBridge] setEnabled(" + (enabled != 0) + ")");
                                if (reply != null) reply.writeNoException();
                                try {
                                    if (enabled != 0) {
                                        sNxpAdapter.getClass().getMethod("enable", IBinder.class).invoke(sNxpAdapter, new Binder());
                                    } else {
                                        sNxpAdapter.getClass().getMethod("disable", IBinder.class).invoke(sNxpAdapter, new Binder());
                                    }
                                } catch (Exception e) {}
                                return true;
                            }

                            case TRANSACTION_getDefaultChipId: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeString("SR100T");
                                    System.out.println("[UwbBridge] getDefaultChipId() -> SR100T");
                                }
                                return true;
                            }

                            case TRANSACTION_getChipIds: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeStringList(Collections.singletonList("SR100T"));
                                    System.out.println("[UwbBridge] getChipIds() -> [SR100T]");
                                }
                                return true;
                            }

                            case TRANSACTION_getChipInfos: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeTypedList(Collections.singletonList(createChipBundle()));
                                    System.out.println("[UwbBridge] getChipInfos() -> [SR100T bundle]");
                                }
                                return true;
                            }

                            case TRANSACTION_getSpecificationInfo: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    PersistableBundle bundle = createSpecBundle();
                                    reply.writeInt(1); // non-null Parcelable flag in AIDL
                                    bundle.writeToParcel(reply, 0);
                                    System.out.println("[UwbBridge] getSpecificationInfo() -> FiRa capabilities sent");
                                }
                                return true;
                            }

                            case TRANSACTION_getTimestampResolutionNanos: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeLong(1000L);
                                    System.out.println("[UwbBridge] getTimestampResolutionNanos() -> 1000");
                                }
                                return true;
                            }

                            case TRANSACTION_queryMaxDataSizeBytes: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeInt(4096);
                                    System.out.println("[UwbBridge] queryMaxDataSizeBytes() -> 4096");
                                }
                                return true;
                            }

                            case TRANSACTION_registerAdapterStateCallbacks: {
                                try {
                                    data.setDataPosition(0);
                                    data.enforceInterface(DESCRIPTOR_AOSP);
                                    IBinder cb = data.readStrongBinder();
                                    if (cb != null) {
                                        // Immediately broadcast current state to callback
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB);
                                        cbData.writeInt(STATE_ENABLED_INACTIVE);
                                        cbData.writeInt(1); // STATE_CHANGED_REASON_ALL_SESSIONS_CLOSED
                                        cb.transact(1, cbData, null, IBinder.FLAG_ONEWAY);
                                        cbData.recycle();
                                    }
                                } catch (Throwable t) {}
                                if (reply != null) reply.writeNoException();
                                System.out.println("[UwbBridge] registerAdapterStateCallbacks()");
                                return true;
                            }

                            case TRANSACTION_unregisterAdapterStateCallbacks: {
                                if (reply != null) reply.writeNoException();
                                System.out.println("[UwbBridge] unregisterAdapterStateCallbacks()");
                                return true;
                            }

                            case TRANSACTION_openRanging: {
                                System.out.println("[UwbBridge] openRanging() invoked!");
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_startRanging: {
                                System.out.println("[UwbBridge] startRanging() invoked!");
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_stopRanging: {
                                System.out.println("[UwbBridge] stopRanging() invoked!");
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_closeRanging: {
                                System.out.println("[UwbBridge] closeRanging() invoked!");
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_addServiceProfile:
                            case TRANSACTION_getAllServiceProfiles:
                            case TRANSACTION_getAdfProvisioningAuthorities:
                            case TRANSACTION_getAdfCertificateAndInfo: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeInt(1);
                                    new PersistableBundle().writeToParcel(reply, 0);
                                }
                                return true;
                            }

                            case TRANSACTION_removeServiceProfile:
                            case TRANSACTION_removeProfileAdf:
                            case TRANSACTION_sendVendorUciMessage: {
                                if (reply != null) {
                                    reply.writeNoException();
                                    reply.writeInt(0);
                                }
                                return true;
                            }

                            default: {
                                System.out.println("[UwbBridge] Unhandled AOSP transaction code: " + code);
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                        }
                    } catch (Throwable t) {
                        System.err.println("[UwbBridge] onTransact error: " + t);
                    }
                    return false;
                }

                private boolean isNxpEnabled() {
                    try {
                        Method m = sNxpAdapter.getClass().getMethod("isUwbServiceEnabled");
                        return (Boolean) m.invoke(sNxpAdapter);
                    } catch (Exception e) {
                        return true;
                    }
                }
            };

            // 3. Register AOSP Bridge under 'uwb' in ServiceManager
            addService.invoke(null, "uwb", aospBridge);
            System.out.println("[UwbBridge] Successfully registered 'uwb' AOSP bridge service! Entering Looper.loop()...");
            Looper.loop();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
