package io.github.shiyi.auth.user.web;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User management API (read-only stub for skeleton stage).
 *
 * Security: matched by the /api/** filter chain, requires an authenticated session.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResult<List<UserDTO>> list() {
        return ApiResult.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResult<UserDTO> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND)));
    }
}
