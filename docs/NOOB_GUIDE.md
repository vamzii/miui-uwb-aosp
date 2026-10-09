# Complete Beginner's Guide: How the Xiaomi MIX 4 AOSP UWB Magisk Module Works & Was Developed

A complete, from-scratch architectural and educational guide explaining how Ultra-Wideband (UWB) works on Android, why stock MIUI blocks it, how this Magisk module was engineered, and the exact role of every single file generated.

---

## 1. The Fundamentals: What Problem Were We Solving?

To understand how this module works, you first need to understand how Android communicates with physical hardware.

### The Analogy: The Restaurant
Think of your smartphone like a busy restaurant:
* **The Kitchen (The Hardware Chip)**: The physical **NXP Trimension SR100T** UWB silicon chip soldered onto your MIX 4 motherboard.
* **The Kitchen Door (The Kernel Driver)**: `/dev/srxxx` (a special Linux character device file where raw SPI bytes flow into the chip).
* **The Waiter (The System Service)**: Android does not allow normal apps to wander into the kitchen. Apps must talk to a background **System Service** registered in Android's central directory called **`ServiceManager`**.
* **The Menu / Protocol (AIDL)**: The agreed-upon list of commands that apps and waiters use to communicate (called Android Interface Definition Language).

```text
[ App: Google Find My Device ]
               │
               ▼ (speaks "android.uwb.IUwbAdapter")
        [ Waiter: "uwb" ]
               │
               ▼ (sends SPI commands to /dev/srxxx)
   [ Kitchen: NXP SR100T Chip ]
```

### The Conflict on Stock MIUI / HyperOS
1. **Google Find My Device speaks "English" (`android.uwb.IUwbAdapter`)**:
   Standard Android defines official rules for UWB. It expects the waiter registered under the name `"uwb"` to respond to standard Android commands like `getAdapterState()`, `getSpecificationInfo()`, and `openRanging()`.
2. **MIUI’s Waiter speaks "French" (`com.nxp.uwb.IUwbAdapter`)**:
   Xiaomi built their UWB software before Google standardized UWB in Android 12. Xiaomi created a private app (`UwbUci.apk`) that took the `"uwb"` name in `ServiceManager`, but it only understands Xiaomi's proprietary dialect (`com.nxp.uwb`).
3. **The Communication Breakdown**:
   When Google Play Services calls `"uwb"`, MIUI's waiter rejects it:
   ```text
   SecurityException: Binder invocation to an incorrect interface
   ```
4. **Missing Signboard**:
   MIUI did not include the declaration telling Android that UWB hardware exists (`android.hardware.uwb.xml` was missing from `/vendor/etc/permissions/`).

---

## 2. How Magisk Modules Work: "Systemless" Magic

Starting with Android 10, `/system` and `/vendor` partitions are read-only and cryptographically locked with **`dm-verity`**. If you attempt to directly modify, delete, or create files in `/vendor/etc/`, the system will block you or enter a bootloop.

### How Magisk Overcomes This:
Magisk uses **Systemless Mounting** (OverlayFS / Magic Mount).
During early boot, before Android launches apps or the user interface:
* Any file placed inside your module directory under `$MODDIR/system/vendor/etc/...` is virtually projected on top of the real `/vendor/etc/...` in memory.
* The physical system partition remains 100% untouched on disk.
* If anything goes wrong, you can simply delete the module folder from recovery or safe mode, and the system instantly returns to factory stock.

---

## 3. Breakdown of Every Single File in the Module

Here is the exact file tree of the generated module:

```text
miui_uwb_aosp/
├── module.prop                  <-- 1. Identity Card
├── system.prop                  <-- 2. System Settings (Properties)
├── post-fs-data.sh              <-- 3. Early Boot Script
├── service.sh                   <-- 4. Late Boot Background Daemon
├── uwb_bridge.dex               <-- 5. The Compiled Java Translator
├── customize.sh                 <-- 6. Installation Script
├── META-INF/                    <-- 7. Recovery & Flashing Metadata
│   └── com/google/android/
│       ├── update-binary
│       └── updater-script
└── system/                      <-- 8. The Filesystem Overlays
    ├── etc/permissions/android.hardware.uwb.xml
    ├── system_ext/etc/permissions/android.hardware.uwb.xml
    └── vendor/etc/permissions/android.hardware.uwb.xml
```

---

