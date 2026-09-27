import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// Custom Metrics theo dõi hiệu năng
export const successfulHolds = new Counter('seat_holds_successful');
export const soldOutRejections = new Counter('seat_holds_sold_out');
export const holdLatency = new Trend('seat_hold_latency_ms');
export const error5xxRate = new Rate('error_5xx_rate');

export const options = {
  scenarios: {
    // Mô phỏng Flash Sale mở bán vé: Tăng nhanh từ 0 -> 2.000 VUs đồng thời trong 30s
    flash_sale_burst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 500 },   // Khởi động 500 users
        { duration: '20s', target: 2000 },  // Đạt đỉnh 2.000 users đồng thời gửi request đặt vé
        { duration: '30s', target: 2000 },  // Duy trì tải cao trong 30s
        { duration: '10s', target: 0 },     // Hạ tải
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // 95% request giữ chỗ phải phản hồi dưới 400ms
    http_req_duration: ['p(95)<400', 'p(99)<800'],
    // Tỷ lệ lỗi 5xx hệ thống phải dưới 0.1% (tuyệt đối không crash server)
    error_5xx_rate: ['rate<0.001'],
  },
};

// api-gateway (KHÔNG có prefix /v1 — route thật là /api/**, xem
// services/config-server/src/main/resources/configurations/api-gateway.yaml).
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID;
const TICKET_CLASS_ID = __ENV.TICKET_CLASS_ID;

// POST /api/bookings yêu cầu JWT với role CUSTOMER (xem BookingController —
// @PreAuthorize("hasRole('CUSTOMER')")). Không có cách nào "giả" JWT vì
// auth-service ký RS256 bằng private key riêng (Zero Trust) — script cần một
// tài khoản Customer ĐÃ VERIFIED (status=ACTIVE) có sẵn, đăng nhập 1 lần ở
// setup() rồi dùng chung token cho toàn bộ VU. Vì atomic seat hold chỉ khoá
// theo (eventId, ticketClassId) chứ không theo customerId, dùng chung 1 JWT
// cho 2.000 VU vẫn kiểm thử đúng race condition — chỉ là không mô phỏng
// 2.000 khách hàng THẬT khác nhau.
export function setup() {
  if (!EVENT_ID || !TICKET_CLASS_ID) {
    throw new Error(
      'Thiếu -e EVENT_ID=... -e TICKET_CLASS_ID=... — phải trỏ tới 1 sự kiện PUBLISHED thật đã có sẵn hạng vé.');
  }

  const email = __ENV.TEST_CUSTOMER_EMAIL;
  const password = __ENV.TEST_CUSTOMER_PASSWORD;
  if (!email || !password) {
    throw new Error(
      'Thiếu -e TEST_CUSTOMER_EMAIL=... -e TEST_CUSTOMER_PASSWORD=... — cần 1 tài khoản Customer đã ACTIVE ' +
      '(đăng ký qua POST /api/auth/register rồi xác thực OTP qua POST /api/auth/verify-email trước khi chạy test này).');
  }

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (loginRes.status !== 200) {
    throw new Error(`Đăng nhập thất bại (status=${loginRes.status}): ${loginRes.body}`);
  }
  const token = loginRes.json('data.accessToken');
  if (!token) {
    throw new Error(`Response đăng nhập không có data.accessToken: ${loginRes.body}`);
  }
  return { token };
}

export default function (data) {
  const payload = JSON.stringify({
    eventId: EVENT_ID,
    items: [{ ticketClassId: TICKET_CLASS_ID, quantity: 2 }],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
    timeout: '5s',
  };

  const startTime = Date.now();
  const res = http.post(`${BASE_URL}/api/bookings`, payload, params);
  const latency = Date.now() - startTime;
  holdLatency.add(latency);

  // Phân loại kết quả
  if (res.status === 201) {
    // Giữ chỗ thành công (Atomic Seat Hold OK)
    successfulHolds.add(1);
    check(res, {
      'status is 201 (Held)': (r) => r.status === 201,
      'has data.id': (r) => r.json('data.id') !== undefined,
    });
  } else if (res.status === 409) {
    // Hết vé hợp lệ (ConflictException -> 409, xem GlobalExceptionHandler) - đúng nghiệp vụ chống Overbooking!
    soldOutRejections.add(1);
    check(res, {
      'status is 409 (Sold Out properly rejected)': (r) => r.status === 409,
    });
  } else if (res.status >= 500) {
    // Lỗi hệ thống server crash hoặc deadlock DB
    error5xxRate.add(1);
  } else {
    console.error(`Response không mong đợi: status=${res.status} body=${res.body}`);
  }

  // Nghỉ ngơi ngắn giữa các vòng lặp của user
  sleep(0.1);
}
