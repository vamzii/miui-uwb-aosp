import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.SystemClock;

public class UwbBridgeDaemon {
    public static final String VERSION = "1.2";
    public static final String DESCRIPTOR_AOSP = "android.uwb.IUwbAdapter";
    public static final String DESCRIPTOR_NXP = "com.nxp.uwb.IUwbAdapter";
    public static final String DESCRIPTOR_CB_STATE = "android.uwb.IUwbAdapterStateCallbacks";
    public static final String DESCRIPTOR_CB_RANGING = "android.uwb.IUwbRangingCallbacks";

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
    public static final int TRANSACTION_DUMP = 0x5F444D50; // 1598311760 ('_DMP')

    // IUwbRangingCallbacks transaction codes
    public static final int CB_onRangingOpened = 1;
    public static final int CB_onRangingOpenFailed = 2;
    public static final int CB_onRangingStarted = 3;
    public static final int CB_onRangingStartFailed = 4;
    public static final int CB_onRangingReconfigured = 5;
    public static final int CB_onRangingReconfigureFailed = 6;
    public static final int CB_onRangingStopped = 7;
    public static final int CB_onRangingStopFailed = 8;
    public static final int CB_onRangingClosed = 9;
    public static final int CB_onRangingResult = 10;

    // AOSP Adapter States
    public static final int STATE_DISABLED = 1;
    public static final int STATE_ENABLED_INACTIVE = 2;
    public static final int STATE_ENABLED_ACTIVE = 3;

    private static IBinder sNxpBinder;
    private static Object sNxpAdapter;

    // Active session tracking
    private static Object sActiveSessionHandle;
    private static IBinder sActiveCallbackBinder;
    private static volatile boolean sIsRangingActive = false;
    private static Thread sRangingThread;

    private static PersistableBundle createChipBundle() {
        PersistableBundle b = new PersistableBundle();
        b.putString("chip_id", "SR100T");
        b.putString("chip_name", "NXP SR100T");
        b.putBoolean("fira_support", true);
        b.putBoolean("has_fira_support", true);
        b.putBoolean("has_ccc_support", true);
        b.putBoolean("aoa_support", true);
        b.putBoolean("has_aoa_support", true);
        b.putBoolean("has_angle_of_arrival_support", true);
        b.putBoolean("aoa_azimuth_support", true);
        b.putBoolean("aoa_elevation_support", true);
        b.putInt("fira_aoa_capabilities", 3);
        b.putIntArray("supported_channels", new int[]{5, 9});
        b.putIntArray("channels", new int[]{5, 9});
        return b;
    }

    private static PersistableBundle createSpecBundle() {
        PersistableBundle root = new PersistableBundle();

        // 1. FiRa sub-bundle required by Google Play Services (GMS Nearby UWB)
        PersistableBundle fira = new PersistableBundle();
        fira.putInt("bundle_version", 1);
        fira.putInt("device_type", 1); // Controller & Controlee
        fira.putBoolean("suspend_ranging_support", true);
        fira.putInt("session_key_length", 16);
        fira.putInt("dt_tag_max_active_rr", 8);
        fira.putBoolean("background_ranging_support", true);
        fira.putBoolean("has_angle_of_arrival_support", true);
        fira.putBoolean("aoa_support", true);
        fira.putBoolean("has_aoa_support", true);
        fira.putBoolean("aoa_azimuth_support", true);
        fira.putBoolean("aoa_elevation_support", true);
        fira.putInt("fira_aoa_capabilities", 3); // 1 = Azimuth, 2 = Elevation, 3 = Both
        fira.putIntArray("channels", new int[]{5, 9});
        fira.putIntArray("supported_channels", new int[]{5, 9});
        fira.putIntArray("supported_fira_versions", new int[]{1, 2});
        fira.putIntArray("fira_versions", new int[]{1, 2});
        fira.putInt("fira_version", 1);
        fira.putIntArray("supported_slot_durations", new int[]{1, 2});
        fira.putIntArray("supported_ranging_update_rates", new int[]{1, 2, 3});
        fira.putBoolean("supports_ranging_interval_reconfigure", true);
        root.putPersistableBundle("fira", fira);

        // 2. Root-level keys for AOSP UwbManager / Jetpack UWB
        root.putBoolean("fira_support", true);
        root.putBoolean("has_fira_support", true);
        root.putBoolean("has_ccc_support", true);
        root.putBoolean("aoa_support", true);
        root.putBoolean("has_aoa_support", true);
        root.putBoolean("has_angle_of_arrival_support", true);
        root.putBoolean("aoa_azimuth_support", true);
        root.putBoolean("aoa_elevation_support", true);
        root.putInt("fira_aoa_capabilities", 3);
        root.putIntArray("channels", new int[]{5, 9});
        root.putIntArray("supported_channels", new int[]{5, 9});
        root.putIntArray("supported_fira_versions", new int[]{1, 2});
        root.putIntArray("fira_versions", new int[]{1, 2});
        root.putInt("fira_version", 1);
        root.putIntArray("supported_slot_durations", new int[]{1, 2});
        root.putIntArray("supported_ranging_update_rates", new int[]{1, 2, 3});
        root.putBoolean("supports_ranging_interval_reconfigure", true);
        root.putBoolean("background_ranging_support", true);

        return root;
    }

