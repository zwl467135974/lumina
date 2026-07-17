package io.lumina.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Webhook 订阅 Mapper
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Mapper
public interface WebhookMapper extends BaseMapper<WebhookDO> {

    /**
     * 查询某用户某事件类别的启用 webhook（events 含 * 或含该类别）
     *
     * <p>租户条件由 TenantLineHandler 自动追加，调用方需保证 BaseContext 已设置 tenantId。
     *
     * @param userId 订阅用户 ID
     * @param event  事件类别（NotificationCategory.name()）
     * @return 启用的 webhook 列表
     */
    @Select("SELECT * FROM lumina_webhook " +
            "WHERE user_id = #{userId} AND enabled = 1 AND deleted = 0 " +
            "AND (events = '*' OR FIND_IN_SET(#{event}, events) > 0)")
    List<WebhookDO> selectEnabledByUserAndEvent(@Param("userId") Long userId, @Param("event") String event);

    /**
     * 更新 webhook 发送状态（含最后触发时间与失败计数）
     *
     * @param id        webhook ID
     * @param status    SUCCESS/FAILED
     * @param error     失败原因（成功时传 null）
     * @param failCount 连续失败次数（成功时重置为 0）
     * @param enabled   启用状态（null 表示不变更，自动禁用时传 0）
     * @return 影响行数
     */
    @Update("UPDATE lumina_webhook SET last_status = #{status}, last_error = #{error}, " +
            "fail_count = #{failCount}, last_triggered_at = NOW(), " +
            "enabled = COALESCE(#{enabled}, enabled) " +
            "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("error") String error, @Param("failCount") int failCount,
                     @Param("enabled") Integer enabled);
}
