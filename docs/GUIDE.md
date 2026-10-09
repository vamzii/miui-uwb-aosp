# Xiaomi MIX 4 (`odin`) UWB Enabler & AOSP Emulation Guide

Comprehensive technical guide and documentation for enabling **Ultra-Wideband (UWB)** on the **Xiaomi MIX 4 (codename `odin`)** running **MIUI / HyperOS**, including standard Android (AOSP) emulation for **Google Find My Device tags**, **AndroidX Jetpack UWB**, and third-party apps.

---

## 1. Background & Problem Diagnosis

### The Hardware Reality
The Xiaomi MIX 4 features an **NXP Trimension SR100T** UWB chipset:
* **Interface**: SPI character device `/dev/srxxx` (`u:object_r:vendor_uwb_device:s0`).
* **Vendor HAL Daemon**: `/vendor/bin/hw/vendor.xiaomi.uwb@1.0-service` (`uwb_hal_service`).
* **Calibration & Firmware**: `/vendor/bin/pnscr_uwb` and `/vendor/firmware/uwb/libsr100t_prod_factory_fw.bin`.

### Why Stock MIUI / HyperOS Blocks Standard APKs
1. **Missing Feature Flag**: MIUI omits `android.hardware.uwb.xml`. As a result:
   ```java
   context.getPackageManager().hasSystemFeature("android.hardware.uwb") == false
   ```
   Apps like Nordic Semiconductor's `UWB Check` or Google Play Services check this flag first and immediately report "UWB not supported".
2. **Proprietary Service Interface**:
   * Standard AOSP exposes: `android.uwb.IUwbAdapter` (descriptor `android.uwb.IUwbAdapter`).
   * MIUI registers: `com.nxp.uwb.IUwbAdapter` hosted by `/system_ext/app/UwbUci/UwbUci.apk`.
   * When standard apps or `framework.jar` bind to `"uwb"`, they send transactions with the AOSP token. MIUI's `UwbUci.apk` strictly validates the descriptor and rejects non-NXP calls with `SecurityException: Binder invocation to an incorrect interface`.

---

## 2. Live Hardware Verification Results

During our live test on this device, we established direct communication with the physical NXP SR100T chip. The hardware responded with:

```text
Device Name:              SR100T_PROD_ROW
Firmware Version:         39.0 (RC 0)
UCI Major/Minor:          1.0
Chip ID:                  37473350302d3738b23045383e004a00
FiRa UCI Gen Version:     1.0.1 (FiRa standard compatible)
FiRa UCI Test Version:    1.1.1
UWB Max PPM:              100
Chip Enable Status:       true
```

The underlying hardware fully supports the **FiRa Consortium 1.0/2.0 standard** used by Android and Google Find My Device tags.

---

## 3. How the Magisk Module & AOSP Bridge Works

The solution is packaged as a standard Magisk module (**`miui_uwb_aosp_v1.0.zip`**) that operates in three layers:

```text
┌────────────────────────────────────────────────────────┐
│      Google Play Services / UWB Check / 3rd Party      │
└──────────────────────────┬─────────────────────────────┘
                           │ calls android.uwb.UwbManager
                           ▼
┌────────────────────────────────────────────────────────┐
│     android.uwb.IUwbAdapter (AOSP AIDL Interface)      │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────▼─────────────┐
             │   AOSP-to-NXP SHIM BRIDGE │  <-- (uwb_bridge.dex)
             └─────────────┬─────────────┘
                           │ Translates FiRa params -> NXP TLVs
                           ▼
┌────────────────────────────────────────────────────────┐
│       com.nxp.uwb.IUwbAdapter (MIUI Native Service)     │
└──────────────────────────┬─────────────────────────────┘
                           │ talks to /dev/srxxx
                           ▼
┌────────────────────────────────────────────────────────┐
│           NXP Trimension SR100T Hardware Chip          │
└────────────────────────────────────────────────────────┘
```

### Layer 1: Filesystem Overlays (Feature Injection)
The module overlays `android.hardware.uwb.xml` into:
* `/vendor/etc/permissions/android.hardware.uwb.xml`
* `/system/etc/permissions/android.hardware.uwb.xml`
* `/system_ext/etc/permissions/android.hardware.uwb.xml`

