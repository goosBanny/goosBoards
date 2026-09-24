# Troubleshooting & Frequently Asked Questions (FAQ)

Find answers to common operational questions, server configuration hurdles, and client quirks below.

---

## ❓ Frequently Asked Questions

### 1. Why do left-clicks work from 20 blocks away, but right-clicks with an empty hand do nothing?
* **Answer**: This is a hard-coded limitation of the vanilla Minecraft client protocol.
  * When a player left-clicks (swings their arm), the client always transmits a `PLAYER_DIGGING` / `ARM_ANIMATION` packet, allowing GoosBoards to raycast from any distance.
  * When a player right-clicks with **both hands empty**, the client performs an internal reach hit-test (~4.5 blocks). If it hits nothing, the client transmits **zero bytes** to the server.
  * If the player holds any item in their hand, right-clicks *are* sent via `USE_ITEM`.
  * **Recommendation**: Design important interactive buttons (such as store purchases or confirmation dialogues) to trigger on **left-click**.

---

### 2. Can GoosBoards cause world corruption or leave invisible entities behind?
* **Answer**: **No.** GoosBoards uses pure virtual packet entities.
  * No entity data is ever passed to Bukkit's world entity manager or saved to chunk `.mca` files.
  * If the server crashes, reboots, or the plugin is uninstalled, nothing remains in your world.

---

### 3. Does GoosBoards work with Bedrock players (Geyser / Floodgate)?
* **Answer**: Yes. Bedrock players connected through Geyser receive map packets translated automatically into Bedrock-compatible map rendering.

---

### 4. Do I need to pre-render or generate physical Minecraft map items?
* **Answer**: No. All map IDs are synthetic virtual numbers allocated from a reserved upper ID space. No in-game map items (`FilledMap`) or map files in the `data/` folder are ever created.

---

## 🛠️ Common Errors & Troubleshooting

### Issue: Font rendering fails or throws `HeadlessException` on Linux
* **Symptom**: Console prints font rendering warnings or fails to load TrueType fonts.
* **Cause**: Minimal Linux server installations (e.g. Alpine, minimal Debian/Ubuntu Docker images) often lack the OS-level font rasterization libraries required by Java AWT.
* **Fix**: Install `fontconfig` and `freetype` on your server host:
  ```bash
  # Ubuntu / Debian:
  sudo apt-get update && sudo apt-get install -y fontconfig libfreetype6

  # Alpine Linux:
  apk add --no-cache fontconfig freetype ttf-dejavu

  # RHEL / Rocky / CentOS:
  sudo dnf install -y fontconfig freetype
  ```

---

### Issue: "PacketEvents not found! GoosBoards requires PacketEvents 2.x"
* **Symptom**: GoosBoards disables itself on startup with a PacketEvents error.
* **Cause**: PacketEvents is not shaded inside the GoosBoards jar; it must be installed as an independent plugin.
* **Fix**: Download the latest PacketEvents 2.x release and place it into your server's `plugins/` directory.

---

### Issue: Remote images or avatars fail to download
* **Symptom**: Web images appear blank or logs report SSRF rejection.
* **Cause**: The remote URL resolved to an IP blocked by GoosBoards' `ssrf-firewall` (e.g., localhost, private LAN IPs, or cloud metadata endpoints).
* **Fix**:
  1. Ensure the URL is publicly reachable over the internet.
  2. If you are hosting an internal image proxy within your LAN (e.g. `192.168.1.50`), you can adjust `blocked-ranges` in `config.yml` to allow your specific proxy IP.

---

### Issue: High host CPU or bandwidth saturation from animated GIFs
* **Context**: All GIF decoding, color quantization, and packet serialization run **100% asynchronously** on background worker pools, meaning GoosBoards places **zero synchronous workload on the main server tick loop**.
* **Cause**: On budget hosts with limited physical CPU cores (e.g. 1–2 vCPU VPS), heavily uncapped GIF animations can saturate overall host CPU capacity, causing the operating system scheduler to starve server tick threads of CPU time. Furthermore, unthrottled 20 FPS animations on massive displays consume high network bandwidth for clients with slower connections.
* **Fix**:
  1. In `config.yml`, set `gif.max-fps: 10` or `15` to cap background animation framerates.
  2. Increase `performance.render-tick-interval` from `1` to `2` (10 FPS cadence).
  3. Ensure source GIF dimensions closely match the target board resolution to minimize runtime resizing calculations.
