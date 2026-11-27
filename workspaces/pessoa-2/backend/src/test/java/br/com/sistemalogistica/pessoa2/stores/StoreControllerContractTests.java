package br.com.sistemalogistica.pessoa2.stores;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractProblemHandler;

class StoreControllerContractTests {

	private final StoreService service = org.mockito.Mockito.mock(StoreService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(service))
				.setControllerAdvice(new ContractProblemHandler())
				.build();
	}

	@Test
	void createsStoreWithContractResponseAndRetrievesItById() throws Exception {
		UUID storeId = UUID.randomUUID();
		StoreResponse response = new StoreResponse(storeId, "Loja Norte", true);
		when(service.create(any(CreateStoreRequest.class))).thenReturn(response);
		when(service.get(storeId)).thenReturn(response);

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Loja Norte\",\"active\":true}"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(storeId.toString()))
				.andExpect(jsonPath("$.name").value("Loja Norte"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.createdAt").doesNotExist());

		mockMvc.perform(get("/api/v1/stores/{storeId}", storeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(storeId.toString()))
				.andExpect(jsonPath("$.name").value("Loja Norte"))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void listsStoresUsingContractPaginationEnvelope() throws Exception {
		UUID storeId = UUID.randomUUID();
		when(service.list(0, 1)).thenReturn(
				new StorePageResponse(List.of(new StoreResponse(storeId, "Loja Sul", false)), 0, 1, 1));

		mockMvc.perform(get("/api/v1/stores").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(storeId.toString()))
				.andExpect(jsonPath("$.items[0].active").value(false))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void rejectsOutOfRangePageSizeAsValidationProblem() throws Exception {
		mockMvc.perform(get("/api/v1/stores").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details").isArray());
	}

	@Test
	void requiresActiveAndRejectsBlankNamesOnCreate() throws Exception {
		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Loja Norte\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://example.invalid/problems/validation-error"))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("active"));

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"  \",\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("name"));

		verify(service, never()).create(any(CreateStoreRequest.class));
	}

	@Test
	void mapsDuplicateNameToProblemDetailConflict() throws Exception {
		when(service.create(any(CreateStoreRequest.class))).thenThrow(new ContractException(
				"CONFLICT",
				"A store with this name already exists.",
				List.of(Map.of("field", "name", "reason", "Must be unique."))));

		mockMvc.perform(post("/api/v1/stores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Loja Norte\",\"active\":true}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CONFLICT"))
				.andExpect(jsonPath("$.details[0].field").value("name"))
				.andExpect(jsonPath("$.details[0].reason").value("Must be unique."));
	}

	@Test
	void patchCanInactivateStoreWithoutPhysicalDelete() throws Exception {
		UUID storeId = UUID.randomUUID();
		when(service.patch(org.mockito.ArgumentMatchers.eq(storeId), any(PatchStoreRequest.class)))
				.thenReturn(new StoreResponse(storeId, "Loja Norte", false));

		mockMvc.perform(patch("/api/v1/stores/{storeId}", storeId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(storeId.toString()))
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void rejectsMalformedStoreIdAsValidationProblem() throws Exception {
		mockMvc.perform(get("/api/v1/stores/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details").isArray());
	}
}
