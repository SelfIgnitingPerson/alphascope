package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * 组合已知敞口与固定压力情景，未知资产和相关性不按零处理。
 *
 * @author chenxuefeng
 */
public final class PortfolioImpactCalculator {
  private PortfolioImpactCalculator() {}

  /**
   * 汇总全量已登记范围，不受页面截断影响。
   *
   * @param input 带日期和来源的持仓及个股影响
   * @return 有边界的局部组合影响；无法估计时为null
   */
  public static ResearchFocusView.PortfolioImpact calculate(Input input) {
    var date = input.date();
    var knowledge = input.knowledgeDate() == null ? date : input.knowledgeDate();
    var a = input.account();
    var held =
        input.positions().stream()
            .filter(
                p ->
                    p.getQuantity() != null
                        && p.getQuantity().signum() > 0
                        && p.getAsOfDate() != null
                        && !p.getAsOfDate().isAfter(knowledge))
            .toList();
    var marked =
        input.items().stream()
            .filter(
                i ->
                    i.positionImpact() != null
                        && Set.of("HELD", "ESTIMATED").contains(i.positionImpact().state()))
            .toList();
    boolean dated =
        a != null
            && a.getAsOfDate() != null
            && !a.getAsOfDate().isAfter(knowledge)
            && (date.equals(a.getAsOfDate()) || a.isHistoryVerified() || knowledge.isAfter(date));
    if (marked.isEmpty()
        && !(dated && Boolean.TRUE.equals(a.getPositionsComplete()) && held.isEmpty())) return null;
    double value = marked.stream().mapToDouble(i -> i.positionImpact().marketValue()).sum();
    Double assets =
        dated && a.getTotalAssets() != null && a.getTotalAssets().signum() > 0
            ? a.getTotalAssets().doubleValue()
            : null;
    Double cash = dated && a.getCashBalance() != null ? a.getCashBalance().doubleValue() : null;
    Double available =
        cash != null && a.getLiquidityReserve() != null
            ? cash - a.getLiquidityReserve().doubleValue()
            : null;
    boolean breached =
        input.items().stream()
            .anyMatch(
                i ->
                    i.positionImpact() != null
                        && !"FLAT".equals(i.positionImpact().state())
                        && "BREACHED".equals(i.positionImpact().referenceState()));
    boolean complete =
        dated && Boolean.TRUE.equals(a.getPositionsComplete()) && held.size() == marked.size();
    boolean allRefs =
        held.size() == marked.size()
            && marked.stream().allMatch(i -> i.positionImpact().referenceLoss() != null);
    Double ref =
        allRefs ? marked.stream().mapToDouble(i -> i.positionImpact().referenceLoss()).sum() : null;
    String budget =
        breached
            ? "BREACHED"
            : !dated || a.getRiskBudget() == null || ref == null
                ? "UNKNOWN"
                : ref > a.getRiskBudget().doubleValue()
                    ? "EXCEEDED"
                    : complete ? "WITHIN_REFERENCE_ONLY" : "UNKNOWN";
    var values = new TreeMap<String, Double>();
    for (var item : marked) {
      var position =
          held.stream().filter(p -> item.symbol().equals(p.getSymbol())).findFirst().orElse(null);
      var labels =
          position == null || position.getRiskGroup() == null || position.getRiskGroup().isBlank()
              ? Set.of("未分类")
              : Arrays.stream(position.getRiskGroup().split("[,，]"))
                  .map(String::strip)
                  .filter(v -> !v.isBlank())
                  .collect(java.util.stream.Collectors.toSet());
      for (var label : labels)
        if (!label.isBlank())
          values.merge(label.strip(), item.positionImpact().marketValue(), Double::sum);
    }
    var groups =
        values.entrySet().stream()
            .map(
                e ->
                    new ResearchFocusView.RiskGroup(
                        e.getKey(), e.getValue(), assets == null ? null : e.getValue() / assets))
            .sorted(Comparator.comparingDouble(ResearchFocusView.RiskGroup::marketValue).reversed())
            .toList();
    var notes = new ArrayList<String>();
    if (knowledge.isAfter(date))
      notes.add("用截至" + knowledge + "的当前登记按" + date + "收盘估算，不代表研究日当时的历史账户；未知交易、负债和盘中变化仍须核对");
    notes.add("压力情景仅假设已知股票共同下跌10%或20%，不预测发生概率；真实极端损失可能更大，未给予分散化折扣");
    notes.add("风险分组由用户人工登记，可重叠，各组不能相加；未登记的持仓、负债与关联敞口不能推断为零");
    notes.add("参考预算只比较原结构价差合计，不能代替最大损失、组合回撤或完整现金风险");
    if (!complete) notes.add("当前组合资料未确认完整，所有金额只覆盖已知部分，不判断尚可加仓额度");
    if (available != null && available < 0) notes.add("已登记近期用款预留超过现金余额，应优先核对用款与变现安排");
    return new ResearchFocusView.PortfolioImpact(
        held.size(),
        held.size() - marked.size(),
        value,
        assets == null ? null : value / assets,
        cash,
        available,
        value * .1,
        value * .2,
        budget,
        groups,
        List.copyOf(notes));
  }

  /**
   * 全量持仓与当前研究影响，账户可未知。
   *
   * @author chenxuefeng
   * @param date 研究日
   * @param positions 当时实际登记的持仓
   * @param account 当时账户资料
   * @param items 已处理的全量研究
   */
  public record Input(
      LocalDate date,
      List<PositionDO> positions,
      PortfolioAccount account,
      List<ResearchFocusView.Item> items,
      LocalDate knowledgeDate) {
    /** 严格历史视角的兼容构造。 */
    public Input(
        LocalDate date,
        List<PositionDO> positions,
        PortfolioAccount account,
        List<ResearchFocusView.Item> items) {
      this(date, positions, account, items, date);
    }
  }
}
