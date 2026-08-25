<div align="center">

<img src="image.png" alt="HaoHan Backpack banner" width="100%">

# HaoHan Backpack (BetterStorage)

A custom persistent backpack GUI plugin for HaoHan SMP, featuring SQLite storage, font/glyph GUI customization, and HaoHanItemCore integration.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-Database-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.sqlite.org/)

Language: [Tiếng Việt](README.md) | English

</div>

## Overview

HaoHan Backpack is a Minecraft plugin built for HaoHan SMP. It provides a custom persistent backpack system (BetterStorage) featuring a 54-slot (6-row) GUI, per-backpack UUID item storage powered by SQLite, death item protection, and strict anti-duplication mechanisms.

## Tech Stack

| Toolkit | Role |
| --- | --- |
| Paper API | Main server API framework for plugin development (1.21+). |
| Purpur | Recommended server environment for deployment. |
| Java 21 | Primary programming language and runtime. |
| Gradle | Dependency management and `.jar` build pipeline. |
| SQLite JDBC | SQLite database backend for persistent UUID-based storage. |
| HaoHanItemCore | Optional dependency (softdepend) for custom item handling. |

## Requirements

- Minecraft server running Paper or Purpur (version 1.21 or newer).
- Java 21 or newer.
- No separate Gradle installation is required; the Gradle Wrapper is included.
- Optional: `HaoHanItemCore` plugin installed.
- Client resource pack for custom backpack item model (`haohan:backpack`) and custom font/glyphs (`haohan:gui`).

## Installation

1. Build or download the plugin `.jar` file.
2. Copy the `.jar` file into the server `plugins/` directory.
3. Install the client resource pack for custom textures, fonts, and models.
4. Restart the server.

On first startup, the plugin generates its configuration and database at `plugins/HaoHanBackpack/config.yml` and `plugins/HaoHanBackpack/backpacks.db`.

## Build From Source

Run this command in the plugin project root:

```bash
.\gradlew clean build
```

The built `.jar` file will be generated in the `build/libs/` directory.

For a faster build without running unit tests:

```bash
.\gradlew clean assemble
```

## Commands

Main command: `/hhbp` (Aliases: `/backpack`, `/bp`, `/balo`).

| Command | Description | Permission |
| --- | --- | --- |
| `/hhbp help` | Displays command usage and help menu. | Everyone |
| `/hhbp give <player> <amount>` | Gives expedition backpacks to a player. | `haohanbackpack.give` |
| `/hhbp list` | Displays list of personal saved backpack IDs. | Default |
| `/hhbp info <player\|UUID>` | Inspects backpack database status by player or UUID. | `haohanbackpack.admin` |
| `/hhbp delete <player\|UUID>` | Deletes backpack data from the database. | `haohanbackpack.admin` |
| `/hhbp reload` | Reloads `config.yml` configuration. | `haohanbackpack.admin` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `haohanbackpack.use` | All players | Allows players to open and use expedition backpacks. |
| `haohanbackpack.give` | OP | Allows granting backpacks using `/hhbp give`. |
| `haohanbackpack.admin` | OP | Allows administrative actions (info, delete, reload). |

## Configuration & Features

The main configuration file is located at `plugins/HaoHanBackpack/config.yml`:

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

### Security & Data Safety Features

- **Unique UUID Identification**: Each backpack item is assigned a unique UUID in its PDC (`PersistentDataContainer`). Backpacks are unstackable to guarantee no item data overwrites.
- **Container Lock**: `block-backpack-in-containers: true` prevents players from opening a backpack while interacting with chests, shulker boxes, or containers.
- **Nested Backpack Prevention**: `allow-backpacks-inside-backpacks: false` prevents nesting backpacks inside other backpacks.
- **Keep On Death**: `keep-backpacks-after-death: true` retains backpacks in the player's inventory upon death to prevent loss or drops.
- **Hopper Integration**: Safely manages backpack movement through Hoppers.

## Operational Notes

- Backpack item contents are saved directly into the SQLite database file `backpacks.db`. Ensure regular backups of this file.
- Avoid editing `backpacks.db` directly while the server is running.
- Apply configuration updates from `config.yml` at runtime using `/hhbp reload`.
