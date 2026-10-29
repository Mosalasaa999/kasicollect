# KasiCollect Core Platform

Zero-asset, non-transactional circular economy orchestration platform engineered for township environments (Alexandra Wards 107 & 108, Johannesburg).

## Architecture Stack
- **Backend:** Golang microservices (WhatsApp Webhook Engine + Reverse-APN Sync Gateway)
- **Database:** PostgreSQL 16 + PostGIS 3.4 (Enforcing POPIA 50-meter centroid truncation)
- **Edge Client:** Android Go Native Client (<8MB, Room/SQLCipher, WorkManager, BLE Scale Driver)
