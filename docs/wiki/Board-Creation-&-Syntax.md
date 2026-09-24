# Board Creation & YAML Syntax Specification

Every interactive board in GoosBoards is defined in its own YAML configuration file inside the `plugins/GoosBoards/boards/` directory (e.g. `plugins/GoosBoards/boards/hub.yml`).

---

## 📑 File Structure Overview

A board configuration contains three top-level sections:

```yaml
settings:
  # Global behavior, dithering, and conditional view groupings
  ...

displays:
  # Physical world coordinates, dimensions, and orientations
  ...

scenes:
  # The UI scenes containing visual, text, and interactive components
  ...
```

---

## 1. Board Settings (`settings`)

The `settings` section defines rendering behavior and view resolution for players approaching the board.

```yaml
settings:
  # Enables tile-isolated Floyd-Steinberg dithering for this specific board
  dithering: true

  # If true, resets a player's active scene back to the default scene
  # when they step outside the display's activation distance
  reset-on-radius-exit: true

  # Override global placeholder refresh ticks for this specific board
  placeholder-refresh-ticks: 20

  # Controls which default scene a player sees when entering the board area
  # Checked sequentially from top to bottom; the first matching condition wins
  view-grouping:
    group-staff:
      type: individual
      default-scene: staff-dashboard
      access-conditions:
        perm-check:
          type: permission
          permission: "server.staff"

    group-vip:
      type: individual
      default-scene: vip-lounge
      access-conditions:
        perm-check:
          type: permission
          permission: "rank.vip"

    group-default:
      type: individual
      default-scene: main-hub
```

### View Grouping Attributes
* **`type`**: Currently `individual` (each viewer tracks their own active scene and private UI state).
* **`default-scene`**: The scene ID displayed when a player enters range or resets.
* **`access-conditions`**: Optional permission gate. If `permission` is present, the player must hold the given permission node for this group to apply.

---

## 2. Physical Display Rigs (`displays`)

A single board definition can be mapped to one or more physical display locations in your server worlds.

```yaml
displays:
  spawn-wall:
    world: "world"
    # Dimensions in Minecraft blocks (1 block = 128x128 pixels)
    width: 4
    height: 3

    # Anchor coordinate of the top-left block of the display
    top-left:
      x: 100.0
      y: 72.0
      z: -250.0

    # Facing direction of the virtual item frames
    # Options: north, south, east, west, up, down
    direction: south

    # Downward orientation vector along the display's Y plane (usually "down")
    down: down

    # Network transmission radius in blocks
    distance: 32.0

    # Maximum interaction and click distance in blocks
    interaction-distance: 16.0

    # Frame properties
    invisible: true            # Hide item frame borders
    glow: false                # Use glowing item frames
```

### Key Properties

| Property | Type | Description |
| :--- | :--- | :--- |
| **`world`** | String | The exact Bukkit world name where the board is located. |
| **`width`** | Integer | Horizontal size in blocks. Total canvas width = `width * 128` pixels. |
| **`height`** | Integer | Vertical size in blocks. Total canvas height = `height * 128` pixels. |
| **`top-left`** | Section | The `x`, `y`, `z` world coordinates of the upper-left block. |
| **`direction`** | Enum | Facing direction of the display surface (`north`, `south`, `east`, `west`, `up`, `down`). |
| **`down`** | Enum | Which direction points downward relative to the display plane (`down`, `north`, `south`, etc.). |
| **`distance`** | Double | Outer sphere radius in blocks within which client packets are sent. |
| **`interaction-distance`** | Double | Maximum reach distance for player clicks and hover raycasts. |
| **`invisible`** | Boolean | Renders the virtual frames without the wooden background border. |
| **`glow`** | Boolean | Uses glow item frame packets (brighter at night). |

---

## 3. Scenes Definition (`scenes`)

The `scenes` section contains named scene definitions (e.g. `main-hub`, `server-shop`, `stats-view`). A scene is composed of an arbitrary number of UI components.

```yaml
scenes:
  main-hub:
    background-box:
      type: background
      position: 0 0
      size: max max
      color: "20 20 30 255"

    header-title:
      type: text
      position: 16 16
      text: "<gradient:#FFD700:#FFA500><b>WELCOME TO THE SERVER</b></gradient>"
      font: "Impact"
      font-size: 24

    shop-button:
      type: button
      position: 16 60
      size: 160 36
      text: "Open Store"
      on-click:
        action1:
          type: switch_scene
          scene: server-shop
```

For complete documentation of every UI component and action type, see:
* [🧩 UI Components Reference](UI-Components-Reference)
* [🖱️ Actions & Interactions](Actions-&-Interactions)

---

## 🛠️ In-Game Management Commands

Instead of calculating coordinates by hand, you can define displays using commands:

1. **/gb select**: Gives you the selection tool. Left-click block 1 (top-left) and right-click block 2 (bottom-right).
2. **/gb create <name>**: Automatically generates the board YAML, computes width/height, assigns facing direction, and initializes virtual frames.
3. **/gb coordinates <name>**: Displays calculated world boundaries and dimensions.
4. **/gb teleport <name>**: Teleports you directly in front of the board's viewing plane.
5. **/gb reload**: Atomically reloads all board YAML files from disk without restarting the server.
