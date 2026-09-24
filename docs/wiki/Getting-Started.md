# Getting Started with GoosBoards

This guide walks you through system requirements, dependency setup, plugin installation, and creating your very first interactive board.

---

## 📋 System Requirements

| Requirement | Minimum Supported | Recommended |
| :--- | :--- | :--- |
| **Java Runtime** | Java 21 (64-bit) | Java 21 or Java 25 LTS |
| **Server Software** | Paper 1.20.4 | Paper or Folia 1.20.4 – 1.21.x |
| **Target Clients** | Minecraft 1.19.4 (Protocol ≥ 762) | Minecraft 1.20.4+ |

> [!IMPORTANT]
> **Folia Compatibility**: GoosBoards natively detects Folia at runtime (`io.papermc.paper.threadedregions.RegionizedServer`). It schedules player packet transmissions and regional lookups onto their respective region threads automatically.

---

## 📦 Dependencies

GoosBoards integrates with the following plugins:

### 1. PacketEvents (Required)
* GoosBoards utilizes [PacketEvents 2.x](https://github.com/retrooper/packetevents) for low-level protocol virtualization and packet interception.
* **Installation**: Download the latest PacketEvents 2.x plugin `.jar` and place it into your server's `plugins/` directory.
* *Note: GoosBoards does not shade PacketEvents; it expects PacketEvents to be installed as an independent server plugin.*

### 2. PlaceholderAPI (Optional)
* Supports all PlaceholderAPI placeholders in `text`, `pixel-text`, commands, and chat messages.
* When installed, placeholders are updated on configurable cadences with automatic debouncing.

### 3. Vault (Optional)
* Enables economic transactions on interactive elements (e.g. charging players in-game currency when clicking buttons).
* Supported with any Vault-compatible economy plugin (EssentialsX, VaultUnlocked, etc.).

---

## 🚀 Installation Guide

1. Stop your Minecraft server.
2. Download the latest `GoosBoards.jar`.
3. Download the latest `packetevents` plugin jar.
4. Place both `.jar` files into your server's `plugins/` folder.
5. If using placeholders or economy features, ensure `PlaceholderAPI` and `Vault` are also present.
6. Start your server.
7. Verify that the plugin loaded properly by typing `/gb list` in the console.

> [!TIP]
> **Linux / Headless Environments**: If your server runs on a minimal Linux VPS without a desktop environment, ensure font libraries are installed so Java AWT can render text:
> ```bash
> # Debian / Ubuntu:
> sudo apt-get install fontconfig libfreetype6
> 
> # RHEL / CentOS:
> sudo yum install fontconfig freetype
> ```

---

## 🎮 Creating Your First Board

You can create a board either in-game using the visual selection tool or by defining a YAML configuration file.

### Method A: In-Game Selection Wand

1. Hold an empty hand and run:
   ```text
   /gb select
   ```
2. You will receive an **Interactive Board Selection Tool** (a golden hoe).
3. **Left-Click** a block in the world to select the **Top-Left** anchor corner.
4. **Right-Click** a block on the same plane to select the **Bottom-Right** corner.
5. Once both corners are selected, run:
   ```text
   /gb create my_first_board
   ```
6. The plugin will calculate the dimensions in blocks, generate a configuration file at `plugins/GoosBoards/boards/my_first_board.yml`, and initialize the display.

### Method B: Testing the Bundled Showcase Board

To immediately see what GoosBoards can do, spawn the pre-configured showcase board:
1. Stand facing a wall where you want the display to appear.
2. Run:
   ```text
   /gb showcase
   ```
3. A 4×3 block interactive demonstration board will appear directly in front of you, featuring buttons, image rendering, animated GIFs, player skin heads, and sound effects.

---

## 📁 Directory Structure

After startup, GoosBoards creates the following directory structure inside `plugins/GoosBoards/`:

```text
plugins/GoosBoards/
├── config.yml           # Global plugin settings, network tunables, and cache options
├── messages.yml         # Localized chat prefixes and command feedback
├── boards/              # Individual board configuration YAML files
│   └── showcase.yml     # Bundled interactive reference board
├── images/              # Local image assets (PNG, JPEG) referenced by boards
└── media-cache/         # Cached player avatars and downloaded remote images
```
