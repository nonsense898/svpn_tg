import re
import shutil

svpn_build = '/Users/apple/AndroidStudioProjects/SVPN/app/build.gradle.kts'
sdoc_build = '/Users/apple/AndroidStudioProjects/SDocument/app/build.gradle.kts'

shutil.copyfile(svpn_build, sdoc_build)

with open(sdoc_build, 'r') as f:
    content = f.read()

# Remove wireguard dependency
content = re.sub(r'implementation\(libs\.wireguard\.android\).*?\n', '', content)

with open(sdoc_build, 'w') as f:
    f.write(content)
