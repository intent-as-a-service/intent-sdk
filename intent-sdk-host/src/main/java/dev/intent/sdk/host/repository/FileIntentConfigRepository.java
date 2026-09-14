package dev.intent.sdk.host.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.intent.sdk.host.IntentConfigRepository;
import dev.intent.sdk.host.IntentConfigState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件配置仓储：全部配置存在一个 JSON 文件里（默认 {@code <dir>/intent-config.json}）。
 *
 * <p>用于<b>零建表模式</b>——宿主不愿或不能建表时，上架开关与角色配置照样能持久化。
 * 写入是"整体覆盖 + 原子替换"（先写临时文件再 move），避免写一半崩了把配置写坏。</p>
 */
public final class FileIntentConfigRepository implements IntentConfigRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final Map<String, IntentConfigState> cache = new ConcurrentHashMap<>();

    public FileIntentConfigRepository(Path dir) {
        this(dir.resolve("intent-config.json"), true);
    }

    /** 直接指定配置文件路径（含文件名）。 */
    public static FileIntentConfigRepository ofFile(Path file) {
        return new FileIntentConfigRepository(file, true);
    }

    private FileIntentConfigRepository(Path path, boolean isFile) {
        this.file = path.toAbsolutePath();
        reload();
    }

    /** 从磁盘重新读取（外部改过文件后调用）。 */
    public void reload() {
        cache.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return;
            }
            IntentConfigState[] states = JSON.readValue(json, IntentConfigState[].class);
            for (IntentConfigState state : states) {
                if (state != null && state.intentId() != null) {
                    cache.put(state.intentId(), state);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取意图配置失败: " + file, e);
        }
    }

    @Override
    public List<IntentConfigState> findAll() {
        return List.copyOf(new ArrayList<>(new LinkedHashMap<>(cache).values()));
    }

    @Override
    public void save(IntentConfigState state) {
        if (state == null || state.intentId() == null) {
            return;
        }
        Map<String, IntentConfigState> ordered = new LinkedHashMap<>(new LinkedHashMap<>(cache));
        ordered.put(state.intentId(), state);
        cache.put(state.intentId(), state);
        flush(new ArrayList<>(ordered.values()));
    }

    private void flush(List<IntentConfigState> states) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(states),
                    StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("写入意图配置失败: " + file, e);
        }
    }
}
