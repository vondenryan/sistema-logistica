package br.com.sistemalogistica.pessoa2.fleet;

import contracts.v1.RouteSnapshot;
import java.math.BigDecimal;

@FunctionalInterface
public interface RouteCapacityValidator {
    void validate(RouteSnapshot route, BigDecimal requestedCapacityKg);
}
