package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alphascope.common.config.RequestFilter;
import com.alphascope.common.exception.ApiExceptionHandler;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.scheduler.controller.JobController;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.scheduler.vo.JobView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 覆盖 HTTP 成功、异步接收、参数错误及过滤器拒绝使用同一响应协议。
 *
 * @author chenxuefeng
 */
class ApiContractTest {
  private final JobService jobs = mock(JobService.class);
  private final LocalDate date = LocalDate.of(2026, 10, 30);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(jobs.submit(any(JobSubmissionCommand.class))).thenReturn(42L);
    when(jobs.get(42L))
        .thenReturn(
            new JobView(
                42L,
                "DAILY",
                "DEMO",
                date,
                1,
                "PENDING",
                null,
                null,
                null,
                null,
                null,
                "fingerprint",
                List.of(),
                null));
    mvc =
        MockMvcBuilders.standaloneSetup(new JobController(jobs))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestFilter())
            .build();
  }

  @Test
  void successfulReadCarriesOneTraceIdInHeaderAndEnvelope() throws Exception {
    var response =
        mvc.perform(get("/api/v1/admin/jobs/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(42))
            .andReturn()
            .getResponse();
    assertEquals(
        response.getHeader("X-Request-Id"),
        new ObjectMapper().readTree(response.getContentAsString()).path("requestId").asText());
  }

  @Test
  void acceptedJobStillUses202AndRateLimitedWriteUsesSameEnvelope() throws Exception {
    mvc.perform(post("/api/v1/admin/jobs/daily").param("date", date.toString()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
    mvc.perform(post("/api/v1/admin/jobs/daily").param("date", date.toString()))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.requestId").isString());
    verify(jobs, times(1)).submit(any(JobSubmissionCommand.class));
  }

  @Test
  void missingRequiredDateIsA400BusinessError() throws Exception {
    mvc.perform(post("/api/v1/admin/jobs/daily"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.requestId").isString());
    verify(jobs, never()).submit(any(JobSubmissionCommand.class));
  }

  @Test
  void nonexistentJobKeeps404AndTypedFailure() throws Exception {
    when(jobs.get(999L)).thenThrow(new NotFoundException("任务不存在"));
    mvc.perform(get("/api/v1/admin/jobs/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("任务不存在"))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void nonLoopbackWriteIsRejectedBeforeTheService() throws Exception {
    mvc.perform(
            post("/api/v1/admin/jobs/daily")
                .param("date", date.toString())
                .with(
                    request -> {
                      request.setRemoteAddr("192.0.2.1");
                      return request;
                    }))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LOCAL_ONLY"))
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.requestId").isString());
    verify(jobs, never()).submit(any(JobSubmissionCommand.class));
  }
}
