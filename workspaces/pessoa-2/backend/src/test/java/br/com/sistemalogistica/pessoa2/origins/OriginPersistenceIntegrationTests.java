package br.com.sistemalogistica.pessoa2.origins;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.stores.CreateStoreRequest;
import br.com.sistemalogistica.pessoa2.stores.StoreResponse;
import br.com.sistemalogistica.pessoa2.stores.StoreService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OriginPersistenceIntegrationTests {

	private static final String ADDRESS =
			"{\"line1\":\"Rua Exemplo, 100\",\"line2\":null,\"city\":\"Cidade Fictícia\","
					+ "\"state\":\"SC\",\"postalCode\":\"00000-000\",\"country\":\"BR\","
					+ "\"latitude\":-26.48,\"longitude\":-49.07}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OriginService originService;

	@Autowired
	private StoreService storeService;

	@Test
	void persistsListsRetrievesAndInactivatesOrigin() throws Exception {
		StoreResponse store = storeService.create(new CreateStoreRequest("Origin test " + UUID.randomUUID(), true));
		OriginSnapshot created = originService.create(
				new CreateOriginRequest(OriginType.DISTRIBUTION_CENTER, store.id(), address(), true));

		mockMvc.perform(get("/api/v1/origins/{originId}", created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id().toString()))
				.andExpect(jsonPath("$.type").value("DISTRIBUTION_CENTER"))
				.andExpect(jsonPath("$.storeId").value(store.id().toString()))
				.andExpect(jsonPath("$.address.longitude").value(-49.07));

		mockMvc.perform(get("/api/v1/origins").param("page", "0").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == '" + created.id() + "')]").isNotEmpty());

		mockMvc.perform(patch("/api/v1/origins/{originId}", created.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/origins/{originId}", created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void rejectsUnknownJsonPropertiesAndMissingStoreReferences() throws Exception {
		UUID storeId = UUID.randomUUID();
		String validRequest = "{\"type\":\"STORE\",\"storeId\":\"" + storeId
				+ "\",\"address\":" + ADDRESS + ",\"active\":true}";
		String requestWithUnknownProperty = "{\"type\":\"STORE\",\"storeId\":\"" + storeId
				+ "\",\"address\":" + ADDRESS + ",\"active\":true,\"unexpected\":true}";

		mockMvc.perform(post("/api/v1/origins")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestWithUnknownProperty))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/origins")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private AddressSnapshot address() {
		return new AddressSnapshot(
				"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07);
	}
}
