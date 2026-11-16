package com.alphascope.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.dto.SourceLicenseInput;
import com.alphascope.operations.entity.SourceLicense;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.operations.mapper.PlatformCapacityMapper;
import com.alphascope.operations.mapper.SourceLicenseMapper;
import com.alphascope.operations.service.impl.AlertPublisherImpl;
import com.alphascope.operations.service.impl.CapacityServiceImpl;
import com.alphascope.operations.service.impl.SourceLicenseServiceImpl;
import com.alphascope.operations.vo.PlatformUsage;
import com.alphascope.operations.vo.ResearchCapacity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 验证实际许可状态、角色与平台总量边界；不以配置开关冒充取得授权。
 *
 * @author chenxuefeng
 */
class ProductPolicyTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void reportedLicenseGateMatchesLocalOverrideAndMandatoryPublicChecks() {
    var mapper = mock(SourceLicenseMapper.class);
    when(mapper.latest()).thenReturn(List.of());
    var access = new AccessProperties();
    var options = new ProductProperties();
    var service =
        new SourceLicenseServiceImpl(
            mapper, options, access, new AlphaScopeProperties(), new MockEnvironment(), clock);
    assertFalse(service.isEnforced());
    assertDoesNotThrow(() -> service.require("SINA", "DISPLAY"));
    assertDoesNotThrow(service::requireDisplay);
    options.setEnforceSourceLicense(true);
    assertTrue(service.isEnforced());
    assertThrows(BusinessException.class, () -> service.require("SINA", "DISPLAY"));
    assertThrows(BusinessException.class, service::requireDisplay);
    options.setEnforceSourceLicense(false);
    access.setMode(AccessProperties.Mode.PUBLIC);
    assertTrue(service.isEnforced());
    assertThrows(BusinessException.class, () -> service.require("SINA", "DISPLAY"));
    assertThrows(BusinessException.class, service::requireDisplay);
  }

  @Test
  void publicSourcePermissionsAreExplicitAndExpireRatherThanFallingBackToOldGrants() {
    var mapper = mock(SourceLicenseMapper.class);
    when(mapper.latest()).thenReturn(List.of());
    var access = new AccessProperties();
    access.setMode(AccessProperties.Mode.PUBLIC);
    var service =
        new SourceLicenseServiceImpl(
            mapper,
            new ProductProperties(),
            access,
            new AlphaScopeProperties(),
            new MockEnvironment(),
            clock);
    assertEquals("MISSING", service.status().getFirst().status());
    assertThrows(BusinessException.class, () -> service.require("EASTMONEY_DIRECTORY", "DISPLAY"));
    var grant = new SourceLicense();
    grant.setSource("EASTMONEY_DIRECTORY");
    grant.setPermissionsJson("[\"DISPLAY\"]");
    grant.setValidFrom(LocalDate.of(2026, 1, 1));
    grant.setValidUntil(LocalDate.of(2026, 12, 31));
    when(mapper.latest()).thenReturn(List.of(grant));
    assertDoesNotThrow(() -> service.require("EASTMONEY_DIRECTORY", "DISPLAY"));
    assertThrows(BusinessException.class, () -> service.require("EASTMONEY_DIRECTORY", "COLLECT"));
    grant.setValidUntil(LocalDate.of(2026, 11, 8));
    assertEquals("EXPIRED", service.status().getFirst().status());
    assertThrows(BusinessException.class, () -> service.require("EASTMONEY_DIRECTORY", "DISPLAY"));
  }

  @Test
  void onlyAdministratorCanDeclareAndUnsafeEvidenceIsRejected() {
    var mapper = mock(SourceLicenseMapper.class);
    var service =
        new SourceLicenseServiceImpl(
            mapper,
            new ProductProperties(),
            new AccessProperties(),
            new AlphaScopeProperties(),
            new MockEnvironment(),
            clock);
    var input =
        new SourceLicenseInput(
            "SINA",
            Set.of("DISPLAY"),
            "https://example.test/license",
            "fixture-only",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31));
    try (var ignored = TenantContext.bind(new UserView(2, "alice", "USER"))) {
      assertEquals(
          ResultCode.FORBIDDEN,
          assertThrows(BusinessException.class, () -> service.declare(input)).getCode());
    }
    try (var ignored = TenantContext.bind(new UserView(1, "owner", "ADMIN"))) {
      service.declare(input);
      verify(mapper)
          .save(argThat(row -> row.getReviewerId() == 1 && row.getSource().equals("SINA")));
      assertThrows(
          BusinessException.class,
          () ->
              service.declare(
                  new SourceLicenseInput(
                      "SINA",
                      Set.of("DISPLAY"),
                      "http://example.test/license",
                      "fixture-only",
                      input.validFrom(),
                      input.validUntil())));
    }
  }

  @Test
  void platformAndUserLimitsBothRejectWithoutDeletingEvidence() {
    var mapper = mock(CapacityMapper.class);
    var platform = mock(PlatformCapacityMapper.class);
    var options = new ProductProperties();
    options.setMaximumJobs(2);
    options.setMaximumPlatformJobs(3);
    var service = new CapacityServiceImpl(mapper, options);
    service.setPlatform(platform);
    when(mapper.capacity()).thenReturn(new ResearchCapacity(0, 0, 0, null));
    when(platform.usage()).thenReturn(new PlatformUsage(3, 0, 0));
    assertThrows(BusinessException.class, service::requireJobSlot);
    when(platform.usage()).thenReturn(new PlatformUsage(0, 0, 0));
    when(mapper.capacity()).thenReturn(new ResearchCapacity(2, 0, 0, null));
    assertThrows(BusinessException.class, service::requireJobSlot);
    when(mapper.capacity()).thenReturn(new ResearchCapacity(0, 0, 1073741824L, null));
    assertThrows(BusinessException.class, () -> service.requireEvidenceSpace(33554432));
  }

  @Test
  void optionalWebhookDoesNotSendAnythingWithoutAnExplicitHttpsReceiver() {
    var options = new ProductProperties();
    var publisher = new AlertPublisherImpl(options, new AccessProperties(), clock);
    assertFalse(publisher.configured());
    assertDoesNotThrow(() -> publisher.publish(null));
    options.setAlertWebhookUrl("http://127.0.0.1/internal");
    options.setAlertWebhookHosts(List.of("127.0.0.1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AlertPublisherImpl(options, new AccessProperties(), clock));
  }
}
