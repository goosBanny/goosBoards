# Welcome to GoosBoards

**GoosBoards** is a high-performance, zero-entity virtual display engine for **Paper** and **Folia** (1.20.4+). It allows server operators and developers to build rich, interactive graphical boards, menus, dashboards, and HUDs directly inside the Minecraft world using map packets.

Unlike traditional plugins that spawn physical item frames, maps, or invisible armor stands into world chunks, GoosBoards creates **pure client-side virtual entities** through network packets. Zero entities exist on the server, resulting in no world chunk modification, no entity tick lag, and zero chance of orphaned entity corruption.

---

## ⚡ Key Architectural Highlights

* **Pure Virtual Display Grid**: All item frames and maps are synthesized strictly via Netty packet pipelines. Nothing is ever saved to chunk region files or world disk storage.
* **Folia Regionized Multithreading**: Built from the ground up for Folia's multi-region threading model. State queries respect regional threading, while packet serialization and raycasting run on isolated asynchronous worker pools.
* **Zero-Allocation Hot Loops**: Per-tick rendering routines avoid heap object allocations, boxing, and stream overhead. Coordinates are bit-packed, collections are backed by primitive FastUtil maps, and calculations reuse pre-allocated vector caches.
* **Sub-Millisecond Color Quantization**: Fast direct-table RGB565 lookups map 24-bit ARGB colors to the 256-color Minecraft map palette in ~15 microseconds per 128×128 tile.
* **Tile-Isolated Floyd-Steinberg Dithering**: Diffusion errors are clamped at 128×128 tile boundaries, preventing visual artifact bleeding across adjacent map displays.
* **xxHash64 Dirty-Tile Diffing**: High-speed 64-bit hashing identifies exactly which 128×128 tiles have changed between frames. Unchanged tiles are never sent across the network.
* **Netty Channel Flush Batching**: Accumulates all dirty tile packets into a single Netty pipeline write batch, issuing a single TCP flush per player frame to prevent network packet spam.
* **Broad-Phase Spatial Gating**: Raycasting checks are gated through a 32-block chunk-bucket spatial index, keeping CPU usage near zero even with 50+ nearby players.
* **Anti-SSRF Defense**: Remote image and GIF downloads are strictly filtered via pre-resolved DNS inspection, blocking internal loopbacks (RFC1918) and cloud metadata services (e.g., AWS/GCP/Azure link-local endpoints).

---

## 📖 Wiki Navigation

Explore the comprehensive guides below to learn how to install, configure, and customize GoosBoards:

| Section | Description |
| :--- | :--- |
| [🚀 Getting Started](Getting-Started.md) | Server requirements, dependencies, installation steps, and your first board. |
| [⚙️ Configuration Guide](Configuration-Guide.md) | Complete line-by-line documentation of `config.yml` with a reference configuration. |
| [📐 Board Creation & Syntax](Board-Creation-&-Syntax.md) | Comprehensive specification of board YAML files, display rigs, and view groupings. |
| [🧩 UI Components Reference](UI-Components-Reference.md) | Documentation for all layout components: text, images, animated GIFs, buttons, scroll panes, etc. |
| [🖱️ Actions & Interactions](Actions-&-Interactions.md) | Click detection, raycasting, sound triggers, scene switches, and Vault economy protection. |
| [🔄 Placeholders & Dynamic Content](Placeholders-&-Dynamic-Content.md) | PlaceholderAPI integration, internal tokens, update intervals, and render cache deduplication. |
| [💻 Commands & Permissions](Commands-&-Permissions.md) | Full command reference, selection wand guide, and administrative permissions. |
| [🏗️ Architecture & Performance](Architecture-&-Performance-Guide.md) | Deep dive into virtual entity allocation, Folia concurrency, dirty diffing, and security. |
| [🗄️ Database & Persistence](Database-&-Persistence-Guide.md) | Setting up embedded H2 or external MySQL/MariaDB for persistent player scene state. |
| [❓ Troubleshooting & FAQ](Troubleshooting-&-FAQ.md) | Common setup hurdles, headless Java font installation, and protocol details. |

---

## 📄 License & Terms

GoosBoards is released under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)** license.
* **Free to Use**: You may freely use GoosBoards on your server or network.
* **Public Forks Only**: If you fork or modify the codebase, your changes must be published openly under the exact same license terms.
* **No Commercial Resale**: You are strictly prohibited from selling, reselling, charging for custom builds, or paywalling access to the plugin or derivative works.
