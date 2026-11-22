package br.com.sistemalogistica.pessoa2.contracts.v1;

public record AddressSnapshot(
		String line1,
		String line2,
		String city,
		String state,
		String postalCode,
		String country,
		Double latitude,
		Double longitude) {
}
