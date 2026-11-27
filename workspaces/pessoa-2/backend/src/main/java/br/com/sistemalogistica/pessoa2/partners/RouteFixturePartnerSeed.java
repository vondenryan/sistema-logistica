package br.com.sistemalogistica.pessoa2.partners;

import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "test", "integration-demo & integration"})
public class RouteFixturePartnerSeed implements ApplicationRunner {

	private static final UUID STORE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private final JdbcTemplate jdbcTemplate;

	public RouteFixturePartnerSeed(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		jdbcTemplate.update(
				"INSERT INTO stores (id, name, active) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
				STORE_ID,
				"Loja de demonstração",
				true);
		jdbcTemplate.update(
				"""
				INSERT INTO origins (
				    id, type, store_id, address_line1, address_line2, address_city, address_state,
				    address_postal_code, address_country, address_latitude, address_longitude, active
				) VALUES (?, 'STORE', ?, 'Rua Exemplo, 100', NULL, 'Cidade Fictícia', 'SC',
				    '00000-000', 'BR', -26.480000, -49.070000, TRUE)
				ON CONFLICT (id) DO NOTHING
				""",
				ORIGIN_ID,
				STORE_ID);
	}
}
