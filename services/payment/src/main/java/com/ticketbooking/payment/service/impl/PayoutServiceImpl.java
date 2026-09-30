package com.ticketbooking.payment.service.impl;

import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.payment.client.CatalogClient;
import com.ticketbooking.payment.client.UserClient;
import com.ticketbooking.payment.client.dto.BankAccountDto;
import com.ticketbooking.payment.client.dto.CatalogEventDto;
import com.ticketbooking.payment.dto.request.CreatePayoutRequest;
import com.ticketbooking.payment.dto.request.UpdatePayoutStatusRequest;
import com.ticketbooking.payment.dto.response.PayoutResponse;
import com.ticketbooking.payment.dto.response.WalletResponse;
import com.ticketbooking.payment.entity.PayoutRequest;
import com.ticketbooking.payment.enums.PayoutSource;
import com.ticketbooking.payment.enums.PayoutStatus;
import com.ticketbooking.payment.momo.MomoClient;
import com.ticketbooking.payment.momo.dto.MomoDisburseResponse;
import com.ticketbooking.payment.repository.PayoutRequestRepository;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.payment.service.OrganizerWalletService;
import com.ticketbooking.payment.service.PayoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final OrganizerWalletService organizerWalletService;
    private final TransactionRepository transactionRepository;
    private final UserClient userClient;
    private final CatalogClient catalogClient;
    private final MomoClient momoClient;

    public PayoutServiceImpl(
            PayoutRequestRepository payoutRequestRepository,
            OrganizerWalletService organizerWalletService,
            TransactionRepository transactionRepository,
            UserClient userClient,
            CatalogClient catalogClient,
            MomoClient momoClient) {
        this.payoutRequestRepository = payoutRequestRepository;
        this.organizerWalletService = organizerWalletService;
        this.transactionRepository = transactionRepository;
        this.userClient = userClient;
        this.catalogClient = catalogClient;
        this.momoClient = momoClient;
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID organizerId) {
        return organizerWalletService.findWallet(organizerId)
                .map(WalletResponse::from)
                .orElseGet(WalletResponse::empty);
    }

    @Override
    public PayoutResponse requestManualPayout(UUID organizerId, String bearerToken, CreatePayoutRequest request) {
        BankAccountDto bank = userClient.getMyBankAccount(bearerToken);
        if (bank == null || !bank.verified()) {
            throw new ConflictException("Chưa có tài khoản ngân hàng đã xác minh, không thể xin rút tiền.");
        }
        if (!organizerWalletService.reserveForPayout(organizerId, request.amount())) {
            throw new ConflictException("Số dư khả dụng không đủ để rút số tiền này.");
        }

        PayoutRequest entity = PayoutRequest.builder()
                .organizerId(organizerId)
                .amount(request.amount())
                .bankName(bank.bankName())
                .bankAccountNumber(bank.bankAccountNumber())
                .bankAccountHolder(bank.bankAccountHolder())
                .status(PayoutStatus.PENDING)
                .source(PayoutSource.MANUAL)
                .build();
        return PayoutResponse.from(payoutRequestRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutResponse> listMine(UUID organizerId, PayoutStatus status, Pageable pageable) {
        Page<PayoutRequest> page = status != null
                ? payoutRequestRepository.findByOrganizerIdAndStatus(organizerId, status, pageable)
                : payoutRequestRepository.findByOrganizerId(organizerId, pageable);

        return PageResponse.<PayoutResponse>builder()
                .items(page.getContent().stream().map(PayoutResponse::from).toList())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    public PayoutResponse updateStatus(UUID payoutId, UpdatePayoutStatusRequest request) {
        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu rút tiền."));

        if ((request.status() == PayoutStatus.REJECTED || request.status() == PayoutStatus.HOLD)
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("Cần nhập lý do khi từ chối hoặc tạm giữ yêu cầu rút tiền.");
        }

        switch (request.status()) {
            case APPROVED -> {
                if (payout.getStatus() != PayoutStatus.PENDING) {
                    throw new ConflictException("Chỉ có thể duyệt yêu cầu đang PENDING.");
                }
                payout.setStatus(PayoutStatus.APPROVED);
            }
            case REJECTED -> {
                if (payout.getStatus() == PayoutStatus.PAID || payout.getStatus() == PayoutStatus.REJECTED) {
                    throw new ConflictException("Không thể từ chối yêu cầu đã ở trạng thái cuối.");
                }
                organizerWalletService.releaseReservedPayout(payout.getOrganizerId(), payout.getAmount());
                payout.setStatus(PayoutStatus.REJECTED);
                payout.setReason(request.reason());
                payout.setProcessedAt(Instant.now());
            }
            case HOLD -> {
                payout.setStatus(PayoutStatus.HOLD);
                payout.setReason(request.reason());
            }
            case PAID -> {
                if (payout.getStatus() != PayoutStatus.APPROVED) {
                    throw new ConflictException("Chỉ có thể chi trả yêu cầu đã được duyệt (APPROVED).");
                }
                String requestId = "disburse-" + payout.getId();
                MomoDisburseResponse response = momoClient.disburse(payout.getId(), payout.getAmount(),
                        payout.getBankAccountNumber(), "Chi trả doanh thu tổ chức sự kiện", requestId);
                if (!response.isSuccess()) {
                    log.error("MoMo từ chối disburse cho payout {}: resultCode={}, message={}",
                            payout.getId(), response.resultCode(), response.message());
                    throw new BadGatewayException("MoMo từ chối chi trả: " + response.message());
                }
                organizerWalletService.markPayoutPaid(payout.getOrganizerId(), payout.getAmount());
                payout.setStatus(PayoutStatus.PAID);
                payout.setMomoDisbursementId(response.requestId());
                payout.setProcessedAt(Instant.now());
            }
            case PENDING -> throw new BadRequestException("Không thể chuyển yêu cầu về trạng thái PENDING.");
        }

        return PayoutResponse.from(payoutRequestRepository.save(payout));
    }

    @Override
    public void createAutoPayouts() {
        List<UUID> eventIds = transactionRepository.findDistinctEventIdsWithSuccessfulPayment();
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        for (UUID eventId : eventIds) {
            if (payoutRequestRepository.existsByEventIdAndSource(eventId, PayoutSource.AUTO)) {
                continue;
            }

            CatalogEventDto event;
            try {
                event = catalogClient.getEvent(eventId);
            } catch (RuntimeException e) {
                log.warn("Không thể lấy thông tin sự kiện {} khi tạo payout tự động: {}", eventId, e.getMessage());
                continue;
            }
            if (!"COMPLETED".equals(event.status()) || event.endTime() == null || event.endTime().isAfter(sevenDaysAgo)) {
                continue;
            }

            BigDecimal grossAmount = transactionRepository.sumSuccessAmountByEventId(eventId);
            Integer quantity = transactionRepository.sumSuccessQuantityByEventId(eventId);
            if (grossAmount == null || grossAmount.signum() <= 0) {
                continue;
            }

            BigDecimal commission = grossAmount.multiply(event.commissionRate());
            BigDecimal flatFees = event.flatFeePerTicket().multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
            BigDecimal netRevenue = grossAmount.subtract(commission).subtract(flatFees);
            if (netRevenue.signum() <= 0) {
                continue;
            }

            UUID organizerId = event.organizerId();
            if (!organizerWalletService.reserveForPayout(organizerId, netRevenue)) {
                log.warn("Không đủ availableBalance để tạo payout tự động cho event {} (organizer {}), thử lại lần chạy sau.",
                        eventId, organizerId);
                continue;
            }

            BankAccountDto bank = userClient.getBankAccountByOrganizerId(organizerId);
            boolean verified = bank != null && bank.verified();

            PayoutRequest payout = PayoutRequest.builder()
                    .organizerId(organizerId)
                    .amount(netRevenue)
                    .bankName(verified ? bank.bankName() : "")
                    .bankAccountNumber(verified ? bank.bankAccountNumber() : "")
                    .bankAccountHolder(verified ? bank.bankAccountHolder() : "")
                    .status(verified ? PayoutStatus.PENDING : PayoutStatus.HOLD)
                    .source(PayoutSource.AUTO)
                    .eventId(eventId)
                    .reason(verified ? null : "Organizer chưa xác minh tài khoản ngân hàng")
                    .build();
            payoutRequestRepository.save(payout);
            log.info("Đã tạo payout tự động {} cho event {} (organizer {}), amount={}",
                    payout.getId(), eventId, organizerId, netRevenue);
        }
    }
}
