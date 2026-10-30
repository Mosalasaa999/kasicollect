package main

import (
    "context"
    "database/sql"
    "encoding/json"
    "log"
    "net/http"
    "os"
    "time"

    _ "github.com/lib/pq"
)

type ManifestPayload struct {
    ManifestID string  `json:"manifest_id"`
    Landmark   string  `json:"landmark"`
    Materials  string  `json:"materials"`
    Status     string  `json:"status"`
    Timestamp  int64   `json:"timestamp"`
}

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

    schema := `
    CREATE TABLE IF NOT EXISTS manifests (
        manifest_id VARCHAR(64) PRIMARY KEY,
        landmark TEXT NOT NULL,
        materials TEXT NOT NULL,
        status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
        created_at TIMESTAMPTZ DEFAULT NOW()
    );

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

func (g *IngestGateway) HandleManifests(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    w.Header().Set("Access-Control-Allow-Origin", "*")

    if r.Method == http.MethodPost {
        var req ManifestPayload
        if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
            http.Error(w, "Invalid JSON", http.StatusBadRequest)
            return
        }

        if g.db != nil {
            _, err := g.db.Exec(`
                INSERT INTO manifests (manifest_id, landmark, materials, status)
                VALUES ($1, $2, $3, 'PENDING')
                ON CONFLICT (manifest_id) DO NOTHING;
            `, req.ManifestID, req.Landmark, req.Materials)

            if err != nil {
                log.Printf("[ERR] Manifest insert error: %v", err)
            }
        }

        json.NewEncoder(w).Encode(map[string]string{"status": "CREATED", "manifest_id": req.ManifestID})
        return
    }

    if r.Method == http.MethodGet {
        var manifests []ManifestPayload
        if g.db != nil {
            rows, err := g.db.Query(`SELECT manifest_id, landmark, materials, status FROM manifests WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 10`)
            if err == nil {
                defer rows.Close()
                for rows.Next() {
                    var m ManifestPayload
                    if err := rows.Scan(&m.ManifestID, &m.Landmark, &m.Materials, &m.Status); err == nil {
                        manifests = append(manifests, m)
                    }
                }
            }
        }
        if manifests == nil {
            manifests = []ManifestPayload{}
        }
        json.NewEncoder(w).Encode(manifests)
        return
    }

    http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
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

            // Mark manifest as claimed
            _, _ = g.db.Exec(`UPDATE manifests SET status = 'COLLECTED' WHERE manifest_id = $1`, rec.ManifestID)
        }
    }

    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(map[string]interface{}{
        "status":    "SUCCESS",
        "processed": batch.BatchSize,
        "timestamp": time.Now().UTC().Format(time.RFC3339),
    })
}

// Live HTML Table for instant verification in browser
func (g *IngestGateway) HandleLedgerView(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "text/html; charset=utf-8")
    html := `<!DOCTYPE html><html><head><title>KasiCollect Live Ledger</title><style>body{background:#0A1120;color:#CBD5E1;font-family:sans-serif;padding:20px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #1E293B;padding:12px;text-align:left}th{background:#059669;color:#fff}tr:nth-child(even){background:#131E32}</style></head><body><h1>KasiCollect Live Township Ledger</h1><p>Ward 107/108 ? Alexandra Corridor</p><table><tr><th>Manifest ID</th><th>Landmark</th><th>Materials</th><th>Status</th></tr>`

    if g.db != nil {
        rows, err := g.db.Query(`SELECT manifest_id, landmark, materials, status FROM manifests ORDER BY created_at DESC LIMIT 50`)
        if err == nil {
            defer rows.Close()
            for rows.Next() {
                var id, landmark, mat, st string
                if err := rows.Scan(&id, &landmark, &mat, &st); err == nil {
                    html += "<tr><td>" + id + "</td><td>" + landmark + "</td><td>" + mat + "</td><td>" + st + "</td></tr>"
                }
            }
        }
    }
    html += `</table></body></html>`
    w.Write([]byte(html))
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
            log.Printf("[WARN] DB init warning: %v", err)
        } else {
            log.Println("[BOOT] PostgreSQL tables initialized successfully.")
        }
    }

    gateway := &IngestGateway{db: db}

    http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
        w.Header().Set("Content-Type", "application/json")
        json.NewEncoder(w).Encode(map[string]string{
            "status":   "ONLINE",
            "system":   "KasiCollect Cloud Gateway",
            "ward":     "Alexandra Ward 107/108",
            "version":  "1.0.0",
        })
    })

    http.HandleFunc("/v1/manifests", gateway.HandleManifests)
    http.HandleFunc("/v1/sync/batch", gateway.HandleBatchSync)
    http.HandleFunc("/admin/ledger", gateway.HandleLedgerView)

    log.Printf("[BOOT] KasiCollect Gateway listening on :%s", port)
    log.Fatal(http.ListenAndServe(":"+port, nil))
}
