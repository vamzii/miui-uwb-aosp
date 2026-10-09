#!/system/bin/sh
# Wait until system is booted
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 3
done

# Ensure UWB device permissions
if [ -e /dev/srxxx ]; then
    chmod 660 /dev/srxxx
    chown uwb:uwb /dev/srxxx
fi

# Set properties
setprop ro.vendor.uwb.available 1
setprop persist.vendor.uwb.calibration 1
setprop persist.vendor.uwb.enabled 1

MODDIR=/data/adb/modules/miui_uwb_aosp
log -t UWB_ENABLER "Xiaomi MIX 4 AOSP UWB Module loaded successfully"

# Keep AOSP UWB Bridge Daemon active
while true; do
    if [ "$(service check uwb)" = "Service uwb: found" ]; then
        log -t UWB_ENABLER "Launching UwbBridgeDaemon..."
        CLASSPATH=$MODDIR/uwb_bridge.dex:/system_ext/framework/com.nxp.uwb.jar /system/bin/app_process / UwbBridgeDaemon >> /data/local/tmp/uwb_bridge.log 2>&1
    fi
    sleep 5
done
