package dev.intent.sdk.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.IntentFeedback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * JSONL 文件留痕存储：每条记录一行追加写入，天然按时间有序。
 *
 * <p>每次意图执行的完整转录（含每步工具调用）落盘，可回放、可审计。
 * 规模化阶段替换为数据库实现，接口不变。</p>
 */
public final class JsonlTraceStore implements TraceStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path traceFile;
    private final Path feedbackFile;
    private final Object lock = new Object();

    public JsonlTraceStore(Path dir) {
        try {
            Files.createDirectories(dir);
            this.traceFile = dir.resolve("intent-traces.jsonl");
            this.feedbackFile = dir.resolve("intent-feedback.jsonl");
        } catch (IOException e) {
            throw new IllegalStateException("初始化留痕目录失败: " + dir, e);
        }
    }

    @Override
    public void save(ExecutionTraceRecord record) {
        append(traceFile, record);
    }

    @Override
    public List<ExecutionTraceRecord> list(String userId, int limit) {
        List<ExecutionTraceRecord> all = new ArrayList<>();
        forEachLine(traceFile, ExecutionTraceRecord.class, all::add);
        return all.stream()
                .filter(r -> userId == null || userId.equals(r.userId()))
                .sorted(Comparator.comparingLong(ExecutionTraceRecord::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ExecutionTraceRecord> get(String traceId) {
        List<ExecutionTraceRecord> all = new ArrayList<>();
        forEachLine(traceFile, ExecutionTraceRecord.class, all::add);
        return all.stream().filter(r -> traceId.equals(r.traceId())).findFirst();
    }

    @Override
    public void saveFeedback(IntentFeedback feedback) {
        append(feedbackFile, feedback);
    }

    @Override
    public List<IntentFeedback> listFeedback(String intentId, int limit) {
        List<IntentFeedback> all = new ArrayList<>();
        forEachLine(feedbackFile, IntentFeedback.class, all::add);
        return all.stream()
                .filter(f -> intentId == null || intentId.equals(f.intentId()))
                .limit(limit)
                .toList();
    }

    private void append(Path file, Object value) {
        synchronized (lock) {
            try {
                Files.writeString(file, MAPPER.writeValueAsString(value) + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("写入留痕失败", e);
            }
        }
    }

    private <T> void forEachLine(Path file, Class<T> type, java.util.function.Consumer<T> consumer) {
        if (!Files.exists(file)) {
            return;
        }
        synchronized (lock) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    consumer.accept(MAPPER.readValue(line, type));
                }
            } catch (IOException e) {
                throw new IllegalStateException("读取留痕失败", e);
            }
        }
    }
}
