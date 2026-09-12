package com.ticketbooking.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.ConflictException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mốc nghiệm thu GĐ3 (xem docs/development-plan.md): "50 luồng tranh nhau mua
 * 10 vé cuối cùng -> chỉ đúng 10 luồng thành công, 40 luồng bị từ chối".
 * <p>
 * Dùng Redis THẬT qua Testcontainers (không mock) để chứng minh Lua script
 * check-and-increment trong {@link SeatHoldService} thực sự atomic dưới tải
 * đồng thời — điều mà unit test mock {@code StringRedisTemplate} không thể
 * chứng minh. Tên lớp kết thúc bằng {@code IT}, KHÔNG khớp pattern mặc định
 * của Surefire ({@code *Test.java}/{@code *Tests.java}), nên bị bỏ qua trong
 * {@code mvn test} thông thường — chạy rõ ràng bằng
 * {@code mvn test -Dtest=SeatHoldConcurrencyIT} (yêu cầu Docker daemon).
 */
@Testcontainers
class SeatHoldConcurrencyIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @Test
    void only10Of50ConcurrentHolds_succeed_forLast10Tickets() throws InterruptedException {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        SeatHoldService seatHoldService = new SeatHoldService(redisTemplate, new ObjectMapper());

        UUID eventId = UUID.randomUUID();
        UUID ticketClassId = UUID.randomUUID();
        int availableQuantity = 10;
        int concurrentRequests = 50;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    seatHoldService.hold(eventId, ticketClassId, 1, availableQuantity);
                    succeeded.incrementAndGet();
                } catch (ConflictException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Các luồng không sẵn sàng kịp thời gian chờ");
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Các luồng không hoàn thành kịp thời gian chờ");
        executor.shutdown();

        assertEquals(10, succeeded.get(), "Phải có đúng 10 luồng giữ chỗ thành công");
        assertEquals(40, rejected.get(), "40 luồng còn lại phải bị từ chối vì hết vé");

        String holdCount = redisTemplate.opsForValue().get(SeatHoldService.holdCountKey(eventId, ticketClassId));
        assertEquals("10", holdCount, "hold_count trên Redis phải dừng đúng ở available_quantity, không vượt quá");
    }
}
