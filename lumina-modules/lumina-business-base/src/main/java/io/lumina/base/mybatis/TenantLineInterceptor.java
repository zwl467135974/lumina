package io.lumina.base.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.lumina.common.core.BaseContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        try {
            String sql = boundSql.getSql();
            if (sql == null || sql.trim().isEmpty()) {
                return;
            }

            // 解析 SQL
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            // 检查是否需要添加租户条件
            Set<String> tables = extractTableNames(statement);
            boolean needTenantFilter = tables.stream().anyMatch(TenantLineInterceptor::needTenantFilter);
            
            if (!needTenantFilter) {
                return;
            }

            // 获取当前租户ID
            Long tenantId = BaseContext.getTenantId();
            if (tenantId == null) {
                // 如果没有租户信息，跳过（或者可以抛出异常）
                return;
            }

            // 添加租户条件
            if (statement instanceof Select) {
                processSelect((Select) statement, tenantId);
            } else if (statement instanceof Update) {
                processUpdate((Update) statement, tenantId);
            } else if (statement instanceof Delete) {
                processDelete((Delete) statement, tenantId);
            }

            // 更新 BoundSql
            updateBoundSql(boundSql, statement.toString());

        } catch (JSQLParserException e) {
            // SQL 解析失败，记录日志但不影响执行
            // 可以通过日志级别控制是否输出
        } catch (Exception e) {
            // 其他异常也记录但不影响执行
        }
    }

    /**
     * 提取 SQL 中的表名
     */
    private Set<String> extractTableNames(Statement statement) {
        Set<String> tableNames = new HashSet<>();
        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tables = tablesNamesFinder.getTableList(statement);
        for (String table : tables) {
            // 去除表别名，只保留表名
            String tableName = table.contains(" ") ? table.split(" ")[0] : table;
            tableName = tableName.replaceAll("`", ""); // 去除反引号
            tableNames.add(tableName.toLowerCase());
        }
        return tableNames;
    }

    /**
     * 处理 SELECT 语句
     */
    private void processSelect(Select select, Long tenantId) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            addTenantCondition(plainSelect.getWhere(), plainSelect, tenantId);
        } else if (selectBody instanceof SetOperationList) {
            // 处理 UNION 等集合操作
            SetOperationList setOperationList = (SetOperationList) selectBody;
            List<SelectBody> selects = setOperationList.getSelects();
            for (SelectBody selectBodyItem : selects) {
                if (selectBodyItem instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBodyItem;
                    addTenantCondition(plainSelect.getWhere(), plainSelect, tenantId);
                }
            }
        }
    }

    /**
     * 处理 UPDATE 语句
     */
    private void processUpdate(Update update, Long tenantId) {
        // 检查表是否需要租户过滤
        Table table = update.getTable();
        String tableName = table.getName().replaceAll("`", "").toLowerCase();
        if (!needTenantFilter(tableName)) {
            return;
        }

        Expression where = update.getWhere();
        Expression tenantCondition = createTenantCondition(tenantId);
        
        if (where == null) {
            update.setWhere(tenantCondition);
        } else {
            update.setWhere(new AndExpression(where, tenantCondition));
        }
    }

    /**
     * 处理 DELETE 语句
     */
    private void processDelete(Delete delete, Long tenantId) {
        // 检查表是否需要租户过滤
        Table table = delete.getTable();
        String tableName = table.getName().replaceAll("`", "").toLowerCase();
        if (!needTenantFilter(tableName)) {
            return;
        }

        Expression where = delete.getWhere();
        Expression tenantCondition = createTenantCondition(tenantId);
        
        if (where == null) {
            delete.setWhere(tenantCondition);
        } else {
            delete.setWhere(new AndExpression(where, tenantCondition));
        }
    }

    /**
     * 添加租户条件（针对 SELECT）
     */
    private void addTenantCondition(Expression where, PlainSelect plainSelect, Long tenantId) {
        // 获取主表
        Table mainTable = (Table) plainSelect.getFromItem();
        if (mainTable == null) {
            return;
        }

        String tableName = mainTable.getName().replaceAll("`", "").toLowerCase();
        if (!needTenantFilter(tableName)) {
            return;
        }

        // 构建租户条件
        Expression tenantCondition = createTenantCondition(tenantId, mainTable.getAlias());

        if (where == null) {
            plainSelect.setWhere(tenantCondition);
        } else {
            plainSelect.setWhere(new AndExpression(where, tenantCondition));
        }
    }

    /**
     * 创建租户条件表达式
     */
    private Expression createTenantCondition(Long tenantId) {
        return createTenantCondition(tenantId, null);
    }

    /**
     * 创建租户条件表达式（带表别名）
     */
    private Expression createTenantCondition(Long tenantId, net.sf.jsqlparser.expression.Alias alias) {
        Column column = new Column();
        if (alias != null && alias.getName() != null) {
            column.setTable(new Table(alias.getName()));
        }
        column.setColumnName("tenant_id");

        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(new LongValue(tenantId));

        return equalsTo;
    }

    /**
     * 更新 BoundSql 的 SQL
     */
    private void updateBoundSql(BoundSql boundSql, String newSql) {
        try {
            Field sqlField = ReflectionUtils.findField(BoundSql.class, "sql");
            if (sqlField != null) {
                sqlField.setAccessible(true);
                ReflectionUtils.setField(sqlField, boundSql, newSql);
            }
        } catch (Exception e) {
            // 反射设置失败，记录日志但不影响执行
        }
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
