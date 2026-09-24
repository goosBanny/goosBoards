# Placeholders & Dynamic Content

GoosBoards supports live, dynamic data on displays through internal tokens and full [PlaceholderAPI](https://placeholderapi.com/) (PAPI) integration.

---

## 📌 Built-In Placeholders

The following placeholders are supported out of the box without requiring any external plugins:

| Placeholder | Description | Example Output |
| :--- | :--- | :--- |
| **`%player_name%`** | Sanitized alphanumeric username of the viewer | `Steve` |
| **`%player_displayname%`** | Formatted display name of the viewer | `[VIP] Steve` |
| **`%player_ping%`** | Viewer's current network latency in milliseconds | `42` |
| **`%player_uuid%`** | Viewer's unique player UUID | `069a79f4-44e9-4726-a5be-fca90e38aaf5` |

---

## 🔌 PlaceholderAPI Integration

When PlaceholderAPI is installed on your server, any valid PAPI placeholder can be embedded in `text`, `pixel-text`, commands, and message actions:

```yaml
player-stats-card:
  type: text
  position: 20 60
  text: "<gray>Balance:</gray> <gold>%vault_eco_balance_formatted%</gold>\n<gray>Kills:</gray> <red>%statistic_player_kills%</red>"
  font: "Arial"
  font-size: 14
```

---

## ⏱️ Update Cadence & Debouncing

Evaluating placeholders for multiple viewers every tick would create severe CPU lag. GoosBoards uses an intelligent scheduling and debouncing model:

1. **Board-Level Refresh Interval**:
   Configured in `config.yml` (`placeholders.default-refresh-ticks: 20`) or overridden per-board in board YAML:
   ```yaml
   settings:
     placeholder-refresh-ticks: 20   # Evaluated once per second (20 ticks)
   ```
2. **Debounce Throttling**:
   To prevent rapid placeholder re-evaluations during sudden scene transitions or click bursts, updates are clamped to `placeholders.debounce-ms: 150`. If multiple events trigger within 150ms, only one render cycle is executed.

---

## 🧠 Smart Render-State Caching

To scale across hundreds of players, GoosBoards classifies all UI components during YAML parsing:

### 1. Context-Independent Components (Static)
* Elements with no placeholders (backgrounds, static labels, server logos, borders).
* **Rendered exactly once** into the global RAM cache.
* Shared across all players with zero per-player CPU cost.

### 2. Context-Dependent Components (Dynamic)
* Elements containing placeholders.
* Cached by **`resolvedStateHash`** (a 64-bit hash of the *actual resolved text values*, rather than the player's UUID).
* **Cross-Player Deduplication**: If two players have identical placeholder values (e.g., both viewing `%server_online%` or players with the same stats), they automatically share the exact same rendered tile cache.
