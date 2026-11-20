package com.alphascope.planning.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.service.MarketService;
import com.alphascope.planning.dto.PlanCreateInput;
import com.alphascope.planning.dto.PlanEvidence;
import com.alphascope.planning.dto.PlanQuery;
import com.alphascope.planning.dto.PlanReviewInput;
import com.alphascope.planning.dto.PlanReviewQuery;
import com.alphascope.planning.entity.PlanReview;
import com.alphascope.planning.entity.ResearchPlan;
import com.alphascope.planning.mapper.ResearchPlanMapper;
import com.alphascope.planning.service.ResearchPlanService;
import com.alphascope.planning.vo.PlanReviewView;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.planning.vo.PlanView;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存原始研究指纹；复核保留原风险线，只按同源因子换算价格尺度。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchPlanServiceImpl implements ResearchPlanService {
  private final ResearchPlanMapper mapper;
  private final HoldingResearchService research;
  private final MarketService market;

  public ResearchPlanServiceImpl(
      ResearchPlanMapper mapper, HoldingResearchService research, MarketService market) {
    this.mapper = mapper;
    this.research = research;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public PlanView create(PlanCreateInput input) {
    mapper.lockUser(TenantContext.id());
    if (market.synthetic()) throw new IllegalArgumentException("演示资料不能保存真实研究计划");
    var view = research.read(input.symbol(), query(input.date()));
    var horizon =
        view.horizons().stream()
            .filter(h -> h.code().equals(input.horizon()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("周期不存在"));
    if (view.close() == null
        || !view.asOf().equals(view.quoteDate())
        || horizon.status().equals("UNAVAILABLE"))
      throw new IllegalArgumentException("该日研究证据不足，补齐有效行情后再保存计划");
    horizon.actions().stream()
        .filter(a -> a.code().equals(input.actionCode()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("行动方案不存在"));
    var row = new ResearchPlan();
    row.setSymbol(view.symbol());
    row.setHorizon(horizon.code());
    row.setActionCode(input.actionCode());
    var anchor =
        market
            .bars(
                view.symbol(),
                DateRangeRequest.builder().start(view.asOf()).end(view.asOf()).build())
            .stream()
            .filter(
                b ->
                    Objects.equals(b.getSource(), view.source())
                        && b.getFactor() != null
                        && b.getFactor().signum() > 0)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("缺少保存时同源因子，不能冻结风险价位尺度"));
    row.setSnapshotJson(
        Jsons.write(
            new PlanEvidence(
                view.toBuilder().inputSources(orderedSources(view.inputSources())).build(),
                anchor.getFactor())));
    row.setSnapshotHash(Jsons.hash(row.getSnapshotJson()));
    var existing = mapper.identicalPlan(row);
    if (existing != null) return from(existing);
    requireRoom();
    mapper.insert(row);
    return from(required(row.getId()));
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<PlanSummary> page(PlanQuery query) {
    return page(query, null);
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<PlanSummary> pageWithOriginalRisk(PlanQuery query) {
    return page(query, "ORIGINAL_STOP_BREACHED");
  }

  private PageResponse<PlanSummary> page(PlanQuery query, String reviewStatus) {
    query.validate();
    var rows = mapper.page(query);
    var latest = new java.util.HashMap<Long, PlanReviewView>();
    var hashes = new java.util.HashMap<Long, String>();
    if (!rows.isEmpty()) {
      for (var review :
          mapper.latestReviews(
              new com.alphascope.planning.dto.PlanReviewBatchQuery(
                  rows.stream().map(PlanSummary::id).toList(), query.getAsOf(), reviewStatus))) {
        if (!Jsons.hash(review.getReviewJson()).equals(review.getReviewHash()))
          throw new IllegalStateException("计划复核指纹不匹配");
        var view = Jsons.read(review.getReviewJson(), PlanReviewView.class);
        if (view.planId() != review.getPlanId()) throw new IllegalStateException("计划复核归属不匹配");
        latest.put(review.getPlanId(), view);
        hashes.put(review.getPlanId(), review.getReviewHash());
      }
    }
    var items =
        rows.stream()
            .map(
                row -> {
                  var review = latest.get(row.id());
                  return new PlanSummary(
                      row.id(),
                      row.symbol(),
                      row.name(),
                      row.horizon(),
                      row.actionCode(),
                      row.archived(),
                      row.createdAt(),
                      row.asOf(),
                      review == null ? null : review.date(),
                      review == null ? null : review.status(),
                      review == null ? null : review.explanation(),
                      review != null
                          && Objects.equals(hashes.get(row.id()), row.acknowledgedReviewHash()),
                      row.acknowledgedReviewHash(),
                      row.source(),
                      review != null && review.inputSources() != null
                          ? review.inputSources()
                          : row.source() != null && row.source().matches("[A-Z_]{2,40}")
                              ? java.util.Set.of(row.source())
                              : java.util.Set.of());
                })
            .toList();
    return new PageResponse<>(items, mapper.count(query), query.getPage(), query.getSize());
  }

  /** {@inheritDoc} */
  @Override
  public PlanView get(long id) {
    return from(required(id));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void archive(long id) {
    mapper.lockUser(TenantContext.id());
    required(id);
    mapper.archive(id);
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<PlanReviewView> reviews(long id, PageRequest page) {
    required(id);
    page.validate();
    var items =
        mapper
            .reviews(new PlanReviewQuery(id, page.getSize(), (page.getPage() - 1) * page.getSize()))
            .stream()
            .map(
                row -> {
                  if (!Jsons.hash(row.getReviewJson()).equals(row.getReviewHash()))
                    throw new IllegalStateException("计划复核指纹不匹配");
                  return Jsons.read(row.getReviewJson(), PlanReviewView.class);
                })
            .toList();
    return new PageResponse<>(items, mapper.reviewCount(id), page.getPage(), page.getSize());
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public PlanReviewView review(long id, PlanReviewInput input) {
    mapper.lockUser(TenantContext.id());
    var row = required(id);
    var original = from(row).research();
    if (Boolean.TRUE.equals(row.getArchived())) throw new IllegalArgumentException("归档计划不再追加跟踪");
    if (input.date().isBefore(original.asOf()))
      throw new IllegalArgumentException("复核日期不能早于计划研究日期");
    var current = research.read(row.getSymbol(), query(input.date()));
    var originalAction =
        original.horizons().stream()
            .filter(h -> h.code().equals(row.getHorizon()))
            .flatMap(h -> h.actions().stream())
            .filter(a -> a.code().equals(row.getActionCode()))
            .findFirst()
            .orElseThrow();
    var action =
        current.horizons().stream()
            .filter(h -> h.code().equals(row.getHorizon()))
            .flatMap(h -> h.actions().stream())
            .filter(a -> a.code().equals(row.getActionCode()))
            .findFirst();
    Double stop = null;
    String status = "REVIEW_REQUIRED";
    String explanation = "请结合原始条件、当前研究和个人前提重新核对，系统未执行交易。";
    if (current.close() == null || !input.date().equals(current.quoteDate())) {
      status = "DATA_INSUFFICIENT";
      explanation = "复核日缺少有效收盘行情，未判断原风险线是否失守。";

    } else if (!Objects.equals(original.source(), current.source())) {
      status = "SOURCE_CHANGED";
      explanation = "来源发生变化，价格尺度不能直接比较，请人工核对。";
    } else {
      var bars =
          market.bars(
              row.getSymbol(),
              DateRangeRequest.builder().start(original.asOf()).end(input.date()).build());
      var originalFactor = Jsons.read(row.getSnapshotJson(), PlanEvidence.class).anchorFactor();
      var last =
          bars.stream()
              .filter(
                  b ->
                      b.getTradeDate().equals(input.date())
                          && Objects.equals(b.getSource(), original.source()))
              .findFirst();
      if (last.isPresent()
          && originalFactor != null
          && last.get().getFactor() != null
          && originalFactor.signum() > 0
          && last.get().getFactor().signum() > 0) {
        if (originalAction.stopReference() != null)
          stop =
              originalAction.stopReference()
                  * originalFactor.doubleValue()
                  / last.get().getFactor().doubleValue();
        var touched =
            bars.stream()
                .filter(
                    b ->
                        b.getTradeDate().isAfter(original.asOf())
                            && !b.getTradeDate().isAfter(input.date())
                            && Objects.equals(b.getSource(), original.source())
                            && b.getLow() != null
                            && b.getFactor() != null
                            && b.getFactor().signum() > 0)
                .filter(
                    b ->
                        originalAction.stopReference() != null
                            && b.getLow().doubleValue() * b.getFactor().doubleValue()
                                <= originalAction.stopReference() * originalFactor.doubleValue())
                .map(b -> b.getTradeDate())
                .min(java.time.LocalDate::compareTo);
        if (touched.isPresent() || (stop != null && current.close() <= stop)) {
          status = "ORIGINAL_STOP_BREACHED";
          explanation =
              (touched.isPresent()
                      ? touched.get() + "盘中最低价已触及原风险线，即使随后收盘收复也保留该风险事实。"
                      : "当前收盘已到达或跌破原风险线。")
                  + "已核对同源因子尺度，优先复核原计划，不向下重设止损。";
        } else if (!Objects.equals(original.version(), current.version())) {
          status = "METHOD_CHANGED";
          explanation = "研究方法已变更；原风险价位仍保留，但不能用新方法自动确认原买卖条件。";
        } else {
          status =
              action.isPresent()
                      && Objects.equals(originalAction.status(), action.get().status())
                      && sameConditions(originalAction, action.get())
                  ? "UNCHANGED"
                  : "CONDITIONS_CHANGED";
          explanation =
              "CONDITIONS_CHANGED".equals(status)
                  ? "方案总状态或具体条件满足情况已变化；请逐项核对当前方案，不能由总状态相同推断条件没有变化。"
                  : "本次已有有效行情未确认触及原风险线，方案总状态与逐项条件满足情况一致；不表示原买卖条件已经满足。";
        }
      } else {
        status = "DATA_INSUFFICIENT";
        explanation = "原日或复核日缺少同源因子，不能直接比较冻结风险线。";
      }
    }
    var result =
        new PlanReviewView(
            id,
            input.date(),
            status,
            explanation,
            stop,
            current.close(),
            action.map(HoldingResearchView.Action::status).orElse(null),
            List.of("复核使用当前已保存版本，可能包含后来修订；不是当时可得性证明。", "价格条件不替代原始经营假设与个人仓位核对。"),
            orderedSources(
                com.alphascope.operations.policy.SourceEvidence.inspect(
                        java.util.List.of(original, current))
                    .sources()));
    var review = new PlanReview();
    review.setPlanId(id);
    review.setReviewJson(Jsons.write(result));
    review.setReviewHash(Jsons.hash(review.getReviewJson()));
    if (mapper.identicalReview(review) != null) return result;
    requireRoom();
    mapper.review(review);
    return result;
  }

  /** 比较三态条件结果，不把每天变化的实测价格误当成规则条件改变。 */
  private static boolean sameConditions(
      HoldingResearchView.Action original, HoldingResearchView.Action current) {
    var before = original.conditions();
    var after = current.conditions();
    if (before == null || after == null) return false;
    if (before.size() != after.size()) return false;
    return before.stream()
        .allMatch(
            c ->
                after.stream()
                    .anyMatch(
                        v ->
                            Objects.equals(c.label(), v.label())
                                && Objects.equals(c.met(), v.met())));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void acknowledge(long id, PlanReviewInput input) {
    mapper.lockUser(TenantContext.id());
    var row = required(id);
    var found =
        mapper.latestReviews(
            new com.alphascope.planning.dto.PlanReviewBatchQuery(List.of(id), input.date()));
    if (found.isEmpty()) throw new IllegalArgumentException("所选日期前尚无复核，请先复核后再确认阅读");
    var review = found.getFirst();
    if (!Jsons.hash(review.getReviewJson()).equals(review.getReviewHash()))
      throw new IllegalStateException("复核证据指纹不匹配");
    row.setAcknowledgedReviewHash(review.getReviewHash());
    mapper.acknowledge(row);
  }

  /** 来源是集合语义，冻结前稳定排序，服务重启后相同依据仍命中同一指纹。 */
  private static java.util.Set<String> orderedSources(java.util.Set<String> sources) {
    return sources == null
        ? null
        : java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(sources));
  }

  private HoldingResearchQuery query(java.time.LocalDate date) {
    var query = new HoldingResearchQuery();
    query.setAsOf(date);
    return query;
  }

  private ResearchPlan required(long id) {
    var row = mapper.get(id);
    if (row == null) throw new NotFoundException("研究计划不存在");
    return row;
  }

  private PlanView from(ResearchPlan row) {
    if (!Jsons.hash(row.getSnapshotJson()).equals(row.getSnapshotHash()))
      throw new IllegalStateException("原计划证据指纹不匹配");
    return new PlanView(
        row.getId(),
        row.getHorizon(),
        row.getActionCode(),
        Boolean.TRUE.equals(row.getArchived()),
        row.getCreatedAt(),
        row.getSnapshotHash(),
        Jsons.read(row.getSnapshotJson(), PlanEvidence.class).research());
  }

  private void requireRoom() {
    if (mapper.evidenceCount() >= 1000)
      throw new IllegalArgumentException("个人计划及复核总数已达1000条保护上限，请联系管理员核对存储预算，原证据不会自动删除");
  }
}
