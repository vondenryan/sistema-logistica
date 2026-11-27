package sprint4;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReadinessController {
    private final ApplicationAvailability availability;
    private final JdbcTemplate database;

    ReadinessController(ApplicationAvailability availability, JdbcTemplate database) {
        this.availability = availability;
        this.database = database;
    }

    @GetMapping("/ready")
    ResponseEntity<Void> ready() {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            try {
                database.queryForObject("SELECT 1", Integer.class);
                return ResponseEntity.ok().build();
            } catch (DataAccessException unavailable) {
                return ResponseEntity.status(503).build();
            }
        }
        return ResponseEntity.status(503).build();
    }
}
