package io.lumina.notification.service;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.notification.BaseIntegrationTest;
import io.lumina.notification.api.dto.NotificationQueryDTO;
import io.lumina.notification.api.vo.NotificationVO;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.NotificationDO;
import io.lumina.notification.infrastructure.mapper.NotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NotificationService 集成测试
 *
 * <p>验证通知中心核心链路：事件持久化、分页查询（用户+租户隔离）、未读计数、
 * 单条/全部标记已读、跨租户隔离。
 *
 * <p>测试事务回滚保证不污染数据库；{@link BaseContext} 在每个用例前后设置与清理，
 * 避免线程上下文残留影响其他用例。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Transactional
class NotificationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    private static final Long TENANT_A = 9101L;
    private static final Long USER_A = 9101L;
    private static final Long TENANT_B = 9102L;
    private static final Long USER_B = 9102L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT_A);
        BaseContext.setUserId(USER_A);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void handleEventPersistsNotification() {
        NotificationEvent event = buildEvent(USER_A, TENANT_A, "BUDGET", "预算超限");

        notificationService.handleEvent(event);

        // 直接查 DB 确认持久化（handleEvent 会插入并回填主键）
        // 通过 list 接口查询更贴近真实使用
        NotificationQueryDTO query = new NotificationQueryDTO();
        PageResult<NotificationVO> page = notificationService.list(query);

        assertThat(page.hasData()).isTrue();
        NotificationVO vo = page.getList().get(0);
        assertThat(vo.getUserId()).isEqualTo(USER_A);
        assertThat(vo.getCategory()).isEqualTo("BUDGET");
        assertThat(vo.getTitle()).isEqualTo("预算超限");
        assertThat(vo.getIsRead()).isEqualTo(0);
        assertThat(vo.getSeverity()).isEqualTo("WARN");
    }

    @Test
    void listReturnsOnlyCurrentUserNotifications() {
        // 给 USER_A 与 USER_B 各建一条（同租户）
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "TASK", "A 的任务"));
        notificationService.handleEvent(buildEvent(USER_B, TENANT_A, "TASK", "B 的任务"));

        NotificationQueryDTO query = new NotificationQueryDTO();
        PageResult<NotificationVO> page = notificationService.list(query);

        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().get(0).getUserId()).isEqualTo(USER_A);
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    void getUnreadCountReturnsCorrectNumber() {
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "BUDGET", "n1"));
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "TASK", "n2"));
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "SYSTEM", "n3"));

        long unread = notificationService.getUnreadCount();

        assertThat(unread).isEqualTo(3L);
    }

    @Test
    void markAsReadUpdatesStatus() {
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "BUDGET", "待读"));
        Long id = notificationService.list(new NotificationQueryDTO()).getList().get(0).getId();

        notificationService.markAsRead(id);

        NotificationDO reloaded = notificationMapper.selectById(id);
        assertThat(reloaded.getIsRead()).isEqualTo(1);
        assertThat(reloaded.getReadTime()).isNotNull();

        // 已读计数减 1
        assertThat(notificationService.getUnreadCount()).isEqualTo(0L);
    }

    @Test
    void markAllAsReadClearsAll() {
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "BUDGET", "n1"));
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "TASK", "n2"));
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "SYSTEM", "n3"));
        assertThat(notificationService.getUnreadCount()).isEqualTo(3L);

        notificationService.markAllAsRead();

        assertThat(notificationService.getUnreadCount()).isEqualTo(0L);
    }

    @Test
    void crossTenantIsolation() {
        // 租户 A 的通知
        notificationService.handleEvent(buildEvent(USER_A, TENANT_A, "BUDGET", "租户 A 通知"));

        // 切换到租户 B：查询、计数、标记均不可见
        BaseContext.setTenantId(TENANT_B);
        BaseContext.setUserId(USER_B);

        PageResult<NotificationVO> page = notificationService.list(new NotificationQueryDTO());
        assertThat(page.getList()).isEmpty();
        assertThat(notificationService.getUnreadCount()).isEqualTo(0L);

        // 租户 B 用户去标记 A 的通知 ID 应被拒绝（租户隔离校验）
        // 先查出 A 的通知 ID（切回 A 查，再切到 B 标记）
        BaseContext.setTenantId(TENANT_A);
        BaseContext.setUserId(USER_A);
        Long idA = notificationService.list(new NotificationQueryDTO()).getList().get(0).getId();

        BaseContext.setTenantId(TENANT_B);
        BaseContext.setUserId(USER_B);
        assertThatThrownBy(() -> notificationService.markAsRead(idA))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 辅助方法 ====================

    private NotificationEvent buildEvent(Long userId, Long tenantId, String category, String title) {
        return new NotificationEvent(
                userId,
                category,
                title,
                "通知内容: " + title,
                "WARN",
                "agent_task",
                "ref-" + System.nanoTime(),
                tenantId
        );
    }
}
