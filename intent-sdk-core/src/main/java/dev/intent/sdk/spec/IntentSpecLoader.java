package dev.intent.sdk.spec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.intent.protocol.IntentSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * IntentSpec 加载器：支持 YAML / JSON（按扩展名识别），支持单文件与目录批量加载。
 *
 * <p>目录批量加载时逐个校验，任一规范不合法即抛出 {@link IntentSpecException}，
 * 保证不合规范的意图无法进入运行时（规范化的第一道闸）。</p>
 */
public final class IntentSpecLoader {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private IntentSpecLoader() {
    }

    /** 从文件加载并校验一个意图规范。 */
    public static IntentSpec load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in, file.toString());
        } catch (IOException e) {
            throw new IntentSpecException(file.toString(), List.of("读取失败: " + e.getMessage()));
        }
    }

    /** 从 classpath 资源加载并校验。 */
    public static IntentSpec loadFromClasspath(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IntentSpecException(resource, List.of("classpath 资源不存在"));
            }
            return read(in, resource);
        } catch (IOException e) {
            throw new IntentSpecException(resource, List.of("读取失败: " + e.getMessage()));
        }
    }

    /** 从输入流加载并校验（供宿主框架适配：Spring Resource / ClassLoader 等自行解包）。 */
    public static IntentSpec loadFromStream(InputStream in, String sourceName) {
        try {
            return read(in, sourceName == null ? "(stream)" : sourceName);
        } catch (IOException e) {
            throw new IntentSpecException(sourceName == null ? "(stream)" : sourceName,
                    List.of("读取失败: " + e.getMessage()));
        }
    }

    /** 从目录批量加载（*.yaml / *.yml / *.json），并逐一校验。 */
    public static List<IntentSpec> loadDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IntentSpecException(dir.toString(), List.of("目录不存在"));
        }
        List<IntentSpec> specs = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".json");
            }).sorted().forEach(p -> specs.add(load(p)));
        } catch (IOException e) {
            throw new IntentSpecException(dir.toString(), List.of("遍历目录失败: " + e.getMessage()));
        }
        return specs;
    }

    /** 解析并校验内容（供沙箱 / 在线编辑使用）。 */
    public static IntentSpec parse(String content, boolean yaml) {
        return read(content, yaml);
    }

    /** 将 IntentSpec 序列化为 YAML（供编辑器回显与导出）。 */
    public static String toYaml(IntentSpec spec) {
        try {
            return YAML.writeValueAsString(spec);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 IntentSpec 失败", e);
        }
    }

    private static IntentSpec read(InputStream in, String source) throws IOException {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return read(content, isYaml(source));
    }

    private static IntentSpec read(String content, boolean yaml) {
        IntentSpec spec;
        try {
            spec = (yaml ? YAML : JSON).readValue(content, IntentSpec.class);
        } catch (IOException e) {
            throw new IntentSpecException("(parse)", List.of("解析失败: " + e.getMessage()));
        }
        List<String> errors = IntentSpecValidator.validate(spec);
        if (!errors.isEmpty()) {
            throw new IntentSpecException(spec.getId() == null ? "(unknown)" : spec.getId(), errors);
        }
        return spec;
    }

    private static boolean isYaml(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".yaml") || n.endsWith(".yml");
    }
}
