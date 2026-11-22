package br.com.sistemalogistica.pessoa2.suppliers;

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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractProblemHandler;

class SupplierControllerContractTests {

	private final SupplierService service = org.mockito.Mockito.mock(SupplierService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new SupplierController(service))
				.setControllerAdvice(new ContractProblemHandler())
				.build();
	}

	@Test
	void createsSupplierAndOmitsMissingContactEmail() throws Exception {
		UUID supplierId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		SupplierResponse response = new SupplierResponse(supplierId, "Fornecedor Norte", null, storeId);
		when(service.create(any(CreateSupplierRequest.class))).thenReturn(response);
		when(service.get(supplierId)).thenReturn(response);

		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Fornecedor Norte\",\"storeId\":\"" + storeId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(supplierId.toString()))
				.andExpect(jsonPath("$.name").value("Fornecedor Norte"))
				.andExpect(jsonPath("$.storeId").value(storeId.toString()))
				.andExpect(jsonPath("$.contactEmail").doesNotExist());

		mockMvc.perform(get("/api/v1/suppliers/{supplierId}", supplierId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contactEmail").doesNotExist());
	}

	@Test
	void includesContactEmailWhenPresentAndUsesPageEnvelope() throws Exception {
		UUID supplierId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		SupplierResponse response =
				new SupplierResponse(supplierId, "Fornecedor Sul", "compras@example.com", storeId);
		when(service.list(0, 1)).thenReturn(
				new SupplierPageResponse(List.of(response), 0, 1, 1));
		when(service.create(any(CreateSupplierRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Fornecedor Sul\",\"contactEmail\":\"compras@example.com\",\"storeId\":\""
								+ storeId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contactEmail").value("compras@example.com"));

		mockMvc.perform(get("/api/v1/suppliers").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].storeId").value(storeId.toString()))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void rejectsMissingNameInvalidEmailUnknownPropertyAndNullPatchEmail() throws Exception {
		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"contactEmail\":\"compras@example.com\",\"storeId\":\""
								+ UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/suppliers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Fornecedor\",\"contactEmail\":\"invalid\",\"storeId\":\""
								+ UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"contactEmail\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		verify(service, never()).create(any(CreateSupplierRequest.class));
	}

	@Test
	void rejectsNullAndWrongTypedPatchFieldsAsValidationProblems() throws Exception {
		UUID supplierId = UUID.randomUUID();

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", supplierId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("name"));

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", supplierId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"storeId\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("storeId"));

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", supplierId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":123}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("name"));

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", supplierId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":\"false\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("active"));

		verify(service, never()).patch(org.mockito.ArgumentMatchers.eq(supplierId),
				any(PatchSupplierRequest.class));
	}

	@Test
	void patchesActiveFalseAndKeepsSupplierVisible() throws Exception {
		UUID supplierId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		when(service.patch(org.mockito.ArgumentMatchers.eq(supplierId), any(PatchSupplierRequest.class)))
				.thenReturn(new SupplierResponse(supplierId, "Fornecedor Norte", null, storeId));

		mockMvc.perform(patch("/api/v1/suppliers/{supplierId}", supplierId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(supplierId.toString()))
				.andExpect(jsonPath("$.active").doesNotExist());

		when(service.get(supplierId))
				.thenReturn(new SupplierResponse(supplierId, "Fornecedor Norte", null, storeId));
		mockMvc.perform(get("/api/v1/suppliers/{supplierId}", supplierId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(supplierId.toString()));
	}

	@Test
	void rejectsInvalidUuidAndMapsNotFoundAndForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/suppliers/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(patch("/api/v1/suppliers/not-a-uuid")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		when(service.list(0, 20)).thenThrow(
				new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation."));
		mockMvc.perform(get("/api/v1/suppliers"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}
