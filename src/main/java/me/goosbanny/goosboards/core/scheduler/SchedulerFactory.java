package me.goosbanny.goosboards.core.scheduler;

import me.goosbanny.goosboards.core.scheduler.impl.FoliaSchedulerImpl;
import me.goosbanny.goosboards.core.scheduler.impl.PaperSchedulerImpl;

import org.bukkit.plugin.Plugin;

public final class SchedulerFactory {
    private SchedulerFactory() {}

    public static UniversalScheduler create(Plugin plugin) {
        if (isFolia()) {
            return new FoliaSchedulerImpl(plugin);
        }
        return new PaperSchedulerImpl(plugin);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
