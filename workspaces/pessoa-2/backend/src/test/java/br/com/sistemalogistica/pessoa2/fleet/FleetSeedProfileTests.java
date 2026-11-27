package br.com.sistemalogistica.pessoa2.fleet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class FleetSeedProfileTests {

	@Test
	void seedLoadsOnlyForDevelopmentAndTestProfiles() {
		assertSeedProfile("dev", true);
		assertSeedProfile("test", true);
		assertSeedProfile(null, false);
	}

	private void assertSeedProfile(String profile, boolean expected) {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			if (profile != null) {
				context.getEnvironment().setActiveProfiles(profile);
			}
			context.registerBean(CarrierRepository.class, () -> mock(CarrierRepository.class));
			context.registerBean(DriverRepository.class, () -> mock(DriverRepository.class));
			context.registerBean(VehicleRepository.class, () -> mock(VehicleRepository.class));
			context.register(FleetSeed.class);
			context.refresh();

			if (expected) {
				assertTrue(context.getBeansOfType(FleetSeed.class).size() == 1);
			} else {
				assertFalse(context.getBeansOfType(FleetSeed.class).size() > 0);
			}
		}
	}
}
