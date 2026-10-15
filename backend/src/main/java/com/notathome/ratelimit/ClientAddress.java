package com.notathome.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The key a client is limited by. The address is the one Tomcat reports, which for requests that
 * came through the frontend is the real visitor's (see {@code server.forward-headers-strategy} in
 * application.yaml and the proxy in the frontend).
 */
public final class ClientAddress {

  private ClientAddress() {}

  public static String of(HttpServletRequest request) {
    return key(request.getRemoteAddr());
  }

  /**
   * IPv4 addresses are used as they are. An IPv6 client usually owns a whole /64 and can switch
   * addresses inside it at will, so all of them share one key: the first 64 bits. An IPv4 address
   * written in IPv6 form ({@code ::ffff:1.2.3.4}) is the IPv4 address.
   */
  static String key(String address) {
    if (address == null || address.isBlank()) {
      return "unknown";
    }
    String text = address.strip();
    int zone = text.indexOf('%');
    if (zone >= 0) {
      text = text.substring(0, zone);
    }
    if (!text.contains(":")) {
      return text;
    }
    try {
      // A literal address: it contains ':', so no name lookup is attempted.
      InetAddress parsed = InetAddress.getByName(text);
      if (!(parsed instanceof Inet6Address)) {
        return parsed.getHostAddress(); // IPv4-mapped
      }
      byte[] bytes = parsed.getAddress();
      return "%02x%02x:%02x%02x:%02x%02x:%02x%02x/64"
          .formatted(
              bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
    } catch (UnknownHostException e) {
      return text;
    }
  }
}
