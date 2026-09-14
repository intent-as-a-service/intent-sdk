package dev.intent.sdk.host;

import dev.intent.protocol.IntentSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 宿主一致性测试套件：任何新宿主接完适配层，跑一遍这个就能知道接得对不对。
 *
 * <p>为什么要有一套：源码级集成最容易出的不是编译错误，而是<b>语义错误</b>——
 * 角色判定方向反了、仓储返回 null、find 未知 id 抛异常、
 * findAll 顺序每次都在变（界面顺序乱跳）。这些用人工点页面很难覆盖。</p>
 *
 * <p>用法（宿主测试里）：</p>
 * <pre>
 * IntentHostConformance.assertCompliant(new IntentHostConformance.Host() {
 *     public IntentPermissionPolicy permissionPolicy() { return myPolicy; }
 *     public IntentSpecRepository specRepository() { return mySpecRepo; }
 *     public IntentConfigRepository configRepository() { return myConfigRepo; }
 * });
 * </pre>
 */
public final class IntentHostConformance {

    /** 宿主适配层的四个必答项（其余 SPI 都有默认实现，可不实现）。 */
    public interface Host {

        default IntentPrincipalProvider principalProvider() {
            return IntentPrincipalProvider.anonymous();
        }

        IntentPermissionPolicy permissionPolicy();

        IntentSpecRepository specRepository();

        IntentConfigRepository configRepository();
    }

    private IntentHostConformance() {
    }

    /** 返回问题清单；空 = 通过。 */
    public static List<String> check(Host host) {
        List<String> problems = new ArrayList<>();
        if (host == null) {
            problems.add("宿主未提供适配层");
            return problems;
        }
        checkPrincipal(host, problems);
        checkPermissionPolicy(host, problems);
        checkSpecRepository(host, problems);
        checkConfigRepository(host, problems);
        return problems;
    }

    /** 不通过直接抛断言错误（测试里用）。 */
    public static void assertCompliant(Host host) {
        List<String> problems = check(host);
        if (!problems.isEmpty()) {
            throw new AssertionError("宿主适配层不符合约定:\n - " + String.join("\n - ", problems));
        }
    }

    private static void checkPrincipal(Host host, List<String> problems) {
        IntentPrincipalProvider provider = host.principalProvider();
        if (provider == null) {
            problems.add("principalProvider 不能为 null（无登录态请返回 IntentPrincipal.anonymous()）");
            return;
        }
        IntentPrincipal principal;
        try {
            principal = provider.current();
        } catch (Exception e) {
            problems.add("principalProvider.current() 抛异常: " + e.getMessage());
            return;
        }
        if (principal == null) {
            problems.add("principalProvider.current() 不能返回 null，无登录态请返回 anonymous()");
            return;
        }
        try {
            principal.roles().add("x");
            problems.add("IntentPrincipal.roles() 必须是不可变集合（当前可写）");
        } catch (UnsupportedOperationException expected) {
            // 正常
        }
    }

    private static void checkPermissionPolicy(Host host, List<String> problems) {
        IntentPermissionPolicy policy = host.permissionPolicy();
        if (policy == null) {
            problems.add("permissionPolicy 不能为 null");
            return;
        }
        IntentPrincipal member = IntentPrincipal.of("1", "成员", "1", List.of("member"));
        try {
            if (!policy.canUse(member, List.of())) {
                problems.add("空角色要求应视为不限制（当前返回 false）");
            }
            if (!policy.canUse(member, List.of("*"))) {
                problems.add("角色 * 应视为不限制（当前返回 false）");
            }
            if (!policy.canUse(member, List.of("member", "admin"))) {
                problems.add("角色有交集时应放行（当前返回 false）");
            }
            if (policy.canUse(member, List.of("admin"))) {
                problems.add("角色无交集时应拒绝（当前返回 true）");
            }
            if (policy.canUse(IntentPrincipal.anonymous(), List.of("member"))) {
                problems.add("匿名身份 + 受限角色应拒绝（当前返回 true）");
            }
        } catch (Exception e) {
            problems.add("permissionPolicy.canUse 抛异常: " + e.getMessage());
        }
    }

    private static void checkSpecRepository(Host host, List<String> problems) {
        IntentSpecRepository repository = host.specRepository();
        if (repository == null) {
            problems.add("specRepository 不能为 null");
            return;
        }
        List<IntentSpec> first;
        List<IntentSpec> second;
        try {
            first = repository.findAll();
            second = repository.findAll();
        } catch (Exception e) {
            problems.add("specRepository.findAll() 抛异常: " + e.getMessage());
            return;
        }
        if (first == null) {
            problems.add("specRepository.findAll() 不能返回 null（没有意图请返回空列表）");
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (IntentSpec spec : first) {
            if (spec == null || spec.getId() == null || spec.getId().isBlank()) {
                problems.add("存在没有编号的意图（列表渲染会缺 key）");
                continue;
            }
            if (!ids.add(spec.getId())) {
                problems.add("意图编号重复: " + spec.getId());
            }
        }
        if (second != null && !idsIn(first).equals(idsIn(second))) {
            problems.add("两次 findAll() 结果不一致（顺序必须稳定，否则界面顺序会跳）");
        }
        try {
            if (repository.findById("__not_exists__").isPresent()) {
                problems.add("findById(不存在的编号) 不该有结果");
            }
        } catch (Exception e) {
            problems.add("findById(未知编号) 应返回 empty 而不是抛异常: " + e.getMessage());
        }
    }

    private static void checkConfigRepository(Host host, List<String> problems) {
        IntentConfigRepository repository = host.configRepository();
        if (repository == null) {
            problems.add("configRepository 不能为 null");
            return;
        }
        try {
            if (repository.findAll() == null) {
                problems.add("configRepository.findAll() 不能返回 null");
            }
            if (repository.find("__not_exists__").isPresent()) {
                problems.add("find(不存在的编号) 不该有结果");
            }
        } catch (Exception e) {
            problems.add("configRepository 查询未知编号时抛异常: " + e.getMessage());
        }
    }

    private static String idsIn(List<IntentSpec> specs) {
        return specs.stream()
                .filter(spec -> spec != null && spec.getId() != null)
                .map(IntentSpec::getId)
                .collect(Collectors.joining(","));
    }
}