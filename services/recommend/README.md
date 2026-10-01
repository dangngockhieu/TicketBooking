# 🤖 AI Recommendation & Chatbot Service — TicketBooking

Microservice chịu trách nhiệm thông minh hóa trải nghiệm tìm kiếm và khám phá sự kiện trên nền tảng **TicketBooking** bằng trí tuệ nhân tạo **OpenAI (ChatGPT & Text Embeddings)**.

---

## 🌟 Tính Năng Chính

Dịch vụ cung cấp 2 nhóm tính năng cốt lõi:

### 1. Trợ Lý Ảo AI Tư Vấn Vé Sự Kiện (AI Ticketing Concierge)
* **API:** `POST /api/recommendations/chat`
* **Công nghệ:** **OpenAI GPT-4o-mini + RAG (Retrieval-Augmented Generation)**.
* **Chức năng:**
  * Tiếp nhận câu hỏi bằng ngôn ngữ tự nhiên từ người dùng (VD: *"Tối thứ 7 này ở Hà Nội có show acoustic nào dưới 500k đi cùng bạn gái không?"*).
  * Tự động phân tích ý định, ngân sách, địa điểm và sở thích.
  * Truy vấn dữ liệu thực tế từ `catalog-service`.
  * Trả lời tự nhiên, thân thiện và đính kèm danh sách thẻ sự kiện (card vé) để người dùng bấm đặt vé trực tiếp.
  * Đề xuất các câu hỏi gợi mở tiếp theo (Follow-up questions).

### 2. Gợi Ý Cá Nhân Hóa & Sự Kiện Tương Tự (Recommendation Feed)
* **Gợi ý trên Trang Chủ (For You Feed):**
  * **API:** `GET /api/recommendations/events/for-you?limit=10`
  * Hiển thị danh sách sự kiện được cá nhân hóa theo sở thích danh mục (`category`), lịch sử xem và xu hướng cộng đồng.
* **Sự kiện tương tự (Similar Events):**
  * **API:** `GET /api/recommendations/events/{eventId}/similar?limit=5`
  * **Công nghệ:** **OpenAI `text-embedding-3-small` + Cosine Similarity**.
  * Tính toán độ tương đồng nội dung ngữ nghĩa giữa các sự kiện và hiển thị ở chân trang chi tiết sự kiện.

---

## ⚙️ Cấu Hình Biến Môi Trường (`.env`)

```properties
# OpenAI API Key (Bắt buộc để kích hoạt chế độ AI)
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxxxxxxxxx
OPENAI_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# Cổng dịch vụ
PORT=8088

# Địa chỉ Catalog Service nội bộ
CATALOG_SERVICE_URL=http://catalog-service:8083
```

> 💡 **Cơ chế Fallback thông minh:** Nếu chưa cấu hình `OPENAI_API_KEY` hoặc mạng gặp sự cố, dịch vụ sẽ tự động chuyển sang chế độ Rule-based & Keyword Matching nội bộ, không bao giờ làm crash ứng dụng hay gián đoạn trải nghiệm người dùng!

---

## 🚀 Hướng Dẫn Chạy Cục Bộ (Local Development)

### 1. Cài đặt Python 3.11+ và Virtual Environment
```bash
cd services/recommend

# Tạo môi trường ảo
python -m venv venv

# Kích hoạt môi trường (Windows PowerShell)
.\venv\Scripts\Activate.ps1
# Hoặc trên Linux/macOS:
source venv/bin/activate

# Cài đặt thư viện
pip install -r requirements.txt
```

### 2. Khởi chạy dịch vụ
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8088 --reload
```

* **Swagger UI (Interactive API Docs):** [http://localhost:8088/docs](http://localhost:8088/docs)
* **OpenAPI Schema:** [http://localhost:8088/v3/api-docs](http://localhost:8088/v3/api-docs)

---

## 🐳 Chạy với Docker

```bash
docker build -t ticketbooking-recommend:latest .
docker run -p 8088:8088 --env-file ../../.env ticketbooking-recommend:latest
```
