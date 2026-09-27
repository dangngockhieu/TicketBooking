import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

export const queueJoins = new Counter('queue_joined_total');
export const queueAdmitted = new Counter('queue_admitted_total');
export const queueWaitTrend = new Trend('queue_wait_duration_ms');
export const wsConnectErrors = new Counter('ws_connect_errors');

export const options = {
  scenarios: {
    waiting_room_rush: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { duration: '15s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '20s', target: 30 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    ws_connect_errors: ['count==0'],
  },
};

// api-gateway proxy cho WebSocket qua route `lb:ws://queue-service` (xem
// api-gateway.yaml, id: queue-service-websocket). KHÔNG có prefix /v1.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = (__ENV.WS_URL || 'ws://localhost:8080') + '/ws/queue/websocket';
const EVENT_ID = __ENV.EVENT_ID;

if (!EVENT_ID) {
  throw new Error('Thiếu -e EVENT_ID=... — phải trỏ tới 1 sự kiện có phòng chờ đang bật ' +
    '(PATCH /api/admin/queue/{eventId}/config với enabled=true, hoặc để auto-enable tự bật khi đủ tải).');
}

/**
 * Queue Service định danh mỗi người xếp hàng bằng userId lấy từ claim JWT
 * (xem StompAuthChannelInterceptor + SecurityUtil#extractUserId) — không thể
 * "giả" hàng nghìn userId khác nhau bằng một JWT chung như REST test thông
 * thường, vì token được auth-service ký RS256 (Zero Trust). Kịch bản này cần
 * MỘT DANH SÁCH tài khoản Customer đã ACTIVE, cung cấp qua file JSON
 * (-e CREDENTIALS_FILE=k6/fixtures/queue-test-accounts.json), dạng:
 *   [{"email": "cust1@test.local", "password": "..."}, ...]
 * Số lượng tài khoản trong file quyết định số "người xếp hàng" PHÂN BIỆT tối
 * đa mà test có thể mô phỏng trung thực (nhiều VU trùng account = trùng
 * userId = chỉ tính là 1 người trong hàng chờ trên Redis Sorted Set).
 * Không cung cấp file → fallback dùng 1 tài khoản chung
 * (TEST_CUSTOMER_EMAIL/PASSWORD) cho mọi VU — chỉ đủ để kiểm tra ĐÚNG GIAO
 * THỨC (connect/join/heartbeat/admit), KHÔNG phản ánh đúng số liệu công bằng
 * FIFO dưới tải hàng nghìn người thật.
 */
const credentials = new SharedArray('queue-test-accounts', function () {
  const file = __ENV.CREDENTIALS_FILE;
  if (!file) {
    const email = __ENV.TEST_CUSTOMER_EMAIL;
    const password = __ENV.TEST_CUSTOMER_PASSWORD;
    if (!email || !password) {
      throw new Error(
        'Thiếu -e CREDENTIALS_FILE=... (khuyến nghị) hoặc tối thiểu -e TEST_CUSTOMER_EMAIL=... -e TEST_CUSTOMER_PASSWORD=...');
    }
    return [{ email, password }];
  }
  return JSON.parse(open(file));
});

// Cache token theo VU — mỗi VU login đúng 1 lần rồi tái sử dụng cho mọi iteration.
let cachedToken = null;

function loginAndGetToken() {
  if (cachedToken) {
    return cachedToken;
  }
  const account = credentials[__VU % credentials.length];
  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify(account), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (loginRes.status !== 200) {
    throw new Error(`Đăng nhập thất bại cho ${account.email} (status=${loginRes.status}): ${loginRes.body}`);
  }
  cachedToken = loginRes.json('data.accessToken');
  return cachedToken;
}

export default function () {
  const token = loginAndGetToken();
  const startWait = Date.now();
  let joined = false;
  let admitted = false;

  const res = ws.connect(WS_URL, {}, function (socket) {
    socket.on('open', function () {
      // STOMP CONNECT frame — Authorization là NATIVE HEADER của khung STOMP,
      // không phải HTTP header (trình duyệt không cho set Authorization khi
      // mở WebSocket thô, xem StompAuthChannelInterceptor).
      socket.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:0,0\n' +
        `Authorization:Bearer ${token}\n\n\0`);
    });

    socket.on('message', function (data) {
      if (data.startsWith('CONNECTED')) {
        // Subscribe TRƯỚC khi join để không lỡ mất message đầu tiên.
        socket.send(`SUBSCRIBE\nid:sub-0\ndestination:/user/queue/${EVENT_ID}/updates\n\n\0`);
        socket.send(`SEND\ndestination:/app/queue/${EVENT_ID}/join\ncontent-length:0\n\n\0`);
        joined = true;
        queueJoins.add(1);
        return;
      }

      if (!data.startsWith('MESSAGE')) {
        return; // ERROR frame hoặc heartbeat newline — bỏ qua.
      }

      const body = data.substring(data.indexOf('\n\n') + 2).replace(/\0$/, '');
      let payload;
      try {
        payload = JSON.parse(body);
      } catch (e) {
        return;
      }

      if (payload.type === 'ADMITTED') {
        admitted = true;
        queueAdmitted.add(1);
        queueWaitTrend.add(Date.now() - startWait);
        socket.close();
      }
    });

    socket.on('error', function () {
      wsConnectErrors.add(1);
    });

    // Heartbeat mỗi 10s (đúng nhịp client thật, xem docs/virtual-waiting-room.md §5.2)
    // + đóng kết nối sau 30s nếu chưa được ADMITTED (tránh treo VU vô hạn).
    socket.setInterval(function () {
      socket.send(`SEND\ndestination:/app/queue/${EVENT_ID}/heartbeat\ncontent-length:0\n\n\0`);
    }, 10000);
    socket.setTimeout(function () {
      socket.close();
    }, 30000);
  });

  check(res, { 'ws handshake status is 101': (r) => r && r.status === 101 });
  check(null, { 'joined queue': () => joined });

  sleep(0.5);
}
