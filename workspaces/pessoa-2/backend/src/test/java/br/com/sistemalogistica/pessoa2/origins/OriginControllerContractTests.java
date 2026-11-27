package br.com.sistemalogistica.pessoa2.origins;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
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

class OriginControllerContractTests {

	private static final String VALID_ADDRESS = """
			{"line1":"Rua Exemplo, 100","line2":null,"city":"Cidade Fictícia","state":"SC",
			"postalCode":"00000-000","country":"BR","latitude":-26.48,"longitude":-49.07}
			""";

	private final OriginService service = org.mockito.Mockito.mock(OriginService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new OriginController(service))
				.setControllerAdvice(new ContractProblemHandler())
				.build();
	}

	@Test
	void createsOriginAndRetrievesSnapshotById() throws Exception {
		UUID originId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		OriginSnapshot response = snapshot(originId, storeId, true);
		when(service.create(any(CreateOriginRequest.class))).thenReturn(response);
		when(service.get(originId)).thenReturn(response);

		mockMvc.perform(post("/api/v1/origins")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request("STORE", storeId)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(originId.toString()))
				.andExpect(jsonPath("$.type").value("STORE"))
				.andExpect(jsonPath("$.storeId").value(storeId.toString()))
				.andExpect(jsonPath("$.address.line1").value("Rua Exemplo, 100"))
				.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(get("/api/v1/origins/{originId}", originId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(originId.toString()))
				.andExpect(jsonPath("$.address.country").value("BR"));
	}

	@Test
	void listsOriginsUsingStorePaginationEnvelope() throws Exception {
		UUID storeId = UUID.randomUUID();
		when(service.list(0, 1)).thenReturn(
				new OriginPageResponse(List.of(snapshot(UUID.randomUUID(), storeId, false)), 0, 1, 1));

		mockMvc.perform(get("/api/v1/origins").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].storeId").value(storeId.toString()))
				.andExpect(jsonPath("$.items[0].active").value(false))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void rejectsMissingOrInvalidAddressFieldsAsProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/origins")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"STORE\",\"storeId\":\"" + UUID.randomUUID()
								+ "\",\"address\":{\"line1\":\"\",\"city\":\"Town\",\"state\":\"S\","
								+ "\"postalCode\":\"12\",\"country\":\"US\",\"latitude\":null},\"active\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[*].field", hasItem("address.line1")))
				.andExpect(jsonPath("$.details[*].reason").isArray())
				.andExpect(jsonPath("$.details[*].message").doesNotExist());

		verify(service, never()).create(any(CreateOriginRequest.class));
	}

	@Test
	void patchCanInactivateOrigin() throws Exception {
		UUID originId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		when(service.patch(eq(originId), any(PatchOriginRequest.class))).thenReturn(snapshot(originId, storeId, false));

		mockMvc.perform(patch("/api/v1/origins/{originId}", originId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void rejectsMalformedOriginIdAsValidationProblem() throws Exception {
		mockMvc.perform(get("/api/v1/origins/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details").isArray());
	}

	@Test
	void mapsForbiddenAccessToProblemDetail() throws Exception {
		when(service.list(0, 20)).thenThrow(
				new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation."));

		mockMvc.perform(get("/api/v1/origins"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void rejectsOutOfRangeCoordinatesOnCreateAndPatch() throws Exception {
		for (String[] invalid : new String[][] {
				{"latitude", "-26.48", "-90.001"}, {"latitude", "-26.48", "90.001"},
				{"longitude", "-49.07", "-180.001"}, {"longitude", "-49.07", "180.001"}
		}) {
			String address = VALID_ADDRESS.replace(invalid[1], invalid[2]);
			for (var request : List.of(post("/api/v1/origins"),
					patch("/api/v1/origins/{originId}", UUID.randomUUID()))) {
				mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
								.content("{\"type\":\"STORE\",\"storeId\":\"" + UUID.randomUUID()
										+ "\",\"address\":" + address + ",\"active\":true}"))
						.andExpect(status().isBadRequest())
						.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
						.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
						.andExpect(jsonPath("$.details.length()").value(1))
						.andExpect(jsonPath("$.details[0].field").value("address." + invalid[0]))
						.andExpect(jsonPath("$.details[0].reason").isNotEmpty())
						.andExpect(jsonPath("$.details[0].message").doesNotHaveJsonPath());
			}
		}
		verify(service, never()).create(any(CreateOriginRequest.class));
		verify(service, never()).patch(any(UUID.class), any(PatchOriginRequest.class));
	}

	private OriginSnapshot snapshot(UUID originId, UUID storeId, boolean active) {
		return new OriginSnapshot(
				originId,
				OriginType.STORE,
				storeId,
				new AddressSnapshot(
						"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07),
				active);
	}

	private String request(String type, UUID storeId) {
		return "{\"type\":\"" + type + "\",\"storeId\":\"" + storeId + "\",\"address\":"
				+ VALID_ADDRESS + ",\"active\":true}";
	}
}
