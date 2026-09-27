import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    browse_events_heavy: {
      executor: 'constant-vus',
      vus: 1000,
      duration: '45s',
    },
  },
  thresholds: {
    // Với Redis Cache, 95% request duyệt sự kiện phải trả lời dưới 100ms
    http_req_duration: ['p(95)<100', 'p(99)<250'],
    http_req_failed: ['rate<0.005'],
  },
};

// api-gateway (KHÔNG có prefix /v1 — xem api-gateway.yaml). Catalog Service
// cấu hình pageable one-indexed (page=1 là trang đầu, xem catalog-service.yaml).
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// Optional — nếu không truyền, script chỉ test GET /api/events (list + cache),
// bỏ qua bước xem chi tiết (không có ID thật thì không đoán bừa UUID để tránh
// health/UUID ngẫu nhiên luôn trả 404, gây sai lệch số liệu p95).
const EVENT_ID = __ENV.EVENT_ID;

export default function () {
  // 1. Duyệt danh sách sự kiện kèm phân trang & category
  const listRes = http.get(`${BASE_URL}/api/events?page=1&size=10`);
  check(listRes, {
    'catalog list 200': (r) => r.status === 200,
  });

  // 2. Xem chi tiết 1 sự kiện thật (chỉ chạy nếu có -e EVENT_ID=...)
  if (EVENT_ID) {
    const detailRes = http.get(`${BASE_URL}/api/events/${EVENT_ID}`);
    check(detailRes, {
      'event detail 200': (r) => r.status === 200,
    });
  }

  sleep(0.2);
}
