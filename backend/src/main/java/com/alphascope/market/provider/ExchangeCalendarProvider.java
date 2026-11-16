package com.alphascope.market.provider;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.DailyBarDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 依据交易所已核验年度通知的离线日历；只覆盖资源中明确登记的年份，不推测其他年份。
 *
 * <p>官方通知URL、发布日期、复核日期和特殊休市均保存在随JAR发布的资源中；来源包含资源指纹。
 *
 * @author chenxuefeng
 */
public final class ExchangeCalendarProvider implements MarketDataProvider {
  private final Set<Integer> years = new HashSet<>();
  private final Set<LocalDate> closed = new HashSet<>();
  private final String source;

  public ExchangeCalendarProvider() {
    try (var input = getClass().getResourceAsStream("/market/exchange-calendar.json")) {
      if (input == null) throw new IllegalStateException("EXCHANGE_CALENDAR_RESOURCE_MISSING");
      String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      JsonNode book = new ObjectMapper().readTree(content);
      if (!"EXCHANGE_CALENDAR_V1".equals(book.path("schema").asText())
          || !"SSE".equals(book.path("market").asText()))
        throw new IllegalStateException("EXCHANGE_CALENDAR_RESOURCE_INVALID");
      LocalDate review = LocalDate.parse(book.path("reviewedOn").asText());
      source =
          "EXCHANGE_CALENDAR_"
              + review.toString().replace("-", "")
              + "_"
              + Jsons.hash(content).substring(0, 12);
      for (var schedule : book.path("schedule")) {
        int year = schedule.path("year").asInt();
        var url = java.net.URI.create(schedule.path("sourceUrl").asText());
        if (year < 1990
            || !years.add(year)
            || !"https".equals(url.getScheme())
            || !"www.sse.com.cn".equals(url.getHost())
            || url.getUserInfo() != null
            || LocalDate.parse(schedule.path("publishedOn").asText()).isAfter(review))
          throw new IllegalStateException("EXCHANGE_CALENDAR_SOURCE_INVALID");
        for (var range : schedule.path("closedRanges")) {
          LocalDate start = LocalDate.parse(range.path("from").asText());
          LocalDate end = LocalDate.parse(range.path("through").asText());
          if (start.getYear() != year || end.getYear() != year || start.isAfter(end))
            throw new IllegalStateException("EXCHANGE_CALENDAR_RANGE_INVALID");
          for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) closed.add(day);
        }
      }
      for (var date : book.path("exceptionalClosures")) {
        LocalDate day = LocalDate.parse(date.asText());
        if (!years.contains(day.getYear()))
          throw new IllegalStateException("EXCHANGE_CALENDAR_RANGE_INVALID");
        closed.add(day);
      }
      if (years.isEmpty()) throw new IllegalStateException("EXCHANGE_CALENDAR_RESOURCE_INVALID");
    } catch (java.io.IOException error) {
      throw new IllegalStateException("EXCHANGE_CALENDAR_RESOURCE_UNREADABLE", error);
    }
  }

  @Override
  public String code() {
    return "EXCHANGE_CALENDAR";
  }

  @Override
  public String calendarSource() {
    return source;
  }

  @Override
  public boolean supportsIndexBars() {
    return false;
  }

  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.TRADING_CALENDAR);
  }

  /**
   * 读取已核验年度日历；任一年度未登记时整窗失败，允许路由尝试其他合法来源。
   *
   * @param from 区间首日，包含
   * @param to 区间末日，包含
   * @return 每个自然日明确的开休市状态
   */
  @Override
  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to))
      throw new IllegalArgumentException("无效日历范围");
    for (int year = from.getYear(); year <= to.getYear(); year++)
      if (!years.contains(year))
        throw new IllegalStateException("EXCHANGE_CALENDAR_YEAR_NOT_REVIEWED");
    Map<LocalDate, Boolean> result = new TreeMap<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      boolean weekend =
          day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
      result.put(day, !weekend && !closed.contains(day));
    }
    return ProviderValidation.calendar(result, from, to);
  }

  @Override
  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    throw new UnsupportedOperationException("EXCHANGE_CALENDAR_QUOTES_UNSUPPORTED");
  }
}
