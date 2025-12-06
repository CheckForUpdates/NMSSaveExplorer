# NMSSaveExplorer

Desktop save editor for _No Man's Sky_ built with JavaFX. It can discover your `.hg` saves, decode them, and provide both a friendly inventory editor and a raw JSON view for deeper tweaks.

## Features

- Auto-detects save locations on Windows, macOS, and Linux/Steam (including Proton) via `SaveGameLocator`.
- Decode/re-encode `.hg` save files using LZ4, preserving the original header structure.
- Raw JSON explorer with a tree view, inline editor (RichTextFX), and mapping.json key translation for readable fields.
- Inventory editors for exosuit, ship (cargo + tech), and multitool (main + tech) with drag-and-drop slot rearranging, stack editing, and iconized item lookup from bundled game data.
- Currency and expedition state editing from within the JSON tree (units, nanites, quicksilver, expedition milestones).
- Save/Save As and JSON export flows, plus quick “most recent save” loading.
- Right click tabs to open them in separate windows.

## Requirements

- Java 21 (Gradle toolchain is configured to download it if needed)
- Gradle wrapper included; no global install required.

## Build & Run

```bash
# Run the app
./gradlew run

# Build a fat JAR
./gradlew shadowJar
# Output: build/libs/NMSSaveExplorer-all.jar

# (Optional) Create a trimmed runtime image
./gradlew runtime
```

## Usage

1. Launch the app; it will list detected saves. Browse manually if needed.
2. Open Raw JSON to inspect/edit values with friendly field names; changes are tracked per node.
3. Open Inventories to manage exosuit/ship/multitool slots with drag-and-drop and stack editing.
4. Save Changes to write back to the `.hg` file (re-encoded with the original header). You can also Export JSON for backups.

## Project Structure

- `src/main/java/com/nmssaveexplorer/` — core app, save codec, discovery utilities, controllers.
- `src/main/resources/` — FXML layouts, styles, mapping.json, native-image configs, and game data/icons used for lookups.
- `scripts/` — helper scripts (e.g., localization map extraction).

## Notes

- The `.gitignore` excludes common build/IDE clutter; the Gradle wrapper JAR remains tracked.
- If you add new assets (icons/data) for items, place them under `src/main/resources` so the registries can load them.
- Always back up saves before editing.
