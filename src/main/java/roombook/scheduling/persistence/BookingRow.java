package roombook.scheduling.persistence;

import roombook.scheduling.domain.Booking;

/** Booking and room name loaded together without aggregate associations. */
public record BookingRow(Booking booking, String roomName) {}
