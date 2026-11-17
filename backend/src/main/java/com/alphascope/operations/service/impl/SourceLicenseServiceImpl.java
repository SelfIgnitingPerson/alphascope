package com.alphascope.operations.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.dto.SourceLicenseInput;
import com.alphascope.operations.entity.SourceLicense;
import com.alphascope.operations.mapper.SourceEvidenceMapper;
import com.alphascope.operations.mapper.SourceLicenseMapper;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.operations.vo.SourceLicenseView;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 按当前真实来源和日期核验人工许可登记；缺失、过期与未开始分开显示。
 *
 * @author chenxuefeng
 */
@Service
public class SourceLicenseServiceImpl implements SourceLicenseService {
  private final SourceLicenseMapper mapper;
  private final ProductProperties product;
  private final AccessProperties access;
  private final AlphaScopeProperties configuration;
  private final Environment environment;
  private final Clock clock;
  private static final Set<String> PERMISSIONS =
      Set.of("DISPLAY", "COLLECT", "EXPORT", "DERIVED_REPORT");

  public SourceLicenseServiceImpl(
      SourceLicenseMapper mapper,
      ProductProperties product,
      AccessProperties access,
      AlphaScopeProperties configuration,
      Environment environment,
      Clock clock) {
    this.mapper = mapper;
    this.product = product;
    this.access = access;
    this.configuration = configuration;
    this.environment = environment;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public void declare(SourceLicenseInput input) {
    if (TenantContext.user() == null || !"ADMIN".equals(TenantContext.user().role()))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "来源许可登记需要管理员权限");
    URI url;
    try {
      url = URI.create(input.evidenceUrl());
    } catch (RuntimeException error) {
      throw invalid();
    }
    if (!"https".equals(url.getScheme())
        || url.getHost() == null
        || url.getUserInfo() != null
        || !PERMISSIONS.containsAll(input.permissions())
        || input.validFrom().isAfter(input.validUntil())) throw invalid();
    var row = new SourceLicense();
    row.setSource(input.source());
    row.setPermissionsJson(Jsons.write(input.permissions().stream().sorted().toList()));
    row.setEvidenceUrl(input.evidenceUrl());
    row.setReference(input.reference());
    row.setValidFrom(input.validFrom());
    row.setValidUntil(input.validUntil());
    row.setReviewerId(TenantContext.id());
    row.setContentHash(Jsons.hash(Jsons.write(input)));
    mapper.save(row);
  }

  /** {@inheritDoc} */
  @Override
  public List<SourceLicenseView> status() {
    var latest = mapper.latest();
    var sources = new LinkedHashSet<String>();
    if (configuration.getProvider() != AlphaScopeProperties.ProviderType.DEMO) {
      var types =
          configuration.getProvider() == AlphaScopeProperties.ProviderType.MULTI
              ? configuration.getMarket().getChannels()
              : List.of(configuration.getProvider());
      for (var type : types) {
        if (type == AlphaScopeProperties.ProviderType.TUSHARE
            && environment.getProperty("TUSHARE_TOKEN", "").isBlank()) continue;
        sources.add(type.name());
      }
    }
    sources.add("EASTMONEY_DIRECTORY");
    sources.add("EASTMONEY_FINANCIAL");
    sources.add("EASTMONEY_FORECAST");
    latest.forEach(row -> sources.add(row.getSource()));
    LocalDate now = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    return sources.stream()
        .map(
            source -> {
              var row =
                  latest.stream()
                      .filter(value -> value.getSource().equals(source))
                      .findFirst()
                      .orElse(null);
              if (row == null)
                return new SourceLicenseView(source, "MISSING", Set.of(), null, null, null);
              String state =
                  now.isBefore(row.getValidFrom())
                      ? "NOT_YET_VALID"
                      : now.isAfter(row.getValidUntil()) ? "EXPIRED" : "DECLARED_BY_OPERATOR";
              return new SourceLicenseView(
                  source,
                  state,
                  Set.of(Jsons.read(row.getPermissionsJson(), String[].class)),
                  row.getValidUntil(),
                  row.getEvidenceUrl(),
                  row.getReference());
            })
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEnforced() {
    return access.getMode() == AccessProperties.Mode.PUBLIC || product.isEnforceSourceLicense();
  }

  /** {@inheritDoc} */
  @Override
  public void require(String source, String permission) {
    if (!isEnforced()) return;
    var license =
        status().stream().filter(value -> value.source().equals(source)).findFirst().orElse(null);
    if (license == null
        || !license.status().equals("DECLARED_BY_OPERATOR")
        || !license.permissions().contains(permission))
      throw new BusinessException(
          ResultCode.DATA_UNAVAILABLE,
          503,
          "数据源使用范围尚未就绪：" + source + " / " + permission + "，请联系管理员核对授权登记");
  }

  private SourceEvidenceMapper evidenceMapper;

  @org.springframework.beans.factory.annotation.Autowired
  public void setEvidenceMapper(SourceEvidenceMapper mapper) {
    this.evidenceMapper = mapper;
  }

  /** {@inheritDoc} */
  @Override
  public void requireEvidence(Object evidence, String permission) {
    if (!isEnforced() || evidence == null) return;
    if (!PERMISSIONS.contains(permission)) throw invalid();
    var references = SourceEvidence.inspect(evidence);
    var sources = new java.util.TreeSet<>(references.sources());
    if (!references.batches().isEmpty()) {
      if (evidenceMapper == null || references.batches().size() > 2000)
        throw new BusinessException(ResultCode.DATA_UNAVAILABLE, 503, "来源批次尚未核验，不能发布研究结果");
      sources.addAll(evidenceMapper.batchSources(references.batches()));
    }
    if (sources.isEmpty() && references.material())
      throw new BusinessException(
          ResultCode.DATA_UNAVAILABLE, 503, "这份旧研究缺少可核验来源，请在获准使用的数据范围内重新生成；不会猜测来源或改写旧证据");
    for (var source : sources) require(SourceEvidence.canonical(source), permission);
  }

  private BusinessException invalid() {
    return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "许可需有明确HTTPS依据、有效日期和支持的使用范围");
  }
}
