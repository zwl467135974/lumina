package io.lumina.base.api.controller;

import io.lumina.base.api.dto.apitoken.CreateApiTokenDTO;
import io.lumina.base.api.dto.apitoken.ValidateApiTokenDTO;
import io.lumina.base.api.vo.apitoken.ApiTokenUserVO;
import io.lumina.base.api.vo.apitoken.ApiTokenVO;
import io.lumina.base.service.ApiTokenService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Token 控制器
 *
 * <p>自助管理外部调用 Token（创建/列表/撤销），仅能操作当前用户自己的 Token。
 * {@code /validate} 为 Gateway 内部校验接口。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Tag(name = "API Token 管理", description = "外部调用 API Token 的创建、列表、撤销")
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/base/api-tokens")
public class ApiTokenController {

    private final ApiTokenService apiTokenService;

    /**
     * 创建 API Token（明文只在本次响应返回一次）
     */
    @Audit(module = "api_token", action = "CREATE", description = "创建API Token")
    @PostMapping
    public R<ApiTokenVO> createToken(@Valid @RequestBody CreateApiTokenDTO dto) {
        log.info("创建 API Token: name={}", dto.getName());
        return R.success(apiTokenService.createToken(dto));
    }

    /**
     * 查询当前用户的 API Token 列表
     */
    @GetMapping
    public R<List<ApiTokenVO>> listTokens() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return R.success(apiTokenService.listTokens(userId));
    }

    /**
     * 撤销 API Token（仅限本人，撤销后立即失效）
     */
    @Audit(module = "api_token", action = "DELETE", description = "撤销API Token")
    @DeleteMapping("/{id}")
    public R<Void> revokeToken(@PathVariable("id") Long id) {
        log.info("撤销 API Token: id={}", id);
        apiTokenService.revokeToken(id);
        return R.success();
    }

    /**
     * 校验 API Token（Gateway 内部调用，无效时 data 为 null）
     */
    @PostMapping("/validate")
    public R<ApiTokenUserVO> validateToken(@Valid @RequestBody ValidateApiTokenDTO dto) {
        return R.success(apiTokenService.validateToken(dto.getToken()));
    }
}
