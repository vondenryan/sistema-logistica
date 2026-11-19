package logistics.billing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface QuoteStore {
  record Quote(
      UUID quoteId,
      UUID orderId,
      BigDecimal distanceKm,
      BigDecimal tariffPerKm,
      BigDecimal amount,
      String currency,
      Instant calculatedAt,
      String source) {}

  Quote save(Quote quote);

  Quote latestQuote(UUID orderId);

  BigDecimal latestAmount(UUID orderId);
}
