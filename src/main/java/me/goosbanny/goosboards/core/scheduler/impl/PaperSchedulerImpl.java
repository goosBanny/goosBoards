package me.goosbanny.goosboards.core.scheduler.impl;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PaperSchedulerImpl implements UniversalScheduler {
    private final Plugin plugin;

    public PaperSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runOnEntity(Player player, Consumer<Player> task) {
        Bukkit.getScheduler().runTask(plugin, () -> task.accept(player));
    }

    @Override
    public void runOnEntityLater(Player player, Consumer<Player> task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(player), delayTicks);
    }

    @Override
    public void scheduleAsyncRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
    }

    @Override
    public void scheduleGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
    }
}
