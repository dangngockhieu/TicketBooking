package com.ticketbooking.catalog.repository;

import com.ticketbooking.catalog.entity.TicketClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TicketClassRepository extends JpaRepository<TicketClass, UUID> {

    /**
     * Trừ vĩnh viễn {@code available_quantity} sau khi thanh toán thành công
     * Guard {@code available_quantity >= :quantity} chống âm kho — về lý thuyết
     * không xảy ra vì Redis hold_count đã chặn overbooking từ bước giữ chỗ,
     * đây chỉ là lưới an toàn.
     *
     * @return số dòng bị ảnh hưởng (0 nếu không đủ tồn kho hoặc id không tồn tại)
     */
    @Modifying
    @Query("UPDATE TicketClass tc SET tc.availableQuantity = tc.availableQuantity - :quantity, "
            + "tc.updatedAt = CURRENT_TIMESTAMP WHERE tc.id = :id AND tc.availableQuantity >= :quantity")
    int decrementAvailableQuantity(@Param("id") UUID id, @Param("quantity") int quantity);
}
