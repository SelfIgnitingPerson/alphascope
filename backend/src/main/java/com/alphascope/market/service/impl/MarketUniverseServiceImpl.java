package com.alphascope.market.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.config.AlphaScopeProperties.ProviderType;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.UniverseMember;
import com.alphascope.market.entity.MarketUniverseSnapshot;
import com.alphascope.market.mapper.MarketUniverseMapper;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.alphascope.market.provider.research.SinaUniverseProvider;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.market.vo.MarketUniverseView;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 单独保存供应商北交所目录及分页证明；目录不冒充日线、停牌或同源复权历史。
 *
 * @author chenxuefeng
 */
@Service
public class MarketUniverseServiceImpl implements MarketUniverseService {
  private final MarketUniverseMapper mapper;
  private final SinaUniverseProvider provider;
  private final AlphaScopeProperties configuration;
  private final SourceLicenseService licenses;
  private final ResearchCollectionMapper collections;
  private final Clock clock;
  private final ConcurrentHashMap<String, MarketUniverseView> cache = new ConcurrentHashMap<>();

  public MarketUniverseServiceImpl(
      MarketUniverseMapper mapper,
      SinaUniverseProvider provider,
      AlphaScopeProperties configuration,
      SourceLicenseService licenses,
      ResearchCollectionMapper collections,
      Clock clock) {
    this.mapper = mapper;
    this.provider = provider;
    this.configuration = configuration;
    this.licenses = licenses;
    this.collections = collections;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public void sync(LocalDate date) {
    if (!configured()) throw new ProviderDataUnavailableException("UNIVERSE_SOURCE_NOT_CONFIGURED");
    licenses.require("SINA", "COLLECT");
    var existing = snapshot(date);
    if (!date.equals(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))))) {
      if ("AVAILABLE".equals(existing.status())) return;
      throw new ProviderDataUnavailableException("PUBLIC_MARKET_HISTORY_NOT_ARCHIVED");
    }
    // 同日短时重复提交复用已核验归档；不会修改它的原始观测时刻。
    if (existing.observedAt() != null
        && existing
            .observedAt()
            .plusMinutes(15)
            .isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")))) return;
    var batch = provider.fetch(date);
    if (batch == null
        || !date.equals(batch.businessDate())
        || batch.observedAt() == null
        || !date.equals(LocalDate.ofInstant(batch.observedAt(), ZoneId.of("Asia/Shanghai"))))
      throw new IllegalStateException("UNIVERSE_EVIDENCE_INVALID");
    var row = new MarketUniverseSnapshot();
    row.setBusinessDate(batch.businessDate());
    row.setUniverseScope("BSE_A");
    row.setSource("SINA");
    row.setMembersJson(Jsons.write(batch.members()));
    row.setEvidenceJson(batch.evidenceJson());
    row.setContentHash(Jsons.hash(row.getMembersJson() + "\n" + row.getEvidenceJson()));
    row.setObservedAt(
        LocalDateTime.ofInstant(batch.observedAt(), ZoneId.of("Asia/Shanghai"))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    mapper.save(row);
    var verified = snapshot(date);
    if (!"AVAILABLE".equals(verified.status()))
      throw new IllegalStateException("UNIVERSE_SAVE_INVALID");
    collections.collectionSave(
        ResearchCollection.builder()
            .dataKind("MARKET_UNIVERSE")
            .scope("BSE_A")
            .businessDate(date)
            .status("AVAILABLE")
            .evidenceJson(
                Jsons.write(
                    Map.of(
                        "members",
                        verified.memberCount(),
                        "snapshotId",
                        verified.snapshotId(),
                        "contentHash",
                        verified.contentHash(),
                        "scope",
                        "BSE_A",
                        "source",
                        "SINA",
                        "note",
                        "当前供应商目录已核验；未核验日线与历史窗口")))
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public void refresh(LocalDate date) {
    if (configuration.getProvider() == ProviderType.DEMO) return;
    try {
      sync(date);
    } catch (TaskStoppedException e) {
      throw e;
    } catch (RuntimeException e) {
      var diagnosis = FailureDiagnostics.from(e);
      collections.collectionSave(
          ResearchCollection.builder()
              .dataKind("MARKET_UNIVERSE")
              .scope("BSE_A")
              .businessDate(date)
              .status("UNAVAILABLE")
              .evidenceJson(Jsons.write(Map.of("diagnostic", diagnosis)))
              .build());
    }
  }

  /** {@inheritDoc} */
  @Override
  public MarketUniverseView snapshot(LocalDate date) {
    if (!configured()) return absent(date, "NOT_CONFIGURED", "当前未配置新浪目录通道；演示模式不读取真实目录。");
    var row = mapper.forDate(date);
    if (row == null) return absent(date, "NOT_ARCHIVED", "该日没有北交所目录归档；当前名单不能倒填过去日期。");
    String key = row.getId() + ":" + row.getContentHash();
    if (cache.size() >= 16 && !cache.containsKey(key)) cache.clear();
    return cache.computeIfAbsent(key, ignored -> read(row));
  }

  private MarketUniverseView read(MarketUniverseSnapshot metadata) {
    var row = mapper.evidence(metadata.getId());
    if (row == null
        || !metadata.getBusinessDate().equals(row.getBusinessDate())
        || !metadata.getContentHash().equals(row.getContentHash())
        || !Jsons.hash(row.getMembersJson() + "\n" + row.getEvidenceJson())
            .equals(row.getContentHash()))
      throw new IllegalStateException("UNIVERSE_EVIDENCE_INVALID");
    var members = Arrays.asList(Jsons.read(row.getMembersJson(), UniverseMember[].class));
    if (members.isEmpty()
        || members.stream()
            .anyMatch(
                x ->
                    !x.symbol().matches("[489][0-9]{5}\\.BJ")
                        || x.name() == null
                        || x.name().isBlank())
        || members.stream().map(UniverseMember::symbol).distinct().count() != members.size())
      throw new IllegalStateException("UNIVERSE_EVIDENCE_INVALID");
    return new MarketUniverseView(
        row.getBusinessDate(),
        "AVAILABLE",
        row.getSource(),
        members.size(),
        row.getId(),
        row.getContentHash(),
        row.getObservedAt(),
        SinaUniverseProvider.SOURCE_URL,
        List.of(
            "按新浪当前在市A股目录的全部分页、前后总数与首屏复核；不是北交所官方清单。",
            "目录日期是实际观测日期，不能证明更早日期的成员。",
            "仅证明本批次供应商目录完整；日线、停牌、复权和历史广度须另外核验。"),
        List.copyOf(members));
  }

  private boolean configured() {
    return configuration.getProvider() == ProviderType.SINA
        || configuration.getProvider() == ProviderType.MULTI
            && configuration.getMarket().getChannels().contains(ProviderType.SINA);
  }

  private MarketUniverseView absent(LocalDate date, String status, String reason) {
    return new MarketUniverseView(
        date,
        status,
        "SINA",
        null,
        null,
        null,
        null,
        SinaUniverseProvider.SOURCE_URL,
        List.of(reason),
        List.of());
  }
}
