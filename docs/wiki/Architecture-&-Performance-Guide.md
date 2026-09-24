# Architecture & Performance Guide

GoosBoards is engineered for high-concurrency Minecraft server environments (including large networks and Folia multi-threaded regions). This guide provides an in-depth look at its core architecture, zero-allocation invariants, protocol virtualization, and rendering pipeline.

---

## 🏛️ Core Architectural Invariants

### 1. Pure Virtual Entities & Quarantined ID Engine
* **Zero Real Entities**: No `ItemFrame`, `TextDisplay`, or `ArmorStand` entities ever exist in server world chunks. All displays exist purely as synthetic network packets sent to clients.
* **Synthetic ID Allocation**: Entity IDs are allocated from a high-range atomic decrementing counter (`Integer.MAX_VALUE - 100_000` down to `1_000_000_000`). Because vanilla Minecraft allocates entity IDs sequentially starting near 0, collision with real server entities is mathematically impossible.
* **10-Second Quarantine Ring Buffer**: When a virtual item frame is despawned, its synthetic ID is placed into a timestamped quarantine queue for at least 10 seconds before being recycled. This prevents client-side packet-reordering race conditions where an old despawn packet could conflict with a freshly spawned frame reusing the same ID.

---

## 🧵 Folia Concurrency Model

GoosBoards runs natively on both **Paper** and **Folia**:

* **Runtime Detection**: Folia is detected via `io.papermc.paper.threadedregions.RegionizedServer`.
* **Regional Thread Safety**: Whenever player world coordinates, permissions, or game state are queried, tasks are dispatched onto the player's specific Folia region thread via `UniversalScheduler`.
* **Async Netty Writes**: Packet creation, quantization, and Netty channel writes run entirely on background worker threads without blocking server tick loops.
* **Thread Pools**:
  * `RaycastPool`: Dedicated daemon thread pool handling cursor vector raycasts for players near active displays.
  * `RenderPool`: Dedicated worker pool handling image decoding, layout resolution, and color quantization.

---

## 🎨 Rendering & Quantization Pipeline

### 1. RGB565 Direct-Table Palette Lookup
Converting 24-bit ARGB colors to Minecraft's 256-color map palette is traditionally CPU-heavy. GoosBoards uses an O(1) lookup table precalculated on startup:
* The 16-bit RGB565 color space ($32 \times 64 \times 32 = 65,536$ entries) maps directly to the nearest palette color.
* **Benchmark Performance**:
  * Quantization for a $512 \times 512$ pixel canvas (16 tiles = 262,144 pixels) averages **~0.244 ms**.
  * Average time per $128 \times 128$ tile is **~15.26 microseconds**.

### 2. Tile-Isolated Floyd-Steinberg Dithering
Standard Floyd-Steinberg error diffusion propagates color quantization errors to neighboring pixels. Across a grid of maps, unbounded dithering causes a change in one map to bleed subtle noise into neighboring maps.
* GoosBoards enforces **Tile Isolation**: quantization error is clamped at $128 \times 128$ pixel tile boundaries.
* A change inside Tile $(0, 0)$ can never bleed into or alter the hash of Tile $(1, 0)$.

### 3. xxHash64 Dirty-Tile Diffing
Each $128 \times 128$ tile maintains a 64-bit `xxHash64` checksum of its current pixel buffer:
* Every frame, the newly rendered tile buffer is hashed.
* If the hash matches the previous frame, the tile is marked clean and skipped.
* **Zero Network Waste**: Even on animated boards, only the specific tiles with active changes produce network packets.

---

## 🌐 Network Optimization & Flush Batching

### 1. Single Flush Batching
Standard Bukkit packet transmission methods often call `writeAndFlush` for every individual map packet, causing dozens of separate TCP socket flushes per tick.

GoosBoards writes directly to the player's Netty channel pipeline:
```java
Channel channel = (Channel) PacketEvents.getAPI().getProtocolManager().getChannel(player.getUniqueId());
for (DirtyTile tile : dirtyTiles) {
    channel.write(buildMapPacket(tile)); // Accumulate in buffer
}
channel.flush(); // Exactly ONE TCP flush per frame
```

### 2. Netty Channel Backpressure Recovery
When a player experiences high network latency or packet loss, outgoing buffers can queue up.
* Before queuing map updates, GoosBoards checks `channel.isWritable()`.
* If a channel is backed up, non-essential map frames are skipped until the client drains its TCP buffer, preventing server memory bloat.

---

## 📐 Precision Mathematical Raycasting

### Ray-Plane UV Intersection Formula
Given player eye origin $\mathbf{O}$, look vector $\mathbf{D}$, display plane top-left corner $\mathbf{P}_0$, right vector $\mathbf{\hat{U}}$, down vector $\mathbf{\hat{V}}$, width $W$, height $H$, and normal vector $\mathbf{N} = \mathbf{\hat{U}} \times \mathbf{\hat{V}}$:

$$\text{denom} = \mathbf{D} \cdot \mathbf{N}$$
If $|\text{denom}| < 10^{-6}$, the ray is parallel (miss).

$$t = \frac{(\mathbf{P}_0 - \mathbf{O}) \cdot \mathbf{N}}{\text{denom}}$$
If $t < 0$ or $t > \text{interactionDistance}$, the ray misses.

$$\mathbf{p}_{\text{hit}} = \mathbf{O} + t\mathbf{D}$$
$$\mathbf{r} = \mathbf{p}_{\text{hit}} - \mathbf{P}_0$$
$$u = \frac{\mathbf{r} \cdot \mathbf{\hat{U}}}{W}, \quad v = \frac{\mathbf{r} \cdot \mathbf{\hat{V}}}{H}$$
If $u \notin [0, 1]$ or $v \notin [0, 1]$, the ray misses.

$$X = \text{clamp}(\lfloor u \times W \times 128 \rfloor, 0, W \times 128 - 1)$$
$$Y = \text{clamp}(\lfloor v \times H \times 128 \rfloor, 0, H \times 128 - 1)$$

* The clamping step guarantees that grazing hits at exactly $u = 1.0$ or $v = 1.0$ never produce out-of-bounds pixel array indices.

---

## 🔒 Security & Anti-SSRF Defense

Remote web images and skin downloads are secured against Server-Side Request Forgery (SSRF):
1. **Pre-Resolved DNS Pinned Validation**: Resolves hostnames via `InetAddress.getAllByName()` and validates every returned IP against the `ssrf-firewall` CIDR rules before initiating HTTP requests.
2. **Loopback & Cloud Metadata Blocking**: All RFC1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), localhost (`127.0.0.0/8`), and cloud instance metadata addresses (`169.254.169.254`) are blocked.
3. **Redirect Hop Verification**: The firewall re-inspects destination IPs on every HTTP redirect hop (up to 3 hops maximum).
4. **Decompression Bomb Protection**: Hard limits on downloaded byte sizes (8MB default), maximum GIF frame counts, and image pixel dimensions prevent memory denial-of-service attacks.
