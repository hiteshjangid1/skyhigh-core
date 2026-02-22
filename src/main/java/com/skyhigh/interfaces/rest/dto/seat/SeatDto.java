package com.skyhigh.interfaces.rest.dto.seat;

import com.skyhigh.domain.seat.entity.Seat;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeatDto {
    private Long id;
    private String seatNumber;
    private Integer rowNumber;
    private String columnLetter;
    private String state;

    public static SeatDto from(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .rowNumber(seat.getRowNumber())
                .columnLetter(seat.getColumnLetter())
                .state(seat.getState().name())
                .build();
    }
}
