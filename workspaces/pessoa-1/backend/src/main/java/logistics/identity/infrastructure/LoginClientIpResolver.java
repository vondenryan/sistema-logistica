package logistics.identity.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Trust forwarding metadata only from explicitly configured socket peers. */
@Component
class LoginClientIpResolver {
  private final Set<String> trustedPeers;

  LoginClientIpResolver(@Value("${app.security.login-trusted-proxy-ips:}") String configuredPeers) {
    Set<String> peers = new HashSet<>();
    for (String entry : configuredPeers.split(",")) {
      if (entry.isBlank()) continue;
      String address = normalize(entry.trim());
      if (address == null)
        throw new IllegalArgumentException("LOGIN_TRUSTED_PROXY_IPS must contain only IP literals");
      peers.add(address);
    }
    trustedPeers = Set.copyOf(peers);
  }

  String resolve(HttpServletRequest request) {
    String peer = normalize(request.getRemoteAddr());
    if (peer != null && trustedPeers.contains(peer)) {
      var values = request.getHeaders("X-Real-IP");
      if (values != null && values.hasMoreElements()) {
        String candidate = values.nextElement();
        if (!values.hasMoreElements()) {
          String client = normalize(candidate);
          if (client != null) return client;
        }
      }
    }
    return peer == null ? "unknown" : peer;
  }

  /** Literal parsing only: getByAddress never performs DNS, unlike getByName. */
  static String normalize(String value) {
    if (value == null || value.isEmpty() || value.length() > 45) return null;
    try {
      byte[] address = value.indexOf(':') >= 0 ? ipv6(value) : ipv4(value);
      return address == null ? null : InetAddress.getByAddress(address).getHostAddress();
    } catch (IllegalArgumentException | UnknownHostException invalid) {
      return null;
    }
  }

  private static byte[] ipv4(String value) {
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4) return null;
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      String part = parts[i];
      if (!part.matches("0|[1-9][0-9]{0,2}")) return null;
      int number = Integer.parseInt(part);
      if (number > 255) return null;
      bytes[i] = (byte) number;
    }
    return bytes;
  }

  private static byte[] ipv6(String value) {
    if (!value.matches("[0-9a-fA-F:.]+")) return null;
    if (value.indexOf('.') >= 0) {
      int colon = value.lastIndexOf(':');
      byte[] tail = ipv4(value.substring(colon + 1));
      if (tail == null) return null;
      value =
          value.substring(0, colon + 1)
              + Integer.toHexString((tail[0] & 255) * 256 + (tail[1] & 255))
              + ":"
              + Integer.toHexString((tail[2] & 255) * 256 + (tail[3] & 255));
    }
    String[] sides = value.split("::", -1);
    if (sides.length > 2) return null;
    List<Integer> left = groups(sides[0]);
    List<Integer> right = sides.length == 2 ? groups(sides[1]) : List.of();
    if (left == null || right == null) return null;
    int missing = 8 - left.size() - right.size();
    if ((sides.length == 1 && missing != 0) || (sides.length == 2 && missing < 1)) return null;
    List<Integer> all = new ArrayList<>(left);
    for (int i = 0; i < missing; i++) all.add(0);
    all.addAll(right);
    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      bytes[i * 2] = (byte) (all.get(i) >>> 8);
      bytes[i * 2 + 1] = (byte) (all.get(i) & 255);
    }
    return bytes;
  }

  private static List<Integer> groups(String text) {
    if (text.isEmpty()) return List.of();
    List<Integer> result = new ArrayList<>();
    for (String group : text.split(":", -1)) {
      if (!group.matches("[0-9a-fA-F]{1,4}")) return null;
      result.add(Integer.parseInt(group, 16));
    }
    return result;
  }
}
