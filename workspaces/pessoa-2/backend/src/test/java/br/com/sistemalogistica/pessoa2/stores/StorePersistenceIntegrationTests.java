package br.com.sistemalogistica.pessoa2.stores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StorePersistenceIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StoreService storeService;

	@Test
	void persistsListsRetrievesInactivatesAndRejectsDuplicateStoreNames() throws Exception {
		String name = "Integration store " + UUID.randomUUID();

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("active"));

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"active\":true,\"unexpected\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		StoreResponse created = storeService.create(new CreateStoreRequest(name, true));

		mockMvc.perform(get("/api/v1/stores/{storeId}", created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id().toString()))
				.andExpect(jsonPath("$.name").value(name));

		mockMvc.perform(get("/api/v1/stores").param("page", "0").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == '" + created.id() + "')]").isNotEmpty());

		mockMvc.perform(patch("/api/v1/stores/{storeId}", created.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name.toUpperCase() + "\",\"active\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}
}
