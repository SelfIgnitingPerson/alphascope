package com.alphascope.market.calculator;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.vo.MarketUniverseView;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 用同日真实在市目录核对三所截面，缺少北交所、日期或分页证明时拒绝生成完整声明。
 *
 * @author chenxuefeng
 */
public final class FullMarketAssembler {
  private FullMarketAssembler() {}

  /**
   * 合并实际清单与价格来源，冻结完整声明。
   *
   * @param snapshot 同日供应商截面
   * @param roster 实际北交所目录
   * @return 可再校验的原文
   */
  public static String assemble(ResearchMarketSnapshot snapshot, MarketUniverseView roster) {
    if (roster == null
        || !"AVAILABLE".equals(roster.status())
        || !"SINA".equals(roster.source())
        || roster.members() == null
        || !java.util.Objects.equals(roster.memberCount(), roster.members().size())
        || !snapshot.date().equals(roster.businessDate()))
      throw new IllegalStateException("FULL_A_ROSTER_DATE_MISSING");
    var raw = Jsons.read(snapshot.upstreamJson(), JsonNode.class);
    int expected = raw.path("rosterCount").asInt(-1);
    if (!"EASTMONEY".equals(snapshot.source()) || expected != snapshot.universe().size())
      throw new IllegalStateException("FULL_A_ROSTER_PROOF_MISSING");
    var codes = new TreeSet<String>();
    var bse = new TreeSet<String>();
    var exchanges = new TreeSet<String>();
    for (var member : snapshot.universe()) {
      String code = member.get("code");
      if (!MarketBreadthCalculator.stock(code, "FULL_A"))
        throw new IllegalStateException("FULL_A_MEMBER_INVALID");
      String normalized = code.substring(3) + "." + code.substring(0, 2).toUpperCase(Locale.ROOT);
      if (!codes.add(normalized)) throw new IllegalStateException("FULL_A_MEMBER_DUPLICATE");
      if (code.startsWith("bj.")) {
        bse.add(normalized);
        exchanges.add("BSE");
      } else exchanges.add(code.startsWith("sh.") ? "SSE" : "SZSE");
    }
    if (!exchanges.equals(Set.of("SSE", "SZSE", "BSE"))
        || !bse.equals(new TreeSet<>(roster.members().stream().map(x -> x.symbol()).toList())))
      throw new IllegalStateException("FULL_A_EXCHANGE_MEMBERS_MISMATCH");
    return Jsons.write(
        Jsons.evidenceObject(
            Map.of(
                "universe",
                snapshot.universe(),
                "daily",
                snapshot.daily(),
                "upstreamJson",
                snapshot.upstreamJson(),
                "rosterEvidence",
                roster.summary(),
                "universeProof",
                Map.of(
                    "businessDate",
                    snapshot.date(),
                    "source",
                    snapshot.source(),
                    "scope",
                    "FULL_A",
                    "completeRoster",
                    true,
                    "exchanges",
                    exchanges,
                    "expectedCount",
                    codes.size(),
                    "rosterSha256",
                    Jsons.hash(Jsons.write(codes)),
                    "sourceUrl",
                    roster.sourceUrl()))));
  }
}
