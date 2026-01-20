package io.lumina.base.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import io.lumina.common.core.BaseContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 租户行处理器实现
 *
 * <p>自动在 SQL 中添加租户隔离条件。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    /**
     * 需要忽略租户隔离的表
     *
     * <p>系统表、权限表等不需要租户隔离
     */
    private static final List<String> IGNORE_TABLES = Arrays.asList(
        "lumina_permission",           // 权限表是全局的
        "lumina_role_permission",      // 角色权限关联表
        "lumina_tenant"                // 租户表本身
    );

    /**
     * 获取当前租户ID
     *
     * @return 租户ID
     */
    @Override
    public Expression getTenantId() {
        Long tenantId = BaseContext.getTenantId();
        if (tenantId == null) {
            // 如果没有租户信息，默认为0（系统租户）
            return new LongValue(0);
        }
        return new LongValue(tenantId);
    }

    /**
     * 获取租户字段名
     *
     * @return 租户字段名
     */
    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 判断是否忽略该表
     *
     * @param tableName 表名
     * @return true-忽略租户隔离，false-启用租户隔离
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 检查表是否在忽略列表中
        return IGNORE_TABLES.stream().anyMatch(tableName::equalsIgnoreCase);
    }
}
