# Place directory

The local implementation provides published-place search/detail, aggregate ratings, published
review listing and upsert, plus booking-request creation. Review listing intentionally returns
only review id, rating, body, and timestamp; it does not expose account identifiers or profile
fields. Booking creation is a request (`REQUESTED`), not a confirmed appointment.

Routes are documented by `PlaceController` under `/api/v1/places`. Place hours, doctor profiles,
service catalog/pricing, clinic image galleries, booking availability, and booking management
are not represented by this API and must not be presented as verified place data.
