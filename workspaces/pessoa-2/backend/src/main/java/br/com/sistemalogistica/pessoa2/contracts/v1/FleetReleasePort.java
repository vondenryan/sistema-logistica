package br.com.sistemalogistica.pessoa2.contracts.v1;

public interface FleetReleasePort {

	ReleaseReceipt release(ReleaseRequest request);
}
