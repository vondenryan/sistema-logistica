package logistics.billing.infrastructure;

import contracts.v1.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import logistics.billing.application.*;
import logistics.geography.application.RoutingProvider;
import logistics.shared.FreightLookupPort;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

@Repository
class QuoteAdapter implements QuoteStore, FreightLookupPort {
  private final JdbcTemplate db;

  QuoteAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public Quote save(Quote q) {
    try {
      db.update(
          "INSERT INTO"
              + " billing.freight_quotes(id,order_id,distance_km,tariff_per_km,amount,calculated_at,source)"
              + " VALUES (?,?,?,?,?,?,?)",
          q.quoteId(),
          q.orderId(),
          q.distanceKm(),
          q.tariffPerKm(),
          q.amount(),
          Timestamp.from(q.calculatedAt()),
          q.source());
      return latestQuote(q.orderId());
    } catch (org.springframework.dao.DuplicateKeyException e) {
      Quote winner = latestQuote(q.orderId());
      if (winner == null) throw e;
      return winner;
    }
  }

  public Quote latestQuote(UUID orderId) {
    var rows =
        db.query(
            "SELECT id,order_id,distance_km,tariff_per_km,amount,calculated_at,source FROM"
                + " billing.freight_quotes WHERE order_id=? ORDER BY calculated_at DESC,id DESC"
                + " LIMIT 1",
            (r, n) ->
                new Quote(
                    r.getObject(1, UUID.class),
                    r.getObject(2, UUID.class),
                    r.getBigDecimal(3).setScale(3),
                    r.getBigDecimal(4).setScale(2),
                    r.getBigDecimal(5).setScale(2),
                    "BRL",
                    r.getTimestamp(6).toInstant(),
                    r.getString(7)),
            orderId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public BigDecimal latestAmount(UUID orderId) {
    var rows =
        db.query(
            "SELECT amount FROM billing.freight_quotes WHERE order_id=? ORDER BY calculated_at"
                + " DESC,id DESC LIMIT 1",
            (r, n) -> r.getBigDecimal(1).setScale(2),
            orderId);
    return rows.isEmpty() ? null : rows.get(0);
  }
}

@Configuration
class QuoteConfiguration {
  @Bean
  QuoteService quoteService(
      QuoteStore store,
      OrderSnapshotPort orders,
      CurrentActorPort actors,
      RoutingProvider routing,
      Environment env) {
    return new QuoteService(
        store, orders, actors, routing, env.getProperty("FREIGHT_TARIFF_PER_KM"));
  }
}

@RestController
@RequestMapping("/api/v1/orders")
class QuoteController {
  private final QuoteService service;

  QuoteController(QuoteService service) {
    this.service = service;
  }

  record QuoteView(
      UUID quoteId,
      UUID orderId,
      String distanceKm,
      String tariffPerKm,
      String amount,
      String currency,
      java.time.Instant calculatedAt,
      String source) {}

  @PostMapping("/{id}/freight-quote")
  QuoteView quote(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    if (!body.isEmpty()) throw new ContractException("VALIDATION_ERROR");
    var q = service.quote(id);
    return new QuoteView(
        q.quoteId(),
        q.orderId(),
        q.distanceKm().toPlainString(),
        q.tariffPerKm().toPlainString(),
        q.amount().toPlainString(),
        q.currency(),
        q.calculatedAt(),
        q.source());
  }
}
