package me.goosbanny.goosboards.protocol.packet;

import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubTilePacketTest {

    @Test
    @DisplayName("Acceptance Criterion 2: Mutating a 16x16 button generates packet with payload <= 350 bytes")
    void testSmallComponentGeneratesSubTilePacket() {
        CanvasBufferImpl buffer = new CanvasBufferImpl(1, 1);

        // Mutate a 16x16 button at local coordinates (30, 40) -> (45, 55)
        for (int y = 40; y < 40 + 16; y++) {
            for (int x = 30; x < 30 + 16; x++) {
                buffer.setPixel(x, y, (byte) 25);
            }
        }

        List<DirtyTile> dirtyTiles = TileDiffer.findDirtyTiles(buffer, 1, 1);
        assertEquals(1, dirtyTiles.size());

        DirtyTile dirtyTile = dirtyTiles.get(0);
        assertTrue(dirtyTile.isPartial(), "16x16 mutation must qualify as a partial tile update");
        assertEquals(30, dirtyTile.startX());
        assertEquals(40, dirtyTile.startY());
        assertEquals(16, dirtyTile.columns());
        assertEquals(16, dirtyTile.rows());
        assertEquals(256, dirtyTile.payloadSize());

        // Build the network packet
        WrapperPlayServerMapData packet = BatchedMapSender.buildDirtyTilePacket(101, dirtyTile);

        assertNotNull(packet);
        assertEquals(16, packet.getColumns());
        assertEquals(16, packet.getRows());
        assertEquals(30, packet.getX());
        assertEquals(40, packet.getZ()); // Z is row offset in Minecraft protocol

        byte[] payload = packet.getData();
        assertNotNull(payload);
        assertEquals(256, payload.length, "Payload length must be exactly 16 * 16 = 256 bytes");
        assertTrue(payload.length <= 350, "Acceptance criterion: packet payload must be <= 350 bytes");

        // Verify that all pixels inside the sub-payload contain the expected color
        for (byte b : payload) {
            assertEquals((byte) 25, b);
        }
    }

    @Test
    @DisplayName("Dirty coverage > 40% gracefully falls back to full 128x128 map packet")
    void testLargeDirtyCoverageFallsBackToFullTile() {
        CanvasBufferImpl buffer = new CanvasBufferImpl(1, 1);

        // Mutate a 100x100 pixel area (10,000 pixels > 6,553 max partial limit of 40%)
        for (int y = 10; y < 110; y++) {
            for (int x = 10; x < 110; x++) {
                buffer.setPixel(x, y, (byte) 42);
            }
        }

        List<DirtyTile> dirtyTiles = TileDiffer.findDirtyTiles(buffer, 1, 1);
        assertEquals(1, dirtyTiles.size());

        DirtyTile dirtyTile = dirtyTiles.get(0);
        assertFalse(dirtyTile.isPartial(), "Mutations > 40% must fallback to full 128x128 packet");
        assertEquals(0, dirtyTile.startX());
        assertEquals(0, dirtyTile.startY());
        assertEquals(128, dirtyTile.columns());
        assertEquals(128, dirtyTile.rows());
        assertEquals(16384, dirtyTile.payloadSize());

        WrapperPlayServerMapData packet = BatchedMapSender.buildDirtyTilePacket(102, dirtyTile);
        assertNotNull(packet);
        assertEquals(128, packet.getColumns());
        assertEquals(128, packet.getRows());
        assertEquals(16384, packet.getData().length);
    }
}