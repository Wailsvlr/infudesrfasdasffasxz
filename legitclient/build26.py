"""Builds Prism Client for Minecraft 26.1.2 + Fabric from the same sources as the 1.21.11 build (plus the files in
src26/, which replace a source file of the same path when an API changed). 26.1 ships unobfuscated, so the code is
compiled straight against the game jar the Modrinth app installed, with Fabric API 0.155.2+26.1.2; no Loom, nothing
downloaded. Output: build26/prismclient-1.0.0+26.1.2.jar

  python build26.py
"""
import json
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
META = 'C:/Users/PC/AppData/Roaming/ModrinthApp/meta'
VER = '26.1.2-0.19.5'
JAVA = 'C:/Users/PC/verity-dev/jdk/jdk-25.0.4.1+1/bin/'
FAPI = 'C:/Users/PC/verity-dev/uikit-client26/run/mods/fabric-api-0.155.2+26.1.2.jar'
OUT = os.path.join(HERE, 'build26')


def libraries():
    j = json.load(open(f'{META}/versions/{VER}/{VER}.json'))
    out = []
    for lib in j['libraries']:
        rules = lib.get('rules')
        if rules and not any(r['action'] == 'allow' and r.get('os', {}).get('name') in (None, 'windows') for r in rules):
            continue
        a = lib.get('downloads', {}).get('artifact')
        p = a['path'] if a else '{0}/{1}/{2}/{1}-{2}.jar'.format(lib['name'].split(':')[0].replace('.', '/'), *lib['name'].split(':')[1:3])
        full = f'{META}/libraries/{p}'
        if os.path.exists(full):
            out.append(full)
    return out


def fabric_api_jars():
    d = os.path.join(OUT, 'fapi')
    os.makedirs(d, exist_ok=True)
    with zipfile.ZipFile(FAPI) as z:
        for n in z.namelist():
            if n.startswith('META-INF/jars/') and n.endswith('.jar'):
                p = os.path.join(d, os.path.basename(n))
                if not os.path.exists(p):
                    with open(p, 'wb') as f:
                        f.write(z.read(n))
    return [os.path.join(d, f) for f in os.listdir(d)]


def sources():
    files = {}
    for base in (os.path.join(HERE, 'src', 'client', 'java'), os.path.join(HERE, 'src', 'main', 'java'), os.path.join(HERE, 'src26', 'java')):
        for root, _, fs in os.walk(base):
            for f in fs:
                if f.endswith('.java'):
                    full = os.path.join(root, f)
                    files[os.path.relpath(full, base)] = full   # src26 comes last and wins
    return list(files.values())


def main():
    shutil.rmtree(os.path.join(OUT, 'classes'), ignore_errors=True)
    os.makedirs(os.path.join(OUT, 'classes'))
    cp = [f'{META}/versions/{VER}/{VER}.jar'] + libraries() + fabric_api_jars()
    argfile = os.path.join(OUT, 'sources.txt')
    with open(argfile, 'w', encoding='utf-8') as f:
        f.write('\n'.join('"' + s.replace('\\', '/') + '"' for s in sources()))
    r = subprocess.run([JAVA + 'javac', '-encoding', 'UTF-8', '--release', '25', '-proc:none', '-nowarn', '-Xmaxerrs', '400',
                        '-d', os.path.join(OUT, 'classes'), '-cp', os.pathsep.join(cp), '@' + argfile])
    if r.returncode != 0:
        sys.exit(r.returncode)
    jar = os.path.join(OUT, 'legitclient-1.0.0+26.1.2.jar')
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, fs in os.walk(os.path.join(OUT, 'classes')):
            for f in fs:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, os.path.join(OUT, 'classes')).replace(os.sep, '/'))
        for base in (os.path.join(HERE, 'src', 'main', 'resources'), os.path.join(HERE, 'src', 'client', 'resources')):
            for root, _, fs in os.walk(base):
                for f in fs:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, base).replace(os.sep, '/')
                    if rel == 'fabric.mod.json':
                        m = json.load(open(full, encoding='utf-8'))
                        m['version'] = '1.0.0+26.1.2'
                        m['depends']['minecraft'] = '~26.1.2'
                        m['depends']['java'] = '>=25'
                        z.writestr(rel, json.dumps(m, indent=1))
                    else:
                        z.write(full, rel)
    print('built', jar)


if __name__ == '__main__':
    main()
