package me.goosbanny.goosboards.core.scheduler.impl;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class FoliaSchedulerImpl implements UniversalScheduler {
    private final Plugin plugin;

    public FoliaSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public void runOnEntity(Player player, Consumer<Player> task) {
        player.getScheduler().run(plugin, scheduledTask -> task.accept(player), null);
    }

    @Override
    public void runOnEntityLater(Player player, Consumer<Player> task, long delayTicks) {
        player.getScheduler().runDelayed(plugin, scheduledTask -> task.accept(player), null, Math.max(1L, delayTicks));
    }

    @Override
    public void scheduleAsyncRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                initialDelayTicks * 50L,
                periodTicks * 50L,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void scheduleGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
    }
}
