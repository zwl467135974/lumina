package io.lumina.base.api.controller;

import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.service.OAuth2LoginService;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 第三方登录 API（路径已加入认证白名单）
 *
 * <p>authorize 302 跳三方授权页；callback 换取登录态后 302 回前端
 * `${frontend-redirect-uri}?token=...`（前端回调页存 Token 后跳首页）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "OAuth2 登录", description = "第三方授权码登录：GitHub 预设 + 通用 OIDC")
@RestController
@RequestMapping("/api/v1/base/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2AuthController {

    private final OAuth2LoginService oAuth2LoginService;

    @Operation(summary = "已启用的三方登录 Provider（登录页按钮）")
    @GetMapping("/providers")
    public R<List<Map<String, Object>>> providers() {
        return R.success(oAuth2LoginService.listEnabledProviders());
    }

    @Operation(summary = "跳转三方授权页")
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable("provider") String provider) {
        String url = oAuth2LoginService.buildAuthorizeUrl(provider);
        log.info("OAuth2 authorize: provider={}", provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @Operation(summary = "三方回调（换 token → 登录/建号 → 302 回前端带 token）")
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") String provider,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         HttpServletRequest request) {
        LoginVO login = oAuth2LoginService.handleCallback(provider, code, state);
        // 前端回调地址取请求 Origin（同源部署），异常时由全局错误处理器返回 JSON
        String origin = request.getHeader("Origin") != null
                ? request.getHeader("Origin")
                : "http://localhost:5173";
        String redirect = UriComponentsBuilder.fromHttpUrl(origin + "/oauth2/callback")
                .queryParam("token", login.getToken())
                .queryParam("username", login.getUsername())
                .build().toUriString();
        log.info("OAuth2 callback 完成: provider={}, username={}, redirect={}", provider, login.getUsername(), origin);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect)
                .build();
    }
}
