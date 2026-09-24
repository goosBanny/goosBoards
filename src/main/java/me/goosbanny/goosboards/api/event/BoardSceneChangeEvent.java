package me.goosbanny.goosboards.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired when a player's active scene changes on a board display.
 */
public class BoardSceneChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String boardId;
    private final String fromScene;
    private final String toScene;

    private boolean cancelled;

    public BoardSceneChangeEvent(Player player, String boardId, String fromScene, String toScene) {
        this.player = Objects.requireNonNull(player, "player");
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.fromScene = fromScene != null ? fromScene : "";
        this.toScene = Objects.requireNonNull(toScene, "toScene");
    }

    public Player getPlayer() {
        return player;
    }

    public String getBoardId() {
        return boardId;
    }

    public String getButtonId() {
        return boardId;
    }


    public String getFromScene() {
        return fromScene;
    }

    public String getToScene() {
        return toScene;
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
