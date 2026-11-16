package com.alphascope.common.config;

import com.alphascope.common.task.PipelineStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * 集中校验行情能力、任务时限及研究样本配置；密钥独立读取，不进入配置对象toString。
 *
 * @author chenxuefeng
 */
@Data
@Validated
@ConfigurationProperties(prefix = "alphascope")
public class AlphaScopeProperties {
  /** 明确的数据模式；真实通道失败时不能切换为DEMO。 */
  @NotNull private ProviderType provider = ProviderType.DEMO;

  /** 行情通道和缓存设置。 */
  @Valid @NotNull private Market market = new Market();

  /** 后台任务截止时间和进度设置。 */
  @Valid @NotNull private Jobs jobs = new Jobs();

  /** 历史研究验证设置。 */
  @Valid @NotNull private Backtest backtest = new Backtest();

  /** 独立研究验证的容量、截止和证据限制。 */
  @Valid @NotNull private Validation validation = new Validation();

  /** 本机备份计划和保留策略。 */
  @Valid @NotNull private Operations operations = new Operations();

  /**
   * 可配置的供应商模式；MULTI只能用于组合，不能作为组合中的通道。
   *
   * @author chenxuefeng
   */
  public enum ProviderType {
    DEMO,
    MULTI,
    BAOSTOCK,
    SINA,
    TUSHARE,
    EASTMONEY,
    EXCHANGE_CALENDAR
  }

  /**
   * 行情访问配置，复用现有环境变量和属性名称。
   *
   * @author chenxuefeng
   */
  @Data
  public static class Market {
    /** 按优先级排列的真实通道；未配置Tushare密钥时组合模式明确跳过。 */
    @NotEmpty
    private List<ProviderType> channels =
        List.of(
            ProviderType.EXCHANGE_CALENDAR,
            ProviderType.BAOSTOCK,
            ProviderType.SINA,
            ProviderType.EASTMONEY,
            ProviderType.TUSHARE);

    /** 普通缓存分钟数，0禁用普通及历史复用。 */
    @Min(0)
    private long cacheMinutes = 1440;

    /** 单个能力失败的冷却秒数。 */
    @Min(1)
    @Max(86400)
    private long cooldownSeconds = 30;

    /** BaoStock协议主机，仅用于配置合法端点，不绕过持久访问限制。 */
    @NotEmpty private String baostockHost = "public-api.baostock.com";

    /** 本机上海自然日请求预算，包含登录、分页与重试，低于官方5万次上限。 */
    @Min(1)
    @Max(45000)
    private int baostockDailyBudget = 40000;

    /** BaoStock协议端口。 */
    @Min(1)
    @Max(65535)
    private int baostockPort = 10030;

    /**
     * 防止真实组合掺入演示数据或递归组合。
     *
     * @return 所有通道均为具体真实通道时为true
     */
    @AssertTrue(message = "多通道列表不能包含DEMO、MULTI或空值")
    public boolean isRealChannels() {
      return channels != null
          && channels.stream()
              .allMatch(
                  value ->
                      value != null && value != ProviderType.DEMO && value != ProviderType.MULTI);
    }
  }

  /**
   * 任务的可取消执行时限；大规模历史采集允许单独覆盖阶段时限。
   *
   * @author chenxuefeng
   */
  @Data
  public static class Jobs {
    /** 是否启动队列消费，保留现有配置含义。 */
    private boolean workerEnabled = true;

    /** 默认每阶段最长执行时间，默认两小时，不根据心跳自动延长。 */
    @NotNull private Duration stageTimeout = Duration.ofHours(2);

    /** 按阶段代码指定截止时间，键必须是已定义的阶段。 */
    @NotNull private Map<String, Duration> stageTimeouts = Map.of();

    /**
     * 校验阶段截止配置，避免零时限、负时限及拼错阶段静默失效。
     *
     * @return 时限在1秒至24小时且阶段名有效时为true
     */
    @AssertTrue(message = "阶段时限须在1秒至24小时，阶段代码必须有效")
    public boolean isTimeoutsValid() {
      return valid(stageTimeout)
          && stageTimeouts != null
          && stageTimeouts.entrySet().stream()
              .allMatch(entry -> PipelineStage.known(entry.getKey()) && valid(entry.getValue()));
    }

    private boolean valid(Duration value) {
      return value != null
          && value.compareTo(Duration.ofSeconds(1)) >= 0
          && value.compareTo(Duration.ofHours(24)) <= 0;
    }
  }

  /**
   * 描述统计的小样本边界，不能解释为方法有效性的保证。
   *
   * @author chenxuefeng
   */
  @Data
  public static class Backtest {
    /** 低于此值显示SMALL_SAMPLE，仍保留实际描述统计。 */
    @Min(2)
    @Max(10000)
    private int minimumSamples = 20;
  }

  /**
   * 后台验证的本机资源边界，不复用行情采集线程。
   *
   * @author chenxuefeng
   */
  @Data
  public static class Validation {
    /** 最多十只固定自选。 */
    @Min(1)
    @Max(10)
    private int maximumSymbols = 10;

    /** 最多二十个活动请求，重复条件只返回已有任务。 */
    @Min(1)
    @Max(100)
    private int maximumActive = 20;

    /** 完整冻结证据最大字节数，默认32MiB。 */
    @Min(1048576)
    @Max(67108864)
    private int maximumEvidenceBytes = 33554432;

    /** 一次最多生成的逐规则评估数量。 */
    @Min(1000)
    @Max(100000)
    private int maximumSignals = 50000;

    /** 验证执行时限，不随心跳延长。 */
    @Min(10)
    @Max(3600)
    private int timeoutSeconds = 900;

    /** 超过此时间无心跳的旧执行归为失联。 */
    @Min(30)
    @Max(3600)
    private int staleSeconds = 120;
  }

  /**
   * 应用运行期间的定期备份，恢复验证固定在隔离数据库进行。
   *
   * @author chenxuefeng
   */
  @Data
  public static class Operations {
    /** 显式启用应用内自动备份；IDE或测试默认不自动执行。 */
    private boolean backupEnabled;

    /** 上海时区备份计划，默认每天02:00。 */
    @NotEmpty private String backupCron = "0 0 2 * * *";

    /** 第二存储的已挂载目录；空值表示尚未配置，不伪装成异地备份。 */
    @NotNull private String backupSecondaryDirectory = "";

    /** 最短保留7天，默认14天；始终保留最近两份已实际恢复验证的备份。 */
    @Min(7)
    @Max(3650)
    private int backupRetentionDays = 14;

    /**
     * 启动时校验计划表达式，避免无效配置静默不执行。
     *
     * @return 合法Spring六段Cron表达式时为true
     */
    @AssertTrue(message = "备份Cron表达式无效")
    public boolean isCronValid() {
      return backupCron != null && CronExpression.isValidExpression(backupCron);
    }
  }
}
