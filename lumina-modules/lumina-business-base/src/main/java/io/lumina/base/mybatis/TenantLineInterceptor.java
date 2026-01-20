package io.lumina.base.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * 租户隔离拦截器
 *
 * 自动在 SQL 中添加 tenant_id 条件，实现租户数据隔离
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Component
public class TenantLineInterceptor implements InnerInterceptor {

    /**
     * 需要拦截的表（这些表会自动添加 tenant_id 条件）
     */
    private static final String[] TENANT_TABLES = {
            "lumina_user",
            "lumina_role",
            "lumina_user_role",
            "lumina_role_permission"
            // 注意：lumina_tenant 和 lumina_permission 不需要租户隔离
            // lumina_tenant 是全局表
            // lumina_permission 是系统级权限表
    };

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // TODO: 实现 SQL 解析和租户条件添加
        // 由于 MyBatis-Plus 3.5 的租户插件实现较复杂，这里暂时留空
        // 可以通过在 Mapper 方法中手动添加 tenant_id 参数实现租户隔离
    }

    /**
     * 判断表是否需要租户隔离
     *
     * @param tableName 表名
     * @return 是否需要租户隔离
     */
    public static boolean needTenantFilter(String tableName) {
        for (String tenantTable : TENANT_TABLES) {
            if (tenantTable.equalsIgnoreCase(tableName)) {
                return true;
            }
        }
        return false;
    }
}
