package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handles /gb debug <board> [--raw / -r].
 */
public class DebugSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MetricsCollector metricsCollector;
    private final MessageService messageService;

    public DebugSubCommand(ConfigReloadManager reloadManager, MetricsCollector metricsCollector, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || args[0].startsWith("-")) {
            messageService.send(sender, "unknown-command", Map.of("sub", "debug <board>", "label", label));
            return;
        }

        String boardId = args[0];
        BoardConfig board = reloadManager.getBoard(boardId);
        if (board == null) {
            messageService.send(sender, "board-not-found", Map.of("board", boardId));
            return;
        }

        Set<String> flags = CommandUtils.extractFlags(args);
        boolean raw = flags.contains("raw") || flags.contains("r");

        MetricsCollector.BoardMetrics metrics = metricsCollector.getBoardMetrics(boardId);
        double cacheHit = metricsCollector.getCacheHitRatio();
        double packetRate = metricsCollector.getPacketsPerSecond();

        if (raw) {
            sender.sendMessage(String.format("board=%s;min_ms=%.2f;avg_ms=%.2f;max_ms=%.2f;rate=%.1f;hit_ratio=%.2f;viewers=%d;frames=%d;packets=%d",
                    boardId, metrics.getMinRenderLatencyMs(), metrics.getAvgRenderLatencyMs(), metrics.getMaxRenderLatencyMs(),
                    packetRate, cacheHit, metrics.getActiveViewers(), metrics.getTotalFrames(), metrics.getTotalPackets()));
            return;
        }

        messageService.send(sender, "debug-header");
        messageService.send(sender, "debug-board-id", Map.of("board", boardId));
        messageService.send(sender, "debug-latency", Map.of(
                "min", String.format("%.2f", metrics.getMinRenderLatencyMs()),
                "avg", String.format("%.2f", metrics.getAvgRenderLatencyMs()),
                "max", String.format("%.2f", metrics.getMaxRenderLatencyMs())
        ));
        messageService.send(sender, "debug-packet-rate", Map.of("rate", String.format("%.1f", packetRate)));
        messageService.send(sender, "debug-cache-hit", Map.of("ratio", String.format("%.1f", cacheHit)));
        messageService.send(sender, "debug-viewers", Map.of("viewers", String.valueOf(metrics.getActiveViewers())));
        messageService.send(sender, "debug-frames", Map.of("frames", String.valueOf(metrics.getTotalFrames())));
        messageService.send(sender, "debug-packets", Map.of("packets", String.valueOf(metrics.getTotalPackets())));
        messageService.send(sender, "debug-footer");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(new ArrayList<>(reloadManager.getActiveBoards().keySet()), args[0]);
        }
        if (args.length == 2) {
            return CommandUtils.filterPrefix(List.of("--raw", "-r"), args[1]);
        }
        return List.of();
    }
}
