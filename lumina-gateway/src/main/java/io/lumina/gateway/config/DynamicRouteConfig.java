package io.lumina.gateway.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Gateway 动态路由配置
 * 从 Nacos 读取路由配置并动态刷新
 *
 * @author Lumina Framework
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class DynamicRouteConfig {

    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private ConfigService nacosConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Nacos 配置的 Data ID
     */
    private static final String ROUTE_DATA_ID = "gateway-routes.json";

    /**
     * Nacos 配置的 Group
     */
    private static final String ROUTE_GROUP = "GATEWAY_GROUP";

    /**
     * Nacos 配置的命名空间
     */
    @NacosValue(value = "${spring.cloud.nacos.config.namespace:public}", autoRefreshed = true)
    private String namespace;

    /**
     * 初始化动态路由
     */
    @PostConstruct
    public void initDynamicRoutes() {
        try {
            // 从 Nacos 加载初始路由配置
            loadRoutesFromNacos();

            // 清理已删除的静态路由（保留动态路由）
            cleanObsoleteStaticRoutes();

            log.info("Gateway 动态路由初始化完成");
        } catch (Exception e) {
            log.error("Gateway 动态路由初始化失败", e);
        }
    }

    /**
     * 从 Nacos 加载路由配置
     */
    private void loadRoutesFromNacos() {
        try {
            String config = nacosConfigService.getConfig(
                ROUTE_DATA_ID,
                ROUTE_GROUP,
                5000
            );

            if (config != null && !config.trim().isEmpty()) {
                List<RouteDefinition> routeDefinitions = objectMapper.readValue(
                    config,
                    new TypeReference<List<RouteDefinition>>() {}
                );

                // 更新路由
                routeDefinitions.forEach(this::updateRoute);

                log.info("从 Nacos 加载了 {} 条动态路由", routeDefinitions.size());
            } else {
                log.warn("Nacos 中未找到路由配置，使用默认静态路由");
            }
        } catch (Exception e) {
            log.error("从 Nacos 加载路由配置失败", e);
        }
    }

    /**
     * 监听 Nacos 配置变化并动态刷新路由
     */
    @NacosConfigListener(dataId = ROUTE_DATA_ID, groupId = ROUTE_GROUP)
    public void onRouteConfigChanged(String newConfig) {
        log.info("检测到 Nacos 路由配置变化，开始刷新路由...");

        try {
            List<RouteDefinition> routeDefinitions = objectMapper.readValue(
                newConfig,
                new TypeReference<List<RouteDefinition>>() {}
            );

            // 获取当前所有路由
            Flux<RouteDefinition> currentRoutes = routeDefinitionLocator.getRouteDefinitions();
            CountDownLatch latch = new CountDownLatch(1);

            currentRoutes.collectList().subscribe(routes -> {
                try {
                    // 删除所有动态路由（保留静态路由）
                    routes.forEach(route -> {
                        if (isDynamicRoute(route.getId())) {
                            routeDefinitionWriter.delete(Mono.just(route.getId())).subscribe();
                            log.debug("删除动态路由: {}", route.getId());
                        }
                    });

                    // 添加新的路由配置
                    routeDefinitions.forEach(this::updateRoute);

                    log.info("路由刷新完成，当前共有 {} 条路由", routeDefinitions.size());
                } finally {
                    latch.countDown();
                }
            });

            latch.await();
        } catch (Exception e) {
            log.error("刷新路由配置失败", e);
        }
    }

    /**
     * 更新或添加路由
     *
     * @param routeDefinition 路由定义
     */
    private void updateRoute(RouteDefinition routeDefinition) {
        try {
            routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
            log.info("更新路由: {} -> {}", routeDefinition.getId(), routeDefinition.getUri());
        } catch (Exception e) {
            log.error("更新路由失败: {}", routeDefinition.getId(), e);
        }
    }

    /**
     * 判断是否为动态路由
     *
     * @param routeId 路由ID
     * @return true 如果是动态路由，false 否则
     */
    private boolean isDynamicRoute(String routeId) {
        // 动态路由以 "dynamic-" 开头
        return routeId != null && routeId.startsWith("dynamic-");
    }

    /**
     * 清理已过时的静态路由
     * 删除在配置文件中定义但已被 Nacos 动态路由替代的静态路由
     */
    private void cleanObsoleteStaticRoutes() {
        // 这里可以实现自定义逻辑，例如：
        // 1. 读取 application.yml 中定义的静态路由
        // 2. 对比 Nacos 中的动态路由
        // 3. 删除重复的静态路由

        log.debug("清理过时的静态路由");
    }

    /**
     * 手动刷新路由（可通过 Actuator 端点调用）
     */
    public void refreshRoutes() {
        log.info("手动刷新路由...");
        loadRoutesFromNacos();
    }

    /**
     * 获取所有路由信息（用于监控和调试）
     *
     * @return 路由定义列表
     */
    public Flux<RouteDefinition> getAllRoutes() {
        return routeDefinitionLocator.getRouteDefinitions();
    }
}
