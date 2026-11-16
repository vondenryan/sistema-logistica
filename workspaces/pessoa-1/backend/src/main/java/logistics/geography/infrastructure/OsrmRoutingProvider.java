package logistics.geography.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import contracts.v1.*;
import java.io.ByteArrayOutputStream;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import logistics.geography.application.RoutingProvider;

public class OsrmRoutingProvider implements RoutingProvider {
  private final String base;
  private final ObjectMapper json;
  private final HttpClient client;

  public OsrmRoutingProvider(String base, ObjectMapper json) {
    this.base = base.replaceAll("/+$", "");
    this.json = json;
    var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2));
    URI target = URI.create(this.base);
    String noProxy =
        System.getenv().getOrDefault("NO_PROXY", System.getenv().getOrDefault("no_proxy", ""));
    String host = target.getHost();
    boolean bypass =
        host == null
            || host.equalsIgnoreCase("localhost")
            || host.equals("127.0.0.1")
            || host.equals("::1");
    for (String item : noProxy.split(",")) {
      String rule = item.trim().toLowerCase();
      String h = host == null ? "" : host.toLowerCase();
      if (rule.equals("*")
          || (!rule.isEmpty()
              && (h.equals(rule) || h.endsWith(rule.startsWith(".") ? rule : "." + rule))))
        bypass = true;
    }
    String proxy =
        System.getenv(target.getScheme().equalsIgnoreCase("https") ? "HTTPS_PROXY" : "HTTP_PROXY");
    if (proxy == null)
      proxy =
          System.getenv(
              target.getScheme().equalsIgnoreCase("https") ? "https_proxy" : "http_proxy");
    if (!bypass && proxy != null && !proxy.isBlank()) {
      URI p = URI.create(proxy);
      if (p.getHost() != null)
        builder.proxy(
            ProxySelector.of(
                new InetSocketAddress(p.getHost(), p.getPort() < 0 ? 80 : p.getPort())));
    }
    this.client = builder.build();
  }

  public BigDecimal roadDistanceKm(AddressSnapshot a, AddressSnapshot b) {
    RoutingProviders.check(a, b);
    try {
      URI uri =
          URI.create(
              base
                  + "/route/v1/driving/"
                  + a.longitude().toPlainString()
                  + ","
                  + a.latitude().toPlainString()
                  + ";"
                  + b.longitude().toPlainString()
                  + ","
                  + b.latitude().toPlainString()
                  + "?overview=false");
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).GET().build();
      var pending = client.sendAsync(request, info -> new LimitedBody());
      HttpResponse<byte[]> response;
      try {
        response = pending.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        pending.cancel(true);
        throw e;
      } catch (ExecutionException e) {
        if (e.getCause() instanceof TooLarge) throw new ContractException("UPSTREAM_ERROR");
        throw e;
      }
      byte[] bytes = response.body();
      if (response.statusCode() >= 500 || response.statusCode() == 429)
        throw new ContractException("DEPENDENCY_UNAVAILABLE");
      if (response.statusCode() != 200 || bytes.length > 100000)
        throw new ContractException("UPSTREAM_ERROR");
      try {
        var root = json.readTree(bytes);
        if (root == null) throw new IllegalArgumentException();
        var distance = root.path("routes").path(0).path("distance");
        if (!"Ok".equals(root.path("code").asText())
            || !distance.isNumber()
            || distance.decimalValue().signum() < 0) throw new IllegalArgumentException();
        return distance.decimalValue().divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
      } catch (Exception e) {
        throw new ContractException("UPSTREAM_ERROR");
      }
    } catch (ContractException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    } catch (Exception e) {
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    }
  }

  private static final class TooLarge extends RuntimeException {}

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > 100000 - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new TooLarge());
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }

  public String source() {
    return "OSRM";
  }
}
