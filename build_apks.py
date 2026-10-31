import os
import re
import shutil
import subprocess

PROJECT_DIR = "/Users/apple/AndroidStudioProjects/SVPN_Telegram"
MAIN_ACTIVITY_PATH = os.path.join(PROJECT_DIR, "app/src/main/java/com/tgvpn/offapp/MainActivity.kt")
APKS_DIR = os.path.join(PROJECT_DIR, "apks")

# Clean apks directory
if os.path.exists(APKS_DIR):
    print("Deleting previous APKs...")
    shutil.rmtree(APKS_DIR)
os.makedirs(APKS_DIR, exist_ok=True)

# Read original MainActivity
with open(MAIN_ACTIVITY_PATH, 'r') as f:
    original_content = f.read()

try:
    build_ids = ["26", "24", "22", "16"]
    for bid in build_ids:
        print(f"\n--- Building for buildId: {bid} ---")
        
        # Modify MainActivity.kt with the new buildId
        modified_content = re.sub(
            r'metadata\s*=\s*AppMetadata\((appId\s*=\s*\d+),\s*buildId\s*=\s*"[^"]+"\)',
            f'metadata = AppMetadata(\\1, buildId = "{bid}")',
            original_content
        )
        
        with open(MAIN_ACTIVITY_PATH, 'w') as f:
            f.write(modified_content)
            
        # Run `./gradlew assembleDebug`
        print("Running gradle build...")
        result = subprocess.run(
            ["./gradlew", "assembleDebug"],
            cwd=PROJECT_DIR,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        if result.returncode != 0:
            print(f"Error compiling buildId {bid}:")
            print(result.stderr)
            raise RuntimeError(f"Build failed for ID {bid}")
            
        # Copy generated APK
        src_apk = os.path.join(PROJECT_DIR, "app/build/outputs/apk/debug/app-debug.apk")
        dest_apk = os.path.join(APKS_DIR, f"tgvpn_{bid}.apk")
        if os.path.exists(src_apk):
            shutil.copyfile(src_apk, dest_apk)
            print(f"Successfully created: {dest_apk} (Size: {os.path.getsize(dest_apk)} bytes)")
        else:
            raise FileNotFoundError(f"Generated APK not found at {src_apk}")

finally:
    # Restore original MainActivity.kt
    with open(MAIN_ACTIVITY_PATH, 'w') as f:
        f.write(original_content)
    print("\nMainActivity.kt restored.")

print("\nAll builds completed successfully!")