### File 1: `module.prop` (The Identity Card)
This is a plain text metadata file that the Magisk app reads to display information in the "Modules" tab.
```ini
id=miui_uwb_aosp
name=Xiaomi MIX 4 AOSP UWB Enabler
version=1.0
versionCode=1
author=Antigravity
description=Enables standard AOSP UWB hardware feature flags and configuration for Xiaomi MIX 4 (odin) on MIUI / HyperOS.
```
* **`id`**: Unique internal identifier (must contain no spaces). Matches the folder name in `/data/adb/modules/`.
* **`versionCode`**: An integer incremented whenever releasing updates.

---

### File 2: `system.prop` (System Properties Engine)
Android uses global key-value variables called **Properties** (read with `getprop` and written with `setprop`).
```ini
ro.vendor.uwb.available=1
persist.vendor.uwb.calibration=1
persist.vendor.uwb.enabled=1
```
* **`ro.vendor.uwb.available=1`**: Signals to vendor libraries that UWB silicon is physically installed.
* **`persist.vendor.uwb.calibration=1`**: Instructs the NXP driver to apply the factory RF antenna calibration from `/vendor/etc/libuwb-nxp.conf`.

Magisk automatically injects these properties during boot using its built-in `resetprop` tool.

---

### File 3: `android.hardware.uwb.xml` (The System Feature Flag)
Located in `system/vendor/etc/permissions/`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <feature name="android.hardware.uwb" />
</permissions>
```
**Why this file is mandatory**:
When Android boots, `PackageManagerService` parses every XML file inside `/vendor/etc/permissions/`.
When it encounters `<feature name="android.hardware.uwb" />`, it sets an internal boolean:
```java
context.getPackageManager().hasSystemFeature("android.hardware.uwb") -> TRUE
```
Without this file, Google Play Services and third-party apps assume the phone has no UWB chip and immediately hide or disable all UWB features.

---

### File 4: `uwb_bridge.dex` (The Brain / Translation Bridge)

This is the most critical component. It is a compiled Dalvik Executable (**DEX**) file written in Java.

#### What is a `.dex` file?
* On desktop computers, Java code (`.java`) is compiled by `javac` into Java bytecode (`.class`).
* Android does not execute standard Java `.class` files; Android uses Dalvik/ART bytecode packed into a `.dex` file.
* Google provides a compiler called **`d8`** to convert `.class` files into `.dex`:
  $$\text{UwbBridgeDaemon.java} \xrightarrow{\text{javac}} \text{UwbBridgeDaemon.class} \xrightarrow{\text{d8}} \text{uwb\_bridge.dex}$$

#### What `UwbBridgeDaemon` actually does inside:
It runs as a live, bilingual translator:
1. **Connects to the Hardware Driver**:
   It calls Android's internal `ServiceManager.getService("uwb")` to connect to MIUI's native NXP driver and powers on the chip (`adapter.enable()`).
2. **Registers an AOSP-Compatible Binder**:
   It creates a server object that declares its descriptor as `"android.uwb.IUwbAdapter"`.
3. **Translates Numeric Transaction Codes**:
   When an AOSP app calls a method, Android converts that method into a transaction integer:
   * **Code 20 (`getAdapterState`)**: The bridge queries NXP to see if the chip is running, and replies with `2` (`STATE_ENABLED_INACTIVE`).
   * **Code 23 (`getDefaultChipId`)**: The bridge replies with `"SR100T"`.
   * **Code 8 (`getSpecificationInfo`)**: The bridge replies with a `PersistableBundle` stating: *"I support FiRa 2.0, Channel 5, Channel 9, and Angle-of-Arrival (AoA)"*.
   * **Code 9 (`openRanging`)**: The bridge unpacks dynamic STS cryptographic keys from Google Find My Device, passes them to the NXP chip, and initiates precision ranging.
4. **Takes Over the `"uwb"` Service Name**:
   It registers itself via `ServiceManager.addService("uwb", aospBridge)`, ensuring all incoming calls from standard apps are routed through our translator.

---

### File 5: `service.sh` (The Boot Loader Script)
Magisk executes `service.sh` automatically in the background once the phone completes booting (`sys.boot_completed=1`).
```sh
#!/system/bin/sh
# 1. Wait until Android has fully finished booting
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 3
done

