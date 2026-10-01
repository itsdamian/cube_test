# 設定與執行環境

[← 回到 README](../README.md) · [設定](configuration.md) · [API](api.md) · [測試](testing.md) · [驗收步驟](acceptance.md)

## 需要的工具

| 工具 | 用途 | 備註 |
|---|---|---|
| Docker Desktop | 執行整個系統、跑後端測試（Testcontainers）、建 image | 多架構建置需 containerd image store（Docker Desktop 預設）或 `docker buildx create --use --driver docker-container` |
| JDK 21 | 在本機跑後端測試／開發 | `brew install openjdk@21`。**不需要安裝 Maven**，用專案內的 `./mvnw` |
| Node.js 24+ | 在本機跑前端測試／開發 | 只在不透過 Docker 開發前端時需要 |
| `unzip` | `./mvnw` 第一次下載 Maven 時使用 | macOS 與 GitHub 的 Ubuntu runner 都有；沒有的話 mvnw 會改抓 .tar.gz，導致 SHA-256 校驗失敗 |

## 快速開始與服務

```bash
docker compose up -d --build --wait     # 建置並啟動，等到全部 healthy（第一次約 5 分鐘）
open http://localhost:3000              # 30 秒內可看到跳動的 BTC-USD 價格
```

- 停止（保留資料）：`docker compose down`
- 停止並**刪除所有資料**：`docker compose down -v`
- port 被占用時：`FRONTEND_PORT=3001 BACKEND_PORT=8081 docker compose up -d --wait`，之後文件中的 3000／8080 請換成你設定的 port。

| 服務 | 對外 port | 說明 |
|---|---|---|
| frontend | 3000 | nginx 提供網頁，並把 `/api` 轉給 backend |
| backend | 8080 | Spring Boot API、SSE、`/actuator/health/{liveness,readiness}` |
| kafka | （無） | 單節點 KRaft，只在內部網路 |
| postgres | （無） | 只在內部網路；查資料用 `docker compose exec postgres psql -U currency -d currency` |

**網路**：`currency_internal`（`internal: true`，無法連外）讓四個服務互連；只有 backend 另外接 `currency_egress`，可以連交易所與匯率網站；frontend 另接 `currency_public` 用來對外開放 port。

## 設定（環境變數）

所有設定都有預設值（[`src/main/resources/application.yml`](../src/main/resources/application.yml)），可用環境變數覆寫。下表中標 ★ 的變數，compose 會從你的 shell 傳給 backend，例如 `APP_ALERT_COOLDOWN=30s docker compose up -d backend`。

| 變數 | 預設 | 說明 |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/currency` / `currency` / `currency` | 資料庫 |
| `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | `5000` | 取連線逾時（毫秒）；DB 掛掉時 readiness 約 5 秒內回 503 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `APP_FEED_PRIMARY_URL` ★ / `APP_FEED_BACKUP_URL` ★ | Coinbase / Kraken 的 wss 網址 | 價格來源 |
| `APP_FEED_STALE_THRESHOLD` ★ | `10s` | 多久沒收到**任何**訊息（含 heartbeat）視為失效 |
| `APP_FEED_PRICE_STALE_THRESHOLD` ★ | `60s` | 多久沒有**成交**視為失效 |
| `APP_FEED_RECOVERY_PERIOD` | `15s` | 主來源恢復健康多久後切回 |
| `APP_FEED_IDLE_TIMEOUT` | `10s` | 連線多久沒訊息就中斷重連（處理 half-open） |
| `APP_FX_URL` / `APP_FX_REFRESH_INTERVAL` ★ | open.er-api.com / `30m` | 匯率來源與更新頻率 |
| `APP_RETENTION_TICKS` ★ | `P30D` | 逐筆價格保留期 |
| `APP_RETENTION_INTERVAL` ★ / `APP_RETENTION_INITIAL_DELAY` ★ | `PT1H` / `PT1M` | 清除工作的間隔／啟動後第一次執行 |
| `APP_ALERT_COOLDOWN` ★ | `5m` | 警示冷卻期（以成交時間計算） |
| `APP_STREAMS_GRACE` | `5s` | K 線視窗關閉後仍接受遲到成交的時間 |
| `APP_STREAMS_STATE_DIR` | `/var/lib/currency/streams`（容器內） | Kafka Streams 本地狀態 |
| `APP_KAFKA_GROUP_PREFIX` | （空） | consumer group 前綴；多個環境共用一個 broker 時使用 |
| `APP_INGEST_ENABLED` / `APP_STREAMS_ENABLED` / `APP_PERSIST_ENABLED` / `APP_ALERTS_ENABLED` | `true` | 功能開關（例如之後在 K8s 讓 ingest 只跑一份） |
| `SPRING_PROFILES_ACTIVE=chaos` | （無） | 開啟故障注入端點 `/actuator/feeds`，**只用於手動驗收** |

## 常見問題

- **port 3000 或 8080 被占用**：用 `FRONTEND_PORT` / `BACKEND_PORT`（見本頁〈快速開始與服務〉）。
- **`./mvnw` 顯示 SHA-256 驗證失敗**：安裝 `unzip`（見本頁〈需要的工具〉）。
- **公司網路或 VPN 擋了交易所**：用 `APP_FEED_PRIMARY_URL` / `APP_FEED_BACKUP_URL` 指到可用的端點，或換一個網路。
- **看 backend log**：`docker compose logs -f backend`。
- **從 Kafka 3.9 的舊 volume 升級**：compose 的 Kafka 已升到 4.3（與 Kubernetes 叢集一致）。沿用舊的 `kafka-data` volume 可以直接啟動，資料都在，但 `metadata.version` 會停在 3.9（KRaft 的正常升級狀態）。若要啟用 4.x 的新功能，執行：
  ```bash
  docker compose exec kafka /opt/kafka/bin/kafka-features.sh --bootstrap-server localhost:9092 upgrade --release-version 4.3
  ```
  全新的 volume 一開始就是 4.3，不需要這一步。
