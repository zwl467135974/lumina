package io.lumina.base.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.config.OAuth2Properties;
import io.lumina.base.config.OAuth2Properties.ProviderConfig;
import io.lumina.base.infrastructure.entity.OAuth2IdentityDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.OAuth2IdentityMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.infrastructure.mapper.UserRoleMapper;
import io.lumina.base.service.AuthService;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2LoginServiceImpl 单元测试（state 生命周期、userinfo 解析、首登建号/复用）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class OAuth2LoginServiceImplTest {

    private final OAuth2IdentityMapper identityMapper = Mockito.mock(OAuth2IdentityMapper.class);
    private final UserMapper userMapper = Mockito.mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = Mockito.mock(UserRoleMapper.class);
    private final AuthService authService = Mockito.mock(AuthService.class);
    private final RedisCacheManager redisCacheManager = Mockito.mock(RedisCacheManager.class);
    private final OAuth2HttpClient httpClient = Mockito.mock(OAuth2HttpClient.class);

    private OAuth2Properties properties;
    private OAuth2LoginServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new OAuth2Properties();
        ProviderConfig github = new ProviderConfig();
        github.setClientId("client-1");
        github.setClientSecret("secret-1");
        github.setAuthorizeUrl("https://github.example/authorize");
        github.setTokenUrl("https://github.example/token");
        github.setUserinfoUrl("https://github.example/user");
        github.setScopes("read:user");
        github.setOpenidField("id");
        github.setUsernameField("login");
        properties.getProviders().put("github", github);
        service = new OAuth2LoginServiceImpl(properties, identityMapper, userMapper,
                userRoleMapper, authService, redisCacheManager, httpClient, new ObjectMapper());
    }

    @Test
    void buildAuthorizeUrlStoresStateInRedis() {
        String url = service.buildAuthorizeUrl("github");

        assertThat(url).startsWith("https://github.example/authorize?")
                .contains("client_id=client-1")
                .contains("redirect_uri=")
                .contains("state=");
        verify(redisCacheManager).set(Mockito.startsWith(OAuth2LoginServiceImpl.STATE_KEY_PREFIX),
                eq("github"), any());
    }

    @Test
    void callbackRejectsInvalidState() {
        when(redisCacheManager.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.handleCallback("github", "code-1", "bad-state"))
                .hasMessageContaining("state");
        verify(identityMapper, never()).insert(any(OAuth2IdentityDO.class));
    }

    @Test
    void callbackCreatesUserOnFirstLogin() {
        stubValidState();
        stubTokenAndUserinfo("{\"id\":\"12345\",\"login\":\"octocat\",\"avatar_url\":\"http://a/1.png\"}");
        when(identityMapper.selectOne(any())).thenReturn(null);
        UserDO created = new UserDO();
        created.setUserId(9001L);
        created.setStatus(1);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, UserDO.class).setUserId(9001L);
            return 1;
        });
        when(userMapper.selectByTenantIdAndUsername(0L, "github_octocat")).thenReturn(null);
        LoginVO login = new LoginVO();
        login.setUserId(9001L);
        when(authService.loginByUserId(9001L)).thenReturn(login);

        LoginVO result = service.handleCallback("github", "code-1", "state-1");

        assertThat(result.getUserId()).isEqualTo(9001L);
        verify(userMapper).insert(Mockito.<UserDO>argThat(u ->
                "github_octocat".equals(u.getUsername()) && u.getStatus() == 1
                        && u.getPassword() != null && !u.getPassword().isBlank()));
        verify(userRoleMapper).insert(Mockito.<io.lumina.base.infrastructure.entity.UserRoleDO>argThat(r ->
                r.getUserId().equals(9001L) && r.getRoleId().equals(4L)));
        verify(identityMapper).insert(Mockito.<OAuth2IdentityDO>argThat(i ->
                "github".equals(i.getProvider()) && "12345".equals(i.getOpenId())
                        && i.getUserId().equals(9001L)));
    }

    @Test
    void callbackReusesBoundUserWithoutCreating() {
        stubValidState();
        stubTokenAndUserinfo("{\"id\":\"12345\",\"login\":\"octocat\"}");
        OAuth2IdentityDO existing = new OAuth2IdentityDO();
        existing.setId(7L);
        existing.setUserId(42L);
        existing.setProvider("github");
        existing.setOpenId("12345");
        when(identityMapper.selectOne(any())).thenReturn(existing);
        UserDO bound = new UserDO();
        bound.setUserId(42L);
        bound.setStatus(1);
        bound.setDeleted(0);
        when(userMapper.selectById(42L)).thenReturn(bound);
        LoginVO login = new LoginVO();
        login.setUserId(42L);
        when(authService.loginByUserId(42L)).thenReturn(login);

        LoginVO result = service.handleCallback("github", "code-1", "state-1");

        assertThat(result.getUserId()).isEqualTo(42L);
        verify(userMapper, never()).insert(Mockito.<UserDO>any());
        verify(userRoleMapper, never()).insert(Mockito.<io.lumina.base.infrastructure.entity.UserRoleDO>any());
        verify(identityMapper).updateById(Mockito.<OAuth2IdentityDO>argThat(i -> i.getId().equals(7L)));
    }

    @Test
    void callbackRejectsTokenError() {
        stubValidState();
        when(httpClient.postForm(anyString(), any())).thenReturn(
                "{\"error\":\"bad_verification_code\",\"error_description\":\"code expired\"}");

        assertThatThrownBy(() -> service.handleCallback("github", "bad-code", "state-1"))
                .hasMessageContaining("token");
    }

    @Test
    void callbackRejectsMissingOpenId() {
        stubValidState();
        stubTokenAndUserinfo("{\"login\":\"octocat\"}");

        assertThatThrownBy(() -> service.handleCallback("github", "code-1", "state-1"))
                .hasMessageContaining("唯一标识");
    }

    @Test
    void buildAuthorizeUrlIncludesPkceChallengeWhenEnabled() {
        String url = service.buildAuthorizeUrl("github");

        assertThat(url).contains("code_challenge=").contains("code_challenge_method=S256");
        // verifier 存本方 Redis（与 state 同 TTL），不上送授权页
        verify(redisCacheManager).set(Mockito.startsWith(OAuth2LoginServiceImpl.PKCE_KEY_PREFIX),
                Mockito.argThat(v -> v instanceof String s && s.length() >= 43 && s.length() <= 128),
                any());
        assertThat(url).doesNotContain("code_verifier");
    }

    @Test
    void buildAuthorizeUrlOmitsPkceWhenDisabled() {
        properties.getProviders().get("github").setPkce(false);

        assertThat(service.buildAuthorizeUrl("github")).doesNotContain("code_challenge");
    }

    @Test
    void callbackSendsCodeVerifierToTokenExchange() {
        stubValidState();
        stubTokenAndUserinfo("{\"id\":\"12345\",\"login\":\"octocat\"}");
        OAuth2IdentityDO existing = new OAuth2IdentityDO();
        existing.setId(7L);
        existing.setUserId(42L);
        when(identityMapper.selectOne(any())).thenReturn(existing);
        UserDO bound = new UserDO();
        bound.setUserId(42L);
        bound.setStatus(1);
        bound.setDeleted(0);
        when(userMapper.selectById(42L)).thenReturn(bound);
        when(authService.loginByUserId(42L)).thenReturn(new LoginVO());

        service.handleCallback("github", "code-1", "state-1");

        verify(httpClient).postForm(anyString(), Mockito.<Map<String, String>>argThat(
                form -> "verifier-1".equals(form.get("code_verifier"))));
    }

    @Test
    void callbackRejectsMissingVerifierWhenPkceEnabled() {
        when(redisCacheManager.get(OAuth2LoginServiceImpl.STATE_KEY_PREFIX + "state-1"))
                .thenReturn("github");
        // PKCE 键缺失/过期：fail-closed
        when(redisCacheManager.get(OAuth2LoginServiceImpl.PKCE_KEY_PREFIX + "state-1"))
                .thenReturn(null);

        assertThatThrownBy(() -> service.handleCallback("github", "code-1", "state-1"))
                .hasMessageContaining("code_verifier");
        verify(httpClient, never()).postForm(anyString(), any());
    }

    @Test
    void s256ChallengeMatchesRfc7636AppendixBVector() {
        // RFC 7636 Appendix B 官方测试向量
        assertThat(OAuth2LoginServiceImpl.s256Challenge(
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void generateCodeVerifierIsBase64UrlAndSingleUse() {
        String v1 = OAuth2LoginServiceImpl.generateCodeVerifier();
        String v2 = OAuth2LoginServiceImpl.generateCodeVerifier();

        assertThat(v1).matches("[A-Za-z0-9_-]{43,128}");
        assertThat(v1).isNotEqualTo(v2);
    }

    private void stubValidState() {
        when(redisCacheManager.get(OAuth2LoginServiceImpl.STATE_KEY_PREFIX + "state-1"))
                .thenReturn("github");
        // PKCE 默认开启：verifier 与 state 同键族暂存
        when(redisCacheManager.get(OAuth2LoginServiceImpl.PKCE_KEY_PREFIX + "state-1"))
                .thenReturn("verifier-1");
    }

    private void stubTokenAndUserinfo(String userinfoJson) {
        when(httpClient.postForm(anyString(), any())).thenReturn(
                "{\"access_token\":\"at-1\",\"token_type\":\"bearer\"}");
        when(httpClient.getBearer(anyString(), eq("at-1"))).thenReturn(userinfoJson);
    }
}
