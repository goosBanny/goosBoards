package me.goosbanny.goosboards.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a player hovers their cursor over an interactive display plane.
 */
public class BoardHoverEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String boardId;
    private final int pixelX;
    private final int pixelY;

    public BoardHoverEvent(Player player, String boardId, int pixelX, int pixelY) {
        this.player = Objects.requireNonNull(player, "player");
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.pixelX = pixelX;
        this.pixelY = pixelY;
    }

    public Player getPlayer() {
        return player;
    }

    public String getBoardId() {
        return boardId;
    }

    public int getPixelX() {
        return pixelX;
    }

    public int getPixelY() {
        return pixelY;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
