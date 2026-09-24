# Actions & Interactions Specification

GoosBoards allows any interactive component (such as a `button`) to trigger server actions upon clicks or cursor hovers.

---

## 🎯 How Click Detection Works

In vanilla Minecraft, the client only sends an entity interaction packet (`UseEntity`) if its local raycast hits an entity within ~4.5 blocks. Beyond this distance, the client assumes nothing is there.

To enable **long-range interactions (up to 32 blocks)**, GoosBoards intercepts raw client input packets via **PacketEvents**:

* **Left-Click Detection**: Intercepts `PLAYER_DIGGING` (`START_DIGGING_BLOCK`) and `ARM_ANIMATION` packets. These packets are sent unconditionally by the client every time the player swings their arm, regardless of distance or what item they hold.
* **Right-Click Detection**: Intercepts `USE_ITEM` (`ServerboundUseItemPacket`).
  > [!NOTE]
  > **Vanilla Client Limitation**: The standard Minecraft client only sends `USE_ITEM` if the player is holding an item in either hand. Clicking air with an empty hand at a distance sends 0 bytes from the client. For critical actions (e.g., store purchases), **left-click is the recommended default**.
* **Server-Authoritative Precision Raycast**: When an input packet is received, the server shoots an eye-ray through the board's 3D plane using normalized look vectors, computing exact UV pixel coordinates `(X, Y)`.

---

## ⚡ Interaction Triggers

Components support three distinct event triggers:

```yaml
sample-button:
  type: button
  position: 50 50
  size: 140 40
  text: "Click Me"

  # Triggered when clicked (left-click or right-click)
  on-click:
    action1:
      type: play_sound
      sound: "minecraft:ui.button.click"

  # Triggered when player cursor enters the button bounds
  on-hover:
    action1:
      type: play_sound
      sound: "minecraft:block.note_block.hat"
      pitch: 1.5

  # Triggered when player cursor leaves the button bounds
  off-hover:
    action1:
      type: play_sound
      sound: "minecraft:block.note_block.hat"
      pitch: 0.8
```

---

## 🛠️ Action Handlers

You can chain multiple actions under an event trigger. Each action has a unique key and a `type`:

### 1. `command`
Executes one or more Minecraft commands from the player or the server console.

A command action can specify either a **single command** or a **list of commands** to perform multiple actions in sequence:

```yaml
# Single command example:
buy-diamonds:
  type: command
  command: "give %player_name% diamond 5"
  execute-from-console: true   # true = console, false = player runs command
  price: 50.0                  # Optional: charges $50 via Vault

# Multiple commands list example (one action runs many commands!):
rank-bundle:
  type: command
  price: 100.0                 # Charges $100 via Vault before executing
  execute-from-console: true
  commands:
    - "lp user %player_name% parent add vip"
    - "give %player_name% emerald 10"
    - "say %player_name% has upgraded to VIP Rank!"
```

* **Single or List Format**: Supports `command: "..."`, `command: [...]`, or `commands: [...]`.
* **Placeholder Support**: Supports `%player_name%` and any PlaceholderAPI token.
* **Anti-Injection Protection**: Newline (`\n`) and carriage return (`\r`) characters are automatically stripped from each command string to prevent malicious command chaining.

---

### 2. `switch_scene`
Navigates the player's view to another scene on the board.

```yaml
to-store-menu:
  type: switch_scene
  scene: store-page-2          # Target scene ID defined in board YAML
```

* **Custom Event**: Dispatches `BoardSceneChangeEvent`. Other plugins can intercept and cancel this event. If cancelled, the scene does not switch and any charged economy price is automatically refunded.

---

### 3. `play_sound`
Plays a Minecraft sound effect directly to the interacting player.

```yaml
chime-sound:
  type: play_sound
  sound: "minecraft:entity.player.levelup"
  volume: 1.0                  # Default: 1.0
  pitch: 1.2                   # Default: 1.0 (range 0.5 - 2.0)
```

---

### 4. `send_message`
Sends formatted chat messages to the interacting player. Supports either a single message or a list of messages:

```yaml
# Single message:
greeting-msg:
  type: send_message
  message: "<green>Welcome back, <gold>%player_name%</gold>!</green>"
  formatting: minimessage      # Options: minimessage (default), legacy

# Multi-line message list:
welcome-kit:
  type: send_message
  formatting: minimessage
  messages:
    - "<gradient:#38BDF8:#818CF8><b>[Store]</b> Thank you for your purchase!</gradient>"
    - "<yellow>Check your inventory for your reward items.</yellow>"
```

---

## 💰 Economy Integration & Exploit Protection

If **Vault** is installed, you can attach a `price` to any action:

```yaml
rank-purchase:
  type: command
  command: "lp user %player_name% parent set vip"
  execute-from-console: true
  price: 250.0
```

### Protection Guarantees:
1. **Balance Check**: Verifies the player has at least `$250.0` before processing.
2. **Atomic Idempotency**: Each transaction generates an idempotency key:
   $$\text{UUID} : \text{ComponentID} : \text{UnixTimestampSeconds}$$
   Duplicate clicks within the configured idempotency window (`config.yml -> interaction.economy-idempotency-window-ms`) are rejected, preventing double-charge and duplication exploits from click-macros.
3. **Automatic Refunds**: If an action fails (e.g. downstream command error or cancelled scene change), deducted funds are refunded to the player's account.

---

## 🛡️ Rate Limiting

To prevent network or CPU flooding from auto-clickers:
* **Clicks**: Rate-limited per player via a token-bucket algorithm (`max-clicks-per-second`, default: 4).
* **Hover Raycasts**: Throttled to `max-hover-packets-per-second` (default: 20).
