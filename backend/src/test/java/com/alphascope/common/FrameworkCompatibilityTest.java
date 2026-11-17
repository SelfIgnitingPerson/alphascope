package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.intelligence.dto.ReportResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * 验证Spring AI结构化输出与OpenAPI共用一套注解；升级框架不能破坏冻结报告的JSON读取。
 *
 * @author chenxuefeng
 */
class FrameworkCompatibilityTest {
  @Test
  void swaggerAnnotationHasOneDefinitionAndSupportsCurrentResolver() throws Exception {
    var definitions =
        Collections.list(
            Schema.class
                .getClassLoader()
                .getResources("io/swagger/v3/oas/annotations/media/Schema.class"));
    assertEquals(1, definitions.size(), "同名Swagger注解只能来自一个依赖");
    assertEquals(String.class, Schema.class.getMethod("$dynamicRef").getReturnType());
  }

  @Test
  void modelSchemaAndOutputRemainCompatibleWithFrozenReportEncoding() {
    var output = new BeanOutputConverter<>(ReportResult.class);
    var schema = output.getJsonSchema();
    assertTrue(schema.contains("positiveFactors"));
    assertTrue(schema.contains("sourceIds"));
    var facts =
        new ReportResult(
            "UNKNOWN",
            "已核验事实",
            List.of(),
            List.of("资料有缺项"),
            List.of(),
            List.of(),
            List.of("核对下一交易日"),
            List.of("source-1"),
            List.of("方法尚未验证"));
    assertEquals(facts, output.convert(Jsons.write(facts)));
  }
}
