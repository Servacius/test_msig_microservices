# Test Result (Microservice)
***
>Sebelumnya saya minta maaf karena microservicenya tidak selesai dengan baik, ada beberapa masalah di servicenya, tapi setidaknya saya akan menjelaskan tentang microservice yang dikerjakan, problem apa yang ingin diselesaikan di microservicenya. Berikut:

>Note : Walaupun waktu testnya sudah habis aku akan tetap upload hasil yang sudah di saya fix perlahan dan cara testnya nantinya di branch`fix`.
---

### Masalah yang muncul
- Payment Gateway dapat mengirim callback lebih dari satu kali
- Network Timeout sering terjadi
- System tidak boleh terjadi double charge
### Akibat dari masalah
Ketika service kita mengalami gangguan seperti network timeout dan gangguan service sementara akan bisa mengakibatkan yang namanya Double Charge atau user dapat di charge 2 kali atau lebih dan susah untuk recover dari masalah ini. Juga bisa membuat data corruption dan notifikasi yang berulang kali dikirimkan.

### Solusi
1. Menggunakan idempotency key di request.
```
POST /api/payments
Header: Idempotency-Key: PAY-IDEMPOTENCY-ORD-123
```
> Dengan menyimpan idempotent key di database, kita bisa mengembalikan request jika kita menemukan request yang duplikat sebelum melanjutkan proses. 
2. Untuk mengatasi juga melakukan hal yang sama untuk Duplicate Callback dengan menyimpan callback_id di db
3. Untuk Mengatasi Network Timeout kita bisa menggunakan metode Circuit Breaker dan retry dengan exponential backoff
```
@Retryable(
    value = {NetworkTimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
// Retry delays: 2s, 4s, 8s
```
4. Dan cara terakhir adalah menggunakan Kafka karena Kafka memastikan stidaknya 1 kali delivery message, adanya ordering di dalam partisi.

### Asumsi Flow Event

User creates order
    ↓
Order Service creates order 
    ↓
Order Service calls Payment Service with idempotency key
    ↓
Payment Service checks idempotency key (if not exists → create)
    ↓
Payment Service saves payment (status: PENDING)
    ↓
Payment Service calls payment gateway
    ↓
Payment gateway processes (async)
    ↓
Payment gateway sends callback (mungkin terjadi berulang kali)
    ↓
Payment Service receives callback
    |-- Check callback_id (check ke database jika ada? → skip)
    |-- Check payment status
    |-- Update payment status
    |-- Save callback record
    |-- Publish event to Kafka
    ↓
Order Service consumes event/message
    |-- Check event version
    |-- Update order status
    |-- Publish order event
    ↓
Notification Service consumes event/message
    |-- Check event ID (sudah diproses? → skip)
    |-- send notification

### Quick Start and Test Guide
1. Masuk ke folder docker untuk menjalannkan docker-compose
```
//start container
docker-compose up -d

//check service
docker-compose ps 
```
2. Build Services
```
cd payment-service && mvn clean install
cd ../order-service && mvn clean install
cd ../notification-service && mvn clean install
```
3. Run Services
```
# Payment Service
cd payment-service
mvn spring-boot:run

# Order Service
cd order-service
mvn spring-boot:run

# Notification Service
cd notification-service
mvn spring-boot:run
```
4. Test Normal Payment Flow
```
-- Health Check
# Check Payment Service
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}
# Check Order Service
curl http://localhost:8082/actuator/health
# Check Notification Service
curl http://localhost:8083/actuator/health


# 1. Create order - Note : Masih Gagal
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "items": [
      {
        "productId": "prod-1",
        "productName": "Laptop",
        "quantity": 1,
        "price": 1000.00
      }
    ],
    "currency": "USD"
  }'

# Response:
{
  "orderId": "ORD-abc-123",
  "status": "PAYMENT_PENDING",
  "totalAmount": 1000.00,
  "paymentId": "PAY-xyz-456"
}

# 2. Check payment status
curl http://localhost:8081/api/payments/PAY-xyz-456

# 3. Simulate payment gateway callback (success)
curl -X POST http://localhost:8081/api/payments/callback \
  -H "Content-Type: application/json" \
  -d '{
    "callbackId": "CB-unique-123",
    "paymentReference": "PAY-xyz-456",
    "status": "SUCCESS",
    "transactionId": "GW-txn-789"
  }'

# 4. Verify order status updated
curl http://localhost:8082/api/orders/ORD-abc-123
# Status should be: PAID
```

### Database Schema
```
-- Untuk Payment
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) UNIQUE NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    gateway_reference VARCHAR(255),
    version INTEGER NOT NULL,  -- Optimistic locking
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    failure_reason TEXT
);

-- payment_callbacks table 
CREATE TABLE payment_callbacks (
    id BIGSERIAL PRIMARY KEY,
    callback_id VARCHAR(255) UNIQUE NOT NULL,  -- Untuk mengatasi proses duplicate
    payment_reference VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    raw_payload TEXT,
    received_at TIMESTAMP NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_order_id ON payments(order_id);
CREATE INDEX idx_gateway_ref ON payments(gateway_reference);
CREATE INDEX idx_callback_payment ON payment_callbacks(payment_reference);

-- Untuk Order
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,

    order_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,

    total_amount NUMERIC(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,

    status VARCHAR(30) NOT NULL,

    payment_id VARCHAR(100),

    items TEXT NOT NULL,

    version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_orders_order_id
ON orders(order_id);

CREATE INDEX idx_orders_user_id
ON orders(user_id);

CREATE INDEX idx_orders_payment_id
ON orders(payment_id);

CREATE INDEX idx_orders_status
ON orders(status);

-- Untuk Notification
CREATE TABLE notification_logs (
    id BIGSERIAL PRIMARY KEY,

    event_id VARCHAR(150) NOT NULL,
    user_id VARCHAR(50) NOT NULL,

    order_id VARCHAR(50),
    payment_id VARCHAR(100),

    type VARCHAR(20) NOT NULL,      -- EMAIL, SMS, atau PUSH
    recipient VARCHAR(150) NOT NULL,

    subject VARCHAR(255) NOT NULL,
    content TEXT,

    status VARCHAR(20) NOT NULL,    -- PENDING, SENT, atau 	FAILED
    error_message TEXT,

    retry_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

-- Idempotency guarantee
CREATE UNIQUE INDEX uq_notification_event_id
ON notification_logs(event_id);

CREATE INDEX idx_notification_user_id
ON notification_logs(user_id);

CREATE INDEX idx_notification_order_id
ON notification_logs(order_id);

CREATE INDEX idx_notification_status_retry
ON notification_logs(status, retry_count);
```