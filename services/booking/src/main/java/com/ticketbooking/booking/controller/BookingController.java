package com.ticketbooking.booking.controller;

import com.ticketbooking.booking.dto.request.CheckInRequest;
import com.ticketbooking.booking.dto.request.CreateBookingRequest;
import com.ticketbooking.booking.dto.response.BookingResponse;
import com.ticketbooking.booking.dto.response.CheckInResponse;
import com.ticketbooking.booking.dto.response.EventReportResponse;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.booking.util.SecurityUtil;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.create(currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn đặt vé thành công.", response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> listMine(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.listMine(currentUserId(), status, pageable)));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponse>> getDetail(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getDetail(currentUserId(), bookingId)));
    }

    @DeleteMapping("/{bookingId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID bookingId) {
        bookingService.cancel(currentUserId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công.", null));
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        CheckInResponse response = bookingService.checkIn(currentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Check-in thành công.", response));
    }

    @GetMapping("/organizer/events/{eventId}/report")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventReportResponse>> getOrganizerEventReport(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getEventReport(eventId, currentUserId())));
    }

    @GetMapping("/admin/events/{eventId}/report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EventReportResponse>> getAdminEventReport(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getEventReport(eventId, null)));
    }

    private UUID currentUserId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
