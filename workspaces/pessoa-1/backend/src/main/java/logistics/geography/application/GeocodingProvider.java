package logistics.geography.application;

import contracts.v1.AddressSnapshot;

public interface GeocodingProvider {
  AddressSnapshot geocode(AddressSnapshot address);
}
