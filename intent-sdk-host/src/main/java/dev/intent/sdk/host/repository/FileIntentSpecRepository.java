package dev.intent.sdk.host.repository;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.host.IntentSpecRepository;
import dev.intent.sdk.spec.IntentSpecLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件意图仓储：一个目录就是一份意图库，每个 {@code <意图编号>.yaml} 一个意图。
 *
 * <p>适合"规格即代码"的团队，也是<b>零建表模式</b>下最省事的做法：
 * 不建表、不接 DB，把 YAML 丢进目录即可运行；改动随代码走评审流程。</p>
 *
 * <p>注意：读取时不做缓存，每次 {@link #findAll()} 都重新扫描目录——
 * 让"改文件后刷新即生效"成为默认行为（上层缓存由调用方负责）。</p>
 */
public final class FileIntentSpecRepository implements IntentSpecRepository {

    private final Path dir;

    public FileIntentSpecRepository(Path dir) {
        this.dir = dir.toAbsolutePath();
    }

    public Path directory() {
        return dir;
    }

    @Override
    public List<IntentSpec> findAll() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> targets = files
                    .filter(Files::isRegularFile)
                    .filter(FileIntentSpecRepository::isSpecFile)
                    .sorted()
                    .toList();
            return targets.stream().map(IntentSpecLoader::load).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("扫描意图目录失败: " + dir, e);
        }
    }

    @Override
    public void upsert(IntentSpec spec, String source) {
        if (spec == null || spec.getId() == null || spec.getId().isBlank()) {
            throw new IllegalArgumentException("意图编号不能为空");
        }
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(spec.getId() + ".yaml");
            Files.writeString(target, IntentSpecLoader.toYaml(spec), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("写入意图失败: " + spec.getId(), e);
        }
    }

    @Override
    public boolean delete(String intentId) {
        if (intentId == null || intentId.isBlank()) {
            return false;
        }
        try {
            return Files.deleteIfExists(dir.resolve(intentId + ".yaml"));
        } catch (IOException e) {
            throw new UncheckedIOException("删除意图失败: " + intentId, e);
        }
    }

    @Override
    public String sourceOf(String intentId) {
        return "builtin";
    }

    private static boolean isSpecFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }
}
