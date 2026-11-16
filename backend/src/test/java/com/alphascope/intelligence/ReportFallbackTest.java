package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.intelligence.provider.ReportProvider;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 模型关闭或生成失败时安全回退规则摘要的验证。
 *
 * @author chenxuefeng
 */
class ReportFallbackTest {
  @Test
  @SuppressWarnings("unchecked")
  void disabledModelBuilderFailureIsSafeFallback() {
    ObjectProvider<ChatClient.Builder> clients = mock(ObjectProvider.class);
    when(clients.getIfAvailable())
        .thenThrow(new BeanCreationException("chatClientBuilder", "No ChatModel configured"));
    assertTrue(
        new ReportProvider(clients).summarize(Map.of("isSynthetic", false), Set.of()).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void absentBuilderIsSafeFallback() {
    ObjectProvider<ChatClient.Builder> clients = mock(ObjectProvider.class);
    assertTrue(
        new ReportProvider(clients).summarize(Map.of("isSynthetic", false), Set.of()).isEmpty());
  }
}
