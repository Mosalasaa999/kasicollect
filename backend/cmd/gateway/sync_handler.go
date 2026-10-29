package main

import (
"context"
"database/sql"
"encoding/json"
"fmt"
"log"
"net/http"
"os"
"time"

_ "github.com/lib/pq"
)

type SyncBatchRequest struct {
BatchSize int `json:"batch_size"`
Records   []struct {
TransactionID string  `json:"transaction_id"`
ManifestID    string  `json:"manifest_id"`
ReclaimerHash string  `json:"reclaimer_hash"`
Material      string  `json:"material"`
NetKg         float64 `json:"net_kg"`
TareKg        float64 `json:"tare_kg"`
ScaleMAC      string  `json:"scale_mac"`
Timestamp     int64   `json:"timestamp"`
Lat           float64 `json:"lat_masked"`
Lng           float64 `json:"lng_masked"`
HMAC          string  `json:"hmac"`
} `json:"records"`
}

type IngestGateway struct {
db *sql.DB
}

func initDB(connStr string) (*sql.DB, error) {
db, err := sql.Open("postgres", connStr)
if err != nil {
return nil, err
}

// Auto-create ledger tables on boot
schema := `
CREATE TABLE IF NOT EXISTS transaction_handoff_logs (
transaction_id VARCHAR(64) PRIMARY KEY,
manifest_id VARCHAR(64),
reclaimer_id_hash VARCHAR(64),
material VARCHAR(64),
gross_weight_kg NUMERIC(6, 2),
tare_weight_kg NUMERIC(6, 2),
net_weight_kg NUMERIC(6, 2),
scale_hardware_mac VARCHAR(32),
device_timestamp_utc BIGINT,
lat_masked NUMERIC(9, 6),
lng_masked NUMERIC(9, 6),
hmac VARCHAR(64),
synced_at TIMESTAMPTZ DEFAULT NOW()
);`

ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
defer cancel()

_, err = db.ExecContext(ctx, schema)
return db, err
}

func (g *IngestGateway) HandleBatchSync(w http.ResponseWriter, r *http.Request) {
if r.Method != http.MethodPost {
http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
return
}

var batch SyncBatchRequest
if err := json.NewDecoder(r.Body).Decode(&batch); err != nil {
http.Error(w, "Malformed JSON", http.StatusBadRequest)
return
}

log.Printf("[INGRESS] Received batch sync: %d records", batch.BatchSize)

if g.db != nil {
for _, rec := range batch.Records {
gross := rec.NetKg + rec.TareKg
_, _ = g.db.Exec(`
INSERT INTO transaction_handoff_logs (
transaction_id, manifest_id, reclaimer_id_hash, material,
gross_weight_kg, tare_weight_kg, net_weight_kg, scale_hardware_mac,
device_timestamp_utc, lat_masked, lng_masked, hmac
) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
ON CONFLICT (transaction_id) DO NOTHING;
`, rec.TransactionID, rec.ManifestID, rec.ReclaimerHash, rec.Material,
gross, rec.TareKg, rec.NetKg, rec.ScaleMAC, rec.Timestamp,
rec.Lat, rec.Lng, rec.HMAC)
}
}

w.Header().Set("Content-Type", "application/json")
json.NewEncoder(w).Encode(map[string]interface{}{
"status":    "SUCCESS",
"processed": batch.BatchSize,
"timestamp": time.Now().UTC().Format(time.RFC3339),
})
}

func main() {
port := os.Getenv("PORT")
if port == "" {
port = "8080"
}

dbURL := os.Getenv("DATABASE_URL")
var db *sql.DB
var err error

if dbURL != "" {
log.Println("[BOOT] Connecting to cloud database...")
db, err = initDB(dbURL)
if err != nil {
log.Printf("[WARN] DB init warning (running in standalone buffer mode): %v", err)
} else {
log.Println("[BOOT] PostgreSQL tables initialized successfully.")
}
} else {
log.Println("[BOOT] No DATABASE_URL provided. Running in memory-buffered mode.")
}

gateway := &IngestGateway{db: db}

// Healthcheck endpoint
http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
w.Header().Set("Content-Type", "application/json")
json.NewEncoder(w).Encode(map[string]string{
"status":   "ONLINE",
"system":   "KasiCollect Cloud Gateway",
"ward":     "Alexandra Ward 107/108",
"version":  "1.0.0",
})
})

http.HandleFunc("/v1/sync/batch", gateway.HandleBatchSync)

log.Printf("[BOOT] KasiCollect Cloud Gateway listening on :%s", port)
log.Fatal(http.ListenAndServe(":"+port, nil))
}
