ui_print "**********************************************"
ui_print "*   Xiaomi MIX 4 (odin) AOSP UWB Enabler     *"
ui_print "*   for MIUI / HyperOS                       *"
ui_print "**********************************************"
ui_print "- Deploying AOSP UWB hardware XMLs"
ui_print "- Deploying UwbBridgeDaemon (AOSP-to-NXP)"
ui_print "- Configuring device permissions for /dev/srxxx"

set_perm /service.sh 0 0 0755
set_perm /post-fs-data.sh 0 0 0755
set_perm /uwb_bridge.dex 0 0 0644
