package com.skyhigh.interfaces.rest;

import com.skyhigh.domain.flight.entity.Flight;
import com.skyhigh.domain.seat.entity.Seat;
import com.skyhigh.domain.seat.entity.SeatState;
import com.skyhigh.infrastructure.persistence.flight.FlightRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.junit.jupiter.api.Disabled("Slow; run with PostgreSQL for full E2E")
class SeatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private SeatRepository seatRepository;

    private Long flightId;
    private Long seatId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        flightRepository.deleteAll();

        Flight flight = flightRepository.save(Flight.builder()
                .flightNumber("SH101")
                .origin("DEL")
                .destination("BOM")
                .departureTime(Instant.now())
                .totalSeats(12)
                .build());
        flightId = flight.getId();

        Seat seat = seatRepository.save(Seat.builder()
                .flightId(flightId)
                .seatNumber("1A")
                .rowNumber(1)
                .columnLetter("A")
                .state(SeatState.AVAILABLE)
                .build());
        seatId = seat.getId();
    }

    @Test
    void getSeatMap_returnsSeats() throws Exception {
        mockMvc.perform(get("/flights/{flightId}/seats", flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightId").value(flightId))
                .andExpect(jsonPath("$.seats", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.seats[0].seatNumber").value("1A"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("H2 does not support PESSIMISTIC_WRITE")
    void holdSeat_succeeds() throws Exception {
        mockMvc.perform(post("/flights/{flightId}/seats/{seatId}/hold", flightId, seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passengerId\":\"p1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HELD"))
                .andExpect(jsonPath("$.passengerId").value("p1"));
    }
}
