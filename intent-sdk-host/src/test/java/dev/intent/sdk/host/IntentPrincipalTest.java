package dev.intent.sdk.host;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 身份与权限策略：默认实现的行为必须可预期，宿主才能放心不复写。 */
class IntentPrincipalTest {

    @Test
    void anonymousHasNoIdentity() {
        IntentPrincipal principal = IntentPrincipal.anonymous();
        assertTrue(principal.isAnonymous());
        assertNull(principal.userIdAsLong());
        assertFalse(principal.hasRole("admin"));
    }

    @Test
    void rolesAreDefensivelyCopiedAndImmutable() {
        Set<String> source = new java.util.LinkedHashSet<>(List.of("hr", "admin"));
        IntentPrincipal principal = IntentPrincipal.of("7", "张三", "1", source);
        source.add("hacker");
        assertFalse(principal.hasRole("hacker"), "外部改动不能影响已建身份");
        assertThrows(UnsupportedOperationException.class, () -> principal.roles().add("x"));
    }

    @Test
    void hasAnyRoleRequiresIntersection() {
        IntentPrincipal principal = IntentPrincipal.of("7", "张三", "1", List.of("hrbp"));
        assertTrue(principal.hasAnyRole(List.of("hrbp", "hrd")));
        assertFalse(principal.hasAnyRole(List.of("hrd")));
        assertTrue(principal.hasAnyRole(List.of()), "空要求视为不限制");
    }

    @Test
    void userIdAsLongToleratesNonNumeric() {
        assertNull(IntentPrincipal.of("abc", "x", "1", List.of()).userIdAsLong());
        assertEquals(42L, IntentPrincipal.of("42", "x", "1", List.of()).userIdAsLong());
    }

    @Test
    void roleBasedPolicyAllowsUnrestrictedIntents() {
        IntentPermissionPolicy policy = IntentPermissionPolicy.roleBased();
        assertTrue(policy.canUse(IntentPrincipal.anonymous(), List.of()));
        assertTrue(policy.canUse(IntentPrincipal.anonymous(), List.of("*")));
        assertTrue(policy.canUse(null, List.of("*")));
    }

    @Test
    void roleBasedPolicyChecksRolesOtherwise() {
        IntentPermissionPolicy policy = IntentPermissionPolicy.roleBased();
        IntentPrincipal hr = IntentPrincipal.of("7", "张三", "1", List.of("hrbp"));
        assertTrue(policy.canUse(hr, List.of("hrbp")));
        assertFalse(policy.canUse(hr, List.of("hrd")));
        assertFalse(policy.canUse(IntentPrincipal.anonymous(), List.of("hrbp")));
    }
}