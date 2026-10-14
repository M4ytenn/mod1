"""Downloads the Vista and Moonlight jars that the add-on compiles against into libs/ (NeoForge, Minecraft 1.21.1).

    python3 tools/fetch_libs.py                 # newest releases
    python3 tools/fetch_libs.py 1.21.1-5.5.7    # a specific Vista version (Moonlight stays the newest)

You can also skip this and copy the two jars from your own mods folder into libs/.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API = 'https://api.modrinth.com/v2'
LIBS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'libs'))
HEADERS = {'User-Agent': 'subsonar-vista-build (github.com/M4ytenn/mod1)'}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS)) as response:
        return response.read()


def fetch(project, version=None):
    query = urllib.parse.urlencode({'loaders': '["neoforge"]', 'game_versions': '["1.21.1"]'})
    releases = json.loads(get(f'{API}/project/{project}/version?{query}'))  # newest first
    if version:
        releases = [r for r in releases if version in r['version_number']]
    if not releases:
        sys.exit(f'No NeoForge 1.21.1 release of {project}' + (f' matching {version}' if version else ''))
    release = releases[0]
    file = next((f for f in release['files'] if f['primary']), release['files'][0])
    with open(os.path.join(LIBS, file['filename']), 'wb') as out:
        out.write(get(file['url']))
    print(f"{project}: {release['version_number']} -> libs/{file['filename']}")


if __name__ == '__main__':
    os.makedirs(LIBS, exist_ok=True)
    fetch('vista_tv', sys.argv[1] if len(sys.argv) > 1 else None)
    fetch('moonlight')
