# Product Requirements Document: SkyHigh Core

## 1. Problem Statement

SkyHigh Airlines needs to transform its airport self-check-in experience to handle heavy peak-hour traffic. During popular flight check-in windows, hundreds of passengers attempt to select seats, add baggage, and complete check-in simultaneously.

## 2. Goals

- Prevent seat conflicts
- Fast check-in (P95 less than 1 second for seat map)
- Time-bound holds (2 minutes, auto-release)
- Baggage validation (25kg limit, fee for overweight)
- Waitlist management
- Abuse protection (detect 50+ seat maps in 2 seconds)

## 3. Key Users

- Passenger: completing self-check-in
- System: automated hold expiry, waitlist assignment
- Operations: monitoring and audit

## 4. Non-Functional Requirements

- Seat map P95 latency: less than 1 second
- Concurrent users: hundreds
- No duplicate seat assignments
- Hold expiry within 15 seconds of 2-minute window
