package com.ticketbooking.catalog.listener;

import com.ticketbooking.catalog.service.EventService;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketsGeneratedListenerTest {

    @Mock
    private EventService eventService;

    @Test
    void onTicketsGenerated_reducesAvailableQuantity_forEachItem() {
        TicketsGeneratedListener listener = new TicketsGeneratedListener(eventService);

        UUID catalogEventId = UUID.randomUUID();
        UUID vipClassId = UUID.randomUUID();
        UUID gaClassId = UUID.randomUUID();

        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .eventType("tickets.generated")
                .bookingId(UUID.randomUUID())
                .catalogEventId(catalogEventId)
                .items(List.of(
                        TicketsGeneratedEvent.TicketClassQuantity.builder().ticketClassId(vipClassId).quantity(2).build(),
                        TicketsGeneratedEvent.TicketClassQuantity.builder().ticketClassId(gaClassId).quantity(5).build()))
                .build();

        listener.onTicketsGenerated(event);

        verify(eventService).reduceAvailableQuantity(catalogEventId, vipClassId, 2);
        verify(eventService).reduceAvailableQuantity(catalogEventId, gaClassId, 5);
    }
}
