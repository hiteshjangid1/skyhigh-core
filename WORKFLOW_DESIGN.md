# Workflow Design

## Seat Lifecycle

AVAILABLE -> HELD -> CONFIRMED. HELD can expire to AVAILABLE. CONFIRMED can cancel to CANCELLED -> AVAILABLE.

## Check-In Flow

Start (hold seat) -> Add baggage -> If overweight: await payment -> Complete payment -> Confirm seat.

## Waitlist

On seat released (expiry or cancel), next PENDING waitlist entry gets the seat via automatic hold.

## Database Tables

flights, seats, seat_reservations, check_ins, baggage_records, waitlist_entries, access_logs, abuse_events.
