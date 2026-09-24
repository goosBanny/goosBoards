# 🤩 GoosBoards

<p align="center">
  <b>The High-Performance, Zero-Entity Virtual Display Engine for Paper & Folia (1.20.4+)</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21%20%7C%2025%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21/25" />
  <img src="https://img.shields.io/badge/Platform-Paper%20%7C%20Folia-1976D2?style=for-the-badge&logo=minecraft&logoColor=white" alt="Paper and Folia" />
  <img src="https://img.shields.io/badge/Protocol-1.19.4%20--%201.21.x-52B152?style=for-the-badge" alt="Protocol Version" />
  <img src="https://img.shields.io/badge/Dependencies-PacketEvents%202.x-8A2BE2?style=for-the-badge" alt="PacketEvents" />
  <img src="https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-FF4081?style=for-the-badge" alt="PolyForm Noncommercial 1.0.0" />
</p>

---

## ⚡ What is GoosBoards?

**GoosBoards** is an enterprise-grade virtual display engine designed for modern Minecraft servers. It allows you to build interactive graphical menus, animated dashboards, real-time leaderboards, and store displays directly onto surfaces in the game world using virtual map packets.

Unlike traditional plugins that place real item frames and filled maps into world chunks, GoosBoards creates **pure client-side virtual entities** via Netty network packets:
* **Zero World Modifications**: No item frames or armor stands ever spawn in your chunks.
* **No Corruption**: No risk of orphaned or duplicated entities if the server crashes.
* **Folia-Native**: Thread-safe region scheduling with asynchronous rendering and packet dispatching.

---

## ✨ Key Features

* 🚀 **Zero-Allocation Hot Loops**: Ultra-optimized per-tick rendering routines with bit-packed coordinates and primitive collections.
* 🎨 **Sub-Millisecond Color Quantization**: Fast RGB565 lookup tables convert 24-bit ARGB colors to the 256-color Minecraft palette in **~15 microseconds per 128×128 tile**.
* 🖼️ **Tile-Isolated Dithering**: Floyd-Steinberg error diffusion is clamped to $128 \times 128$ tile borders, preventing pixel noise from bleeding across adjacent maps.
* ⚡ **xxHash64 Dirty-Tile Diffing**: Calculates 64-bit checksums per tile; only modified tiles produce network packets.
* 📦 **Netty Channel Flush Batching**: Accumulates all dirty tile packets into a single TCP write batch with one flush per frame, eliminating network spam.
* 🎯 **Long-Range Input Interception**: Detects player clicks and hover interactions up to **32 blocks away** using server-authoritative raycasting.
* 🛡️ **Anti-SSRF Firewall**: Pre-resolved DNS inspection blocks remote image downloads to private networks (RFC1918), localhost, and cloud metadata endpoints.
* 🎬 **Animated GIF Support**: Render GIFs with automatic looping and frame-rate caps.
* 👤 **Player Skin Avatars**: Fetches 2D player avatars with outer helmet/hat layer rendering.
* 💰 **Vault Economy Guard**: Atomic striped locking and idempotency tokens eliminate click-macro double-spending exploits.
* 📝 **MiniMessage & TrueType Fonts**: Native Adventure gradients, bold/italic styles, and system TrueType font support.

---

## 🚀 Quick Start

