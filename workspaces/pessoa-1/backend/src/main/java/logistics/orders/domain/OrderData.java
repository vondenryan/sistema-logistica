package logistics.orders.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import contracts.v1.*;
import java.math.*;
import java.time.*;
import java.util.*;

public final class OrderData {
  private OrderData() {}

  public record Item(String description, Integer quantity, String unitWeightKg) {}

  public record Input(
      UUID storeId,
      UUID originId,
      AddressSnapshot destination,
      String recipientName,
      String recipientEmail,
      List<Item> items) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record View(
      UUID orderId,
      UUID storeId,
      UUID originId,
      OrderStatus status,
      AddressSnapshot origin,
      AddressSnapshot destination,
      String recipientName,
      String recipientEmail,
      String trackingCode,
      @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalWeightKg,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @JsonSerialize(using = ToStringSerializer.class)
          BigDecimal freightAmount,
      String currency,
      List<Item> items,
      Instant createdAt) {}

  public record Page(List<View> items, int page, int size, long totalItems) {}

  public record Cancel(String reason) {}
}
