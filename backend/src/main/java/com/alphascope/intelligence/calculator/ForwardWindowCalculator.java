package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.dto.ForwardInput;
import com.alphascope.intelligence.vo.ForwardWindow;
import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 冻结信号后次日开盘尝试、固定交易日末收盘退出的前瞻试算，不事后选入场点。
 *
 * @author chenxuefeng
 */
public final class ForwardWindowCalculator {
  private ForwardWindowCalculator() {}

  /**
   * 按冻结费率与可成交性约束核验一个窗口。
   *
   * @param input 已冻结或待冻结的完整输入
   * @return 有明确排除原因的描述结果
   */
  public static ForwardWindow calculate(ForwardInput input) {
    var p = input.protocol();
    if (p == null || !"FROZEN_FORWARD_V1".equals(p.version()))
      return empty(input, "INSUFFICIENT_DATA", "未知或未冻结执行协议", null, null);
    if (!Set.of(5, 10, 20).contains(input.windowDays()))
      throw new IllegalArgumentException("只支持5、10、20交易日窗口");
    var days =
        input.dates().stream()
            .filter(d -> d.isAfter(input.signalDate()))
            .distinct()
            .sorted()
            .toList();
    if (days.size() < input.windowDays())
      return empty(
          input,
          "WAITING_WINDOW",
          "后续市场交易日尚未成熟，不能按自然日推算",
          days.isEmpty() ? null : days.getFirst(),
          null);
    days = days.subList(0, input.windowDays());
    var entryDate = days.getFirst();
    var exitDate = days.getLast();
    if (input.calendar() == null || input.calendar().isEmpty())
      return empty(input, "INSUFFICIENT_DATA", "缺少带实际来源的连续市场日历", entryDate, exitDate);
    var calendars =
        input.calendar().stream()
            .filter(
                c ->
                    c.getTradeDate() != null
                        && !c.getTradeDate().isBefore(input.signalDate().plusDays(1))
                        && !c.getTradeDate().isAfter(exitDate))
            .toList();
    if (calendars.size()
            != java.time.temporal.ChronoUnit.DAYS.between(input.signalDate().plusDays(1), exitDate)
                + 1
        || calendars.stream().map(c -> c.getTradeDate()).distinct().count() != calendars.size()
        || calendars.stream()
            .anyMatch(
                c ->
                    c.getOpen() == null
                        || c.getSource() == null
                        || Set.of("UNKNOWN", "NONE", "DEMO").contains(c.getSource())))
      return empty(input, "INSUFFICIENT_DATA", "日历存在缺日、重复或来源不明", entryDate, exitDate);
    var calendarOpen =
        calendars.stream()
            .filter(c -> Boolean.TRUE.equals(c.getOpen()))
            .map(c -> c.getTradeDate())
            .sorted()
            .toList();
    if (!calendarOpen.equals(days))
      return empty(input, "INSUFFICIENT_DATA", "窗口日期与冻结日历开市日不一致", entryDate, exitDate);
    var bars = new TreeMap<LocalDate, MarketBarDO>();
    for (var b : input.bars())
      if (b.getTradeDate() != null && !b.getTradeDate().isAfter(exitDate)) {
        if (bars.put(b.getTradeDate(), b) != null)
          return empty(input, "INSUFFICIENT_DATA", "重复标的日线", entryDate, exitDate);
      }
    var anchor = bars.get(input.signalDate());
    if (!valid(anchor, input.source()))
      return empty(input, "INSUFFICIENT_DATA", "缺少信号日同源原价及正因子", entryDate, exitDate);
    if (input.frozenClose() == null
        || !Double.isFinite(input.frozenClose())
        || input.frozenClose() <= 0
        || input.frozenFactor() == null
        || !Double.isFinite(input.frozenFactor())
        || input.frozenFactor() <= 0)
      return empty(input, "INSUFFICIENT_DATA", "原判断未留存信号日收盘价或因子", entryDate, exitDate);
    if (Math.abs(anchor.getClose().doubleValue() - input.frozenClose()) >= .005
        || Math.abs(anchor.getFactor().doubleValue() / input.frozenFactor() - 1) > 1e-8)
      return empty(input, "INSUFFICIENT_DATA", "信号日收盘价或因子已修订，无法沿用原判断做验证", entryDate, exitDate);
    for (var d : days)
      if (!valid(bars.get(d), input.source()))
        return empty(
            input,
            "INSUFFICIENT_DATA",
            "窗口缺少同源交易日行情：" + d + "；停牌或缺失不向后挑选可成交日期",
            entryDate,
            exitDate);
    var entry = bars.get(entryDate);
    var exit = bars.get(exitDate);
    double factor = anchor.getFactor().doubleValue();
    for (var d : days)
      if (Math.abs(bars.get(d).getFactor().doubleValue() / factor - 1) > 1e-8)
        return empty(
            input, "INSUFFICIENT_DATA", "窗口涉及公司行动，未完成真实现金与股份账务核验，不用因子猜测分红", entryDate, exitDate);
    if (flat(entry) || flat(exit))
      return empty(input, "UNFILLED", "入场或退出为单价日，保守视为不可成交", entryDate, exitDate);
    if (Math.abs(entry.getOpen().doubleValue() / anchor.getClose().doubleValue() - 1)
        >= p.gapLimit())
      return empty(input, "UNFILLED", "次日开盘跳空达到冻结排除阈值，未顺延追价", entryDate, exitDate);
    var beforeExit = bars.get(days.get(days.size() - 2));
    if (Math.abs(exit.getClose().doubleValue() / beforeExit.getClose().doubleValue() - 1)
        >= p.gapLimit())
      return empty(input, "UNFILLED", "退出日价格变动达到保守排除阈值，不假定能够平仓", entryDate, exitDate);
    double buy = entry.getOpen().doubleValue() * (1 + p.slippage());
    long shares =
        (long)
                Math.floor(
                    (p.capital() - p.minimumCommission()) / (buy * (1 + p.commission())) / 100)
            * 100;
    if (shares < 100) return empty(input, "UNFILLED", "名义资金不足100股及最低费用", entryDate, exitDate);
    double buyNotional = shares * buy,
        sell = shares * exit.getClose().doubleValue() * (1 - p.slippage());
    if (entry.getAmount() == null
        || exit.getAmount() == null
        || entry.getAmount().signum() <= 0
        || exit.getAmount().signum() <= 0)
      return empty(input, "INSUFFICIENT_DATA", "缺成交额，无法核对冻结参与上限", entryDate, exitDate);
    if (buyNotional > entry.getAmount().doubleValue() * p.maximumParticipation()
        || sell > exit.getAmount().doubleValue() * p.maximumParticipation())
      return empty(input, "UNFILLED", "名义成交额超过当日成交额的冻结参与上限", entryDate, exitDate);
    double buyFee = Math.max(p.minimumCommission(), buyNotional * p.commission());
    double sellFee = Math.max(p.minimumCommission(), sell * p.commission()) + sell * p.sellStamp();
    double cash = p.capital() - buyNotional - buyFee, peak = p.capital(), drawdown = 0;
    for (var d : days) {
      double equity = cash + shares * bars.get(d).getClose().doubleValue();
      if (d.equals(exitDate)) equity = cash + sell - sellFee;
      peak = Math.max(peak, equity);
      drawdown = Math.max(drawdown, 1 - equity / peak);
    }
    Double benchmark = benchmark(input, entryDate, exitDate);
    var sources = new TreeSet<String>();
    sources.add(input.source());
    input.calendar().forEach(c -> sources.add(c.getSource()));
    input.benchmark().stream()
        .filter(
            b ->
                !b.getTradeDate().isBefore(entryDate)
                    && !b.getTradeDate().isAfter(exitDate)
                    && b.getSource() != null)
        .forEach(b -> sources.add(b.getSource()));
    return new ForwardWindow(
        "CALCULATED",
        "冻结协议下试算完成；日线不能证明逐笔可成交性",
        input.signalDate(),
        entryDate,
        exitDate,
        input.windowDays(),
        (cash + sell - sellFee) / p.capital() - 1,
        benchmark,
        drawdown,
        buyFee + sellFee,
        Set.copyOf(sources));
  }