### 1. Requirements
* **Server**: Paper or Folia **1.20.4 – 1.21.x**
* **Java**: **Java 21** or **Java 25 LTS**
* **Dependency**: [PacketEvents 2.x](https://github.com/retrooper/packetevents) *(must be installed in `plugins/`)*
* **Soft Dependencies**: [PlaceholderAPI](https://placeholderapi.com/) (dynamic text), [Vault](https://www.spigotmc.org/resources/vault.34315/) (economy buttons)

### 2. Installation
1. Install **PacketEvents 2.x** into your server's `plugins/` directory.
2. Place `GoosBoards.jar` into your `plugins/` directory.
3. Restart your server.

### 3. Try the Showcase Board
Stand facing a wall and run:
```text
/gb showcase
```
An interactive 4×3 block demonstration board will appear with buttons, GIFs, images, and audio!

### 4. Create Your Own Board
1. Run `/gb select` to receive the selection wand.
2. Left-click the **Top-Left** block of your display area.
3. Right-click the **Bottom-Right** block on the same plane.
4. Run:
   ```text
   /gb create my_board
   ```
5. Edit your new board in `plugins/GoosBoards/boards/my_board.yml`.

---

## 💻 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/gb select` | Gives the selection wand for defining board planes | `goosboards.admin` |
| `/gb create <name>` | Creates a board from your wand selection | `goosboards.admin` |
| `/gb delete <name>` | Deletes an existing board display rig | `goosboards.admin` |
| `/gb list` | Lists all active boards and viewers | `goosboards.admin` |
| `/gb reload` | Atomically reloads configurations with rollback on error | `goosboards.admin` |
| `/gb reset <board>` | Forces an immediate redraw and resets viewer scenes | `goosboards.admin` |
| `/gb showcase` | Spawns the interactive reference board | `goosboards.admin` |
| `/gb tp <board>` | Teleports you directly to a board display | `goosboards.admin` |
| `/gb coords <board>` | Inspects world coordinates and dimensions | `goosboards.admin` |
| `/gb fonts` | Lists system TrueType fonts available for text components | `goosboards.admin` |
| `/gb debug` | Toggles console diagnostic logging | `goosboards.admin` |
| `/gb debugmode` | Toggles on-screen red bounding box outlines | `goosboards.admin` |

---

## 📖 Comprehensive Documentation (Wiki)

Full guides, configuration references, and component schemas are available on the **[GoosBoards GitHub Wiki](https://github.com/goosBanny/goosBoards/wiki)**:

* [🏠 Wiki Home](https://github.com/goosBanny/goosBoards/wiki) — Overview and architecture highlights.
* [🚀 Getting Started](https://github.com/goosBanny/goosBoards/wiki/Getting-Started) — Prerequisites, installation, and headless Linux setup.
* [⚙️ Configuration Guide](https://github.com/goosBanny/goosBoards/wiki/Configuration-Guide) — Complete line-by-line documentation of `config.yml` with reference copy.
* [📐 Board Creation & Syntax](https://github.com/goosBanny/goosBoards/wiki/Board-Creation-&-Syntax) — Complete YAML schema, display rigs, and view groupings.
* [🧩 UI Components Reference](https://github.com/goosBanny/goosBoards/wiki/UI-Components-Reference) — All components: buttons, text, pixel-text, images, GIFs, avatars, scroll panes.
* [🖱️ Actions & Interactions](https://github.com/goosBanny/goosBoards/wiki/Actions-&-Interactions) — Click detection, commands, sounds, scene transitions, and Vault economy.
* [🔄 Placeholders & Dynamic Content](https://github.com/goosBanny/goosBoards/wiki/Placeholders-&-Dynamic-Content) — PlaceholderAPI tokens, update cadences, and render cache deduplication.
* [💻 Commands & Permissions](https://github.com/goosBanny/goosBoards/wiki/Commands-&-Permissions) — Detailed subcommand guide and permissions.
* [🏗️ Architecture & Performance](https://github.com/goosBanny/goosBoards/wiki/Architecture-&-Performance-Guide) — Deep dive into virtual entity allocation, Folia concurrency, and xxHash64 diffing.
* [🗄️ Database & Persistence](https://github.com/goosBanny/goosBoards/wiki/Database-&-Persistence-Guide) — Embedded H2 vs MySQL/MariaDB with HikariCP.
* [❓ Troubleshooting & FAQ](https://github.com/goosBanny/goosBoards/wiki/Troubleshooting-&-FAQ) — Frequently asked questions and common fixes.

---

## 🔨 Building from Source

GoosBoards uses Gradle with Kotlin DSL:

```bash
# Clone the repository
git clone https://github.com/your-username/GoosBoards.git
cd GoosBoards

# Build the shadowed plugin jar
./gradlew shadowJar

# Run the test suite
./gradlew test
```

The compiled plugin will be located in `build/libs/GoosBoards-<version>.jar`.

---

## 📄 License & Terms

GoosBoards is licensed under the **[PolyForm Noncommercial License 1.0.0](LICENSE)**.

* ✅ **Noncommercial Use Permitted**: You may run, modify, and copy the software for personal use, hobbies, education, and testing.
* ❌ **Commercial Purposes Prohibited**: Commercial purposes (monetary compensation, commercial advantage, selling access as a service, or selling/licensing products that include the software) are strictly prohibited.
* 📋 **Notices Required**: Anyone who receives a copy of any part of this software must also receive a copy of the license.
