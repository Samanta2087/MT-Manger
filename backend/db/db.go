package db

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	_ "github.com/lib/pq"
)

var DB *sql.DB

func Init() {
	dsn := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
		getEnv("DB_HOST", "localhost"),
		getEnv("DB_PORT", "5432"),
		getEnv("DB_USER", "postgres"),
		getEnv("DB_PASSWORD", ""),
		getEnv("DB_NAME", "fyloxen"),
		getEnv("DB_SSLMODE", "disable"),
	)

	var err error
	DB, err = sql.Open("postgres", dsn)
	if err != nil {
		log.Fatalf("❌ Failed to open DB: %v", err)
	}

	// Connection pool settings
	DB.SetMaxOpenConns(25)
	DB.SetMaxIdleConns(5)
	DB.SetConnMaxLifetime(5 * time.Minute)

	if err = DB.Ping(); err != nil {
		log.Fatalf("❌ Failed to connect to DB: %v", err)
	}

	log.Println("✅ PostgreSQL connected")
	migrate()
}

func Close() {
	if DB != nil {
		DB.Close()
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// migrate auto-creates tables if they don't exist
func migrate() {
	schema := `
	CREATE TABLE IF NOT EXISTS app_opens (
		id          BIGSERIAL PRIMARY KEY,
		device_id   VARCHAR(64)  NOT NULL,
		app_version VARCHAR(20),
		os_version  VARCHAR(20),
		created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
	);

	CREATE INDEX IF NOT EXISTS idx_app_opens_device ON app_opens(device_id);
	CREATE INDEX IF NOT EXISTS idx_app_opens_time   ON app_opens(created_at);

	CREATE TABLE IF NOT EXISTS feature_usage (
		id           BIGSERIAL PRIMARY KEY,
		device_id    VARCHAR(64)  NOT NULL,
		feature_name VARCHAR(100) NOT NULL,
		screen       VARCHAR(100),
		extra        TEXT,
		created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
	);

	CREATE INDEX IF NOT EXISTS idx_feature_usage_device  ON feature_usage(device_id);
	CREATE INDEX IF NOT EXISTS idx_feature_usage_feature ON feature_usage(feature_name);
	CREATE INDEX IF NOT EXISTS idx_feature_usage_time    ON feature_usage(created_at);

	CREATE TABLE IF NOT EXISTS crash_logs (
		id            BIGSERIAL PRIMARY KEY,
		device_id     VARCHAR(64) NOT NULL,
		app_version   VARCHAR(20),
		os_version    VARCHAR(20),
		error_message TEXT,
		stack_trace   TEXT,
		created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
	);

	CREATE INDEX IF NOT EXISTS idx_crash_logs_device ON crash_logs(device_id);
	CREATE INDEX IF NOT EXISTS idx_crash_logs_time   ON crash_logs(created_at);
	`

	if _, err := DB.Exec(schema); err != nil {
		log.Fatalf("❌ Migration failed: %v", err)
	}
	log.Println("✅ Schema ready")
}
