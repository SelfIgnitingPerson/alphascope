package com.alphascope.intelligence.calculator;

import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.vo.OperatingQuality;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据真实三表字段解释现金流与利润的背离；字段缺失时不代填零。
 *
 * @author chenxuefeng
 */
public final class OperatingQualityCalculator {
  private OperatingQualityCalculator() {}

  /**
   * 计算有来源字段的现金兑现、营运资金、扣非及资本开支观察。
   *
   * @param report 截止日前同源财报
   * @return 指标和仍缺材料
   */
  public static OperatingQuality calculate(FinancialReport report) {
    var metrics = new ArrayList<OperatingQuality.Metric>();
    var missing = new ArrayList<String>();
    var raw = raw(report);
    var net = number(raw, "income", "NETPROFIT");
    var capex = number(raw, "cashflow", "CONSTRUCT_LONG_ASSET");
    var cfo =
        report == null || report.getOperatingCashflow() == null
            ? null
            : report.getOperatingCashflow().doubleValue();
    String basis = net != null && net > 0 ? "CONSOLIDATED_PROFIT" : "PARENT_COMPARISON_ONLY";
    if (net != null && net > 0 && cfo != null)
      add(
          metrics,
          report,
          "合并口径现金兑现",
          cfo / net,
          "RATIO",
          "income.NETPROFIT + cashflow.NETCASH_OPERATE",
          "合并经营现金流与合并净利润对照；不是归母股东可分配现金，不单凭高于1认定经营优质");
    else missing.add("合并净利润缺失或非正，归母利润与合并现金流只作金额对照，不能据此确认现金兑现质量");
    if (capex != null && capex >= 0 && cfo != null)
      add(
          metrics,
          report,
          "经营现金流扣长期资产购建",
          cfo - capex,
          "CNY",
          "cashflow.NETCASH_OPERATE - cashflow.CONSTRUCT_LONG_ASSET",
          "长期资产购建现金支出后的近似余额；不等同完整自由现金流，不含全部并购或租赁支出");
    else missing.add("长期资产购建现金支出缺失，不能核验增长是否持续消耗现金");
    append(
        metrics,
        missing,
        raw,
        report,
        "经营性应收对现金影响",
        "cashflow",
        "OPERATE_RECE_REDUCE",
        "减少为正、占用为负；这是现金流量表调节项，不能替代应收账款余额或坏账核验");
    append(
        metrics,
        missing,
        raw,
        report,
        "存货对现金影响",
        "cashflow",
        "INVENTORY_REDUCE",
        "减少为正、占用为负；与存货减值和销售变化另行核对");
    append(
        metrics,
        missing,
        raw,
        report,
        "经营性应付对现金影响",
        "cashflow",
        "OPERATE_PAYABLE_ADD",
        "增加可能释放现金，也可能延后付款；不能直接当成销售改善");
    append(
        metrics,
        missing,
        raw,
        report,
        "应收账款余额",
        "balance",
        "ACCOUNTS_RECE",
        "期末余额；应与同期间收入、账龄及坏账准备一起看");
    append(
        metrics,
        missing,
        raw,
        report,
        "存货余额",
        "balance",
        "INVENTORY",
        "期末余额；应核对产销、周转与减值，不能用余额单独归因");
    append(
        metrics,
        missing,
        raw,
        report,
        "长期资产购建现金支出",
        "cashflow",
        "CONSTRUCT_LONG_ASSET",
        "现金支出为正；扩产回报与达产时间需要项目公告支持");
    var deduct = number(raw, "income", "DEDUCT_PARENT_NETPROFIT");
    if (deduct != null && report.getNetProfit() != null && report.getNetProfit().signum() > 0)
      add(
          metrics,
          report,
          "归母利润中的扣非差额占比",
          (report.getNetProfit().doubleValue() - deduct) / report.getNetProfit().doubleValue(),
          "RATIO",
          "income.PARENT_NETPROFIT - income.DEDUCT_PARENT_NETPROFIT",
          "同口径归母利润与扣非归母利润的差额；需进一步核查非经常损益明细及可持续性");
    else missing.add("扣非归母净利润缺失或归母利润非正，不能评估非经常损益占比");
    missing.add("销量、售价与产品结构需分业务披露、经营公告支持；三表合计数不能自动解释这些原因");
    return new OperatingQuality(basis, List.copyOf(metrics), List.copyOf(missing));
  }

  private static void append(
      List<OperatingQuality.Metric> metrics,
      List<String> missing,
      JsonNode raw,
      FinancialReport report,
      String label,
      String section,
      String field,
      String meaning) {
    var value = number(raw, section, field);
    if (value == null) missing.add(label + "：缺少原文" + section + "." + field);
    else add(metrics, report, label, value, "CNY", section + "." + field, meaning);
  }

  private static void add(
      List<OperatingQuality.Metric> values,
      FinancialReport r,
      String label,
      Double value,
      String unit,
      String field,
      String meaning) {
    var url = r == null ? null : r.getSourceUrl();
    if (url != null && field.startsWith("balance."))
      url = url.replace("RPT_F10_FINANCE_GINCOME", "RPT_F10_FINANCE_GBALANCE");
    if (url != null && field.startsWith("cashflow."))
      url = url.replace("RPT_F10_FINANCE_GINCOME", "RPT_F10_FINANCE_GCASHFLOW");
    values.add(new OperatingQuality.Metric(label, value, unit, field, url, meaning));
  }

  /**
   * 仅解析已保存原文，不读取外部材料中的指令。
   *
   * @param report 当前证据
   * @return 原文或缺失节点
   */
  public static JsonNode raw(FinancialReport report) {
    if (report == null) return MissingNode.getInstance();
    if (report.getRawPayload() != null) {
      if (report.getContentHash() == null
          || !Jsons.hash(report.getRawPayload()).equals(report.getContentHash()))
        throw new IllegalStateException("财报原始响应指纹不一致");
      return Jsons.read(report.getRawPayload(), JsonNode.class);
    }
    if (report.getRawJson() == null) return MissingNode.getInstance();
    try {
      return Jsons.read(report.getRawJson(), JsonNode.class);
    } catch (RuntimeException invalid) {
      return MissingNode.getInstance();
    }
  }

  /**
   * 读取有限数值，null与非数值均保留未知。
   *
   * @param raw 原文
   * @param section 报表类型
   * @param field 供应商字段
   * @return 有限数字或null
   */
  public static Double number(JsonNode raw, String section, String field) {
    var node = raw.path(section).path(field);
    if (!node.isNumber()) return null;
    double value = node.doubleValue();
    return Double.isFinite(value) ? value : null;
  }
}
