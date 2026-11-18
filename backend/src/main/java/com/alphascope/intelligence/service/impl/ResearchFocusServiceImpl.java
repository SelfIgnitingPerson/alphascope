package com.alphascope.intelligence.service.impl;

import com.alphascope.intelligence.calculator.ResearchFocusCalculator;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.ResearchFocusService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.service.PortfolioService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从已保存行情生成当前账号研究待办，既不访问供应商也不写入个人计划。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchFocusServiceImpl implements ResearchFocusService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResearchFocusServiceImpl.class);
  private final PortfolioService portfolio;
  private final HoldingResearchService research;
  private final MarketService market;

  public ResearchFocusServiceImpl(
      PortfolioService portfolio, HoldingResearchService research, MarketService market) {
    this.portfolio = portfolio;
    this.research = research;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public ResearchFocusView read(ResearchFocusQuery query) {
    query.validate();
    if (query.getSize() > 20) throw new IllegalArgumentException("研究重点每页最多核对20只，请翻页继续");
    var date = query.getAsOf();
    if (date == null
        || date.getYear() < 1990
        || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("研究日期须在1990年至上海当前日期之间");
    var watched = portfolio.watchlistView().stream().filter(w -> w.enabled()).toList();
    var items = new ArrayList<ResearchFocusView.Item>();
    Boolean open = null;
    try {
      open = market.isOpen(date);
    } catch (IllegalStateException missingCalendar) {
      LOGGER.debug("研究重点所选日日历缺失 date={}", date);
    }
    for (var w :
        watched.stream()
            .skip((long) (query.getPage() - 1) * query.getSize())
            .limit(query.getSize())
            .toList()) {
      if (Boolean.FALSE.equals(open)) {
        items.add(
            ResearchFocusView.Item.builder()
                .symbol(w.symbol())
                .name(w.name())
                .category("WAIT")
                .title("所选日期已确认休市")
                .explanation("休市日没有当日收盘，不属于行情缺失；请用页头“最近收盘日”进入交易日研究。")
                .newlyMatched(false)
                .changeLabel("休市日不比较交易信号")
                .priorities(java.util.List.of())
                .build());
        continue;
      }
      try {
        var current = research.read(w.symbol(), at(date));
        LocalDate previousDate = null;
        HoldingResearchView previous = null;
        try {
          previousDate =
              market
                  .expectedDates(
                      TradingDatesQuery.builder()
                          .symbol(w.symbol())
                          .source(current.source())
                          .start(date.minusDays(14))
                          .end(date.minusDays(1))
                          .build())
                  .stream()
                  .max(LocalDate::compareTo)
                  .orElse(null);
          if (previousDate != null) previous = research.read(w.symbol(), at(previousDate));
        } catch (RuntimeException unavailable) {
          LOGGER.debug("自选上期对照不可用 symbol={} date={}", w.symbol(), date, unavailable);
        }
        items.add(
            ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(current)
                    .previous(previous)
                    .previousDate(previousDate)
                    .build()));
      } catch (RuntimeException unavailable) {
        var reference = java.util.UUID.randomUUID().toString().substring(0, 8);
        LOGGER.warn(
            "自选研究读取失败 reference={} symbol={} date={}", reference, w.symbol(), date, unavailable);
        items.add(
            ResearchFocusView.Item.builder()
                .symbol(w.symbol())
                .name(w.name())
                .category("DATA_GAP")
                .title("研究暂不可用")
                .explanation("本标的读取失败，其他标的仍可查看；请进入个股核对资料，维护编号" + reference + "。")
                .newlyMatched(false)
                .changeLabel("尚未取得可用研究，不推断风险消失")
                .priorities(java.util.List.of())
                .build());
      }
    }
    items.sort(Comparator.comparingInt(ResearchFocusCalculator::priority));
    return new ResearchFocusView(
        date, java.util.List.copyOf(items), watched.size(), query.getPage(), query.getSize());
  }

  private HoldingResearchQuery at(LocalDate date) {
    var query = new HoldingResearchQuery();
    query.setAsOf(date);
    return query;
  }
}
