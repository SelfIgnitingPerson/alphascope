package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.config.AlphaScopeProperties;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 集中配置的启动前校验，防止演示混入真实组合、错误阶段及无效运维计划。
 *
 * @author chenxuefeng
 */
class ConfigurationValidationTest {
  @Test
  void defaultsAreValid() {
    assertTrue(valid(new AlphaScopeProperties()));
  }

  @Test
  void syntheticChannelsCannotBeMixedIntoRealOrder() {
    var value = new AlphaScopeProperties();
    value.getMarket().setChannels(List.of(AlphaScopeProperties.ProviderType.DEMO));
    assertFalse(valid(value));
  }

  @Test
  void negativeCacheOrZeroCooldownIsRejected() {
    var value = new AlphaScopeProperties();
    value.getMarket().setCacheMinutes(-1);
    assertFalse(valid(value));
    value.getMarket().setCacheMinutes(0);
    value.getMarket().setCooldownSeconds(0);
    assertFalse(valid(value));
  }

  @Test
  void invalidStageOrNonpositiveDeadlineIsRejected() {
    var value = new AlphaScopeProperties();
    value.getJobs().setStageTimeout(Duration.ZERO);
    assertFalse(valid(value));
    value.getJobs().setStageTimeout(Duration.ofSeconds(1));
    value.getJobs().setStageTimeouts(Map.of("TYPO_STAGE", Duration.ofMinutes(1)));
    assertFalse(valid(value));
  }

  @Test
  void stageSpecificDurationsRemainSupported() {
    var value = new AlphaScopeProperties();
    value.getJobs().setStageTimeouts(Map.of("MARKET_OBSERVATION", Duration.ofHours(3)));
    assertTrue(valid(value));
  }

  @Test
  void unsafeRetentionAndInvalidCronAreRejected() {
    var value = new AlphaScopeProperties();
    value.getOperations().setBackupRetentionDays(1);
    assertFalse(valid(value));
    value.getOperations().setBackupRetentionDays(14);
    value.getOperations().setBackupCron("bad cron");
    assertFalse(valid(value));
  }

  private boolean valid(AlphaScopeProperties value) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      return factory.getValidator().validate(value).isEmpty();
    }
  }
}
