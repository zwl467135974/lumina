package io.lumina.base.service;

import io.lumina.base.api.vo.LoginVO;

import java.util.List;
import java.util.Map;

/**
 * OAuth2 第三方登录服务（标准授权码流程）
 *
 * <p>流程：authorize 跳转三方（state 存 Redis 5 分钟防 CSRF）→ 三方回调
 * code → 换 access_token → 拉 userinfo → 按 (provider, openId) 找绑定用户，
 * 首次登录自动建号（默认角色）并绑定 → 复用 AuthService 签发 Lumina JWT。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface OAuth2LoginService {

    /** 已启用的 Provider 列表（登录页按钮） */
    List<Map<String, Object>> listEnabledProviders();

    /** 构造三方授权页跳转地址（生成并暂存 state） */
    String buildAuthorizeUrl(String provider);

    /**
     * 回调处理：校验 state → 换 token → 拉用户信息 → 登录/建号
     *
     * @return 已签发的登录态
     */
    LoginVO handleCallback(String provider, String code, String state);
}
