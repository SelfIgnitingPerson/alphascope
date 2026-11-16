package com.alphascope.common.config;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.Result;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.controller.AuthController;
import com.alphascope.identity.service.AuthService;
import com.alphascope.identity.vo.UserView;
import com.alphascope.operations.service.SourceLicenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 服务端请求编号、有效会话、角色、浏览器CSRF与身份级限频；不信任转发IP来授权。
 *
 * @author chenxuefeng
 */
@Component
public class RequestFilter extends OncePerRequestFilter {
  private final ConcurrentHashMap<String, Long> writes = new ConcurrentHashMap<>();
  private final AccessProperties properties;
  private final AuthService auth;
  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  /** 供隔离协议测试构建本地过滤器。 */
  public RequestFilter() {
    this(new AccessProperties(), null);
  }

  @Autowired
  public RequestFilter(AccessProperties properties, AuthService auth) {
    this.properties = properties;
    this.auth = auth;
  }

  /** {@inheritDoc} */
  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    r.setAttribute("requestId", id);
    s.setHeader("X-Request-Id", id);
    MDC.put("requestId", id);
    s.setHeader("X-Content-Type-Options", "nosniff");
    s.setHeader("Referrer-Policy", "same-origin");
    String path = r.getRequestURI();
    boolean api = path.startsWith("/api/");
    boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod());
    boolean publicMode = properties.getMode() == AccessProperties.Mode.PUBLIC;
    try {
      if (!api) {
        if (publicMode && (path.startsWith("/swagger") || path.startsWith("/v3/api-docs"))) {
          reject(s, 404, ResultCode.NOT_FOUND, "接口不存在", id);
          return;
        }
        chain.doFilter(r, s);
        return;
      }
      if (path.contains(";")
          || path.contains("//")
          || path.contains("%")
          || path.contains("\\")
          || path.contains("/../")
          || path.contains("/./")) {
        reject(s, 400, ResultCode.INVALID_ARGUMENT, "请求路径无效", id);
        return;
      }
      if (!publicMode
          && (!loopback(r.getRemoteAddr())
              || r.getHeader("X-Forwarded-For") != null
              || r.getHeader("Forwarded") != null)) {
        reject(s, 403, ResultCode.LOCAL_ONLY, "本地模式仅允许回环访问", id);
        return;
      }
      if (r.getMethod().equals("OPTIONS")) {
        chain.doFilter(r, s);
        return;
      }
      if (publicMode && write && !properties.getOrigins().contains(r.getHeader("Origin"))) {
        reject(s, 403, ResultCode.FORBIDDEN, "写请求来源不被允许", id);
        return;
      }
      boolean entry =
          Set.of("/api/v1/auth/status", "/api/v1/auth/login", "/api/v1/auth/register")
              .contains(path);
      if (entry) {
        chain.doFilter(r, s);
        return;
      }
      var session =
          auth == null ? null : auth.authenticate(AuthController.cookie(r, AuthController.SESSION));
      UserView user =
          session == null
              ? null
              : new UserView(session.getUserId(), session.getUsername(), session.getRole());
      if (publicMode && user == null) {
        reject(s, 401, ResultCode.UNAUTHENTICATED, "请登录后继续，原有研究数据已保留", id);
        return;
      }
      if (user == null) user = new UserView(1, "local-owner", "ADMIN");
      if (publicMode && write) {
        String csrf = r.getHeader("X-CSRF-Token");
        if (csrf == null
            || csrf.length() > 128
            || !MessageDigest.isEqual(
                Jsons.hash(csrf).getBytes(StandardCharsets.UTF_8),
                session.getCsrfHash().getBytes(StandardCharsets.UTF_8))) {
          reject(s, 403, ResultCode.FORBIDDEN, "会话校验失败，请刷新后重试", id);
          return;
        }
      }
      if (adminOnly(path, write) && !"ADMIN".equals(user.role())) {
        reject(s, 403, ResultCode.FORBIDDEN, "此操作需要管理员权限", id);
        return;
      }
      if (publicMode && licenses != null && licensedPath(path)) {
        try {
          licenses.requireDisplay();
          if (path.endsWith("/evidence")) licenses.requireAll("EXPORT");
        } catch (BusinessException error) {
          reject(s, error.getStatus(), error.getCode(), error.getMessage(), id);
          return;
        }
      }
      if (write && !path.endsWith("/cancel") && !path.equals("/api/v1/auth/logout")) {
        // 成功接收后才更新限频时刻，取消不受普通写请求节流影响。
        String key = Long.toString(user.id());
        long now = System.currentTimeMillis();
        synchronized (writes) {
          Long old = writes.get(key);
          if (old != null && now - old < 1000) {
            reject(s, 429, ResultCode.RATE_LIMITED, "操作过于频繁，请稍后重试", id);
            return;
          }
          if (writes.size() > 10000)
            writes.entrySet().removeIf(entryValue -> now - entryValue.getValue() > 60000);
          writes.put(key, now);
        }
      }
      MDC.put("tenantId", Long.toString(user.id()));
      try (var ignored = TenantContext.bind(user)) {
        chain.doFilter(r, s);
      }
    } finally {
      MDC.clear();
    }
  }

  private boolean adminOnly(String path, boolean write) {
    if (!path.startsWith("/api/v1/admin/")) return false;
    return !(path.equals("/api/v1/admin/jobs")
        || path.startsWith("/api/v1/admin/jobs/")
        || path.equals("/api/v1/admin/backtest-experiments")
        || path.startsWith("/api/v1/admin/backtest-experiments/")
        || path.equals("/api/v1/admin/backtest-validation-tasks")
        || path.startsWith("/api/v1/admin/backtest-validation-tasks/"));
  }

  private boolean licensedPath(String path) {
    return path.startsWith("/api/v1/backtest/")
        || path.startsWith("/api/v1/market/")
        || path.startsWith("/api/v1/themes")
        || path.startsWith("/api/v1/expectations")
        || path.startsWith("/api/v1/reports")
        || path.matches("/api/v1/instruments/[^/]+/(bars|financials)");
  }

  private boolean loopback(String ip) {
    return Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(ip);
  }

  private void reject(
      HttpServletResponse response, int status, ResultCode code, String message, String id)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(Jsons.write(Result.failure(code, message, id)));
  }
}
