<div align="center">

<img src="image.png" alt="HaoHan Backpack banner" width="100%">

# HaoHan Backpack (BetterStorage)

Custom personal backpack plugin for HaoHan SMP, featuring SQLite storage, font/glyph GUI customization, and HaoHanItemCore integration.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-Database-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.sqlite.org/)

Language: [Tiếng Việt](README.md) | English

</div>

## Overview

HaoHan Backpack is a Minecraft plugin for HaoHan SMP. The plugin provides a custom personal backpack system (BetterStorage) with a 54-slot (6 rows) GUI interface, persistent item data storage via SQLite with unique UUIDs per backpack, death protection, and complete item duplication prevention.

## Tech Stack

| Toolkit | Role |
| --- | --- |
| Paper API | Main API framework for server plugin development (1.21+). |
| Purpur | Recommended server runtime environment for deployment. |
| Java 21 | Main programming language and runtime environment. |
| Gradle | Dependency management and `.jar` file build pipeline. |
| SQLite JDBC | SQLite database backend for storing UUID-based backpack item data. |
| HaoHanItemCore | Optional dependency plugin (softdepend) for custom items. |

## Requirements

- Minecraft server running Paper or Purpur (version 1.21 or newer).
- Java 21 or newer.
- No separate Gradle installation required; project includes Gradle Wrapper.
- Installation of `HaoHanItemCore` (optional but recommended).
- Bundled resource pack for custom backpack model (`haohan:backpack`) and custom font/glyph GUI (`haohan:gui`).

## Installation

1. Build or download the plugin `.jar` file.
2. Copy the `.jar` file to the server's `plugins/` directory.
3. Install the client resource pack to display custom GUI textures and backpack models.
4. Restart the server.

After the first run, the plugin will generate configuration and database files at `plugins/HaoHanBackpack/config.yml` and `plugins/HaoHanBackpack/backpacks.db`.

## Build From Source

Run the following command in the plugin project root directory:

```bash
.\gradlew clean build
```

The built `.jar` file is located in the `build/libs/` directory.

For a quick build without running tests:

```bash
.\gradlew clean assemble
```

## Commands

Main plugin command is `/hhbp` (Aliases: `/backpack`, `/bp`, `/balo`).

| Command | Description | Permission |
| --- | --- | --- |
| `/hhbp help` | Displays the command guide list. | All players |
| `/hhbp give <player> <amount>` | Gives expedition backpacks to the specified player. | `haohanbackpack.give` |
| `/hhbp list` | Displays own saved backpack IDs list. | Default |
| `/hhbp info <player\|UUID>` | Checks backpack data info by UUID or player name. | `haohanbackpack.admin` |
| `/hhbp delete <player\|UUID>` | Deletes backpack data in the database. | `haohanbackpack.admin` |
| `/hhbp reload` | Reloads `config.yml` configuration. | `haohanbackpack.admin` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `haohanbackpack.use` | All players | Allows players to own and open expedition backpacks. |
| `haohanbackpack.give` | OP | Allows using `/hhbp give` command to create backpacks. |
| `haohanbackpack.admin` | OP | Allows administrators to view info, delete data, and reload the plugin. |

## Configuration & Features

Main configuration file is located at `plugins/HaoHanBackpack/config.yml`:

```yaml
title: '&8Ba lô của %player%'
custom-gui:
  enabled: true
  font: 'haohan:gui'
  prefix: ''
  glyph: ''
rows: 6
backpack-item-name: '&b&lBa lô thám hiểm'
backpack-item-model: 'haohan:backpack'
backpack-item-lore:
  - '&7Chuột phải để mở ba lô cá nhân.'
  - '&8Dung lượng: 53 ô + 1 module'
database:
  file: backpacks.db
backpack-limit:
  enabled: false
  default: 1
blocked-materials: []
keep-backpacks-after-death: true
block-backpack-in-containers: true
allow-backpacks-inside-backpacks: false
hopper:
  enabled: true
backpack-collision:
  enabled: true
```

### Anti-Dupe Mechanisms & Data Safety

- **Unique UUID Identification**: Each generated backpack has a unique UUID attached to NBT/PDC (`PersistentDataContainer`). Backpacks are non-stackable to ensure no data loss.
- **Container Lock**: `block-backpack-in-containers: true` prevents players from opening a backpack while interacting inside chests, shulker boxes, or any other container.
- **Prevent Nested Backpacks**: `allow-backpacks-inside-backpacks: false` prevents players from placing one backpack inside another.
- **Protection Upon Death**: `keep-backpacks-after-death: true` automatically retains backpacks in inventory when a player dies, avoiding dropping or losing stored items.
- **Hopper Integration**: Safely manages backpack movement through Hoppers.

## Operational Notes

- Backpack data is saved directly to SQLite database `backpacks.db`. Please perform regular backups of this file.
- Do not directly edit `backpacks.db` while the server is running.
- When updating settings in `config.yml`, apply changes using `/hhbp reload`.
