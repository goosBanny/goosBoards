# UI Components Reference

GoosBoards provides a rich set of declarative UI components to build anything from static informational signage to multi-page, animated, and interactive server menus.

---

## 📐 Box Model & Sizing

Every component supports standard positioning and sizing properties:

```yaml
example-component:
  type: <component_type>
  position: 16 32             # X Y offset in pixels from top-left
  size: 120 40                # Width Height in pixels
  alignment: center           # Options: left, center, right, top, bottom
```

### Sizing Values
* **Pixels**: Absolute integer pixel values (e.g. `size: 256 128`).
* **Percentages**: Proportional to the display dimensions (e.g. `size: 50% 100%`).
* **`max`**: Stretches the component to the full width or height of the canvas (e.g. `size: max max`).
* **`auto`**: Calculates dimensions dynamically based on rendered content (e.g. text bounds).

---

## 📦 Container Components

### `background`
Renders solid colors, borders, and rounded rectangular backgrounds.

```yaml
my-background:
  type: background
  position: 0 0
  size: max max
  color: "25 25 35 255"        # RGBA: red green blue alpha (0-255)
  corner-radius: 8             # Optional: corner rounding in pixels
  border-color: "255 215 0 255"# Optional: border RGBA color
  border-thickness: 2          # Optional: border stroke width
```

---

### `scroll-pane`
Provides a scrollable viewport container for large lists or multi-item catalogs.

```yaml
catalog-scroll:
  type: scroll-pane
  position: 20 50
  size: 472 300
  scroll-direction: vertical   # Options: vertical, horizontal, both
  scroll-bar:
    visible: true
    color: "80 80 80 200"
    thumb-color: "200 200 200 255"
  content:
    item-1:
      type: button
      position: 0 0
      size: 450 40
      text: "Item 1"
    item-2:
      type: button
      position: 0 50
      size: 450 40
      text: "Item 2"
```

> [!NOTE]
> **Hit-Testing Clipping**: Buttons and interactive elements inside a `scroll-pane` strictly clip their hit-testing to the visible pane bounds. An item scrolled beyond the top or bottom of the pane cannot be clicked accidentally.

---

## 🔘 Interactive Components

### `button`
An interactive element that detects player cursor hover and click events.

```yaml
store-button:
  type: button
  position: 100 120
  size: 180 44
  text: "Buy Rank ($100)"
  font: "Impact"
  font-size: 16
  color: "40 120 40 255"       # Normal background RGBA
  hover-color: "50 180 50 255" # Highlighted background RGBA when hovered
  text-color: "255 255 255 255"
  hover-text-color: "255 255 0 255"
  sound: "minecraft:ui.button.click" # Optional click sound override
  on-click:
    action1:
      type: command
      command: "lp user %player_name% parent add vip"
      price: 100.0
      execute-from-console: true
```

---

### `action-listener`
A headless component that triggers actions when external events occur (e.g. server broadcasts, external API signals) without drawing visible pixels.

```yaml
global-notifier:
  type: action-listener
  listen-event: custom_trigger
  on-trigger:
    action1:
      type: play_sound
      sound: "minecraft:entity.player.levelup"
```

---

## 🖼️ Visual Components

### `image`
Displays static images (PNG or JPEG) from local disk files or remote HTTPS URLs.

```yaml
# Local image (from plugins/GoosBoards/images/logo.png):
server-logo:
  type: image
  position: 16 16
  size: 64 64
  cache-behavior: global       # Shared across viewers in RAM cache
  image:
    name: "logo.png"

# Remote HTTPS image:
web-banner:
  type: image
  position: 0 0
  size: max 128
  image:
    url: "https://example.com/banner.png"
```

* **Anti-SSRF Protection**: Remote URLs are validated against private IP ranges and cloud metadata services.
* **Disk Caching**: Remote images are cached locally in `plugins/GoosBoards/media-cache/` to minimize external requests.

---

### `gif`
Renders animated GIF images with automatic frame sequencing and looping.

```yaml
welcome-animation:
  type: gif
  position: 200 50
  size: 112 112
  fps: 15                      # Frame rate override (clamped by config max-fps)
  image:
    name: "fireworks.gif"      # Placed in plugins/GoosBoards/images/
```

* Frame delays defined in the GIF metadata are respected.
* Global frame caps prevent memory exhaustion from oversized GIF files.

---

### `head2d`
Fetches and renders a 2D face avatar of any Minecraft player with the outer hat/helmet layer included.

```yaml
player-avatar:
  type: head2d
  position: 16 16
  size: 48 48
  player: "%player_name%"      # Dynamic placeholder or fixed username/UUID
  overlay: true                # true = includes 2nd skin layer (hat/hair/helmet)
```

* Avatars are queried from the skin service specified in `config.yml` (e.g. Crafatar, Minotar).
* Player skins are cached on disk in `plugins/GoosBoards/media-cache/skins/`.

---

## ✍️ Text Components

### `text`
High-fidelity text rendering with TrueType font support and native **MiniMessage** formatting.

```yaml
title-text:
  type: text
  position: 16 20
  text: "<gradient:#FF007F:#7F00FF><b>SEASON PASS</b></gradient>"
  formatting: minimessage      # Options: minimessage, legacy
  font: "Impact"               # Any installed system font or TTF
  font-size: 20
  outline-color: "0 0 0 255"   # Optional drop shadow/outline stroke
  outline-stroke: 1.5
```

* **MiniMessage Formatting**: Full support for `<gradient>`, `<rainbow>`, `<b>`, `<i>`, and hex colors (`<#FF5555>`).
* **TrueType Fonts**: Uses system-installed fonts. Use `/gb fonts` to see all available fonts on your server.

---

### `pixel-text`
Ultra-fast, zero-allocation bitmap font renderer matching Minecraft's native pixel font style.

```yaml
stat-line:
  type: pixel-text
  position: 20 80
  text: "&aCoins: &e%vault_eco_balance%"
  scale: 1                     # 1 = standard pixel font, 2 = 2x size
```

* **Best Performance**: Avoids AWT font rasterization. Ideal for fast-changing counters, clocks, or server statistics.
* **Colors**: Supports classic Minecraft formatting codes (`&a`, `&b`, `&l`, etc.).

---

## ⏱️ Dynamic & Timing Components

### `delayed_function_timer`
Executes configured actions after a delay or at recurring tick intervals.

```yaml
auto-carousel:
  type: delayed_function_timer
  delay-ticks: 200             # 10 seconds (20 ticks/sec)
  repeat: true                 # Runs continuously every 200 ticks
  actions:
    step:
      type: switch_scene
      scene: news-scene
```
