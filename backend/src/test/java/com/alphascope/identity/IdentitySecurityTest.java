package com.alphascope.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.RequestFilter;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.entity.AuthSession;
import com.alphascope.identity.service.AuthService;
import com.alphascope.identity.service.PasswordHasher;
import com.alphascope.identity.vo.UserView;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 身份来源、嵌套恢复、密码校验及浏览器入口的拒绝测试。
 *
 * @author chenxuefeng
 */
class IdentitySecurityTest {
  @Test
  void localProxyRequestUsesReservedWorkspaceAndReleasesIdentityAfterTheRequest() throws Exception {
    var auth = mock(AuthService.class);
    var filter = new RequestFilter(new AccessProperties(), auth);
    var request = new MockHttpServletRequest("GET", "/api/v1/watchlist");
    request.setRemoteAddr("192.0.2.1");
    request.addHeader("X-Forwarded-For", "198.51.100.1");
    request.addHeader("Forwarded", "for=198.51.100.1;proto=https");
    var response = new MockHttpServletResponse();
    var calls = new AtomicInteger();
    filter.doFilter(
        request,
        response,
        (r, s) -> {
          assertEquals(1, TenantContext.id());
          assertEquals("ADMIN", TenantContext.user().role());
          calls.incrementAndGet();
        });
    assertEquals(200, response.getStatus());
    assertEquals(1, calls.get());
    assertNull(TenantContext.user());
  }

  @Test
  void passwordHashesHaveIndependentSaltsAndRejectMalformedOrWrongValues() {
    var hasher = new PasswordHasher();
    String first = hasher.hash("test-only-long-password"),
        second = hasher.hash("test-only-long-password");
    assertNotEquals(first, second);
    assertTrue(hasher.matches("test-only-long-password", first));
    assertFalse(hasher.matches("different-password", first));
    assertFalse(hasher.matches("anything", "pbkdf2-sha256$1$invalid$invalid"));
  }

  @Test
  void nestedExecutionRestoresPreviousIdentity() {
    assertNull(TenantContext.user());
    try (var outer = TenantContext.bind(new UserView(2, "alice", "USER"))) {
      assertEquals(2, TenantContext.id());
      try (var inner = TenantContext.bind(new UserView(3, "bob", "USER"))) {
        assertEquals(3, TenantContext.id());
      }
      assertEquals(2, TenantContext.id());
    }
    assertNull(TenantContext.user());
  }

  @Test
  void forwardedHeadersCannotAuthenticateAndAmbiguousAdminPathsAreRejected() throws Exception {
    var auth = mock(AuthService.class);
    var config = new AccessProperties();
    config.setMode(AccessProperties.Mode.PUBLIC);
    config.setOrigins(List.of("https://research.example.test"));
    var filter = new RequestFilter(config, auth);
    var chain = new AtomicInteger();
    var request = new MockHttpServletRequest("GET", "/api/v1/watchlist");
    request.addHeader("X-Forwarded-For", "127.0.0.1");
    request.addHeader("Forwarded", "for=127.0.0.1;proto=https");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (r, s) -> chain.incrementAndGet());
    assertEquals(401, response.getStatus());
    assertEquals(0, chain.get());
    request = new MockHttpServletRequest("POST", "/api/v1/admin/financials;ignored");
    request.addHeader("Origin", "https://research.example.test");
    response = new MockHttpServletResponse();
    filter.doFilter(request, response, (r, s) -> chain.incrementAndGet());
    assertEquals(400, response.getStatus());
    assertEquals(0, chain.get());
  }

  @Test
  void usersBehindOneProxyDoNotThrottleEachOtherAndCancellationIsNotBlocked() throws Exception {
    var auth = mock(AuthService.class);
    var config = new AccessProperties();
    config.setMode(AccessProperties.Mode.PUBLIC);
    config.setOrigins(List.of("https://research.example.test"));
    var filter = new RequestFilter(config, auth);
    var calls = new AtomicInteger();
    for (int user = 2; user <= 3; user++) {
      String token = "token" + user;
      var session = new AuthSession();
      session.setUserId((long) user);
      session.setUsername("user" + user);
      session.setRole("USER");
      session.setCsrfHash(Jsons.hash("csrf"));
      when(auth.authenticate(token)).thenReturn(session);
      for (String path : List.of("/api/v1/watchlist", "/api/v1/admin/jobs/1/cancel")) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(new Cookie("ALPHASCOPE_SESSION", token));
        request.addHeader("Origin", "https://research.example.test");
        request.addHeader("X-CSRF-Token", "csrf");
        var response = new MockHttpServletResponse();
        filter.doFilter(
            request,
            response,
            (r, s) -> {
              assertNotNull(TenantContext.user());
              calls.incrementAndGet();
            });
        assertEquals(200, response.getStatus());
      }
    }
    assertEquals(4, calls.get());
    assertNull(TenantContext.user());
  }
}
