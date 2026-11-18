# Cyclic Creative Tabs (Fabric)

Fabric port of [CyclicCreativeTab](https://github.com/icebise/CyclicCreativeTab) by **bisya**.

The creative inventory normally stops at the first and the last page. This mod makes the
page navigation loop: pressing `>` on the last page jumps to the first one and pressing `<`
on the first page jumps to the last one, so you never have to click through every page again.

![Mod icon](icon-256.png)

## What it does

* The two page buttons are always active and wrap around in both directions.
* They sit where NeoForge puts them - above the inventory, `20x20` pixels, at the left and
  right edge of the window - instead of the small Fabric API buttons.
* A `current / total` page indicator is drawn above the inventory.
* Tabs are grouped into pages of ten: five above the inventory and five below it, exactly
  like NeoForge does it.

## Fabric API and Mod Menu

The mod works with or without [Fabric API](https://modrinth.com/mod/fabric-api):

* **Fabric API installed** - Fabric keeps handling the pages and hiding the tabs that are
  not on the current page. The mod only replaces its buttons with cyclic ones and calls
  Fabric's own page switching code, so both mods stay in sync.
* **Fabric API absent** - the mod pages the tabs itself, including moving the tabs that are
  not on the displayed page off screen.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. The mod appears there with its
icon, both authors, the project links and the client badge. There is nothing to configure.

## Supported versions

Every jar covers a whole range of Minecraft versions, so pick the file that matches your
game:

| Jar | Minecraft | Java |
| --- | --- | --- |
| `CyclicCreativeTabs-fabric-1.0.0+1.21.1-1.21.8.jar` | 1.21.1 - 1.21.8 | 21+ |
| `CyclicCreativeTabs-fabric-1.0.0+1.21.9-1.21.11.jar` | 1.21.9 - 1.21.11 | 21+ |
| `CyclicCreativeTabs-fabric-1.0.0+26.1-26.3.jar` | 26.1 - 26.3 | 25+ |

Only Fabric Loader is required. The mod is client side only.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Drop the jar for your version into the `mods` folder.
3. Start the game. Fabric API and Mod Menu are optional, not required.

## Building

Each version range is its own Gradle project:

```bash
cd 1.21.1-1.21.8      # or 1.21.9-1.21.11, or 26.1-26.3
./gradlew build
```

The finished jar is written to `build/libs`. The same sources can be recompiled against any
other version of the range with `-Pmc=<version>`, which is what the verification scripts use.

## Verification

`verify/` contains a development-only harness. It builds the release jars, starts a real
client for the oldest and the newest version of every range, creates a world, switches to
creative mode, opens the creative inventory with twelve extra tabs, presses the paging
buttons, checks the geometry of every tab and writes a report with screenshots next to it.

```powershell
powershell -ExecutionPolicy Bypass -File verify/run-smoke.ps1
```

Every release is checked this way on the oldest and the newest Minecraft version of its range,
with and without Fabric API, and again with Fabric API plus Mod Menu installed:

| Minecraft | Fabric API | Mod Menu |
| --- | --- | --- |
| 1.21.1, 1.21.8 | no | - |
| 1.21.1, 1.21.8 | yes | - |
| 1.21.1, 1.21.8 | yes | yes |
| 1.21.9, 1.21.11 | no | - |
| 1.21.9, 1.21.11 | yes | - |
| 1.21.9, 1.21.11 | yes | yes |
| 26.1, 26.3 | no | - |
| 26.1, 26.3 | yes | - |
| 26.1, 26.3 | yes | yes |

`verify/compile-matrix.ps1` recompiles every supported Minecraft version from the same
sources to catch API changes that a grouped jar would otherwise miss.

`package.ps1` builds the three release jars, copies them to `dist/` and writes
`dist/SHA256SUMS.txt`. The jars in `dist/` are the files to attach to a GitHub release.

## Credits

* **bisya** - original NeoForge mod
* **Mr_Lan123** - Fabric port and multi version support

## License

MIT, see [LICENSE](LICENSE). The original NeoForge mod is MIT licensed by icebise as well.
