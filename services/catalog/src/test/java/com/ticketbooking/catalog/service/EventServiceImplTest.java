package com.ticketbooking.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketbooking.catalog.dto.request.CreateEventRequest;
import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.dto.request.TicketClassRequest;
import com.ticketbooking.catalog.dto.request.UpdateEventRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.catalog.entity.Event;
import com.ticketbooking.catalog.enums.EventStatus;
import com.ticketbooking.catalog.repository.CategoryRepository;
import com.ticketbooking.catalog.repository.EventRepository;
import com.ticketbooking.catalog.repository.ProcessedTicketEventRepository;
import com.ticketbooking.catalog.repository.TicketClassRepository;
import com.ticketbooking.catalog.service.impl.EventServiceImpl;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TicketClassRepository ticketClassRepository;

    @Mock
    private ProcessedTicketEventRepository processedTicketEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EventImageService eventImageService;

    private EventServiceImpl eventService;

    private UUID organizerId;
    private UUID eventId;
    private Event draftEvent;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        eventService = new EventServiceImpl(eventRepository, categoryRepository, ticketClassRepository,
                processedTicketEventRepository, redisTemplate, objectMapper, eventImageService);

        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        draftEvent = Event.builder()
                .id(eventId)
                .organizerId(organizerId)
                .title("Concert ABC")
                .location("Hà Nội")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .status(EventStatus.DRAFT)
                .build();
    }

    @Test
    void testCreate_Success_AvailableQuantityEqualsTotalQuantity() {
        CreateEventRequest request = new CreateEventRequest(
                null, "Concert ABC", "desc", "Hà Nội", null, null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null,
                List.of(new TicketClassRequest("VIP", null, java.math.BigDecimal.valueOf(500000), 100)));

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        EventResponse result = eventService.create(organizerId, request, null);

        assertEquals(EventStatus.DRAFT, result.status());
        assertEquals(1, result.ticketClasses().size());
        assertEquals(100, result.ticketClasses().get(0).availableQuantity());
    }

    @Test
    void testUpdate_NotOwner_ThrowsForbidden() {
        UpdateEventRequest request = new UpdateEventRequest(
                null, "Tên mới", null, "Hà Nội", null, null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null);
        when(eventRepository.findWithDetailsById(eventId)).thenReturn(Optional.of(draftEvent));

        UUID anotherOrganizer = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> eventService.update(anotherOrganizer, eventId, request, null));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void testUpdate_NotFound_ThrowsResourceNotFound() {
        UpdateEventRequest request = new UpdateEventRequest(
                null, "Tên mới", null, "Hà Nội", null, null,
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null);
        when(eventRepository.findWithDetailsById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.update(organizerId, eventId, request, null));
    }

    @Test
    void testPublish_FromDraft_Success() {
        when(eventRepository.findWithDetailsById(eventId)).thenReturn(Optional.of(draftEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of());

        EventResponse result = eventService.publish(organizerId, eventId);

        assertEquals(EventStatus.PUBLISHED, result.status());
        verify(redisTemplate).delete("catalog:event:" + eventId);
    }

    @Test
    void testPublish_AlreadyPublished_ThrowsConflict() {
        draftEvent.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findWithDetailsById(eventId)).thenReturn(Optional.of(draftEvent));

        assertThrows(ConflictException.class, () -> eventService.publish(organizerId, eventId));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void testGetPublicDetail_CacheMiss_NotPublished_ThrowsResourceNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("catalog:event:" + eventId)).thenReturn(null);
        when(eventRepository.findWithDetailsById(eventId)).thenReturn(Optional.of(draftEvent));

        assertThrows(ResourceNotFoundException.class, () -> eventService.getPublicDetail(eventId));
    }

    @Test
    void testGetPublicDetail_CacheHit_ReturnsDeserializedResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        draftEvent.setStatus(EventStatus.PUBLISHED);
        String cachedJson = objectMapper.writeValueAsString(EventResponse.from(draftEvent));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("catalog:event:" + eventId)).thenReturn(cachedJson);

        EventResponse result = eventService.getPublicDetail(eventId);

        assertEquals(eventId, result.id());
        verify(eventRepository, never()).findWithDetailsById(any());
    }

    @Test
    void testSearch_CacheMiss_BuildsFromRepository() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Event> page = new PageImpl<>(List.of(), pageable, 0);
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(page);

        var result = eventService.search(new EventSearchFilter(null, null, null, null, null), pageable);

        assertEquals(0, result.getTotalElements());
        assertEquals(1, result.getPage());
    }

    @Test
    void reduceAvailableQuantity_decrementsAndInvalidatesCache() {
        UUID ticketClassId = UUID.randomUUID();
        when(ticketClassRepository.decrementAvailableQuantity(ticketClassId, 2)).thenReturn(1);
        when(redisTemplate.keys("catalog:event:list:*")).thenReturn(java.util.Set.of());

        eventService.reduceAvailableQuantity(eventId, ticketClassId, 2);

        verify(redisTemplate).delete("catalog:event:" + eventId);
    }

    @Test
    void reduceAvailableQuantity_logsError_andSkipsCacheInvalidation_whenInsufficientStock() {
        UUID ticketClassId = UUID.randomUUID();
        when(ticketClassRepository.decrementAvailableQuantity(ticketClassId, 999)).thenReturn(0);

        eventService.reduceAvailableQuantity(eventId, ticketClassId, 999);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void processTicketsGenerated_firstDelivery_marksProcessedAndReducesEachItem() {
        UUID bookingId = UUID.randomUUID();
        UUID vipClassId = UUID.randomUUID();
        UUID gaClassId = UUID.randomUUID();
        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .eventType("tickets.generated")
                .bookingId(bookingId)
                .catalogEventId(eventId)
                .items(List.of(
                        TicketsGeneratedEvent.TicketClassQuantity.builder().ticketClassId(vipClassId).quantity(2).build(),
                        TicketsGeneratedEvent.TicketClassQuantity.builder().ticketClassId(gaClassId).quantity(5).build()))
                .build();

        when(processedTicketEventRepository.existsById(bookingId)).thenReturn(false);
        when(ticketClassRepository.decrementAvailableQuantity(vipClassId, 2)).thenReturn(1);
        when(ticketClassRepository.decrementAvailableQuantity(gaClassId, 5)).thenReturn(1);
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of());

        eventService.processTicketsGenerated(event);

        verify(processedTicketEventRepository).save(argThat(marker -> marker.getBookingId().equals(bookingId)));
        verify(ticketClassRepository).decrementAvailableQuantity(vipClassId, 2);
        verify(ticketClassRepository).decrementAvailableQuantity(gaClassId, 5);
    }

    @Test
    void processTicketsGenerated_redelivery_skipsReductionEntirely() {
        UUID bookingId = UUID.randomUUID();
        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .eventType("tickets.generated")
                .bookingId(bookingId)
                .catalogEventId(eventId)
                .items(List.of(TicketsGeneratedEvent.TicketClassQuantity.builder()
                        .ticketClassId(UUID.randomUUID()).quantity(2).build()))
                .build();

        when(processedTicketEventRepository.existsById(bookingId)).thenReturn(true);

        eventService.processTicketsGenerated(event);

        verify(processedTicketEventRepository, never()).save(any());
        verify(ticketClassRepository, never()).decrementAvailableQuantity(any(), anyInt());
    }
}
