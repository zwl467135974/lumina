package io.lumina.base.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import io.lumina.common.core.BaseContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 租户行处理器实现
 *
 * <p>启动时自动检测包含 {@code tenant_id} 列的表，仅对这些表自动添加租户隔离条件。
 * 若检测失败则退化为全拦截模式（除 FALLBACK_IGNORE_TABLES 外全部拦截）。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantLineHandlerImpl implements TenantLineHandler {

    private static final List<String> FALLBACK_IGNORE_TABLES = Arrays.asList(
        "lumina_permission",
        "lumina_role_permission",
        "lumina_user_role",
        "lumina_tenant"
    );

    private static final String DETECT_SQL =
        "SELECT table_name FROM information_schema.columns " +
        "WHERE column_name = 'tenant_id' AND table_schema = DATABASE()";

    private final DataSource dataSource;

    private volatile Set<String> tablesWithTenantId = null;

    @PostConstruct
    public void detectTenantTables() {
        Set<String> detected = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DETECT_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                detected.add(rs.getString("table_name").toLowerCase());
            }
            tablesWithTenantId = detected;
            log.info("租户隔离自动检测完成，共 {} 张表包含 tenant_id 列: {}",
                detected.size(), detected);
        } catch (Exception e) {
            log.warn("租户隔离表自动检测失败，退化为全拦截模式（仅忽略 FALLBACK_IGNORE_TABLES）", e);
            tablesWithTenantId = null;
        }
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = BaseContext.getTenantId();
        if (tenantId == null) {
            return new LongValue(0);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        Set<String> detected = tablesWithTenantId;
        if (detected != null) {
            return !detected.contains(tableName.toLowerCase());
        }
        return FALLBACK_IGNORE_TABLES.stream().anyMatch(tableName::equalsIgnoreCase);
    }
}