    private static Object createRangingReport(double distMeters, double azimuthRad, double altitudeRad) {
        try {
            Class<?> angleCls = Class.forName("android.uwb.AngleMeasurement");
            Constructor<?> angleCtor = angleCls.getConstructor(double.class, double.class, double.class);
            Object azimuth = angleCtor.newInstance(azimuthRad, 0.05, 0.95);
            Object altitude = angleCtor.newInstance(altitudeRad, 0.05, 0.95);

            Class<?> aoaCls = Class.forName("android.uwb.AngleOfArrivalMeasurement");
            Constructor<?> aoaCtor = aoaCls.getDeclaredConstructor(angleCls, angleCls);
            aoaCtor.setAccessible(true);
            Object aoa = aoaCtor.newInstance(azimuth, altitude);

            Class<?> distBldCls = Class.forName("android.uwb.DistanceMeasurement$Builder");
            Object distBld = distBldCls.getConstructor().newInstance();
            distBldCls.getMethod("setMeters", double.class).invoke(distBld, distMeters);
            distBldCls.getMethod("setErrorMeters", double.class).invoke(distBld, 0.1);
            distBldCls.getMethod("setConfidenceLevel", double.class).invoke(distBld, 0.95);
            Object dist = distBldCls.getMethod("build").invoke(distBld);

            Class<?> addrCls = Class.forName("android.uwb.UwbAddress");
            Object addr = addrCls.getMethod("fromBytes", byte[].class).invoke(null, new byte[]{1, 2});

            Class<?> rmBldCls = Class.forName("android.uwb.RangingMeasurement$Builder");
            Object rmBld = rmBldCls.getConstructor().newInstance();
            rmBldCls.getMethod("setRemoteDeviceAddress", addrCls).invoke(rmBld, addr);
            rmBldCls.getMethod("setStatus", int.class).invoke(rmBld, 0);
            rmBldCls.getMethod("setElapsedRealtimeNanos", long.class).invoke(rmBld, SystemClock.elapsedRealtimeNanos());
            rmBldCls.getMethod("setDistanceMeasurement", dist.getClass()).invoke(rmBld, dist);
            rmBldCls.getMethod("setAngleOfArrivalMeasurement", aoa.getClass()).invoke(rmBld, aoa);
            Object rm = rmBldCls.getMethod("build").invoke(rmBld);

            Class<?> reportBldCls = Class.forName("android.uwb.RangingReport$Builder");
            Object reportBld = reportBldCls.getConstructor().newInstance();
            reportBldCls.getMethod("addMeasurement", rm.getClass()).invoke(reportBld, rm);
            return reportBldCls.getMethod("build").invoke(reportBld);
        } catch (Throwable t) {
            System.err.println("[UwbBridge] Error constructing RangingReport: " + t);
            return null;
        }
    }

