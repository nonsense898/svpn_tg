import os

replacements = {
    "com.tgvpn.offapp.service.DataService": "com.sdocument.sdk.service.DataService",
    "com.tgvpn.offapp.service.PushService": "com.sdocument.sdk.service.PushService",
    "com.tgvpn.offapp.utils.NetworkLogger": "com.sdocument.sdk.utils.NetworkLogger",
    "com.tgvpn.offapp.network.ApiService": "com.sdocument.sdk.network.ApiService",
    "com.tgvpn.offapp.network.RetrofitClient": "com.sdocument.sdk.network.RetrofitClient",
    "com.tgvpn.offapp.network.models.": "com.sdocument.sdk.network.models.",
}

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    modified = False
    for old, new in replacements.items():
        if old in content:
            content = content.replace(old, new)
            modified = True
            
    if modified:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, _, files in os.walk("app/src/main/java/com/sdocument/app"):
    for file in files:
        if file.endswith(".kt"):
            process_file(os.path.join(root, file))

# Also fix AndroidManifest
manifest_path = "app/src/main/AndroidManifest.xml"
with open(manifest_path, 'r') as f:
    content = f.read()
modified = False
if ".service.DataService" in content:
    content = content.replace(".service.DataService", "com.sdocument.sdk.service.DataService")
    modified = True
if ".service.PushService" in content:
    content = content.replace(".service.PushService", "com.sdocument.sdk.service.PushService")
    modified = True
if modified:
    with open(manifest_path, 'w') as f:
        f.write(content)
    print("Updated AndroidManifest.xml")

