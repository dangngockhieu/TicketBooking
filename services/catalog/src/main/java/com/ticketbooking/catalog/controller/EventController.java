package com.ticketbooking.catalog.controller;

import com.ticketbooking.catalog.dto.request.CreateEventRequest;
import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.dto.request.UpdateEventRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.catalog.service.EventService;
import com.ticketbooking.catalog.util.SecurityUtil;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> search(
            @RequestParam(name = "category", required = false) String categorySlug,
            @RequestParam(required = false) Instant startFrom,
            @RequestParam(required = false) Instant startTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        EventSearchFilter filter = new EventSearchFilter(categorySlug, startFrom, startTo, location, keyword);
        return ResponseEntity.ok(ApiResponse.success(eventService.search(filter, pageable)));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> getDetail(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getPublicDetail(eventId)));
    }

    /**
     * multipart/form-data: phần "data" là JSON của CreateEventRequest, phần
     * "image" (tùy chọn) là file ảnh banner — nếu có, URL ảnh lưu được sẽ ghi
     * đè bannerUrl trong "data" (xem EventImageService#store).
     */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> create(
            @Valid @RequestPart("data") CreateEventRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        EventResponse response = eventService.create(currentOrganizerId(), request, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sự kiện thành công.", response));
    }

    @PutMapping(value = "/{eventId}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> update(
            @PathVariable UUID eventId,
            @Valid @RequestPart("data") UpdateEventRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        EventResponse response = eventService.update(currentOrganizerId(), eventId, request, image);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sự kiện thành công.", response));
    }

    @PatchMapping("/{eventId}/publish")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> publish(@PathVariable UUID eventId) {
        EventResponse response = eventService.publish(currentOrganizerId(), eventId);
        return ResponseEntity.ok(ApiResponse.success("Publish sự kiện thành công.", response));
    }

    private UUID currentOrganizerId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
