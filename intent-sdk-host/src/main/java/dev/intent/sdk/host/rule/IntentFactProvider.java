package dev.intent.sdk.host.rule;

import dev.intent.sdk.catalog.IntentCatalogContext;

import java.util.List;
import java.util.Map;

/**
 * 事实数据源 SPI：规则里的 {@code dataset} 由宿主用这个接口供给数据行。
 *
 * <p>规则文件里<b>不写 SQL</b>——这是刻意的。规则是配置，配置里放可执行 SQL
 * 等于开了一条自由执行路径，且列名无法静态校验、数字容易和宿主自己的页面口径漂移。
 * 正确做法是宿主复用既有 Service/Mapper 把行喂进来（口径天然一致）。</p>
 *
 * <p>约定：同步求值、必须快（目录接口高频调用）。重计算应预计算到表里再查。</p>
 */
public interface IntentFactProvider {

    /**
     * 是否认领该数据集。
     *
     * <p>多模块共存时，聚合分发靠它选出该问谁——不实现则默认认领所有数据集，
     * 单模块宿主因此无需关心这个方法。</p>
     */
    default boolean supports(String datasetId) {
        return true;
    }

    /**
     * 本提供者能供给的数据集清单（含每行列名）。
     *
     * <p>默认空列表 = 不声明。声明了它的宿主，能让"AI 生成规则""规则体检"这类
     * 需要知道"能用什么数据"的能力跑起来；不声明也不影响规则引擎本身。</p>
     */
    default List<DatasetSpec> datasets() {
        return List.of();
    }

    /**
     * @param datasetId 数据集标识（规则里声明）
     * @param context   当前上下文（含用户、租户、页面、对象、时区）
     * @param limit     最大行数；{@code <=0} 表示不限（取数方仍应有硬上限兜底）
     */
    List<Map<String, Object>> rows(String datasetId, IntentCatalogContext context, int limit);

    /**
     * 事实条数：徽标文案要用准确数字，不能拿"取回来几行"当总数。
     * 有 count 查询的宿主应覆盖它，否则退化为拉全量再数。
     */
    default int count(String datasetId, IntentCatalogContext context) {
        List<Map<String, Object>> rows = rows(datasetId, context, 0);
        return rows == null ? 0 : rows.size();
    }
}
