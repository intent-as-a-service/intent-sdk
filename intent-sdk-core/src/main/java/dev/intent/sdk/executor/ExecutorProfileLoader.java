package dev.intent.sdk.executor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 执行器档案加载器：支持 YAML / JSON（按扩展名识别），单文件与目录批量加载。
 *
 * <p>与 IntentSpecLoader 同构：目录批量加载逐个校验，任一档案不合法即抛
 * {@link ExecutorProfileException}，保证不可用执行器无法进入运行时。</p>
 */
public final class ExecutorProfileLoader {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ExecutorProfileLoader() {
    }

    /** 从文件加载并校验一个执行器档案。 */
    public static ExecutorProfile load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in, file.toString());
        } catch (IOException e) {
            throw new ExecutorProfileException(file.toString(), List.of("读取失败: " + e.getMessage()));
        }
    }

    /** 从 classpath 资源加载并校验。 */
    public static ExecutorProfile loadFromClasspath(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new ExecutorProfileException(resource, List.of("classpath 资源不存在"));
            }
            return read(in, resource);
        } catch (IOException e) {
            throw new ExecutorProfileException(resource, List.of("读取失败: " + e.getMessage()));
        }
    }

    /** 从目录批量加载（*.yaml / *.yml / *.json），并逐一校验。 */
    public static List<ExecutorProfile> loadDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new ExecutorProfileException(dir.toString(), List.of("目录不存在"));
        }
        List<ExecutorProfile> profiles = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".json");
            }).sorted().forEach(p -> profiles.add(load(p)));
        } catch (IOException e) {
            throw new ExecutorProfileException(dir.toString(), List.of("遍历目录失败: " + e.getMessage()));
        }
        return profiles;
    }

    /** 解析并校验内容（供管理端在线编辑 / 沙箱预检使用）。 */
    public static ExecutorProfile parse(String content, boolean yaml) {
        return read(content, yaml);
    }

    /** 将执行器档案序列化为 YAML（供编辑器回显与导出）。 */
    public static String toYaml(ExecutorProfile profile) {
        try {
            return YAML.writeValueAsString(profile);
        } catch (IOException e) {
            throw new IllegalStateException("序列化执行器档案失败", e);
        }
    }

    /** 将执行器档案序列化为 JSON（DB 存储 profile_json 列 / 接口传输）。 */
    public static String toJson(ExecutorProfile profile) {
        try {
            return JSON.writeValueAsString(profile);
        } catch (IOException e) {
            throw new IllegalStateException("序列化执行器档案失败", e);
        }
    }

    private static ExecutorProfile read(InputStream in, String source) throws IOException {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return read(content, isYaml(source));
    }

    private static ExecutorProfile read(String content, boolean yaml) {
        ExecutorProfile profile;
        try {
            profile = (yaml ? YAML : JSON).readValue(content, ExecutorProfile.class);
        } catch (IOException e) {
            throw new ExecutorProfileException("(parse)", List.of("解析失败: " + e.getMessage()));
        }
        List<String> errors = ExecutorProfileValidator.validate(profile);
        if (!errors.isEmpty()) {
            throw new ExecutorProfileException(profile.id() == null ? "(unknown)" : profile.id(), errors);
        }
        return profile;
    }

    private static boolean isYaml(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".yaml") || n.endsWith(".yml");
    }
}
