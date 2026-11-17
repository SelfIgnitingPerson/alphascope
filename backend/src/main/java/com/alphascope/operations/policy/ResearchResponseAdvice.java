package com.alphascope.operations.policy;

import com.alphascope.common.response.Result;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.operations.service.SourceLicenseService;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 在响应序列化前按实际证据检查用途；类级声明覆盖新增接口，不依赖路径白名单。
 *
 * @author chenxuefeng
 */
@RestControllerAdvice
public class ResearchResponseAdvice implements ResponseBodyAdvice<Object> {
  private final SourceLicenseService licenses;

  public ResearchResponseAdvice(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  @Override
  public boolean supports(
      MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
    return !method.hasMethodAnnotation(OperationalData.class)
            && AnnotatedElementUtils.hasAnnotation(method.getContainingClass(), ResearchData.class)
        || method.hasMethodAnnotation(ResearchData.class);
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter method,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (!licenses.isEnforced() || body == null) return body;
    Object data =
        body instanceof Result<?> result
            ? result.code() == ResultCode.SUCCESS ? result.data() : null
            : body;
    if (data instanceof byte[] bytes)
      data =
          Jsons.read(
              new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
              com.fasterxml.jackson.databind.JsonNode.class);
    var annotation = method.getMethodAnnotation(ResearchData.class);
    if (annotation == null)
      annotation =
          AnnotatedElementUtils.findMergedAnnotation(
              method.getContainingClass(), ResearchData.class);
    for (var permission : annotation.permissions()) licenses.requireEvidence(data, permission);
    return body;
  }
}
