package com.billdesk.pg.payments.simulator.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class UrlUtil {

  private UrlUtil() {

  }

  public static String withQuery(String baseUrl, Map<String, String> params) {

    StringBuilder sb = new StringBuilder(baseUrl);
    sb.append(baseUrl.contains("?") ? "&" : "?");
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if ( !first) {
        sb.append("&");
      }
      first = false;
      sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
        .append("=")
        .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                                  StandardCharsets.UTF_8));
    }
    return sb.toString();
  }
}
