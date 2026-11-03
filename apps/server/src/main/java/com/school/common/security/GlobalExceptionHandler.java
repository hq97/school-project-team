package com.school.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一错误体：{@code { code, message, data, traceId, timestamp, path }}（TS 语义来源：biz-exception.filter.ts）。
 *
 * <p>40301–40304 / 40901–40904 由前端 axios 拦截器统一提示，这里不做二次包装，保证 {@code code} 原样透出。</p>
 *
 * <p>覆盖范围：{@link BizException}（业务异常）、{@link MethodArgumentNotValidException}（入参校验失败 → 40000）、
 * 兜底 500。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException ex, HttpServletRequest req) {
        // AC-F2-1：业务异常携带了具体冲突原因（如「班级已在该时段有课」「课表版本已更新」）时
        // 优先透出 ex.getMessage()（BizException.conflict(String) 构造时塞入的自定义 message），
        // 而非 bizCode 的通用固定文案（如「操作冲突，请刷新后重试」）。
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank()
                && !ex.getMessage().equals(ex.bizCode().message()))
                ? ex.getMessage() : ex.bizCode().message();
        Map<String, Object> body = baseBody(req, ex.bizCode().code(), message);
        if (ex.data() != null) {
            body.put("data", ex.data());
        }
        return ResponseEntity.status(ex.bizCode().httpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("field", fe.getField());
            e.put("message", fe.getDefaultMessage());
            errors.add(e);
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", errors);
        Map<String, Object> body = baseBody(req, BizCode.PARAM_INVALID.code(), BizCode.PARAM_INVALID.message());
        body.put("data", data);
        return ResponseEntity.status(BizCode.PARAM_INVALID.httpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex, HttpServletRequest req) {
        log.error("未捕获异常 traceId={} path={}", req.getAttribute("traceId"), req.getRequestURI(), ex);
        Map<String, Object> body = baseBody(req, 500, "服务内部错误");
        return ResponseEntity.status(500).body(body);
    }

    private Map<String, Object> baseBody(HttpServletRequest req, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("traceId", traceId(req));
        body.put("timestamp", Instant.now().toString());
        body.put("path", req.getRequestURI());
        return body;
    }

    private String traceId(HttpServletRequest req) {
        Object t = req.getAttribute("traceId");
        if (t instanceof String s && !s.isEmpty()) {
            return s;
        }
        return "t-" + Long.toString(System.currentTimeMillis(), 36) + "-" + Integer.toString((int) (Math.random() * 0xFFFFFF), 36);
    }
}
