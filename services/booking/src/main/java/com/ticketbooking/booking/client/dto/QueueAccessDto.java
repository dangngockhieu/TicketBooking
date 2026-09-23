package com.ticketbooking.booking.client.dto;

/** Phản hồi từ Queue Service — xem {@code QueueAccessResponse} bên queue-service. */
public record QueueAccessDto(boolean enabled, boolean valid) {
}
