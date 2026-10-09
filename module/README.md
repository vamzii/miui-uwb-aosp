# Xiaomi MIX 4 (odin) AOSP UWB Bridge & Feature Enabler

This Magisk module enables the standard Android Open Source Project (AOSP) Ultra-Wideband (UWB) stack on Xiaomi MIX 4 running MIUI / HyperOS.

## What It Does
1. Hardware Feature Flag Injection:
   - Mounts android.hardware.uwb.xml into /vendor/etc/permissions/ and /system/etc/permissions/.
   - Makes PackageManager.hasSystemFeature("android.hardware.uwb") return true globally.
2. Hardware Properties:
   - Injects ro.vendor.uwb.available=1 and sets SPI device permissions (/dev/srxxx).
3. AOSP UWB Bridge Daemon (uwb_bridge.dex):
   - Bridges AOSP android.uwb.IUwbAdapter transactions to the NXP Trimension SR100T hardware stack (com.nxp.uwb.IUwbAdapter).
   - Serves getAdapterState(), getDefaultChipId(), getSpecificationInfo(), and FiRa ranging calls to AOSP clients such as Google Play Services and third-party UWB apps.
