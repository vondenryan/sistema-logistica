package br.com.sistemalogistica.pessoa2.suppliers;

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

import br.com.sistemalogistica.pessoa2.stores.CreateStoreRequest;
import br.com.sistemalogistica.pessoa2.stores.StoreResponse;
import br.com.sistemalogistica.pessoa2.stores.StoreService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SupplierPersistenceIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierService supplierService;

	@Autowired
	private StoreService storeService;

	@Test
	void persistsListsRetrievesAndInactivatesSupplier() throws Exception {
		StoreResponse store =
				storeService.create(new CreateStoreRequest("Supplier test " + UUID.randomUUID(), true));
		SupplierResponse created = supplierService.create(
				new CreateSupplierRequest("Fornecedor Teste", "compras@example.com", store.id()));

		mockMvc.perform(get("/api/v1/suppliers/{supplierId}", created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id().toString()))
				.andExpect(jsonPath("$.name").value("Fornecedor Teste"))
				.andExpect(jsonPath("$.contactEmail").value("compras@example.com"))
				.andExpect(jsonPath("$.storeId").value(store.id().toString()));

		mockMvc.perform(get("/api/v1/suppliers").param("page", "0").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == '" + created.id() + "')]").isNotEmpty());

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", created.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id().toString()))
				.andExpect(jsonPath("$.active").doesNotExist());

		mockMvc.perform(get("/api/v1/suppliers/{supplierId}", created.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(created.id().toString()));
	}

	@Test
	void rejectsUnknownJsonPropertiesMissingStoreAndInvalidUuid() throws Exception {
		UUID storeId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Fornecedor\",\"storeId\":\"" + storeId
								+ "\",\"unexpected\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Fornecedor\",\"storeId\":\"" + storeId + "\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/v1/suppliers/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
