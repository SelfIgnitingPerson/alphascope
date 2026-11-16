package com.alphascope.tools;

import com.alphascope.AlphaScopeApplication;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.service.ThemeCohortService;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.mapper.PlatformCapacityMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TreeSet;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 本机披露主题样本维护：沿用真实行情路由和指标规则，不生成报告或改动个人自选。
 *
 * @author chenxuefeng
 */
public final class ThemeResearchTool {
  private ThemeResearchTool() {}

  /**
   * 显式登记截至业务日可见的样本，串行补采成员窗口并更新最近主题观察。
   *
   * <p>仅限LOCAL真实模式、无活动研究或验证任务；不会清除通道保护、启动后台队列或新增备份。 单个成员失败会继续核对其他成员，最终保留实际缺项并返回失败，不能将部分完成冒充全部完成。
   *
   * @param date 已完成收盘的精确业务日期，不自动改换日期
   */
  public static void run(LocalDate date) {
    requireCompletedDate(date, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    if ("PUBLIC".equalsIgnoreCase(System.getenv("ACCESS_MODE"))) {
      throw new IllegalStateException("主题维护工具仅允许LOCAL模式");
    }
    try (var context =
        new SpringApplicationBuilder(AlphaScopeApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--alphascope.jobs.worker-enabled=false",
                "--alphascope.scheduler.enabled=false",
                "--alphascope.operations.backup-enabled=false")) {
      if (context.getBean(AccessProperties.class).getMode() != AccessProperties.Mode.LOCAL) {
        throw new IllegalStateException("主题维护工具仅允许LOCAL模式");
      }
      var market = context.getBean(MarketService.class);
      if (market.synthetic()) throw new IllegalStateException("主题维护只允许显式真实行情模式");
      var usage = context.getBean(PlatformCapacityMapper.class).usage();
      if (usage.activeJobs() > 0 || usage.activeValidations() > 0) {
        throw new IllegalStateException("存在活动研究或验证任务，请等待完成后再维护主题样本");
      }
      var start = date.minusDays(140);
      market.syncCalendar(start, date);
      if (!market.isOpen(date)) throw new IllegalArgumentException("业务日期不是已确认交易日");
      // 先核对基准，避免基准缺失时发起全部成员的无效请求。
      market.sync("000300.SH", start, date);
      var cohorts = context.getBean(ThemeCohortService.class);
      cohorts.prepare(date);
      var symbols = new TreeSet<>(cohorts.symbols(date));
      var indicators = context.getBean(IndicatorService.class);
      int failures = 0;
      for (String symbol : symbols) {
        try {
          int saved = market.sync(symbol, start, date);
          indicators.calculateRange(symbol, date.minusMonths(4), date);
          System.out.printf("theme_sync symbol=%s saved=%d status=SUCCESS%n", symbol, saved);
        } catch (RuntimeException failure) {
          failures++;
          var diagnosis = FailureDiagnostics.from(failure);
          System.out.printf(
              "theme_sync symbol=%s status=FAILED code=%s reason=%s%n",
              symbol, diagnosis.code(), diagnosis.reason());
        }
      }
      var industry = context.getBean(IndustryService.class);
      industry.calculateRange(date.minusDays(10), date);
      var range = OptionalDateRangeRequest.builder().start(date.minusDays(10)).end(date).build();
      for (var theme : industry.themes()) {
        System.out.printf(
            "theme_observation code=%s result=%s%n",
            theme.getCode(), Jsons.write(industry.strength(theme.getCode(), range)));
      }
      System.out.printf(
          "theme_sync date=%s members=%d success=%d failed=%d%n",
          date, symbols.size(), symbols.size() - failures, failures);
      if (failures > 0) throw new IllegalStateException("主题同步尚有失败成员，详见逐项诊断；不会生成替代数据");
    }
  }

  /**
   * 维护入口沿用收盘日期边界，未来日期和当日15:30前均拒绝。
   *
   * @param date 明确业务日期
   * @param now 上海业务时刻
   */
  static void requireCompletedDate(LocalDate date, LocalDateTime now) {
    if (date == null || date.getYear() < 1990 || date.isAfter(now.toLocalDate())) {
      throw new IllegalArgumentException("业务日期须在1990年至上海当前日期之间");
    }
    if (date.equals(now.toLocalDate()) && now.toLocalTime().isBefore(LocalTime.of(15, 30))) {
      throw new IllegalArgumentException("当日收盘研究最早15:30后同步，仍需等待供应商发布");
    }
  }
}
