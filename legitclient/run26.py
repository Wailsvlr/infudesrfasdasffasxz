"""Runs the Prism Client 26.1.2 build in a real Minecraft 26.1.2 + Fabric client with the test driver (probe26/),
from its own game folder (run26/): Fabric API 0.155.2+26.1.2, the Prism 26.1.2 jar and the driver as the only mods.
The game files come from the Modrinth app's installed 26.1.2 + Fabric Loader 0.19.5; no profile is touched.
  python build26.py && python run26.py"""
import json
import os
import shutil
import subprocess
import zipfile

import build26 as b

HERE = os.path.dirname(os.path.abspath(__file__))
RUN = os.path.join(HERE, 'run26')
OPTIONS = 'renderDistance:6\nsimulationDistance:6\nguiScale:2\npauseOnLostFocus:false\nonboardAccessibility:false\nskipMultiplayerWarning:true\ntutorialStep:none\nsoundCategory_master:0.0\njoinedFirstServer:true\n'


def build_probe():
    out = os.path.join(b.OUT, 'probe')
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    cp = [f'{b.META}/versions/{b.VER}/{b.VER}.jar'] + b.libraries() + b.fabric_api_jars() + [os.path.join(b.OUT, 'classes')]
    src = os.path.join(HERE, 'probe26', 'com', 'wailsvlr', 'prism', 'probe', 'PrismProbe26.java')
    subprocess.run([b.JAVA + 'javac', '-encoding', 'UTF-8', '--release', '25', '-proc:none', '-nowarn', '-d', out, '-cp', os.pathsep.join(cp), src], check=True)
    jar = os.path.join(RUN, 'mods', 'prism-probe26.jar')
    with zipfile.ZipFile(jar, 'w') as z:
        z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'prism-probe26', 'version': '0.1.0', 'environment': 'client',
                                                  'entrypoints': {'client': ['com.wailsvlr.prism.probe.PrismProbe26']},
                                                  'depends': {'prismclient': '*', 'fabric-api': '*'}}))
        for root, _, files in os.walk(out):
            for f in files:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, out).replace(os.sep, '/'))


def main():
    os.makedirs(os.path.join(RUN, 'mods'), exist_ok=True)
    for f in os.listdir(os.path.join(RUN, 'mods')):
        os.remove(os.path.join(RUN, 'mods', f))
    shutil.copy(b.FAPI, os.path.join(RUN, 'mods'))
    shutil.copy(os.path.join(b.OUT, 'prismclient-1.0.0+26.1.2.jar'), os.path.join(RUN, 'mods'))
    build_probe()
    open(os.path.join(RUN, 'options.txt'), 'w').write(OPTIONS)
    rep = os.path.join(RUN, 'prism26-report.txt')
    if os.path.exists(rep):
        os.remove(rep)
    natives = f'{b.META}/natives/{b.VER}'
    cp = b.libraries() + [f'{b.META}/versions/{b.VER}/{b.VER}.jar']
    cmd = [b.JAVA + 'java', '-Xmx3G', '--sun-misc-unsafe-memory-access=allow', '--enable-native-access=ALL-UNNAMED',
           f'-Djava.library.path={natives}', f'-Djna.tmpdir={natives}', f'-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}',
           f'-Dio.netty.native.workdir={natives}', '-Dminecraft.launcher.brand=prism-test', '-Dminecraft.launcher.version=1',
           '-cp', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
           '--username', 'PrismTest', '--version', b.VER, '--gameDir', RUN, '--assetsDir', f'{b.META}/assets', '--assetIndex', '30',
           '--uuid', '5d8c5b7a2f1e4c6a9b3d7e8f0a1b2c3d', '--accessToken', '0', '--clientId', '0', '--xuid', '0',
           '--versionType', 'release', '--width', '1280', '--height', '720']
    with open(os.path.join(RUN, 'client.log'), 'w', encoding='utf-8') as log:
        r = subprocess.run(cmd, cwd=RUN, stdout=log, stderr=subprocess.STDOUT, timeout=900)
    print('exit', r.returncode)
    if os.path.exists(rep):
        print(open(rep, encoding='utf-8').read())


if __name__ == '__main__':
    main()
