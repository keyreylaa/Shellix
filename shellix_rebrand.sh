#!/usr/bin/env bash
set -e
cd /root/ReTerminal

echo "[1/8] Renaming source package folder..."
if ! git mv core/main/src/main/java/com/rk/terminal core/main/src/main/java/com/rk/shellix 2>/dev/null; then
  mv core/main/src/main/java/com/rk/terminal core/main/src/main/java/com/rk/shellix
fi

echo "[2/8] Fixing package + imports inside new folder..."
find core/main/src/main/java/com/rk/shellix -type f \( -name "*.kt" -o -name "*.java" \) \
  -exec sed -i 's/com\.rk\.terminal/com.rk.shellix/g' {} +

echo "[3/8] Fixing cross-package imports..."
sed -i 's/com\.rk\.terminal/com.rk.shellix/g' \
  core/main/src/main/java/com/rk/settings/Settings.kt \
  core/main/src/main/java/com/rk/libcommons/Utils.kt \
  core/main/src/main/java/com/rk/UbuntuDocumentProvider.kt

echo "[4/8] Fixing manifest component names..."
sed -i 's/com\.rk\.terminal\./com.rk.shellix./g' app/src/main/AndroidManifest.xml

echo "[5/8] Setting gradle namespace + applicationId..."
sed -i 's/namespace = "com.rk.terminal"/namespace = "com.shellix.terminal"/' core/main/build.gradle.kts
sed -i 's/applicationId = "com.rk.terminal"/applicationId = "com.shellix.terminal"/' app/build.gradle.kts

echo "[6/8] Renaming KarbonTheme -> ShellixTheme..."
sed -i 's/KarbonTheme/ShellixTheme/g' \
  core/main/src/main/java/com/rk/shellix/ui/theme/Theme.kt \
  core/main/src/main/java/com/rk/shellix/ui/activities/terminal/MainActivity.kt

echo "[7/8] Fixing ThemeManager R.style Theme_Karbon_* -> Theme_Shellix_*..."
sed -i 's/Theme_Karbon/Theme_Shellix/g' \
  core/main/src/main/java/com/rk/shellix/ui/theme/ThemeManager.kt

echo "[8/8] Verifying no leftover com.rk.terminal..."
if grep -rl "com.rk.terminal" core/main app; then
  echo "WARNING: some com.rk.terminal references remain"
else
  echo "OK: zero com.rk.terminal references remain"
fi
echo "DONE. Rebrand to Shellix (package com.shellix.terminal) complete."
