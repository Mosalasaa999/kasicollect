package main

import (
    "context"
    "database/sql"
    "encoding/json"
    "fmt"
    "log"
    "net/http"
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

type SyncResponse struct {
    Status    string `json:"status"`
    Processed int    `json:"processed"`
}

type IngestGateway struct {
    db *sql.DB
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

    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()

    tx, err := g.db.BeginTx(ctx, nil)
    if err != nil {
        http.Error(w, "DB transaction initialization error", http.StatusInternalServerError)
        return
    }
    defer tx.Rollback()

    stmt, err := tx.PrepareContext(ctx, `
        INSERT INTO transaction_handoff_logs (
            transaction_id, manifest_id, reclaimer_id_hash, scale_hardware_mac,
            polymer_type, gross_weight_kg, tare_weight_kg, pickup_point,
            device_recorded_at, transaction_hmac
        ) VALUES (
            $1, $2, $3, $4, $5::polymer_category, $6, $7,
            ST_SetSRID(ST_MakePoint($8, $9), 4326), $10, $11
        ) ON CONFLICT (transaction_id) DO NOTHING;
    `)
    if err != nil {
        http.Error(w, "SQL prepare error", http.StatusInternalServerError)
        return
    }
    defer stmt.Close()

    processed := 0
    for _, rec := range batch.Records {
        gross := rec.NetKg + rec.TareKg
        recordedTime := time.UnixMilli(rec.Timestamp)

        _, execErr := stmt.ExecContext(ctx,
            rec.TransactionID, rec.ManifestID, rec.ReclaimerHash, rec.ScaleMAC,
            rec.Material, gross, rec.TareKg, rec.Lng, rec.Lat, recordedTime, rec.HMAC,
        )
        if execErr == nil {
            processed++
        }
    }

    if err := tx.Commit(); err != nil {
        http.Error(w, "Commit failed", http.StatusInternalServerError)
        return
    }

    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(SyncResponse{Status: "SUCCESS", Processed: processed})
}

func main() {
    connStr := "host=127.0.0.1 port=5432 user=kasicollect password=OperationalSecurity2026 dbname=kasicollect_core sslmode=disable"
    db, err := sql.Open("postgres", connStr)
    if err != nil {
        log.Fatalf("[FATAL] DB connection failed: %v", err)
    }
    defer db.Close()

    gateway := &IngestGateway{db: db}
    http.HandleFunc("/v1/sync/batch", gateway.HandleBatchSync)

    log.Println("[BOOT] Edge Sync Gateway listening on :8443")
    log.Fatal(http.ListenAndServe(":8443", nil))
}
