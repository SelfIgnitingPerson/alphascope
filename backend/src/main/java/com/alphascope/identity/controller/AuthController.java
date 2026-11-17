package com.alphascope.identity.controller;

import com.alphascope.common.response.Result;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.dto.IssuedSession;
import com.alphascope.identity.dto.LoginInput;
import com.alphascope.identity.dto.RegistrationInput;
import com.alphascope.identity.service.AuthService;
import com.alphascope.identity.vo.AuthStatusView;
import com.alphascope.identity.vo.UserView;
import com.alphascope.operations.policy.OperationalData;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号与Cookie会话入口；令牌不出现在JSON、URL或浏览器本地存储。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public static final String SESSION = "ALPHASCOPE_SESSION";
  public static final String CSRF = "ALPHASCOPE_CSRF";
  private final AuthService service;
  private final AccessProperties properties;

  @Value("${alphascope.research.position-risk-enabled:false}")
  private boolean positionRiskEnabled;

  public AuthController(AuthService service, AccessProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  /**
   * 读取登录页所需的功能开关。
   *
   * @return 不含凭据的配置
   */
  @GetMapping("/status")
  public Result<AuthStatusView> status() {
    return Result.success(
        new AuthStatusView(
            properties.getMode().name(), properties.isRegistrationEnabled(), positionRiskEnabled));
  }

  /**
   * 登录并通过Cookie签发会话。
   *
   * @param input 账号及密码
   * @param response Cookie写入目标
   * @return 公开身份
   */
  @PostMapping("/login")
  public Result<UserView> login(
      @Valid @RequestBody LoginInput input, HttpServletResponse response) {
    return issue(service.login(input), response);
  }

  /**
   * 使用邀请注册新账号及空白研究范围。
   *
   * @param input 注册资料
   * @param response Cookie写入目标
   * @return 新用户身份
   */
  @PostMapping("/register")
  public Result<UserView> register(
      @Valid @RequestBody RegistrationInput input, HttpServletResponse response) {
    return issue(service.register(input), response);
  }

  /**
   * 读取已验证的当前用户，不接受用户号参数。
   *
   * @return 当前身份
   */
  @GetMapping("/me")
  public Result<UserView> me() {
    return Result.success(TenantContext.user());
  }

  /**
   * 使当前会话失效并清除Cookie。
   *
   * @param request 当前Cookie
   * @param response Cookie清理目标
   * @return 统一成功响应
   */
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    service.logout(cookie(request, SESSION));
    set(response, SESSION, "", true, 0);
    set(response, CSRF, "", false, 0);
    return Result.success();
  }

  private Result<UserView> issue(IssuedSession issued, HttpServletResponse response) {
    set(response, SESSION, issued.token(), true, properties.getSessionSeconds());
    set(response, CSRF, issued.csrf(), false, properties.getSessionSeconds());
    return Result.success(issued.user());
  }

  private void set(
      HttpServletResponse response, String name, String value, boolean httpOnly, int seconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(properties.isSecureCookie())
            .sameSite("Strict")
            .path("/")
            .maxAge(seconds)
            .build()
            .toString());
  }

  /**
   * 读取单个Cookie，拒绝同名重复值以避免代理解释差异。
   *
   * @param request 当前请求
   * @param name Cookie名称
   * @return 唯一值或null
   */
  public static String cookie(HttpServletRequest request, String name) {
    String value = null;
    if (request.getCookies() != null)
      for (Cookie cookie : request.getCookies())
        if (name.equals(cookie.getName())) {
          if (value != null) return null;
          value = cookie.getValue();
        }
    return value;
  }
}
