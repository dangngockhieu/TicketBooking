package com.ticketbooking.payment.service.impl;

import com.ticketbooking.payment.entity.OrganizerWallet;
import com.ticketbooking.payment.repository.OrganizerWalletRepository;
import com.ticketbooking.payment.service.OrganizerWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
}
