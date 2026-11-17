package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.provider.InstrumentLifecycleProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 生命周期身份、未来日期与旧精选层歧义的拒绝回归。 @author chenxuefeng */
class LifecycleProviderTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 16, 0);

  private ObjectNode row() {
    return (ObjectNode)
        Jsons.read(
            "{\"SECUCODE\":\"920000.BJ\",\"SECURITY_CODE\":\"920000\",\"SECURITY_TYPEE\":\"A股\",\"LISTING_DATE\":\"2023-11-15 00:00:00\"}",
            JsonNode.class);
  }

  @Test
  void identityAndDatesMustBeConfirmed() {
    var raw = row();
    var value =
        InstrumentLifecycleProvider.parse("920000.BJ", raw, "https://example.test/evidence", now);
    assertEquals("2023-11-15", value.listedAt().toString());
    assertTrue(value.evidenceJson().contains("sourceUrl"));
    raw.put("SECURITY_CODE", "600000");
    var mismatch = raw;
    assertThrows(
        IllegalStateException.class,
        () ->
            InstrumentLifecycleProvider.parse(
                "920000.BJ", mismatch, "https://example.test/evidence", now));
    raw = row();
    raw.put("LISTING_DATE", "2027-01-01");
    var future = raw;
    assertThrows(
        IllegalStateException.class,
        () ->
            InstrumentLifecycleProvider.parse(
                "920000.BJ", future, "https://example.test/evidence", now));
  }

  @Test
  void oldSelectedTierDateCannotBePresentedAsBeijingListing() {
    var raw = row().put("LISTING_DATE", "2020-12-23");
    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InstrumentLifecycleProvider.parse(
                    "920000.BJ", raw, "https://example.test/evidence", now));
    assertEquals("LIFECYCLE_BSE_LEGACY_DATE_AMBIGUOUS", failure.getMessage());
  }
}
