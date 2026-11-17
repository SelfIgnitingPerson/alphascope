package com.alphascope.intelligence.calculator;

import static com.alphascope.intelligence.calculator.HoldingResearchSupport.unavailable;

import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.HoldingResearchView.Action;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.util.Comparator;
import java.util.List;

/**
 * 同一证据下的多周期行动研究；复用短线策略，中线核对65期趋势，长线分开核对财报与估值缺项。
 *
 * <p>1–20交易日、1–6个月、6–24个月是本产品观察期限。全部参数均为未完成效果验证的研究假设。
 *
 * @author chenxuefeng
 */
public final class HoldingResearchCalculator {
  public static final String VERSION = "HOLDING_RESEARCH_V1";

  private HoldingResearchCalculator() {}

  /**
   * 只使用截止日及以前的同源证据，不发起网络请求、改变账户或重建冻结报告。
   *
   * @param input 有明确日期、日历、财报可用时点和演示标志的研究输入
   * @return 三个独立周期及其证据、买卖条件和实际缺项
   */
  public static HoldingResearchView calculate(HoldingResearchInput input) {
    var stock = input.stock();
    var study =
        PriceVolumeCalculator.calculate(
            PriceVolumeWindow.builder()
                .symbol(stock.symbol())
                .name(stock.name())
                .date(input.asOf())
                .bars(stock.history())
                .expectedDates(input.expectedDates())
                .suspended(stock.suspended())
                .synthetic(input.synthetic())
                .build());
    var quote =
        stock.history().stream()
            .filter(b -> b.getTradeDate() != null && !b.getTradeDate().isAfter(input.asOf()))
            .max(Comparator.comparing(MarketBarDO::getTradeDate))
            .orElse(null);
    return HoldingResearchView.builder()
        .version(VERSION)
        .symbol(stock.symbol())
        .name(stock.name())
        .asOf(input.asOf())
        .quoteDate(quote == null ? null : quote.getTradeDate())
        .source(study.source())
        .close(study.close())
        .priceVolume(study)
        .horizons(
            List.of(
                shortTerm(study),
                MediumTermResearchCalculator.calculate(input, study),
                LongTermResearchCalculator.calculate(input, study)))
        .notes(
            List.of(
                "本页读取当前保存版本；查看历史日期属于当前版本回顾，不证明历史时点可得。冻结报告保留原修订。",
                "短期关注量价和结构；中期关注趋势与回撤；长期关注经营、现金流、估值和核心逻辑。各周期不相互替代。",
                "长期估值与竞争优势尚需人工核实，不把技术观察带标成长期合理价值。方法尚未完成历史效果验证。"))
        .build();
  }

  private static Horizon shortTerm(PriceVolumeStudy study) {
    if (!"AVAILABLE".equals(study.status()) || study.strategies() == null) {
      return unavailable(
          "SHORT", study.missing().isEmpty() ? "当日量价资料不可用" : String.join("；", study.missing()));
    }
    var actions =
        study.strategies().stream()
            .map(
                plan ->
                    Action.builder()
                        .code(plan.code())
                        .title(plan.title())
                        .status(plan.status())
                        .conclusion(plan.conclusion())
                        .observationZone(plan.observationZone())
                        .referencePrice(
                            plan.referenceZone() == null ? null : plan.referenceZone().upper())
                        .referenceLabel("提前形成的原参考区上沿")
                        .stopReference(plan.stopReference())
                        .conditionLogic(plan.conditionLogic())
                        .conditions(plan.conditions())
                        .invalidation(plan.invalidation())
                        .assumptions(plan.assumptions())
                        .build())
            .toList();
    String status =
        actions.stream().anyMatch(a -> "WATCH".equals(a.status()))
            ? "WATCH"
            : actions.stream().anyMatch(a -> "CONDITIONAL_MATCH".equals(a.status()))
                ? "CONDITIONAL_MATCH"
                : actions.stream().anyMatch(a -> "RISK_BLOCKED".equals(a.status()))
                    ? "RISK_BLOCKED"
                    : "WAIT";
    return Horizon.builder()
        .code("SHORT")
        .title("短期交易")
        .period("1–20个交易日")
        .status(status)
        .conclusion(
            "WATCH".equals(status)
                ? "先复核减仓或风险退出，暂停新增风险；具体触发见下列方案。"
                : "CONDITIONAL_MATCH".equals(status)
                    ? "有方案满足本期行情条件，核对观察带、失效条件和个人前提后再决定。"
                    : "等待独立买入条件；当前没有买入确认，按已列价带与量能要求跟踪。")
        .actions(actions)
        .evidence(study.evidence())
        .missing(List.of())
        .build();
  }
}
