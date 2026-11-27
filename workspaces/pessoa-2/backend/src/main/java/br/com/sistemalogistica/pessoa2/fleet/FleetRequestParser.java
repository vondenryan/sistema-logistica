package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

final class FleetRequestParser {

	private static final Pattern UUID_PATTERN = Pattern.compile(
			"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final Pattern WEIGHT_PATTERN = Pattern.compile("^\\d+\\.\\d{1,3}$");

	private FleetRequestParser() {
	}

	static CarrierCreateRequest carrierCreate(JsonNode body) {
		requireFields(body, Set.of("name", "active"), Set.of("name", "active"));
		return new CarrierCreateRequest(text(body, "name"), bool(body, "active"));
	}

	static CarrierPatchRequest carrierPatch(JsonNode body) {
		requireFields(body, Set.of("name", "active"), Set.of());
		requireNonEmpty(body);
		return new CarrierPatchRequest(optionalText(body, "name"), optionalBoolean(body, "active"));
	}

	static DriverCreateRequest driverCreate(JsonNode body) {
		requireFields(body, Set.of("name", "carrierId", "active"), Set.of("name", "active"));
		return new DriverCreateRequest(
				text(body, "name"),
				optionalUuid(body, "carrierId"),
				bool(body, "active"));
	}

	static DriverPatchRequest driverPatch(JsonNode body) {
		requireFields(body, Set.of("name", "carrierId", "active"), Set.of());
		requireNonEmpty(body);
		return new DriverPatchRequest(
				optionalText(body, "name"),
				optionalUuid(body, "carrierId"),
				body.has("carrierId"),
				optionalBoolean(body, "active"));
	}

	static VehicleCreateRequest vehicleCreate(JsonNode body) {
		requireFields(body, Set.of("identifier", "capacityKg", "fleetType", "carrierId", "active"),
				Set.of("identifier", "capacityKg", "fleetType", "active"));
		return new VehicleCreateRequest(
				text(body, "identifier"),
				weight(body, "capacityKg"),
				fleetType(body, "fleetType"),
				optionalUuid(body, "carrierId"),
				bool(body, "active"));
	}

	static VehiclePatchRequest vehiclePatch(JsonNode body) {
		requireFields(body, Set.of("identifier", "capacityKg", "fleetType", "carrierId", "active"), Set.of());
		requireNonEmpty(body);
		return new VehiclePatchRequest(
				optionalText(body, "identifier"),
				optionalWeight(body, "capacityKg"),
				optionalFleetType(body, "fleetType"),
				optionalUuid(body, "carrierId"),
				body.has("carrierId"),
				optionalBoolean(body, "active"));
	}

	private static void requireFields(JsonNode body, Set<String> allowed, Set<String> required) {
		if (body == null || !body.isObject()) {
			throw ContractException.validation("body", "A JSON object must be provided.");
		}
		body.propertyNames().forEach(field -> {
			if (!allowed.contains(field)) {
				throw ContractException.validation(field, "Unknown field.");
			}
		});
		for (String field : required) {
			if (!body.has(field)) {
				throw ContractException.validation(field, "Must be provided.");
			}
		}
	}

	private static void requireNonEmpty(JsonNode body) {
		if (body.isEmpty()) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}
	}

	private static String text(JsonNode body, String field) {
		JsonNode value = body.get(field);
		if (value == null || !value.isTextual()) {
			throw ContractException.validation(field, "Must be a string.");
		}
		return value.textValue();
	}

	private static String optionalText(JsonNode body, String field) {
		return body.has(field) ? text(body, field) : null;
	}

	private static boolean bool(JsonNode body, String field) {
		JsonNode value = body.get(field);
		if (value == null || !value.isBoolean()) {
			throw ContractException.validation(field, "Must be a boolean.");
		}
		return value.booleanValue();
	}

	private static Boolean optionalBoolean(JsonNode body, String field) {
		return body.has(field) ? bool(body, field) : null;
	}

	private static UUID optionalUuid(JsonNode body, String field) {
		if (!body.has(field)) {
			return null;
		}
		String value = text(body, field);
		if (!UUID_PATTERN.matcher(value).matches()) {
			throw ContractException.validation(field, "Must be a UUID.");
		}
		return UUID.fromString(value);
	}

	private static BigDecimal weight(JsonNode body, String field) {
		String value = text(body, field);
		if (!WEIGHT_PATTERN.matcher(value).matches()) {
			throw ContractException.validation(field, "Must be a decimal string with at most three decimal places.");
		}

		BigDecimal decimal;
		try {
			decimal = new BigDecimal(value);
		} catch (NumberFormatException exception) {
			throw ContractException.validation(field, "Must be a valid decimal string.");
		}
		if (decimal.signum() <= 0) {
			throw ContractException.validation(field, "Must be greater than zero.");
		}
		if (decimal.precision() > 12) {
			throw ContractException.validation(field, "Must fit decimal(12,3).");
		}
		return decimal;
	}

	private static BigDecimal optionalWeight(JsonNode body, String field) {
		return body.has(field) ? weight(body, field) : null;
	}

	private static FleetType fleetType(JsonNode body, String field) {
		String value = text(body, field);
		try {
			return FleetType.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw ContractException.validation(field, "Must be OWN or THIRD_PARTY.");
		}
	}

	private static FleetType optionalFleetType(JsonNode body, String field) {
		return body.has(field) ? fleetType(body, field) : null;
	}
}
