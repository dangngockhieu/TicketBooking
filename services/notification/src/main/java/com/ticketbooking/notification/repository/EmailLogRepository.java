package com.ticketbooking.notification.repository;

import com.ticketbooking.notification.entity.EmailLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailLogRepository extends MongoRepository<EmailLog, String> {

    /** Idempotent consumer cho {@code tickets.generated} — chống gửi trùng E-Ticket khi Kafka redeliver. */
    boolean existsByTypeAndDedupeKeyAndStatus(String type, String dedupeKey, String status);
}
