package dev.intent.sdk.host;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.host.repository.FileIntentConfigRepository;
import dev.intent.sdk.host.repository.FileIntentSpecRepository;
import dev.intent.sdk.host.repository.InMemoryIntentConfigRepository;
import dev.intent.sdk.host.repository.InMemoryIntentSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仓储实现：内存（演示）与文件（零建表模式）两种都要能用。 */
class RepositoryTest {

    private static IntentSpec spec(String id, String name) {
        return IntentSpec.builder(id, name, "你是助手").build();
    }

    @Test
    void inMemorySpecRepositoryKeepsOrderAndTracksSource() {
        InMemoryIntentSpecRepository repository = new InMemoryIntentSpecRepository();
        repository.upsert(spec("b.intent", "B"), "builtin");
        repository.upsert(spec("a.intent", "A"), "custom");

        assertEquals(List.of("b.intent", "a.intent"),
                repository.findAll().stream().map(IntentSpec::getId).toList(),
                "顺序必须稳定，否则界面顺序每次刷新都在跳");
        assertEquals("builtin", repository.sourceOf("b.intent"));
        assertEquals("custom", repository.sourceOf("a.intent"));
        assertFalse(repository.findById("nope").isPresent());
        assertTrue(repository.delete("a.intent"));
        assertFalse(repository.delete("a.intent"));
    }

    @Test
    void fileSpecRepositoryRoundTripsYaml(@TempDir Path dir) {
        FileIntentSpecRepository repository = new FileIntentSpecRepository(dir);
        repository.upsert(spec("crm.contract.risk-review", "合同风险审查"), "builtin");

        assertTrue(Files.exists(dir.resolve("crm.contract.risk-review.yaml")));
        List<IntentSpec> loaded = new FileIntentSpecRepository(dir).findAll();
        assertEquals(1, loaded.size());
        assertEquals("crm.contract.risk-review", loaded.get(0).getId());
        assertEquals("合同风险审查", loaded.get(0).getName());

        assertTrue(repository.delete("crm.contract.risk-review"));
        assertTrue(new FileIntentSpecRepository(dir).findAll().isEmpty());
    }

    @Test
    void missingDirectoryIsEmptyNotError(@TempDir Path dir) {
        assertTrue(new FileIntentSpecRepository(dir.resolve("not-created")).findAll().isEmpty());
    }

    @Test
    void fileConfigRepositoryPersistsAcrossInstances(@TempDir Path dir) {
        FileIntentConfigRepository repository = new FileIntentConfigRepository(dir);
        repository.save(new IntentConfigState("crm.contract.risk-review", false, List.of("hrbp"), "下架中", null));

        FileIntentConfigRepository reopened = new FileIntentConfigRepository(dir);
        IntentConfigState state = reopened.find("crm.contract.risk-review").orElseThrow();
        assertFalse(state.enabled());
        assertEquals(List.of("hrbp"), state.roles());
        assertEquals("下架中", state.remark());
        assertFalse(reopened.find("missing").isPresent());
    }

    @Test
    void inMemoryConfigRepositoryBehavesLikeFind() {
        InMemoryIntentConfigRepository repository = new InMemoryIntentConfigRepository();
        repository.save(IntentConfigState.enabledFor("a.intent"));
        assertTrue(repository.find("a.intent").orElseThrow().enabled());
        assertTrue(repository.find("a.intent").orElseThrow().unrestricted());
        assertTrue(repository.find("b.intent").isEmpty());
    }

    @Test
    void blankIntentIdIsRejected() {
        InMemoryIntentSpecRepository repository = new InMemoryIntentSpecRepository();
        assertThrows(IllegalArgumentException.class, () -> repository.upsert(spec(" ", "X"), "custom"));
    }
}