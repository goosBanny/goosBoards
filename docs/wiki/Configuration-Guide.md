# Global Configuration Guide (`config.yml`)

The `config.yml` file in `plugins/GoosBoards/` controls global engine behavior, thread pool allocations, network burst throttles, image security rules, skin API endpoints, and caching parameters.

---

## 📑 Table of Contents
1. [Persistence & Database](#1-persistence--database)
2. [Concurrency & Thread Pools](#2-concurrency--thread-pools)
3. [Performance, Cadences & LOD](#3-performance-cadences--lod)
4. [Spatial Indexing (Broad-Phase Gate)](#4-spatial-indexing-broad-phase-gate)
5. [Graphics & Color Quantization](#5-graphics--color-quantization)
6. [Placeholders & Dynamic Text](#6-placeholders--dynamic-text)
7. [Animated GIFs](#7-animated-gifs)
8. [Shared Render Cache (RAM)](#8-shared-render-cache-ram)
9. [Persistent Media Cache (Disk)](#9-persistent-media-cache-disk)
10. [Security & Anti-SSRF Firewall](#10-security--anti-ssrf-firewall)
11. [External Skin & Avatar Services](#11-external-skin--avatar-services)
12. [Interaction & Anti-Exploit](#12-interaction--anti-exploit)
13. [Debug & Diagnostics](#13-debug--diagnostics)
14. [Complete Reference `config.yml`](#14-complete-reference-configyml)

---

## 1. Persistence & Database

```yaml
database:
  enabled: false
  type: H2
  host: "localhost"
  port: 3306
  database: "goosboards"
  username: "root"
  password: ""
  table-prefix: "goosboards_"
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 10000
    idle-timeout-ms: 600000
    max-lifetime-ms: 1800000
```

* **`enabled`** *(default: `false`)*: When `false`, the entire database subsystem is disabled at boot. Zero database worker threads and zero background database connections are created. Set to `true` only if you enable `persistent: true` on individual boards to save player scenes or transaction histories across server restarts.
* **`type`** *(default: `H2`)*: Storage engine to use when enabled.
  * `H2`: Embedded zero-configuration file database located in `plugins/GoosBoards/data.db`.
  * `MYSQL`: External MySQL 8.0+ server with HikariCP connection pooling.
  * `MARIADB`: External MariaDB 10.5+ server with HikariCP connection pooling.
* **`pool`**: HikariCP connection pool tunables for MySQL/MariaDB.
  * `maximum-pool-size`: Upper limit of concurrent database connections.
  * `connection-timeout-ms`: Milliseconds before timing out on acquiring a pool connection.

---

## 2. Concurrency & Thread Pools

```yaml
threading:
  raycast-pool-size: 1
  render-pool-size: 1
```

* **`raycast-pool-size`** *(default: `1`)*: Number of background daemon threads dedicated to calculating crosshair look-vector raycasts. Because raycasts are broad-phase gated by the spatial index, a single thread is capable of handling over 50 concurrent players with sub-millisecond execution times.
* **`render-pool-size`** *(default: `1`)*: Thread pool size handling canvas rasterization and RGB565 palette matching. A value of `1` eliminates thread context switching overhead. For servers with dozens of animated 8×8 boards updating every tick, this can be increased to `2` or `4`.

---

## 3. Performance, Cadences & LOD

```yaml
performance:
  render-tick-interval: 1
  hover-raycast-interval-ticks: 2
  lod:
    near-distance: 16.0
    near-divisor: 1
    medium-distance: 16.0
    medium-divisor: 4
    far-divisor: 20
  network:
    max-tiles-per-player-per-tick: 64
    check-channel-writability: true
```

* **`render-tick-interval`** *(default: `1`)*: Server tick cadence between rendering engine passes (`1` = 20 FPS, `2` = 10 FPS). Lower values give the smoothest GIF animations and text updates.
* **`hover-raycast-interval-ticks`** *(default: `2`)*: How often player cursor positions are raycast against active boards for hover detection. `1` provides instant button highlights; `2` halves computational cost while remaining visually responsive.
* **`lod` (Level of Detail)**: Dynamically scales map packet transmission rates based on viewer distance in blocks:
  * `near-distance`: Viewers within this radius receive frames at `near-divisor` (e.g. `1` = 20 FPS).
  * `medium-distance`: Viewers between near and medium distances receive frames at `medium-divisor` (e.g. `4` = 5 FPS).
  * `far-divisor`: Viewers farther away up to activation distance receive frames at `far-divisor` (e.g. `20` = 1 FPS).
* **`network`**:
  * **`max-tiles-per-player-per-tick`** *(default: `64`)*: Caps the number of 128×128 map sub-tiles sent to a single client in one tick. This accommodates full 8×8 block displays (64 tiles) in a single frame while preventing network packet flooding.
  * **`check-channel-writability`** *(default: `true`)*: Queries Netty's `channel.isWritable()` before pushing packets. If a player has a slow internet connection or network backpressure occurs, packets are skipped until the client drains its buffer, preventing server memory bloat.

---

## 4. Spatial Indexing (Broad-Phase Gate)

```yaml
spatial-index:
  bucket-size: 32
  proximity-scan-interval-ticks: 2
```

* **`bucket-size`** *(default: `32`)*: Size of spatial chunk-buckets in blocks. Boards register their bounding boxes into these buckets. Players are only ever raycast against boards that occupy their current or adjacent spatial buckets.
* **`proximity-scan-interval-ticks`** *(default: `2`)*: Cadence in server ticks for updating player-to-board spatial proximity lists.

---

## 5. Graphics & Color Quantization

```yaml
rendering:
  default-dithering: TILE_ISOLATED
  batch-packet-flushes: true
  placeholder-debounce-ms: 500
```

* **`default-dithering`** *(default: `TILE_ISOLATED`)*:
  * `TILE_ISOLATED`: Floyd-Steinberg error diffusion with clamping at 128×128 tile borders. This yields smooth gradients for photos and artwork while guaranteeing that modifying one tile never invalidates adjacent tile hashes.
  * `NONE`: Nearest-neighbor direct color matching without error diffusion. Best suited for clean, flat UI icons, buttons, and text.
* **`batch-packet-flushes`** *(default: `true`)*: When enabled, all dirty map packets for a frame are accumulated via `channel.write(...)` and dispatched with a single `channel.flush()` at the end of the tick, minimizing TCP system calls.
* **`placeholder-debounce-ms`** *(default: `500`)*: Minimum delay between forced canvas re-renders caused by placeholder value changes.

---

## 6. Placeholders & Dynamic Text

```yaml
placeholders:
  default-refresh-ticks: 20
  debounce-ms: 150
```

* **`default-refresh-ticks`** *(default: `20`)*: Interval in server ticks (20 ticks = 1.0 second) for querying PlaceholderAPI expansions on active boards. Can be overridden per-board in board YAML files via `placeholder-refresh-ticks`.
* **`debounce-ms`** *(default: `150`)*: Throttling window protecting the server against rapid back-to-back placeholder updates during rapid scene switching.

---

## 7. Animated GIFs

```yaml
gif:
  max-fps: 20
  default-fps: 10
```

* **`max-fps`** *(default: `20`)*: Hard global ceiling on GIF playback rates. Even if a GIF's internal metadata requests a 50 FPS rate, playback is clamped to 20 FPS (1 frame per Minecraft tick) to prevent CPU overuse.
* **`default-fps`** *(default: `10`)*: Playback speed for GIFs that omit frame-delay metadata.

---

## 8. Shared Render Cache (RAM)

```yaml
render-cache:
  max-memory-mb: 128
  expire-after-access-minutes: 10
```

* **`max-memory-mb`** *(default: `128`)*: Maximum heap memory in megabytes allocated to caching rendered tiles. GoosBoards analyzes board components at parse time: elements that do not contain per-player placeholders are shared globally among all viewers in this cache.
* **`expire-after-access-minutes`** *(default: `10`)*: Inactive scene tiles are evicted from RAM after this idle duration.

---

## 9. Persistent Media Cache (Disk)

```yaml
media-cache:
  max-cached-skins: 1024
  max-cached-images: 256
  retention-days: 7
```

* **`max-cached-skins`** *(default: `1024`)*: Number of player skin avatar PNGs preserved in the local `plugins/GoosBoards/media-cache/skins/` directory.
* **`max-cached-images`** *(default: `256`)*: Maximum number of remote web images cached on disk.
* **`retention-days`** *(default: `7`)*: Cached media files not accessed within this timeframe are automatically cleaned up on server boot.

---

## 10. Security & Anti-SSRF Firewall

```yaml
security:
  media:
    max-file-size-bytes: 8388608
    connect-timeout-ms: 4000
    read-timeout-ms: 6000
    max-redirects: 3
    max-gif-frames: 100
    max-gif-resolution: 2048
  ssrf-firewall:
    enabled: true
    blocked-ranges:
      - '127.0.0.0/8'       # Loopback IPv4
      - '10.0.0.0/8'        # RFC1918 Class A
      - '172.16.0.0/12'     # RFC1918 Class B
      - '192.168.0.0/16'    # RFC1918 Class C
      - '169.254.0.0/16'    # Link-Local / Cloud Metadata (AWS/GCP/Azure)
      - '0.0.0.0/8'         # Current network
      - '::1/128'           # Loopback IPv6
      - 'fc00::/7'          # Unique Local IPv6
      - 'fe80::/10'         # Link-Local IPv6
```

* **`max-file-size-bytes`** *(default: `8388608` = 8MB)*: Hard limit on remote image and GIF downloads. Downloads exceeding this size are aborted immediately to protect server bandwidth.
* **`max-gif-frames`** *(default: `100`)* & **`max-gif-resolution`** *(default: `2048`)*: Decompression bomb guards. GIFs with excessive frame counts or massive canvas dimensions are rejected before decoding.
* **`ssrf-firewall`**:
  * **`enabled`** *(default: `true`)*: Enforces pre-resolution DNS inspection using `java.net.http.HttpClient`.
  * **`blocked-ranges`**: Rejects connections to private IP spaces, localhost, and cloud instance metadata services (e.g. `169.254.169.254`), preventing malicious board configurations from scanning your internal network. Re-evaluated across every HTTP redirect hop.

---

## 11. External Skin & Avatar Services

```yaml
api:
  skin-service:
    primary-avatar-url: "https://crafatar.com/avatars/{uuid}?overlay"
    profile-url: "https://api.mojang.com/users/profiles/minecraft/{username}"
    fallback-avatar-urls:
      - "https://minotar.net/avatar/{uuid}"
      - "https://mc-heads.net/avatar/{uuid}"
    rate-limit:
      max-requests-per-minute: 120
      rate-limit-backoff-seconds: 60
      timeout-ms: 4000
```

* **`primary-avatar-url`**: URL template used by `head2d` components. Supports `{uuid}` and `{username}` tokens.
* **`fallback-avatar-urls`**: Fallback endpoints queried in sequential order if the primary service returns HTTP 429 (Too Many Requests), 5xx errors, or times out.
* **`rate-limit`**: Enforces a client-side request limiter with automatic exponential backoff to prevent your server's IP address from being rate-limited by public skin APIs.

---

## 12. Interaction & Anti-Exploit

```yaml
interaction:
  rate-limiting:
    max-clicks-per-second: 4
    max-hover-packets-per-second: 20
  economy-idempotency-window-ms: 1000
  default-click-sound: "minecraft:ui.button.click"
```

* **`max-clicks-per-second`** *(default: `4`)*: Token-bucket limiter per player. Rejects rapid click-macro spam.
* **`max-hover-packets-per-second`** *(default: `20`)*: Caps look-vector evaluation packets processed from a single player.
* **`economy-idempotency-window-ms`** *(default: `1000`)*: Deduplication window for Vault transactions. Prevents players from being charged twice if they click a paid button multiple times in rapid succession.
* **`default-click-sound`** *(default: `"minecraft:ui.button.click"`)*: Sound played to the player whenever an interactive button is activated.

---

## 13. Debug & Diagnostics

```yaml
debug:
  enabled: false
  performance-logging: false
  draw-bounding-boxes: false
```

* **`enabled`** *(default: `false`)*: Enables verbose console logging for scene parsing, layout bounding box calculation, player packet routing, and entity allocation.
* **`performance-logging`** *(default: `false`)*: Prints per-tick rendering time metrics, raycasting duration, and cache hit ratios to the console.
* **`draw-bounding-boxes`** *(default: `false`)*: Outlines every UI component with a red rectangle on the in-game display for visual layout debugging.

---

## 14. Complete Reference `config.yml`

Below is the complete, fully furnished `config.yml` shipped with GoosBoards:

```yaml
# ==============================================================================
# GoosBoards Engine - Global Configuration
# Target: Paper / Folia 1.20.4+ | Clients: 1.19.4+ (Protocol >= 762)
# ==============================================================================
config-version: 1

# --- PERSISTENCE & DATABASE ---
database:
  enabled: false
  type: H2
  host: "localhost"
  port: 3306
  database: "goosboards"
  username: "root"
  password: ""
  table-prefix: "goosboards_"
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 10000
    idle-timeout-ms: 600000
    max-lifetime-ms: 1800000

# --- CONCURRENCY & THREAD POOLS (Folia-Safe) ---
threading:
  raycast-pool-size: 1
  render-pool-size: 1

# --- PERFORMANCE, CADENCES & LOD TUNABLES ---
performance:
  render-tick-interval: 1
  hover-raycast-interval-ticks: 2
  lod:
    near-distance: 16.0
    near-divisor: 1
    medium-distance: 16.0
    medium-divisor: 4
    far-divisor: 20
  network:
    max-tiles-per-player-per-tick: 64
    check-channel-writability: true

# --- SPATIAL INDEXING (Broad-Phase Raycast Gate) ---
spatial-index:
  bucket-size: 32
  proximity-scan-interval-ticks: 2

# --- GRAPHICS, QUANTIZATION & NETWORK ---
rendering:
  default-dithering: TILE_ISOLATED
  batch-packet-flushes: true
  placeholder-debounce-ms: 500

# --- PLACEHOLDERS & DYNAMIC TEXT ---
placeholders:
  default-refresh-ticks: 20
  debounce-ms: 150

# --- ANIMATED GIF TUNING ---
gif:
  max-fps: 20
  default-fps: 10

# --- SHARED RENDER CACHE (Viewer Deduplication) ---
render-cache:
  max-memory-mb: 128
  expire-after-access-minutes: 10

# --- PERSISTENT MEDIA CACHE (Player Skins & Web Images) ---
media-cache:
  max-cached-skins: 1024
  max-cached-images: 256
  retention-days: 7

# --- SECURITY, SSRF & RESOURCE BOMBS ---
security:
  media:
    max-file-size-bytes: 8388608
    connect-timeout-ms: 4000
    read-timeout-ms: 6000
    max-redirects: 3
    max-gif-frames: 100
    max-gif-resolution: 2048
  ssrf-firewall:
    enabled: true
    blocked-ranges:
      - '127.0.0.0/8'       # Loopback IPv4
      - '10.0.0.0/8'        # RFC1918 Class A
      - '172.16.0.0/12'     # RFC1918 Class B
      - '192.168.0.0/16'    # RFC1918 Class C
      - '169.254.0.0/16'    # Link-Local / Cloud Metadata (AWS/GCP/Azure)
      - '0.0.0.0/8'         # Current network
      - '::1/128'           # Loopback IPv6
      - 'fc00::/7'          # Unique Local IPv6
      - 'fe80::/10'         # Link-Local IPv6

# --- EXTERNAL APIS & FALLBACKS (Player Heads / Skins / Metadata) ---
api:
  skin-service:
    primary-avatar-url: "https://crafatar.com/avatars/{uuid}?overlay"
    profile-url: "https://api.mojang.com/users/profiles/minecraft/{username}"
    fallback-avatar-urls:
      - "https://minotar.net/avatar/{uuid}"
      - "https://mc-heads.net/avatar/{uuid}"
    rate-limit:
      max-requests-per-minute: 120
      rate-limit-backoff-seconds: 60
      timeout-ms: 4000

# --- INTERACTION & ANTI-EXPLOIT ---
interaction:
  rate-limiting:
    max-clicks-per-second: 4
    max-hover-packets-per-second: 20
  economy-idempotency-window-ms: 1000
  default-click-sound: "minecraft:ui.button.click"

# --- DEBUG & OBSERVABILITY ---
debug:
  enabled: false
  performance-logging: false
  draw-bounding-boxes: false
```
