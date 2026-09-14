package dev.intent.sdk.host;

import dev.intent.sdk.catalog.IntentCatalogContext;
import dev.intent.sdk.host.rule.RuleCondition;
import dev.intent.sdk.host.rule.RuleContext;
import dev.intent.sdk.host.rule.RuleEvaluator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 条件求值语义：类型不匹配时宁可判假，也不能给错的结果。 */
class RuleConditionTest {

    private static final IntentCatalogContext CTX = new IntentCatalogContext(
            "1", "admin", "1", "crm/customer", "customer", "9", "客户A", "Asia/Shanghai");

    private static RuleContext ctx(Object amount, Object status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", amount);
        row.put("status", status);
        return RuleContext.of(CTX, row, 0);
    }

    private static RuleCondition leaf(String op, Object left, Object right) {
        return new RuleCondition(null, null, null, op, left, right);
    }

    @Test
    void nullConditionMeansNoRestriction() {
        assertTrue(RuleEvaluator.test(null, ctx(1, "ok")));
    }

    @Test
    void numericComparisonIgnoresStringNumberDifference() {
        assertTrue(RuleEvaluator.test(leaf("gte", "row.amount", 100), ctx("150", "ok")));
        assertTrue(RuleEvaluator.test(leaf("eq", "row.amount", "100"), ctx(100, "ok")));
        assertFalse(RuleEvaluator.test(leaf("lt", "row.amount", 100), ctx("150", "ok")));
    }

    @Test
    void dateComparisonWorksForIsoStrings() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        assertTrue(RuleEvaluator.test(leaf("gte", "row.status", today.toString()), ctx(1, today.toString())));
        assertTrue(RuleEvaluator.test(leaf("lte", "row.status", today.toString()), ctx(1, today.toString())));
        assertFalse(RuleEvaluator.test(leaf("lt", "row.status", today.toString()), ctx(1, today.toString())));
    }

    @Test
    void inAndContainsWork() {
        assertTrue(RuleEvaluator.test(leaf("in", "row.status", List.of("new", "ok")), ctx(1, "ok")));
        assertFalse(RuleEvaluator.test(leaf("in", "row.status", List.of("new")), ctx(1, "ok")));
        assertTrue(RuleEvaluator.test(leaf("contains", "row.status", "o"), ctx(1, "ok")));
    }

    @Test
    void nullChecksWork() {
        assertTrue(RuleEvaluator.test(leaf("isNull", "row.missing", null), ctx(1, "ok")));
        assertFalse(RuleEvaluator.test(leaf("isNull", "row.status", null), ctx(1, "ok")));
        assertTrue(RuleEvaluator.test(leaf("notNull", "row.status", null), ctx(1, "ok")));
    }

    @Test
    void anyAndNotBranchesWork() {
        RuleCondition any = new RuleCondition(null, List.of(
                leaf("eq", "row.status", "new"),
                leaf("eq", "row.status", "ok")), null, null, null, null);
        assertTrue(RuleEvaluator.test(any, ctx(1, "ok")));

        RuleCondition all = new RuleCondition(List.of(
                leaf("eq", "row.status", "new"),
                leaf("eq", "row.status", "ok")), null, null, null, null, null);
        assertFalse(RuleEvaluator.test(all, ctx(1, "ok")), "all 是 AND，一项不成立就该为假");

        RuleCondition not = new RuleCondition(null, null,
                leaf("eq", "row.status", "closed"), null, null, null);
        assertTrue(RuleEvaluator.test(not, ctx(1, "ok")));
    }

    @Test
    void unknownOperatorIsFalseInsteadOfThrowing() {
        assertFalse(RuleEvaluator.test(leaf("roughly", "row.amount", 1), ctx(1, "ok")));
    }

    @Test
    void contextFieldsResolve() {
        assertTrue(RuleEvaluator.test(leaf("eq", "ctx.objectId", "9"), ctx(1, "ok")));
        assertTrue(RuleEvaluator.test(leaf("eq", "ctx.objectName", "客户A"), ctx(1, "ok")));
        assertFalse(RuleEvaluator.test(leaf("eq", "ctx.objectId", "10"), ctx(1, "ok")));
    }
}
