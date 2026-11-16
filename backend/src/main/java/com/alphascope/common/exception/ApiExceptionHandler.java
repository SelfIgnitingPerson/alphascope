package com.alphascope.common.exception;

import com.alphascope.common.response.Result;
import com.alphascope.common.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一转换参数、资源缺失及服务异常；内部详情写日志，公开响应保留requestId。
 *
 * @author chenxuefeng
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<Result<Void>> business(BusinessException e, HttpServletRequest r) {
    return error(e.getStatus(), e.getCode(), e.getMessage(), r);
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<Result<Void>> missing(NotFoundException e, HttpServletRequest r) {
    return error(404, ResultCode.NOT_FOUND, e.getMessage(), r);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    BindException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<Result<Void>> invalid(Exception e, HttpServletRequest r) {
    String message = "参数无效，请检查日期、数量和字段类型";
    if (e instanceof BindException binding) {
      var fields =
          binding.getBindingResult().getFieldErrors().stream()
              .map(field -> field.getField() + "：" + safeConstraint(field.getCode()))
              .distinct()
              .limit(5)
              .toList();
      if (!fields.isEmpty()) message = "请检查以下字段：" + String.join("；", fields);
    }
    return error(400, ResultCode.INVALID_ARGUMENT, message, r);
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<Result<Void>> conflict(Exception e, HttpServletRequest r) {
    return error(409, ResultCode.CONFLICT, "记录已存在，请使用新的版本序号，不能覆盖已有证据", r);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Result<Void>> methodNotAllowed(Exception e, HttpServletRequest r) {
    return error(405, ResultCode.METHOD_NOT_ALLOWED, "该接口不支持此请求方法", r);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Result<Void>> unknownRoute(Exception e, HttpServletRequest r) {
    return error(404, ResultCode.NOT_FOUND, "接口不存在", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Result<Void>> internal(Exception e, HttpServletRequest r) {
    LOGGER.error("requestId={} 请求失败", r.getAttribute("requestId"), e);
    return error(
        500,
        ResultCode.INTERNAL_ERROR,
        "服务暂不可用。原因：内部异常尚未分类；影响：本次操作未能确认成功。建议：保留错误编号联系维护人员，提交操作请先查询结果，避免重复提交。",
        r);
  }

  private String safeConstraint(String code) {
    return switch (code == null ? "" : code) {
      case "NotNull", "NotBlank", "NotEmpty" -> "不能为空";
      case "Size", "Min", "Max", "DecimalMin", "DecimalMax" -> "长度或数值超出允许范围";
      case "Pattern" -> "格式不正确";
      default -> "值无效";
    };
  }

  private ResponseEntity<Result<Void>> error(
      int status, ResultCode code, String message, HttpServletRequest r) {
    return ResponseEntity.status(status)
        .body(Result.failure(code, message, String.valueOf(r.getAttribute("requestId"))));
  }
}
