package io.github.shiyi.auth.user.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.entity.SysMessage;
import io.github.shiyi.auth.user.service.MessageService;

/**
 * M11: the message center API. Every endpoint is scoped to the session user's
 * username; the only admin surface is the announcement broadcast.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> inbox(Authentication auth,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SysMessage> result = messages.inbox(auth.getName(), unreadOnly, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.getTotal());
        body.put("page", result.getCurrent());
        body.put("size", result.getSize());
        body.put("rows", result.getRecords().stream().map(MessageController::view).toList());
        return ApiResult.ok(body);
    }

    @GetMapping("/unread-count")
    public ApiResult<Map<String, Object>> unreadCount(Authentication auth) {
        return ApiResult.ok(Map.of("count", messages.unreadCount(auth.getName())));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Map<String, Object>> markRead(@PathVariable long id, Authentication auth) {
        if (!messages.markRead(id, auth.getName())) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在或不属于当前用户");
        }
        return ApiResult.ok(Map.of("count", messages.unreadCount(auth.getName())));
    }

    @PostMapping("/read-all")
    public ApiResult<Map<String, Object>> markAllRead(Authentication auth) {
        return ApiResult.ok(Map.of("updated", messages.markAllRead(auth.getName())));
    }

    public record AnnounceRequest(String title, String body, String link, String roleCode) {
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Map<String, Object>> announce(@RequestBody AnnounceRequest req, Authentication auth) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "公告标题不能为空");
        }
        if (req.title().length() > 200 || (req.body() != null && req.body().length() > 1000)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "公告标题或正文超长");
        }
        int written = messages.announce(auth.getName(), req.title().trim(), req.body(),
                req.link(), req.roleCode());
        return ApiResult.ok(Map.of("delivered", written));
    }

    private static Map<String, Object> view(SysMessage m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("type", m.getType());
        v.put("event", m.getEvent());
        v.put("title", m.getTitle());
        v.put("body", m.getBody());
        v.put("link", m.getLink());
        v.put("sender", m.getSender());
        v.put("read", m.getReadAt() != null);
        v.put("readAt", m.getReadAt() == null ? null : m.getReadAt().toString());
        v.put("createdAt", m.getCreateTime() == null
                ? null : m.getCreateTime().toString());
        return v;
    }
}