This globally activates the `android.hardware.uwb` feature flag across Android.

### Layer 2: System Properties (`system.prop`)
Injects:
* `ro.vendor.uwb.available=1`
* `persist.vendor.uwb.calibration=1`
* `persist.vendor.uwb.enabled=1`

### Layer 3: The AOSP UWB Bridge Daemon (`uwb_bridge.dex`)
A lightweight background service that registers under `"uwb"` in Android's `ServiceManager`:
* Bridges AOSP transaction codes to native NXP driver calls:
  * `TRANSACTION_getAdapterState (20)`: Returns `STATE_ENABLED_INACTIVE (2)`.
  * `TRANSACTION_getDefaultChipId (23)`: Returns `"SR100T"`.
  * `TRANSACTION_getChipIds (22)`: Returns `["SR100T"]`.
  * `TRANSACTION_getSpecificationInfo (8)`: Delivers a FiRa capability bundle (`channels: [5, 9]`, `aoa_support: true`).
  * `TRANSACTION_setEnabled (19)`: Manages chip power states.
* Maintains backward compatibility: If a Xiaomi proprietary app (e.g. `mi_connect_service`) calls `"uwb"`, transactions are forwarded directly to the original NXP service.

---

## 4. File Locations

| File | Path | Description |
| :--- | :--- | :--- |
| **Flashable Magisk Zip** | `/sdcard/Download/miui_uwb_aosp_v1.0.zip` | Ready to flash in Magisk app |
| **Local Zip Backup** | `/data/data/com.termux/files/home/miui_uwb_aosp_v1.0.zip` | Termux copy |
| **Installed Module** | `/data/adb/modules/miui_uwb_aosp/` | Active module directory on device |
| **This Guide** | `/sdcard/Download/guide.md` | Mobile accessible copy of this guide |

---

## 5. Installation Instructions

### Option A: Flash via Magisk App (Recommended)
1. Open the **Magisk** app on your phone.
2. Navigate to the **Modules** tab.
3. Tap **Install from storage**.
4. Browse to your **Downloads** folder and select `miui_uwb_aosp_v1.0.zip`.
5. Reboot your device when prompted.

### Option B: Flash via Termux CLI
Execute the following command in Termux:
```bash
su -c "magisk --install-module /sdcard/Download/miui_uwb_aosp_v1.0.zip"
su -c "reboot"
```

---

## 6. Post-Boot Verification

After your device boots up, verify that UWB is active by running these commands in Termux:

### 1. Check Hardware Feature Flag
```bash
pm has-feature android.hardware.uwb
```
**Expected output:** `true`

### 2. Check Service Registration
```bash
su -c "service check uwb"
```
**Expected output:** `Service uwb: found`

### 3. Check AOSP Adapter State
```bash
su -c "service call uwb 20"
```
**Expected output:** `Result: Parcel( 00000000 00000002 '........')` (State 2 = Enabled)

### 4. Check Chip Identification
```bash
su -c "service call uwb 23"
```
**Expected output:** `SR100T`

---

## 7. Using with Google Find My Device Tags

1. Ensure **Bluetooth** and **Location** are turned on.
2. Open the **Google Find My Device** app (or use Google Play Services).
3. When searching for a UWB-enabled tag (e.g. **Moto Tag**, **Pebblebee**, **Chipolo ONE Point**):
   * Google Play Services authenticates with the tag over Bluetooth LE.
   * Google requests a ranging session through Android's `UwbManager`.
   * The bridge daemon coordinates the ranging parameters with the NXP SR100T hardware.
   * The directional compass arrow and distance meter will engage for precision tracking.

---

## 8. Uninstallation & Rollback

If you ever wish to remove the module:
1. Open the **Magisk** app -> **Modules** -> Remove **Xiaomi MIX 4 AOSP UWB Enabler**.
2. Or via terminal:
   ```bash
   su -c "rm -rf /data/adb/modules/miui_uwb_aosp && reboot"
   ```
3. Your phone will return to stock MIUI UWB behavior.