  private static Double benchmark(ForwardInput input, LocalDate entryDate, LocalDate exitDate) {
    var a = input.benchmark().stream().filter(b -> entryDate.equals(b.getTradeDate())).toList();
    var b = input.benchmark().stream().filter(v -> exitDate.equals(v.getTradeDate())).toList();
    if (a.size() != 1
        || b.size() != 1
        || a.getFirst().getSource() == null
        || !a.getFirst().getSource().equals(b.getFirst().getSource())
        || !valid(a.getFirst(), a.getFirst().getSource())
        || !valid(b.getFirst(), a.getFirst().getSource())) return null;
    var start = a.getFirst();
    var end = b.getFirst();
    return end.getClose().doubleValue()
            * end.getFactor().doubleValue()
            / (start.getOpen().doubleValue() * start.getFactor().doubleValue())
        - 1;
  }

  private static boolean valid(MarketBarDO b, String source) {
    return b != null
        && Boolean.FALSE.equals(b.getSynthetic())
        && source != null
        && source.equals(b.getSource())
        && b.getOpen() != null
        && b.getOpen().signum() > 0
        && b.getHigh() != null
        && b.getHigh().signum() > 0
        && b.getLow() != null
        && b.getLow().signum() > 0
        && b.getClose() != null
        && b.getClose().signum() > 0
        && b.getFactor() != null
        && b.getFactor().signum() > 0;
  }

  private static boolean flat(MarketBarDO b) {
    return b.getHigh().compareTo(b.getLow()) == 0;
  }

  private static ForwardWindow empty(
      ForwardInput input, String status, String reason, LocalDate entry, LocalDate exit) {
    return new ForwardWindow(
        status,
        reason,
        input.signalDate(),
        entry,
        exit,
        input.windowDays(),
        null,
        null,
        null,
        null,
        input.source() == null ? Set.of() : Set.of(input.source()));
  }
}
