package me.goosbanny.goosboards.core.scheduler;

import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Abstraction over Paper's BukkitScheduler and Folia's EntityScheduler / AsyncScheduler.
 * Obtain via SchedulerFactory — never instantiate implementations directly.
 */
public interface UniversalScheduler {
    /** Runs {@code task} on an async thread pool (safe for I/O and rendering). */
    void runAsync(Runnable task);

    /** Runs {@code task} on the player's owning region thread (Folia) or the main thread (Paper). */
    void runOnEntity(Player player, Consumer<Player> task);

    /** Runs {@code task} on the player's owning region thread (Folia) or the main thread (Paper) after {@code delayTicks}. */
    void runOnEntityLater(Player player, Consumer<Player> task, long delayTicks);

    /**
     * Schedules a repeating async task.
     * @param initialDelayTicks ticks before first run
     * @param periodTicks       ticks between subsequent runs
     */
    void scheduleAsyncRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    /**
     * Schedules a repeating task on the global region scheduler (Folia) or main server thread (Paper).
     * @param task              the task to execute
     * @param initialDelayTicks ticks before first run
     * @param periodTicks       ticks between subsequent runs
     */
    void scheduleGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
