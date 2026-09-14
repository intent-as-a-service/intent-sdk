package dev.intent.sdk.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宿主工具注册表：意图执行引擎可调用工具的唯一来源。
 *
 * <p>业务系统启动时把宿主工具注册进来；意图通过 IntentSpec.tools 白名单
 * 声明自己能用的工具子集（缺省为全部注册工具）。</p>
 */
public final class HostToolRegistry {

    private final Map<String, IntentTool> tools = new ConcurrentHashMap<>();

    public HostToolRegistry register(IntentTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public HostToolRegistry unregister(String name) {
        tools.remove(name);
        return this;
    }

    public IntentTool get(String name) {
        return tools.get(name);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 全部已注册工具（含名称排序，保证提示词稳定）。 */
    public Map<String, IntentTool> all() {
        return Map.copyOf(tools);
    }

    public int size() {
        return tools.size();
    }
}