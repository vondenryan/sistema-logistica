package logistics.orders.infrastructure;

import java.util.*;
import logistics.orders.application.OrderService;
import logistics.orders.domain.OrderData.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<View> create(@RequestBody Input input) {
    return ResponseEntity.status(201).body(service.create(input));
  }

  @PatchMapping("/{id}")
  public View update(@PathVariable UUID id, @RequestBody Input input) {
    return service.update(id, input);
  }

  @GetMapping("/{id}")
  public View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping
  public Page list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.list(page, size);
  }

  @PostMapping("/{id}/confirm")
  public View confirm(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    if (!body.isEmpty()) throw new contracts.v1.ContractException("VALIDATION_ERROR");
    return service.confirm(id);
  }

  @PostMapping("/{id}/cancel")
  public View cancel(@PathVariable UUID id, @RequestBody Cancel cancel) {
    return service.cancel(id, cancel);
  }
}
