package com.ticketbooking.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.catalog.dto.request.CreateEventRequest;
import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.dto.request.TicketClassRequest;
import com.ticketbooking.catalog.dto.request.UpdateEventRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.catalog.entity.Category;
import com.ticketbooking.catalog.entity.Event;
import com.ticketbooking.catalog.entity.TicketClass;
import com.ticketbooking.catalog.enums.EventStatus;
import com.ticketbooking.catalog.repository.CategoryRepository;
import com.ticketbooking.catalog.repository.EventRepository;
import com.ticketbooking.catalog.repository.EventSpecifications;
import com.ticketbooking.catalog.service.EventService;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class EventServiceImpl implements EventService {

    private static final String DETAIL_CACHE_PREFIX = "catalog:event:";
    private static final String LIST_CACHE_PREFIX = "catalog:event:list:";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(15);
    private static final Duration LIST_TTL = Duration.ofMinutes(5);

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public EventServiceImpl(
            EventRepository eventRepository,
            CategoryRepository categoryRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(EventSearchFilter filter, Pageable pageable) {
        String cacheKey = LIST_CACHE_PREFIX + hashFilter(filter, pageable);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructParametricType(PageResponse.class, EventResponse.class));
            } catch (Exception e) {
                log.warn("Không thể deserialize cache list sự kiện, bỏ qua cache: {}", e.getMessage());
            }
        }

        Page<Event> page = eventRepository.findAll(EventSpecifications.matchesPublicFilter(filter), pageable);
        PageResponse<EventResponse> response = PageResponse.<EventResponse>builder()
                .items(page.getContent().stream().map(EventResponse::from).toList())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();

        writeToCache(cacheKey, response, LIST_TTL);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getPublicDetail(UUID eventId) {
        String cacheKey = DETAIL_CACHE_PREFIX + eventId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, EventResponse.class);
            } catch (Exception e) {
                log.warn("Không thể deserialize cache chi tiết sự kiện {}, bỏ qua cache: {}", eventId, e.getMessage());
            }
        }

        Event event = eventRepository.findWithDetailsById(eventId)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện."));

        EventResponse response = EventResponse.from(event);
        writeToCache(cacheKey, response, DETAIL_TTL);
        return response;
    }

    @Override
    public EventResponse create(UUID organizerId, CreateEventRequest request) {
        Category category = resolveCategory(request.categoryId());

        Event event = Event.builder()
                .category(category)
                .organizerId(organizerId)
                .title(request.title())
                .description(request.description())
                .location(request.location())
                .venueName(request.venueName())
                .bannerUrl(request.bannerUrl())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .saleStartTime(request.saleStartTime())
                .saleEndTime(request.saleEndTime())
                .status(EventStatus.DRAFT)
                .build();

        for (TicketClassRequest tc : request.ticketClasses()) {
            event.addTicketClass(TicketClass.builder()
                    .name(tc.name())
                    .description(tc.description())
                    .price(tc.price())
                    .totalQuantity(tc.totalQuantity())
                    .availableQuantity(tc.totalQuantity())
                    .build());
        }

        return EventResponse.from(eventRepository.save(event));
    }

    @Override
    public EventResponse update(UUID organizerId, UUID eventId, UpdateEventRequest request) {
        Event event = eventRepository.findWithDetailsById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện."));
        requireOwner(event, organizerId);

        Category category = resolveCategory(request.categoryId());

        event.setCategory(category);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setVenueName(request.venueName());
        event.setBannerUrl(request.bannerUrl());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setSaleStartTime(request.saleStartTime());
        event.setSaleEndTime(request.saleEndTime());

        Event saved = eventRepository.save(event);
        invalidateCaches(saved);
        return EventResponse.from(saved);
    }

    @Override
    public EventResponse publish(UUID organizerId, UUID eventId) {
        Event event = eventRepository.findWithDetailsById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện."));
        requireOwner(event, organizerId);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ConflictException("Chỉ có thể publish sự kiện đang ở trạng thái DRAFT.");
        }

        event.setStatus(EventStatus.PUBLISHED);
        Event saved = eventRepository.save(event);
        invalidateCaches(saved);
        return EventResponse.from(saved);
    }

    private Category resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục."));
    }

    private void requireOwner(Event event, UUID organizerId) {
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên sự kiện này.");
        }
    }

    private void writeToCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Không thể ghi cache {}: {}", key, e.getMessage());
        }
    }

    /**
     * Xóa cache chi tiết (key chính xác) + toàn bộ cache danh sách (dùng
     * {@code KEYS} — chấp nhận được ở quy mô hiện tại, nhưng cần đổi sang
     * {@code SCAN} theo cursor khi keyspace lớn để tránh chặn Redis).
     */
    private void invalidateCaches(Event event) {
        redisTemplate.delete(DETAIL_CACHE_PREFIX + event.getId());
        Set<String> listKeys = redisTemplate.keys(LIST_CACHE_PREFIX + "*");
        if (listKeys != null && !listKeys.isEmpty()) {
            redisTemplate.delete(listKeys);
        }
    }

    private String hashFilter(EventSearchFilter filter, Pageable pageable) {
        String raw = String.join("|",
                String.valueOf(filter.categorySlug()),
                String.valueOf(filter.startFrom()),
                String.valueOf(filter.startTo()),
                String.valueOf(filter.location()),
                String.valueOf(filter.keyword()),
                String.valueOf(pageable.getPageNumber()),
                String.valueOf(pageable.getPageSize()),
                String.valueOf(pageable.getSort()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
