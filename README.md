# Test Result (Microservice)
***
>Sebelumnya saya minta maaf karena microservicenya tidak selesai dengan baik, ada beberapa masalah, tapi setidaknya aku akan menjelaskan tentang microservice yang dikerjakan , masalah apa yang ingin diselesaikan di microservicenya. Berikut:
---

### Masalah yang muncul
- Payment Gateway dapat mengirim callback lebih dari satu kali
- Network Timeout sering terjadi
- System tidak boleh terjadi double charge
### Akibat dari masalah
Ketika service kita mengalami gangguan seperti network timeout dan gangguan service sementara akan bisa mengakibatkan yang namanya Double Charge atau user dapat di charge 2 kali atau lebih dan susah untuk recover dari masalah ini. Juga bisa membuat data corruption dan notifikasi yang berulang kali dikirimkan.

### Solusi
1. Menggunakan idempotency key di request.
`POST /api/payments
Header: Idempotency-Key: PAY-IDEMPOTENCY-ORD-123`
> Dengan menyimpan idempotent key di database, kita bisa mengembalikan request jika kita menemukan request yang duplikat sebelum melanjutkan proses. 
2. Untuk mengatasi juga melakukan hal yang sama untuk Duplicate Callback dengan menyimpan callback_id di db
3. Untuk Mengatasi Network Timeout kita bisa menggunakan metode Circuit Breaker dan retry dengan exponential backoff
`@Retryable(
    value = {NetworkTimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
// Retry delays: 2s, 4s, 8s`
4. Dan cara terakhir adalah menggunakan Kafka karena Kafka memastikan stidaknya 1 kali delivery message, adanya ordering di dalam partisi.

### Flow Event

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

### Quick Start
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