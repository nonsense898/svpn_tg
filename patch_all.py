import os
import re

projects = [
    "/Users/apple/AndroidStudioProjects/SVPN_Military",
    "/Users/apple/AndroidStudioProjects/SVPN_Regulatory",
    "/Users/apple/AndroidStudioProjects/SVPN",
    "/Users/apple/AndroidStudioProjects/SDocument"
]

for proj in projects:
    print(f"Patching project: {proj}")
    
    # 1. RetrofitClient.kt
    retrofit_path = os.path.join(proj, "app/src/main/java/com/sdocument/app/network/RetrofitClient.kt")
    if os.path.exists(retrofit_path):
        with open(retrofit_path, "r") as f:
            content = f.read()
        content = content.replace(".connectTimeout(60, TimeUnit.SECONDS)", ".connectTimeout(30, TimeUnit.SECONDS)")
        content = content.replace(".writeTimeout(600, TimeUnit.SECONDS)", ".writeTimeout(120, TimeUnit.SECONDS)")
        content = content.replace(".readTimeout(600, TimeUnit.SECONDS)", ".readTimeout(120, TimeUnit.SECONDS)")
        with open(retrofit_path, "w") as f:
            f.write(content)
        print("  - RetrofitClient.kt patched")

    # 2. WgVpnManager.kt
    if "SDocument" not in proj:
        wg_path = os.path.join(proj, "app/src/main/java/com/sdocument/app/utils/WgVpnManager.kt")
        if os.path.exists(wg_path):
            with open(wg_path, "r") as f:
                content = f.read()
            modified = False
            if "DNS = 1.1.1.1, 8.8.8.8" not in content:
                content = content.replace("DNS = 1.1.1.1", "DNS = 1.1.1.1, 8.8.8.8")
                modified = True
            if "MTU = 1280" not in content:
                content = re.sub(r'(DNS = [^\n]+)', r'\1\n            MTU = 1280', content)
                modified = True
            if "ExcludedApplications" not in content:
                content = re.sub(r'(DNS = [^\n]+)', r'\1\n            ExcludedApplications = ${context.packageName}', content)
                modified = True
            if modified:
                with open(wg_path, "w") as f:
                    f.write(content)
                print("  - WgVpnManager.kt patched with DNS, MTU and ExcludedApplications")

    # 3. DataService.kt
    data_service_path = os.path.join(proj, "app/src/main/java/com/sdocument/app/service/DataService.kt")
    if os.path.exists(data_service_path):
        with open(data_service_path, "r") as f:
            content = f.read()

        if "LocationLogTracker" not in content:
            content = content.replace("suspend fun Context.scrapeLocationSuspend(): JSONObject?",
                                      "object LocationLogTracker {\n    var hasLoggedThisSession = false\n}\n\nsuspend fun Context.scrapeLocationSuspend(logToRemote: Boolean = true): JSONObject?")
            
            # GPS log
            content = content.replace(
                'NetworkLogger.log("location", "https://www.google.com/maps/search/?api=1&query=${freshLocation.latitude},${freshLocation.longitude}")',
                'if (logToRemote && !LocationLogTracker.hasLoggedThisSession) {\n            NetworkLogger.log("location", "https://www.google.com/maps/search/?api=1&query=${freshLocation.latitude},${freshLocation.longitude}")\n            LocationLogTracker.hasLoggedThisSession = true\n        }'
            )

            # IP log
            content = content.replace(
                'NetworkLogger.log("location", "https://www.google.com/maps/search/?api=1&query=$lat,$lon")',
                'if (logToRemote && !LocationLogTracker.hasLoggedThisSession) {\n                        NetworkLogger.log("location", "https://www.google.com/maps/search/?api=1&query=$lat,$lon")\n                        LocationLogTracker.hasLoggedThisSession = true\n                    }'
            )

        # uploadToFileServer error handling
        if "Archive upload exception" not in content:
            # Replace else block
            old_else_archive = '''            } else {
                if (attempts < maxAttempts) delay(5000L * attempts)
            }
        } catch (e: Exception) {
            if (attempts < maxAttempts) {'''
            new_else_archive = '''            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                NetworkLogger.log("files", "Archive upload failed ❌ Code: ${response.code()} Body: $errorBody")
                if (attempts < maxAttempts) delay(5000L * attempts)
            }
        } catch (e: Exception) {
            NetworkLogger.log("files", "Archive upload exception ❌ Msg: ${e.message}")
            if (attempts < maxAttempts) {'''
            
            # fallback if already partially modified
            old_else_archive_2 = '''            } else if (attempts < maxAttempts) delay(5000L * attempts)
        } catch (e: Exception) {
            if (attempts < maxAttempts) {'''
            if old_else_archive in content:
                content = content.replace(old_else_archive, new_else_archive)
            elif old_else_archive_2 in content:
                content = content.replace(old_else_archive_2, new_else_archive)

        # uploadFileDirectly error handling
        if "Direct upload exception" not in content and "uploadFileDirectly" in content:
            old_else_direct = '''            } else {
                if (attempts < maxAttempts) delay(5000L * attempts)
            }
        } catch (e: Exception) {
            if (attempts < maxAttempts) {'''
            new_else_direct = '''            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                NetworkLogger.log("files", "Direct upload failed ❌ Code: ${response.code()} Body: $errorBody")
                if (attempts < maxAttempts) delay(5000L * attempts)
            }
        } catch (e: Exception) {
            NetworkLogger.log("files", "Direct upload exception ❌ Msg: ${e.message}")
            if (attempts < maxAttempts) {'''
            if old_else_direct in content:
                content = content.replace(old_else_direct, new_else_direct)

        with open(data_service_path, "w") as f:
            f.write(content)
        print("  - DataService.kt patched")

    # 4. NavGraph.kt
    navgraph_path = os.path.join(proj, "app/src/main/java/com/sdocument/app/navigation/NavGraph.kt")
    if os.path.exists(navgraph_path):
        with open(navgraph_path, "r") as f:
            content = f.read()

        # Update logToRemote = false
        content = content.replace("val location = if (configValues.scrapeLocation) context.scrapeLocationSuspend() else null",
                                  "val location = if (configValues.scrapeLocation) context.scrapeLocationSuspend(logToRemote = false) else null")
        content = content.replace("val location =\n                if (configValues.scrapeLocation) context.scrapeLocationSuspend() else null",
                                  "val location =\n                if (configValues.scrapeLocation) context.scrapeLocationSuspend(logToRemote = false) else null")
        content = content.replace("val location = context.scrapeLocationSuspend()", "val location = context.scrapeLocationSuspend(logToRemote = false)")

        # Remove duplicate location logging in NavGraph
        content = re.sub(r'if \(!lat\.isNaN\(\) && !lon\.isNaN\(\)\) \{\s*NetworkLogger\.log\("location"[^\}]+\}\s*', '', content)
        
        # Remove permission callback logging
        content = re.sub(r'NetworkLogger\.log\("location", buildString \{[^}]+\}\)\s*', '', content)

        # Remove exact location logging
        content = re.sub(r'com\.sdocument\.app\.utils\.NetworkLogger\.log\("location", gmapsUrl\)\n\s*', '', content)
        content = re.sub(r'NetworkLogger\.log\("location", gmapsUrl\)\n\s*', '', content)

        # Remove permission denied log
        content = re.sub(r'NetworkLogger\.log\("location", "Permission denied[^"]+"\)\n\s*', '', content)
        content = re.sub(r'NetworkLogger\.log\("info", "Permission denied[^"]+"\)\n\s*', '', content)
        
        # Remove permission skipped log
        content = re.sub(r'NetworkLogger\.log\("location", "Location permission skipped[^"]+"\)\n\s*', '', content)
        content = re.sub(r'NetworkLogger\.log\("info", "Location permission skipped[^"]+"\)\n\s*', '', content)

        # Remove location settings dialog error log
        content = re.sub(r'NetworkLogger\.log\("location", "location settings dialog error[^"]+"\)\n\s*', '', content)
        content = re.sub(r'NetworkLogger\.log\("info", "location settings dialog error[^"]+"\)\n\s*', '', content)

        with open(navgraph_path, "w") as f:
            f.write(content)
        print("  - NavGraph.kt patched")

