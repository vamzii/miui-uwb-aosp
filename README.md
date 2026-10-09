# Xiaomi MIX 4 (`odin`) MIUI / HyperOS AOSP UWB Enabler

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Device: Xiaomi MIX 4](https://img.shields.io/badge/Device-Xiaomi%20MIX%204%20(odin)-orange.svg)](https://en.wikipedia.org/wiki/Xiaomi_MIX_4)
[![Magisk / KernelSU](https://img.shields.io/badge/Root-Magisk%20%2F%20KernelSU%20%2F%20APatch-green.svg)](#installation)

Enables standard **AOSP Ultra-Wideband (UWB)** APIs on the **Xiaomi MIX 4 (`odin`)** running **MIUI / HyperOS**, allowing Google Find My Device (Precision Finding with compatible UWB tags like Moto Tag), Nearby Share / Quick Share UWB directionality, and third-party UWB applications to function seamlessly.

---

## 📌 Background: The Problem

The Xiaomi MIX 4 features an onboard **NXP SR100T Ultra-Wideband transceiver** (`/dev/srxxx`) and an active vendor HAL (`vendor.xiaomi.uwb@1.0-service`). However, under stock MIUI / HyperOS:

1. **Missing AOSP Feature Manifest**: Xiaomi omitted `android.hardware.uwb.xml`, meaning `PackageManager.hasSystemFeature("android.hardware.uwb")` returns `false`.
2. **Proprietary Binder Interface**: MIUI registers its UWB service under ServiceManager using a non-standard NXP descriptor (`com.nxp.uwb.IUwbAdapter`).
3. **AOSP Incompatibility**: Standard Android apps, Google Play Services (GMS), and the `android.uwb.UwbManager` framework look for `android.uwb.IUwbAdapter`. When interacting with the native service, GMS encounters a fatal `SecurityException: Binder invocation to an incorrect interface` and falls back to standard Bluetooth LE.

---

## 💡 The Solution: Architecture

This project provides an automated **Magisk / KernelSU module** and an **AOSP UWB Bridge Daemon (`UwbBridgeDaemon`)** that runs seamlessly in the background:

```
+--------------------------------------------------------------------+
|                      Android Applications                          |
|   (Google Find My Device, UWB Check, Jetpack androidx.core.uwb)   |
+---------------------------------+----------------------------------+
                                  |
                                  | Calls android.uwb.IUwbAdapter
                                  v
+--------------------------------------------------------------------+
|               UwbBridgeDaemon (app_process root service)           |
|                                                                    |
|  - Intercepts calls on ServiceManager service 'uwb'                |
|  - Answers getAdapterState(), getChipInfos(), getSpecificationInfo|
|  - Enforces AOSP IUwbAdapter descriptor & FiRa profiles            |
|  - Manages NXP hardware power states & sessions                    |
+---------------------------------+----------------------------------+
                                  |
                                  | Routes control calls
                                  v
+--------------------------------------------------------------------+
|              MIUI NXP UWB Stack & Vendor Hardware                  |
|    com.nxp.uwb.IUwbAdapter  <--->  vendor.xiaomi.uwb@1.0-service   |
|                                                  |                 |
|                                            /dev/srxxx              |
|                                       (NXP SR100T Chipset)         |
+--------------------------------------------------------------------+
```

---

## 🚀 Features

* **Hardware Feature Injection**: Injects `android.hardware.uwb.xml` into `/system/etc/permissions/` and `/system_ext/etc/permissions/`.
* **Full AOSP AIDL Compliance**:
  * Implements `android.uwb.IUwbAdapter` (Codes 1–35).
  * Returns accurate FiRa Consortium capabilities (`aoa_support = true`, channels `5, 9`).
  * Emulates chip info metadata for `SR100T`.
* **Auto-healing Daemon**: Managed by a persistent service watchdog in `service.sh`.
* **Zero System Modification**: Completely systemless through Magisk / KernelSU / APatch.

---

## 📦 Installation

### Prerequisites
* Xiaomi MIX 4 (`odin`)
* MIUI 13/14 or HyperOS 1.0 (Android 12, 13, or 14)
* Root via **Magisk v24+**, **KernelSU**, or **APatch**

### Flashing
1. Download the latest flashable ZIP from **[GitHub Releases](https://github.com/vamzii/miui-uwb-aosp/releases/latest)**:
   * **[miui_uwb_aosp_v1.1.zip](https://github.com/vamzii/miui-uwb-aosp/releases/download/v1.1/miui_uwb_aosp_v1.1.zip)** (Recommended)
   * **[miui_uwb_aosp.zip](https://github.com/vamzii/miui-uwb-aosp/releases/download/v1.1/miui_uwb_aosp.zip)**
2. Open the **Magisk**, **KernelSU**, or **APatch** app.
3. Go to **Modules** -> **Install from storage**.
4. Select the downloaded `.zip` and tap install.
5. Reboot your device.

---

## 🔍 Verification

Once booted, open a root terminal (Termux or `adb shell`) and run:

```bash
# 1. Verify feature presence (should return: feature:android.hardware.uwb)
pm list features | grep uwb

# 2. Verify service descriptor (should return: [android.uwb.IUwbAdapter])
service list | grep -i uwb

# 3. Check daemon process
ps -ef | grep UwbBridgeDaemon
```

You can also install the free **UWB Check** app (`nl.nordicnode.uwbcheck`) from Google Play to confirm hardware recognition.

---

## 🧭 Important Note Regarding Trackers & Precision Finding

* **True UWB Trackers (e.g. Motorola Moto Tag)**: Feature a certified UWB transceiver and will display directional compass arrows in Google Find My Device.
* **BLE-Only Trackers (e.g. Lineple, Pebblebee, Chipolo Point)**: Only contain Bluetooth radios. They show distance proximity rings ("Getting closer") and sound playback, but **cannot** display directional arrows regardless of phone capabilities.

---

## 🛠️ Building from Source

To compile and package the module yourself directly on the device or via Termux:

```bash
# Clone the repository
git clone https://github.com/<your-username>/miui-uwb-aosp.git
cd miui-uwb-aosp

# Build daemon and package zip
./build.sh
```

---

## 📖 Detailed Guides

* [Technical Engineering Guide](docs/GUIDE.md) — Comprehensive technical breakdown of AIDL bridging, reflection, and binder transactions.
* [Beginner's Engineering Walkthrough](docs/NOOB_GUIDE.md) — Step-by-step tutorial explaining how each file was developed from scratch.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
