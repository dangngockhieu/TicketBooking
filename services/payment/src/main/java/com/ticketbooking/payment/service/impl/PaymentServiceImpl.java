package com.ticketbooking.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.ticketbooking.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_PAID = "PAID";
    private static final List<TransactionStatus> ACTIVE_TRANSACTION_STATUSES =
            List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS);

    private final TransactionRepository transactionRepository;
    private final BookingClient bookingClient;
    private final MomoClient momoClient;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(
            TransactionRepository transactionRepository,
            BookingClient bookingClient,
            MomoClient momoClient,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.bookingClient = bookingClient;
        this.momoClient = momoClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentInitiateResponse initiate(UUID customerId, String bearerToken, InitiatePaymentRequest request) {
        log.info("Customer {} khởi tạo thanh toán cho booking {}", customerId, request.bookingId());
        BookingDto booking = bookingClient.getBooking(request.bookingId(), bearerToken);

        if (STATUS_PAID.equals(booking.status())) {
            throw new ConflictException("Booking đã thanh toán trước đó.");
        }
        if (!STATUS_PENDING_PAYMENT.equals(booking.status())) {
            throw new ConflictException("Booking không ở trạng thái chờ thanh toán.");
        }
        if (booking.expiredAt() != null && booking.expiredAt().isBefore(Instant.now())) {
            throw new ConflictException("Booking đã hết hạn giữ chỗ, không thể thanh toán.");
        }
        if (transactionRepository.existsByBookingIdAndStatusIn(booking.id(), ACTIVE_TRANSACTION_STATUSES)) {
            throw new ConflictException("Đã có giao dịch thanh toán cho booking này.");
        }

        Transaction transaction = Transaction.builder()
                .bookingId(booking.id())
                .amount(booking.totalAmount())
                .paymentMethod("MOMO")
                .status(TransactionStatus.PENDING)
                .build();
        transaction = transactionRepository.save(transaction);

        MomoCreatePaymentResponse momoResponse = momoClient.createPayment(
                booking.id(), booking.totalAmount(), transaction.getId().toString(), request.returnUrl());

        transaction.setGatewayResponse(toGatewayResponseMap(momoResponse));
        if (!momoResponse.isSuccess()) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.warn("MoMo từ chối tạo payUrl cho booking {}: resultCode={}, message={}",
                    booking.id(), momoResponse.resultCode(), momoResponse.message());
            throw new BadGatewayException("MoMo từ chối khởi tạo thanh toán: " + momoResponse.message());
        }
        transactionRepository.save(transaction);

        return new PaymentInitiateResponse(
                transaction.getId(), booking.id(), booking.totalAmount(), momoResponse.payUrl(), booking.expiredAt());
    }

    private Map<String, Object> toGatewayResponseMap(MomoCreatePaymentResponse response) {
        return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
        });
    }
}