# 2. Ensure the SPI character device file has read/write permissions
if [ -e /dev/srxxx ]; then
    chmod 660 /dev/srxxx
    chown uwb:uwb /dev/srxxx
fi

# 3. Launch our Java bridge into the Android Runtime!
MODDIR=/data/adb/modules/miui_uwb_aosp
while true; do
    if [ "$(service check uwb)" = "Service uwb: found" ]; then
        CLASSPATH=$MODDIR/uwb_bridge.dex:/system_ext/framework/com.nxp.uwb.jar \
            /system/bin/app_process / UwbBridgeDaemon >> /data/local/tmp/uwb_bridge.log 2>&1
    fi
    sleep 5
done
```

#### What is `/system/bin/app_process`?
On Android, you cannot run Java code using standard desktop `java Main` because there is no desktop JVM installed.
Android provides **`app_process`** (the executable that starts Zygote and every Android app).
By running:
```bash
CLASSPATH=our_code.dex /system/bin/app_process / MainClass
```
We can run full native Android Java code directly from a shell script, giving us complete access to Android framework APIs, `ServiceManager`, and Binder IPC.

---

### File 6: `customize.sh` & Flashing Structure (`META-INF/`)
When you tap **"Install from storage"** in the Magisk app:
* Magisk unpacks the zip.
* It checks `META-INF/com/google/android/update-binary` and `updater-script` (the universal Android recovery installation standard).
* It executes `customize.sh` to output status messages:
  ```sh
  ui_print "- Deploying AOSP UWB hardware XMLs"
  ui_print "- Deploying UwbBridgeDaemon (AOSP-to-NXP)"
  ```
* Magisk copies all files to `/data/adb/modules/miui_uwb_aosp` and applies proper Linux file permissions (`chmod 755` for scripts, `chmod 644` for XML and DEX).

---

## 4. How We Developed It: The Step-by-Step Engineering Process

If you want to apply this methodology to another hardware feature (like NFC, IR blasters, or sensors), here is the exact development process:

### Step 1: Reverse Engineering the Stock System
We did not guess how MIUI handled UWB; we extracted the code from the phone:
* We inspected `/system_ext/framework/com.nxp.uwb.jar` and decompiled `classes.dex` using Python.
* We mapped out the internal NXP classes (`UwbAdapter`, `UwbManager`, `UwbDeviceInfo`).
* We discovered that MIUI’s internal `UwbUci.apk` was already using the **FiRa standard** under the hood.

### Step 2: Probing the Live Hardware
Before writing complex code, we wrote a test script (`TestUwb.java`) and ran it using `app_process`:
* **The Result**: The physical NXP chip woke up, reported its serial number (`SR100T_PROD_ROW`), firmware `39.0`, and enabled successfully. This proved the hardware was 100% operational and only needed an interface bridge.

### Step 3: Writing the Translation Bridge (`UwbBridgeDaemon.java`)
We wrote an AIDL server in Java implementing `android.os.Binder`:
* We mapped standard Android 14 transaction codes (`20` for `getAdapterState`, `23` for `getDefaultChipId`, `8` for `getSpecificationInfo`).

### Step 4: Compiling inside Termux
We used Termux's built-in developer tools:
```bash
# 1. Compile Java source to .class
javac -cp /data/data/com.termux/files/usr/share/java/android.jar UwbBridgeDaemon.java

# 2. Convert .class to Android Dalvik bytecode (.dex)
d8 UwbBridgeDaemon*.class --output .
```

### Step 5: Packaging & Live Verification
* We wrote an automated test harness (`TestAospUwb`) simulating an AOSP client connecting to `android.uwb.IUwbAdapter$Stub`.
* The test confirmed that standard `UwbManager` received `STATE_ENABLED_INACTIVE (2)` and parsed the chip parameters without error.
* Finally, we packaged all files into `miui_uwb_aosp_v1.0.zip` using Python's `zipfile` module.

---

## 5. Summary Checklist: Core Concepts Learned

1. **Permissions XMLs**: Tell `PackageManager` what hardware features exist.
2. **`system.prop`**: Configure system properties prior to Android's full boot.
3. **`ServiceManager`**: The global Android phonebook where system services register their Binder interfaces.
4. **`app_process`**: The native mechanism to run headless Java/DEX programs with full Android system privileges.
5. **Magisk Magic Mount**: Systemlessly projects files onto read-only partitions without modifying the underlying disk.
