package br.com.sistemalogistica.pessoa2.origins;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "origins")
public class Origin {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OriginType type;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "address_line1", nullable = false, length = 200)
	private String addressLine1;

	@Column(name = "address_line2", length = 100)
	private String addressLine2;

	@Column(name = "address_city", nullable = false, length = 100)
	private String addressCity;

	@Column(name = "address_state", nullable = false, length = 2)
	private String addressState;

	@Column(name = "address_postal_code", length = 9)
	private String addressPostalCode;

	@Column(name = "address_country", nullable = false, length = 2)
	private String addressCountry;

	@Column(name = "address_latitude", nullable = false)
	private Double addressLatitude;

	@Column(name = "address_longitude", nullable = false)
	private Double addressLongitude;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Origin() {
	}

	public Origin(OriginType type, UUID storeId, AddressSnapshot address, boolean active) {
		this.id = UUID.randomUUID();
		this.type = type;
		this.storeId = storeId;
		setAddress(address);
		this.active = active;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public OriginType getType() {
		return type;
	}

	public UUID getStoreId() {
		return storeId;
	}

	public AddressSnapshot getAddress() {
		return new AddressSnapshot(
				addressLine1,
				addressLine2,
				addressCity,
				addressState,
				addressPostalCode,
				addressCountry,
				addressLatitude,
				addressLongitude);
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void update(OriginType type, UUID storeId, AddressSnapshot address, boolean active) {
		this.type = type;
		this.storeId = storeId;
		setAddress(address);
		this.active = active;
	}

	private void setAddress(AddressSnapshot address) {
		this.addressLine1 = address.line1();
		this.addressLine2 = address.line2();
		this.addressCity = address.city();
		this.addressState = address.state();
		this.addressPostalCode = address.postalCode();
		this.addressCountry = address.country();
		this.addressLatitude = address.latitude();
		this.addressLongitude = address.longitude();
	}
}
