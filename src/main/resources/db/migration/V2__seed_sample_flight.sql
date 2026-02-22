INSERT INTO flights (flight_number, origin, destination, departure_time, total_seats, created_at, updated_at)
VALUES ('SH101', 'DEL', 'BOM', CURRENT_TIMESTAMP, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO seats (flight_id, seat_number, row_number, column_letter, state, version)
SELECT f.id, '1A', 1, 'A', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '1B', 1, 'B', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '1C', 1, 'C', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '2A', 2, 'A', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '2B', 2, 'B', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '2C', 2, 'C', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '3A', 3, 'A', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '3B', 3, 'B', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '3C', 3, 'C', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '4A', 4, 'A', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '4B', 4, 'B', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101'
UNION ALL SELECT f.id, '4C', 4, 'C', 'AVAILABLE', 0 FROM flights f WHERE f.flight_number = 'SH101';
