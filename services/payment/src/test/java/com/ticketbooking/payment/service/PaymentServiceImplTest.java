package com.ticketbooking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.payment.client.BookingClient;
import com.ticketbooking.payment.client.dto.BookingDto;
import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.entity.Transaction;
import com.ticketbooking.payment.enums.TransactionStatus;
import com.ticketbooking.payment.momo.MomoClient;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BookingClient bookingClient;

    @Mock
    private MomoClient momoClient;

    private PaymentServiceImpl paymentService;

    private UUID customerId;
    private UUID bookingId;
    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(transactionRepository, bookingClient, momoClient, new ObjectMapper());
        customerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
    }

    private BookingDto pendingBooking() {
        return new BookingDto(bookingId, UUID.randomUUID(), "PENDING_PAYMENT",
                new BigDecimal("3000000"), Instant.now().plusSeconds(600));
    }

    @Test
    void initiate_succeeds_andReturnsPayUrl() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        MomoCreatePaymentResponse momoResponse = new MomoCreatePaymentResponse(
                "MOMOPARTNER", bookingId.toString(), "req-1", 3000000L, 0, "Successful.",
                "https://payment.momo.vn/pay?t=abc", null, null);
        when(momoClient.createPayment(eq(bookingId), any(), any(), any())).thenReturn(momoResponse);

        PaymentInitiateResponse response = paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, "https://ticketbooking.vn/result"));

        assertEquals("https://payment.momo.vn/pay?t=abc", response.paymentUrl());
        assertEquals(bookingId, response.bookingId());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertEquals(TransactionStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void initiate_throwsConflict_whenBookingAlreadyPaid() {
        BookingDto paidBooking = new BookingDto(bookingId, UUID.randomUUID(), "PAID",
                new BigDecimal("3000000"), Instant.now().plusSeconds(600));
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(paidBooking);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsConflict_whenBookingExpired() {
        BookingDto expiredBooking = new BookingDto(bookingId, UUID.randomUUID(), "PENDING_PAYMENT",
                new BigDecimal("3000000"), Instant.now().minusSeconds(60));
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(expiredBooking);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsConflict_whenActiveTransactionAlreadyExists() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId),
                eq(List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS)))).thenReturn(true);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsBadGateway_andMarksFailed_whenMomoRejects() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        MomoCreatePaymentResponse rejected = new MomoCreatePaymentResponse(
                "MOMOPARTNER", bookingId.toString(), "req-1", 3000000L, 99, "Invalid signature.",
                null, null, null);
        when(momoClient.createPayment(eq(bookingId), any(), any(), any())).thenReturn(rejected);

        assertThrows(BadGatewayException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().getStatus());
    }
}
