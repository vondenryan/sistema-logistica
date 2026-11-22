package br.com.sistemalogistica.pessoa2.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

class DevCurrentActorAdapterTests {

	private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

	private DevCurrentActorAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new DevCurrentActorAdapter(ACTOR_ID, Role.ADMIN, null, null);
	}

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void noHeadersKeepConfiguredDevelopmentActor() {
		setRequest(new MockHttpServletRequest());

		assertEquals(new ActorContext(ACTOR_ID, Role.ADMIN, null, null), adapter.requireActor());
	}

	@Test
	void roleHeaderOverridesConfiguredProfile() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Dev-Role", "OPERATOR");
		setRequest(request);

		assertEquals(new ActorContext(ACTOR_ID, Role.OPERATOR, null, null), adapter.requireActor());
	}

	@Test
	void storeProfileRequiresAndUsesAValidStoreId() {
		UUID storeId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Dev-Role", "STORE");
		request.addHeader("X-Dev-Store-Id", storeId.toString());
		setRequest(request);

		assertEquals(new ActorContext(ACTOR_ID, Role.STORE, storeId, null), adapter.requireActor());
	}

	@Test
	void unknownRoleAndMissingOrInvalidStoreIdAreUnauthenticated() {
		MockHttpServletRequest unknownRole = new MockHttpServletRequest();
		unknownRole.addHeader("X-Dev-Role", "store");
		setRequest(unknownRole);
		assertUnauthenticated();

		MockHttpServletRequest missingStoreId = new MockHttpServletRequest();
		missingStoreId.addHeader("X-Dev-Role", "STORE");
		setRequest(missingStoreId);
		assertUnauthenticated();

		MockHttpServletRequest invalidStoreId = new MockHttpServletRequest();
		invalidStoreId.addHeader("X-Dev-Role", "STORE");
		invalidStoreId.addHeader("X-Dev-Store-Id", "not-a-uuid");
		setRequest(invalidStoreId);
		assertUnauthenticated();
	}

	@Test
	void devAdapterIsNotRegisteredOutsideDevProfile() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("test");
			context.register(AdapterConfiguration.class);
			context.refresh();

			assertTrue(context.getBeansOfType(DevCurrentActorAdapter.class).isEmpty());
			assertEquals(1, context.getBeansOfType(UnauthenticatedCurrentActorAdapter.class).size());
		}
	}

	private void assertUnauthenticated() {
		assertEquals("UNAUTHENTICATED", assertThrows(ContractException.class, adapter::requireActor).getCode());
	}

	private void setRequest(MockHttpServletRequest request) {
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@Configuration(proxyBeanMethods = false)
	@Import({DevCurrentActorAdapter.class, UnauthenticatedCurrentActorAdapter.class})
	static class AdapterConfiguration {
	}
}
