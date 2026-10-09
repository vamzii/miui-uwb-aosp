#!/usr/bin/env bash
set -e

echo "=== Building AOSP UWB Bridge Daemon & Magisk Module ==="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$ROOT_DIR/src"
MODULE_DIR="$ROOT_DIR/module"
RELEASE_DIR="$ROOT_DIR/release"

mkdir -p "$RELEASE_DIR"

echo "[1/3] Compiling UwbBridgeDaemon.java..."
ecj -cp /system/framework/framework.jar:/system_ext/framework/com.nxp.uwb.jar "$SRC_DIR/UwbBridgeDaemon.java"

echo "[2/3] Translating bytecode to Dalvik Executable (classes.dex)..."
d8 "$SRC_DIR"/UwbBridgeDaemon*.class --output "$SRC_DIR"
cp "$SRC_DIR/classes.dex" "$MODULE_DIR/uwb_bridge.dex"
rm -f "$SRC_DIR"/*.class "$SRC_DIR"/classes.dex

echo "[3/3] Packaging Magisk Module Flashable Zip..."
python3 -c "
import zipfile, os, shutil

module_dir = '$MODULE_DIR'
release_zip = '$RELEASE_DIR/miui_uwb_aosp_v1.1.zip'

with zipfile.ZipFile(release_zip, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(module_dir):
        for file in files:
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, module_dir)
            z.write(full_path, rel_path)

print('Successfully created ' + release_zip)
"

echo "=== Build Complete! Flashable module: release/miui_uwb_aosp_v1.1.zip ==="
