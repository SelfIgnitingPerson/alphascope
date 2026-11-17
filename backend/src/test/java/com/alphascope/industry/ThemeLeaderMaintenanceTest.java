package com.alphascope.industry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.industry.dto.ThemeLeaderUpdateInput;
import com.alphascope.industry.entity.ThemeLeaderRevision;
import com.alphascope.industry.mapper.ThemeLeaderMapper;
import com.alphascope.industry.service.impl.ThemeLeadersServiceImpl;
import com.alphascope.industry.vo.ThemeLeadersView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 目录写入的管理员、冲突修订、当前复核日期和来源验证。
 *
 * @author chenxuefeng
 */
class ThemeLeaderMaintenanceTest {
  @Test
  void appendRequiresAdministratorAndMatchingRevisionAndKeepsOriginalCatalog() throws Exception {
    var service = new ThemeLeadersServiceImpl(new ObjectMapper().findAndRegisterModules());
    var mapper = mock(ThemeLeaderMapper.class);
    service.setMapper(mapper);
    var latest = new AtomicReference<ThemeLeaderRevision>();
    when(mapper.latest("CPO")).thenAnswer(i -> latest.get());
    when(mapper.lockTheme("CPO")).thenReturn(1L);
    doAnswer(
            i -> {
              ThemeLeaderRevision row = i.getArgument(0);
              row.setId(7L);
              latest.set(row);
              return null;
            })
        .when(mapper)
        .insert(any());
    var initial = service.get("CPO");
    var updated =
        new ThemeLeadersView(
            initial.themeCode(),
            initial.title(),
            initial.marketAlias(),
            LocalDate.now(ZoneId.of("Asia/Shanghai")),
            initial.selectionBasis(),
            initial.limitation(),
            initial.members());
    try (var ignored = TenantContext.bind(new UserView(2, "user", "USER"))) {
      assertThrows(
          BusinessException.class,
          () -> service.revise("CPO", new ThemeLeaderUpdateInput(0, updated)));
    }
    verify(mapper, never()).insert(any());
    try (var ignored = TenantContext.bind(new UserView(1, "admin", "ADMIN"))) {
      assertEquals(7, service.revise("CPO", new ThemeLeaderUpdateInput(0, updated)).revision());
      var conflict =
          assertThrows(
              BusinessException.class,
              () -> service.revise("CPO", new ThemeLeaderUpdateInput(0, updated)));
      assertEquals(409, conflict.getStatus());
    }
    assertEquals(initial.members(), service.get("CPO").members());
    assertEquals(LocalDate.of(2026, 11, 16), initial.reviewedAt());
    verify(mapper, times(1)).insert(any());
  }

  @Test
  void manualMaintenanceCannotBackdateKnowledge() throws Exception {
    var service = new ThemeLeadersServiceImpl(new ObjectMapper().findAndRegisterModules());
    var mapper = mock(ThemeLeaderMapper.class);
    service.setMapper(mapper);
    when(mapper.lockTheme("CPO")).thenReturn(1L);
    var initial = service.get("CPO");
    var old =
        new ThemeLeadersView(
            initial.themeCode(),
            initial.title(),
            initial.marketAlias(),
            LocalDate.of(2000, 1, 1),
            initial.selectionBasis(),
            initial.limitation(),
            initial.members());
    try (var ignored = TenantContext.bind(new UserView(1, "admin", "ADMIN"))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> service.revise("CPO", new ThemeLeaderUpdateInput(0, old)));
    }
    verify(mapper, never()).insert(any());
  }
}
