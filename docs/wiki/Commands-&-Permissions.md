# Commands & Permissions Reference

GoosBoards provides an administrative command suite for creating, managing, debugging, and testing boards directly in-game.

---

## 🔑 Permissions

| Permission Node | Description | Default |
| :--- | :--- | :--- |
| **`goosboards.admin`** | Grants full access to all GoosBoards commands and selection tools. | OP |
| **`interactiveboard.admin`** | Legacy permission alias for backwards compatibility. | OP |

---

## ⌨️ Command Aliases

All commands can be executed using any of the following aliases:
* `/goosboard`
* `/gb` *(recommended)*
* `/goosboards`
* `/board`
* `/boards`
* `/ib`
* `/interactiveboard`

---

## 📜 Subcommands Reference

### Management & Creation

#### `/gb select`
Gives you the **Interactive Board Selection Tool** (a golden hoe).
* **Left-Click a block**: Sets Corner 1 (Top-Left anchor).
* **Right-Click a block**: Sets Corner 2 (Bottom-Right anchor).

#### `/gb create <name> [width] [height]`
Creates a new board display.
* If a selection wand area is active, dimensions and facing directions are computed automatically from the selection.
* Alternatively, specify explicit block dimensions `[width] [height]`.

#### `/gb cancel`
Clears any currently active corner selections made with the selection wand.

#### `/gb delete <name>`
Deletes the specified board display rig from the world and unregisters its virtual entities.

#### `/gb list`
Lists all currently registered boards, their active display planes, and viewer counts.

---

### Operations & Diagnostics

#### `/gb reload`
Performs an **atomic transactional reload** of all configurations and board YAML files:
* Reads and validates all YAML files.
* If any file has a syntax error, the transaction is safely rolled back to the previous working state without crashing or unloading active boards.
* Refreshes images and palette quantization caches.

#### `/gb reset <board>`
Forces an immediate redraw of the board and resets all active viewer scenes back to their default scene.

#### `/gb showcase`
Spawns the bundled interactive showcase demonstration board directly in front of the executing player.

#### `/gb teleport <board>` *(Alias: `/gb tp`)*
Teleports the player directly in front of the specified board's facing display plane.

#### `/gb coordinates <board>` *(Alias: `/gb coords`)*
Displays the exact world position, facing direction, width, height, and bounding box of the specified board.

#### `/gb name <board>`
Inspects or displays the internal name identifier of a board at your crosshair.

#### `/gb fonts`
Lists all TrueType fonts installed on the host system that can be used in `text` components.

---

### Debugging & Automation

#### `/gb debug`
Toggles verbose diagnostic logging in the server console for raycasts, packet routing, and player proximity.

#### `/gb debugmode`
Toggles the in-game debug overlay, drawing red outline boxes around UI component bounding boxes on the canvas.

#### `/gb trigger <board> <player> <action>`
Programmatically dispatches an action on behalf of a player against the specified board. Useful for integrations with external script engines, quest plugins, or console triggers.
