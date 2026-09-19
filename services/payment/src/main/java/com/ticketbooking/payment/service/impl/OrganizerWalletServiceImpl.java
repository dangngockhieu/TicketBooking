package com.ticketbooking.payment.service.impl;

import com.ticketbooking.payment.entity.OrganizerWallet;
import com.ticketbooking.payment.repository.OrganizerWalletRepository;
import com.ticketbooking.payment.service.OrganizerWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class OrganizerWalletServiceImpl implements OrganizerWalletService {

    private final OrganizerWalletRepository organizerWalletRepository;

    public OrganizerWalletServiceImpl(OrganizerWalletRepository organizerWalletRepository) {
        this.organizerWalletRepository = organizerWalletRepository;
    }

    @Override
    public void creditForBooking(UUID organizerId, BigDecimal grossAmount, BigDecimal commissionRate,
                                  BigDecimal flatFeePerTicket, int quantity) {
        BigDecimal commission = grossAmount.multiply(commissionRate);
        BigDecimal flatFees = flatFeePerTicket.multiply(BigDecimal.valueOf(quantity));
        BigDecimal net = grossAmount.subtract(commission).subtract(flatFees);
        if (net.signum() < 0) {
            log.warn("Phí nền tảng ({} + {}) vượt quá số tiền giao dịch {} của organizer {}, chốt về 0.",
                    commission, flatFees, grossAmount, organizerId);
            net = BigDecimal.ZERO;
        }

        OrganizerWallet wallet = organizerWalletRepository.findByOrganizerId(organizerId)
                .orElseGet(() -> OrganizerWallet.builder().organizerId(organizerId).build());
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(net));
        organizerWalletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizerWallet> findWallet(UUID organizerId) {
        return organizerWalletRepository.findByOrganizerId(organizerId);
    }

    @Override
    public boolean reserveForPayout(UUID organizerId, BigDecimal amount) {
        OrganizerWallet wallet = organizerWalletRepository.findByOrganizerId(organizerId).orElse(null);
        if (wallet == null || wallet.getAvailableBalance().compareTo(amount) < 0) {
            return false;
        }
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setPendingPayout(wallet.getPendingPayout().add(amount));
        organizerWalletRepository.save(wallet);
        return true;
    }

    @Override
    public void releaseReservedPayout(UUID organizerId, BigDecimal amount) {
        organizerWalletRepository.findByOrganizerId(organizerId).ifPresent(wallet -> {
            wallet.setPendingPayout(wallet.getPendingPayout().subtract(amount));
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
            organizerWalletRepository.save(wallet);
        });
    }

    @Override
    public void markPayoutPaid(UUID organizerId, BigDecimal amount) {
        organizerWalletRepository.findByOrganizerId(organizerId).ifPresent(wallet -> {
            wallet.setPendingPayout(wallet.getPendingPayout().subtract(amount));
            wallet.setTotalWithdrawn(wallet.getTotalWithdrawn().add(amount));
            organizerWalletRepository.save(wallet);
        });
    }
}
