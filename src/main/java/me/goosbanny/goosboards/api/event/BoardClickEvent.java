package me.goosbanny.goosboards.api.event;

import me.goosbanny.goosboards.api.ClickType;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a player clicks an interactive display plane.
 */
public class BoardClickEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String boardId;
    private final String displayId;
    private final int pixelX;
    private final int pixelY;
    private final ClickType clickType;

    private boolean cancelled;

    public BoardClickEvent(
            Player player,
            String boardId,
            String displayId,
            int pixelX,
            int pixelY,
            ClickType clickType
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.displayId = Objects.requireNonNull(displayId, "displayId");
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.clickType = Objects.requireNonNull(clickType, "clickType");
    }

    public Player getPlayer() {
        return player;
    }

    public String getBoardId() {
        return boardId;
    }

    public String getSceneId() {
        return boardId;
    }


    public String getDisplayId() {
        return displayId;
    }

    public int getPixelX() {
        return pixelX;
    }

    public int getPixelY() {
        return pixelY;
    }

    public ClickType getClickType() {
        return clickType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
