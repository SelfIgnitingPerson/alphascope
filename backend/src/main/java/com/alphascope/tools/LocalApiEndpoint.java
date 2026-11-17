package com.alphascope.tools;

import java.net.URI;
import java.util.Set;

/**
 * 本机开发工具统一访问应用入口；兼容Docker入口与显式指定的本机开发端口。
 *
 * @author chenxuefeng
 */
final class LocalApiEndpoint {
  private LocalApiEndpoint() {}

  /**
   * 解析回环服务路径，不允许配置携带凭据、远程主机或额外查询参数。
   *
   * @param path 项目固定接口路径，可含工具构造的查询参数
   * @return 经过回环地址核验的本机请求地址
   */
  static URI resolve(String path) {
    return resolve(
        System.getenv().getOrDefault("ALPHASCOPE_API_BASE", "http://127.0.0.1:5173"), path);
  }

  /**
   * 按显式入口解析路径，供纯地址契约验证使用，不发送网络请求。
   *
   * @param base 只含协议、回环主机与端口的本机入口
   * @param path 项目接口绝对路径
   * @return 本机请求URI
   */
  static URI resolve(String base, String path) {
    var uri = URI.create(base);
    if (!Set.of("http", "https").contains(String.valueOf(uri.getScheme()))
        || !Set.of("127.0.0.1", "localhost", "[::1]")
            .contains(uri.getHost() == null ? "" : uri.getHost())
        || uri.getUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
        || uri.getPort() == 0
        || uri.getPort() > 65535
        || !path.startsWith("/")
        || path.startsWith("//")) {
      throw new IllegalArgumentException("ALPHASCOPE_API_BASE必须是无凭据的本机回环服务入口");
    }
    return uri.resolve(path);
  }
}
