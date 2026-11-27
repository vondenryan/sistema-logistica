package br.com.sistemalogistica.pessoa2.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;

class DevFleetAssignmentControllerProfileTests {

	@Test
	void assignmentPostControllerExistsOnlyInDevProfile() {
		assertControllerProfile("dev", 1);
		assertControllerProfile("test", 0);
		assertControllerProfile(null, 0);
	}

	private void assertControllerProfile(String profile, int expectedCount) {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			if (profile != null) {
				context.getEnvironment().setActiveProfiles(profile);
			}
			context.registerBean(FleetAssignmentService.class, () -> mock(FleetAssignmentService.class));
			context.registerBean(CurrentActorPort.class, () -> mock(CurrentActorPort.class));
			context.register(DevFleetAssignmentController.class);
			context.refresh();

			assertEquals(expectedCount, context.getBeansOfType(DevFleetAssignmentController.class).size());
		}
	}
}
