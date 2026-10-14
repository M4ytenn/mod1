# Sub Sonar: Vista Link

Add-on for **NeoForge 1.21.1** that shows the [Sub Sonar](#requirements) display on [Vista](https://www.curseforge.com/minecraft/mc-mods/vista) televisions.

## Use

1. Hold a **hollow cassette** (Vista) and right-click a **sonar console**. The cassette links to the console, the same way it links to a viewfinder.
2. Put the cassette in a **TV**. The TV shows the sonar picture: echoes, contacts, your own silhouette, the sweep, glitches and the power-on animation, with Vista's CRT look on top.

The TV shows exactly what the sonar screen would: same mode, view, range and alarms, and your own zoom, gain, echo hold, heading-up and chart settings from the sonar screen. On a wide or tall TV the round scope keeps its shape.

## How it works

* The console becomes a Vista broadcast source: it gets a saved feed id and registers with Vista like a viewfinder. Vista then sends the console's chunk to whoever watches the TV, even far away.
* Sub Sonar sends scans only to players who have the console's screen open. The add-on also sends them to players watching that console on a TV near them. Each client says once a second which TV it is looking at, and the server checks that the TV is real, close to the player and really shows a sonar console.
* With `scanOnlyWhenViewed` on (Sub Sonar's default), a console watched on a TV counts as viewed, so it keeps scanning.

## Limits

* **One sonar per player at a time.** Sub Sonar's client keeps data for one console at a time. If TVs around you show two different consoles, one of them shows the sonar and the other shows static. While you have a sonar screen open, TVs showing a different console show static too.
* The console has to be loaded and ticking to scan. A TV on the same ship is fine; for far-away consoles, turn on Vista's chunk loading for watched feeds (`view_finder.load_viewed_chunks` in Vista's common config).
* Needs to be installed on both the server and the clients.

## Requirements

* NeoForge 21.1 for Minecraft 1.21.1
* Sub Sonar 1.8.0 or newer
* Vista 1.21.1-5.5.2 or newer (and Moonlight, which Vista needs)

The add-on uses Sub Sonar's and Vista's own classes, not an official API, so a big update to either can break it. Built and checked against Sub Sonar 1.8.0 and the newest Vista on Modrinth at build time; the CI log names the Vista version.

## Building

Requires JDK 21.

```
python3 tools/fetch_libs.py   # downloads Vista and Moonlight into libs/ (or copy them from your mods folder)
./gradlew build               # jar ends up in build/libs/
```

Sub Sonar itself is not needed to build: `src/stubs` holds compile-only copies of the few Sub Sonar signatures the add-on calls (see `src/stubs/java/com/subsonar/README.txt`). They are not packaged; in game the real Sub Sonar classes are used.
