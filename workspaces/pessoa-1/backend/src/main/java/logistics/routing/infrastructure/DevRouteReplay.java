package logistics.routing.infrastructure;

import logistics.routing.application.RouteService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
class DevRouteReplay {
  private final RouteService routes;

  DevRouteReplay(RouteService routes) {
    this.routes = routes;
  }

  @Order(1)
  @EventListener(ApplicationReadyEvent.class)
  public void replay() {
    routes.replayDevFakes();
  }
}