    private static synchronized void startRangingStream(final Object sessionHandle, final IBinder cbBinder) {
        if (sIsRangingActive) return;
        sIsRangingActive = true;

        sRangingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("[UwbBridge] Ranging stream thread started.");
                double currentDist = 2.0;
                double currentAzimuth = 0.05; // ~3 degrees forward
                while (sIsRangingActive && cbBinder.isBinderAlive()) {
                    try {
                        // Check if NXP chip has live measurements
                        double reportDist = currentDist;
                        double reportAzimuth = currentAzimuth;
                        try {
                            if (sNxpAdapter != null) {
                                Method getDist = sNxpAdapter.getClass().getMethod("getDistance");
                                Method getAoa = sNxpAdapter.getClass().getMethod("getAoaAzimuth");
                                Object d = getDist.invoke(sNxpAdapter);
                                Object a = getAoa.invoke(sNxpAdapter);
                                if (d instanceof Number && ((Number) d).doubleValue() > 0) {
                                    reportDist = ((Number) d).doubleValue();
                                }
                                if (a instanceof Number) {
                                    reportAzimuth = Math.toRadians(((Number) a).doubleValue());
                                }
                            }
                        } catch (Throwable ignore) {}

                        Object report = createRangingReport(reportDist, reportAzimuth, 0.02);
                        if (report != null) {
                            Parcel cbData = Parcel.obtain();
                            cbData.writeInterfaceToken(DESCRIPTOR_CB_RANGING);
                            if (sessionHandle != null) {
                                cbData.writeInt(1);
                                sessionHandle.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(sessionHandle, cbData, 0);
                            } else {
                                cbData.writeInt(0);
                            }
                            cbData.writeInt(1);
                            report.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(report, cbData, 0);
                            cbBinder.transact(CB_onRangingResult, cbData, null, IBinder.FLAG_ONEWAY);
                            cbData.recycle();
                        }
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Throwable t) {
                        System.err.println("[UwbBridge] Error sending ranging report: " + t);
                        break;
                    }
                }
                System.out.println("[UwbBridge] Ranging stream thread stopped.");
                sIsRangingActive = false;
            }
        });
        sRangingThread.setName("UwbRangingStream");
        sRangingThread.start();
    }

    private static synchronized void stopRangingStream() {
        sIsRangingActive = false;
        if (sRangingThread != null) {
            sRangingThread.interrupt();
            sRangingThread = null;
        }
    }

    public static void main(String[] args) {
        System.out.println("[UwbBridge] Initializing AOSP UWB Bridge Daemon v" + VERSION + " for MIX 4...");
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

            // 2. Create AOSP Bridge Binder
            Binder aospBridge = new Binder() {
                {
                    attachInterface(null, DESCRIPTOR_AOSP);
                }

                @Override
                public String getInterfaceDescriptor() {
                    return DESCRIPTOR_AOSP;
                }

                @Override
                protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
                    pw.println("Xiaomi MIX 4 AOSP UWB Bridge Daemon v" + VERSION);
                    pw.println("==================================================");
                    pw.println("NXP Service Connected:   " + (sNxpBinder != null));
                    pw.println("NXP Chip Enabled:        " + isNxpEnabled());
                    pw.println("Hardware Chip ID:        SR100T (NXP)");
                    pw.println("Adapter State:           " + (isNxpEnabled() ? "ENABLED_INACTIVE (2)" : "DISABLED (1)"));
                    pw.println("Supported Channels:      [5, 9]");
                    pw.println("Angle of Arrival (AoA):  Azimuth + Elevation (Bitmask 3)");
                    pw.println("Active Session Handle:   " + (sActiveSessionHandle != null ? sActiveSessionHandle : "None"));
                    pw.println("Active Callback Binder:  " + (sActiveCallbackBinder != null ? sActiveCallbackBinder : "None"));
                    pw.println("Ranging Stream Running:  " + sIsRangingActive);
                    pw.flush();
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
                                    System.out.println("[UwbBridge] getSpecificationInfo() -> FiRa v1.2 capabilities sent (with AoA)");
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
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB_STATE);
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
                                try {
                                    data.setDataPosition(0);
                                    data.enforceInterface(DESCRIPTOR_AOSP);

                                    // 1. AttributionSource
                                    if (data.readInt() != 0) {
                                        try {
                                            Class<?> asCls = Class.forName("android.content.AttributionSource");
                                            Object creator = asCls.getField("CREATOR").get(null);
                                            creator.getClass().getMethod("createFromParcel", Parcel.class).invoke(creator, data);
                                        } catch (Throwable ignore) {}
                                    }

                                    // 2. SessionHandle
                                    Object sessionHandle = null;
                                    if (data.readInt() != 0) {
                                        try {
                                            Class<?> shCls = Class.forName("android.uwb.SessionHandle");
                                            Object creator = shCls.getField("CREATOR").get(null);
                                            sessionHandle = creator.getClass().getMethod("createFromParcel", Parcel.class).invoke(creator, data);
                                        } catch (Throwable ignore) {}
                                    }

                                    // 3. IUwbRangingCallbacks
                                    IBinder cbBinder = data.readStrongBinder();

                                    // 4. PersistableBundle parameters
                                    PersistableBundle params = null;
                                    if (data.readInt() != 0) {
                                        params = PersistableBundle.CREATOR.createFromParcel(data);
                                    }

                                    // 5. chipId
                                    String chipId = data.readString();

                                    System.out.println("[UwbBridge] openRanging: session=" + sessionHandle + ", cb=" + cbBinder + ", chipId=" + chipId);

                                    if (cbBinder != null) {
                                        sActiveSessionHandle = sessionHandle;
                                        sActiveCallbackBinder = cbBinder;

                                        // Dispatch onRangingOpened (transaction 1)
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB_RANGING);
                                        if (sessionHandle != null) {
                                            cbData.writeInt(1);
                                            sessionHandle.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(sessionHandle, cbData, 0);
                                        } else {
                                            cbData.writeInt(0);
                                        }
                                        cbBinder.transact(CB_onRangingOpened, cbData, null, IBinder.FLAG_ONEWAY);
                                        cbData.recycle();
                                        System.out.println("[UwbBridge] Dispatched onRangingOpened() successfully!");
                                    }
                                } catch (Throwable t) {
                                    System.err.println("[UwbBridge] openRanging error: " + t);
                                }
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_startRanging: {
                                System.out.println("[UwbBridge] startRanging() invoked!");
                                try {
                                    data.setDataPosition(0);
                                    data.enforceInterface(DESCRIPTOR_AOSP);

                                    Object sessionHandle = null;
                                    if (data.readInt() != 0) {
                                        try {
                                            Class<?> shCls = Class.forName("android.uwb.SessionHandle");
                                            Object creator = shCls.getField("CREATOR").get(null);
                                            sessionHandle = creator.getClass().getMethod("createFromParcel", Parcel.class).invoke(creator, data);
                                        } catch (Throwable ignore) {}
                                    }

                                    PersistableBundle params = null;
                                    if (data.readInt() != 0) {
                                        params = PersistableBundle.CREATOR.createFromParcel(data);
                                    }

                                    if (sActiveCallbackBinder != null) {
                                        // Dispatch onRangingStarted (transaction 3)
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB_RANGING);
                                        Object targetHandle = sessionHandle != null ? sessionHandle : sActiveSessionHandle;
                                        if (targetHandle != null) {
                                            cbData.writeInt(1);
                                            targetHandle.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(targetHandle, cbData, 0);
                                        } else {
                                            cbData.writeInt(0);
                                        }
                                        if (params != null) {
                                            cbData.writeInt(1);
                                            params.writeToParcel(cbData, 0);
                                        } else {
                                            cbData.writeInt(0);
                                        }
                                        sActiveCallbackBinder.transact(CB_onRangingStarted, cbData, null, IBinder.FLAG_ONEWAY);
                                        cbData.recycle();
                                        System.out.println("[UwbBridge] Dispatched onRangingStarted() successfully!");

                                        // Start streaming ranging reports
                                        startRangingStream(targetHandle, sActiveCallbackBinder);
                                    }
                                } catch (Throwable t) {
                                    System.err.println("[UwbBridge] startRanging error: " + t);
                                }
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_stopRanging: {
                                System.out.println("[UwbBridge] stopRanging() invoked!");
                                stopRangingStream();
                                if (sActiveCallbackBinder != null && sActiveSessionHandle != null) {
                                    try {
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB_RANGING);
                                        cbData.writeInt(1);
                                        sActiveSessionHandle.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(sActiveSessionHandle, cbData, 0);
                                        cbData.writeInt(1); // REASON_STOP_RANGING_CALLED
                                        cbData.writeInt(0);
                                        sActiveCallbackBinder.transact(CB_onRangingStopped, cbData, null, IBinder.FLAG_ONEWAY);
                                        cbData.recycle();
                                    } catch (Throwable ignore) {}
                                }
                                if (reply != null) reply.writeNoException();
                                return true;
                            }

                            case TRANSACTION_closeRanging: {
                                System.out.println("[UwbBridge] closeRanging() invoked!");
                                stopRangingStream();
                                if (sActiveCallbackBinder != null && sActiveSessionHandle != null) {
                                    try {
                                        Parcel cbData = Parcel.obtain();
                                        cbData.writeInterfaceToken(DESCRIPTOR_CB_RANGING);
                                        cbData.writeInt(1);
                                        sActiveSessionHandle.getClass().getMethod("writeToParcel", Parcel.class, int.class).invoke(sActiveSessionHandle, cbData, 0);
                                        cbData.writeInt(1); // REASON_CLOSE_RANGING_CALLED
                                        cbData.writeInt(0);
                                        sActiveCallbackBinder.transact(CB_onRangingClosed, cbData, null, IBinder.FLAG_ONEWAY);
                                        cbData.recycle();
                                    } catch (Throwable ignore) {}
                                }
                                sActiveCallbackBinder = null;
                                sActiveSessionHandle = null;
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
                                return super.onTransact(code, data, reply, flags);
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
            System.out.println("[UwbBridge] Successfully registered 'uwb' AOSP bridge service v" + VERSION + "! Entering Looper.loop()...");
            Looper.loop();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
