# Plan: cube_test 改造 — 即時 Bitcoin 價格串流（Kafka + React）

Status: CONFIRMED

Spec: `specs/realtime-btc-kafka-react/spec.md`（CONFIRMED）

## Approach

**一個 Spring Boot 後端（模組化單體）+ 一個 React 前端 + Kafka（KRaft 單 broker）+ PostgreSQL**，全部用 docker compose 啟動。

資料流：

```
Coinbase WS (主) ─┐                                  ┌─► [tick-persister]   → PostgreSQL price_tick
                  ├─► FeedManager ─► Kafka topic ────┼─► [Kafka Streams]   → topic btc.candles → [candle-persister] → PostgreSQL candle
Kraken WS  (備) ─┘   (故障切換)      btc.price.ticks  ├─► [alert-evaluator]  → PostgreSQL alert_event + topic btc.alerts.triggered
                                                     └─► [SSE broadcaster] → 瀏覽器（EventSource）
open.er-api.com (REST, 每 30 分鐘) → PostgreSQL fx_rate → 換算 API
FeedManager 每 5 秒 → topic btc.feed.status → [SSE broadcaster]
```

1. **價格來源**：主要 **Coinbase Exchange WebSocket**（`wss://ws-feed.exchange.coinbase.com`，`ticker` channel，`BTC-USD`），備援 **Kraken WebSocket v2**（`wss://ws.kraken.com/v2`，`trade` channel，`BTC/USD`，訂閱時明確帶 `snapshot: false`；一則訊息可能含多筆成交，parser 會拆成多個 tick）。兩家都免 key、訊息格式簡單、對台灣與美國 IP 都能用（排除 Binance：美國 IP 被擋，之後 CI/K8s 可能在美國）。用 JDK 內建的 `java.net.http.WebSocket`，不額外引入函式庫。
2. **故障切換（hot standby）**：兩條連線同時保持，`FeedManager` 由 scheduler 每秒呼叫一次 `check()`（邏輯本身是純函式式的狀態機，測試直接用可變 `Clock` 呼叫 `check()`，不用 `Thread.sleep`）；只把「目前 active 來源」的 tick 送進 Kafka。判定規則（邊界明確）：
   - 某來源「stale」＝ 未連線，或 `now - lastTickAt >= stale-threshold`（預設 10 秒，剛好 10 秒即算 stale）
   - active=主 且主 stale、備援健康 → 切到備援
   - active=備援 且主來源已連續健康 `>= recovery-period`（預設 15 秒）→ 切回主；recovery 期間主來源又 stale → 計時歸零，維持備援
   - 兩個都 stale → 狀態 `DISCONNECTED`，之後哪個先健康就用哪個；若是備援先恢復，主來源健康滿 15 秒後再切回主
   - 斷線各自以指數退避重連（1s → 30s 上限），重連成功後退避歸零
   - **idle watchdog（處理 half-open 連線）**：真實斷網常不會收到 FIN/RST，JDK WebSocket 會以為仍連著。因此兩個 client 都訂閱交易所的 heartbeat（Coinbase `heartbeats` channel、Kraken v2 內建 `heartbeat`，皆約每秒一則），若 `idle-timeout`（預設 10 秒，`APP_FEED_IDLE_TIMEOUT`）內收不到**任何**訊息，client 主動 `abort()` 舊連線並進入重連流程。這樣網路恢復後主來源一定會重新連上，FeedManager 才能切回。
3. **匯率**：**open.er-api.com**（`/v6/latest/USD`，免 key，含 TWD/JPY/EUR/GBP）。每 30 分鐘抓一次（可設定），存進 `fx_rate` 表（重啟後仍有最後一次匯率），並記錄來源提供的 `time_last_update_utc` 作為「匯率更新時間」給前端顯示。遇到 429 / 5xx / timeout → 保留上次匯率並記 warn log，下個週期再試。依來源條款，前端換算表旁放「Rates By Exchange Rate API」attribution 連結。
4. **Kafka Streams K 線**：讀 `btc.price.ticks`，以**事件時間**（自訂 `TimestampExtractor` 取 payload 的 `eventTime`，不是 Kafka record timestamp）做 1m、5m tumbling window，grace 5 秒（可設定），`suppress(untilWindowCloses)` 後輸出「定稿」K 線到 `btc.candles`，再由 consumer upsert 到 `candle` 表。
   - open/close 依排序鍵 `(eventTime, receivedAt, eventId)` 最小/最大者決定（不依到達順序）；同一 `eventTime` 多筆時也有確定結果。README 的 AC4 對照 SQL 使用同一排序鍵。
   - window 範圍為 `[start, end)`：剛好落在 `12:01:00.000` 的 tick 屬於 12:01 那根。
   - 超過 grace 的遲到 tick 會被 Streams 丟棄但仍寫入 DB → 以 Streams 內建 `dropped-records` metric 記錄並打 warn log；AC4 比對時若數值不一致，先檢查此 metric（正常情況下 Coinbase/Kraken 的遲到量為 0）。
   - `DeserializationExceptionHandler` 設為 `LogAndContinue`，壞訊息不會讓 Streams thread 死掉；`state.dir` 可用環境變數設定。
5. **警示**：後端 consumer（共用 consumer group，全系統只有一份在判斷）逐筆比對警示；條件（ABOVE：`price > threshold`；BELOW：`price < threshold`）成立，且 `last_triggered_at` 為 null 或 `eventTime - last_triggered_at >= cooldown`（預設 5 分鐘，剛好 5:00 可再觸發）→ 寫 `alert_event`（`read_at` 為 null 表示未讀）、更新 `last_triggered_at`，並發佈到 `btc.alerts.triggered`。前端開著時經 SSE 即時跳出通知；打開頁面時呼叫 API 撈未讀警示。
   - **「已讀」的定義**：toast 在「頁面可見」（`document.visibilityState === 'visible'`）時顯示，就視為使用者已看過，前端呼叫 `POST /api/alert-events/{id}/read`；分頁在背景時收到的警示先不標記，等切回可見並顯示後才標記。頁面沒開時觸發的警示一律保持未讀，下次打開顯示在「未讀警示」清單，使用者可逐筆或全部標記已讀。
6. **即時推播**：**Server-Sent Events**（`GET /api/stream`，Spring MVC `SseEmitter`），事件類型 `price` / `status` / `alert`。SSE broadcaster 的 consumer 用「每個實例唯一」的 group id 並設 `auto.offset.reset=latest`（只推新事件，不重播歷史），讓之後 K8s 多副本時每個實例都能推給自己的連線。
7. **狀態顯示**：`FeedManager` 每 5 秒發佈 `FeedStatus{activeSource, state: LIVE|STALE|DISCONNECTED, lastTickAt}`；前端另外自己計時，若 SSE 本身斷掉或超過 15 秒沒收到任何事件，也顯示「已斷線/延遲」—— 兩層保護，不會靜默顯示舊價格。
8. **保留期限**：`@Scheduled` 每小時執行一次，在同一次執行中以每批 10,000 筆**迴圈刪除直到沒有符合 `event_time < now - retention` 的資料為止**（`APP_RETENTION_TICKS`，預設 `P30D`）；`candle` 表不動。`Clock` 注入以便測試縮短保留期。
9. **Kafka 不可用時的行為**：WebSocket listener thread 只把 tick 放進有界佇列（`offer`，不阻塞），由獨立的 publisher thread 呼叫 `KafkaTemplate.send()`；producer `max.block.ms=5s`、`delivery.timeout.ms=30s`。Kafka 斷線期間佇列滿了就丟棄最舊的 tick 並累計 `feed.ticks.dropped` metric 與節流 warn log（spec 不要求補資料；Non-Goal「不回補歷史」）。`FeedManager` 的 stale 判斷用「從 WS 收到 tick 的時間」，和 Kafka 是否可用無關，所以 Kafka 掛掉不會誤判來源斷線。Kafka 恢復後 publisher 自動繼續送出。
10. **前端**：Vite + React + TypeScript，圖表用 **TradingView lightweight-charts**（Apache-2.0，原生支援 K 線與折線）；測試用 Vitest + React Testing Library + MSW（mock API）。正式環境由 nginx 容器提供靜態檔並反向代理 `/api`（SSE 需關閉 buffering），瀏覽器只需連一個 origin，不用處理 CORS。

## Alternatives Considered

| 題目 | 選擇 | 放棄的方案與原因 |
|---|---|---|
| 服務切分 | 模組化單體（一個 Spring Boot app，用 package 分職責，`app.ingest.enabled` 等開關控制） | **多個微服務**（ingest / stream / api 各一個）：學習 K8s 很好玩，但現在就要多份 pom、多個 image、跨服務 DTO 共用，第一階段複雜度太高。單體內的邊界已經是用 Kafka topic 溝通，之後要拆只需換開關分成多個 Deployment。 |
| 主/備價格來源 | Coinbase WS / Kraken WS | **Binance**：美國 IP 限制；**CoinGecko REST**：輪詢、非逐筆，當即時來源太粗；也有嚴格 rate limit。 |
| 匯率來源 | open.er-api.com | **Frankfurter / ECB**：沒有 TWD（實測）；**CoinGecko 多幣別 BTC 價格反推匯率**：混合了交易所價差，不是真正匯率，且 AC3 要和「匯率來源」比對，不直觀。 |
| 推播協定 | SSE | **WebSocket + STOMP**：雙向，但這裡只需要伺服器→瀏覽器；SSE 更簡單、瀏覽器自動重連、走一般 HTTP 好除錯、nginx/K8s Ingress 設定更少。 |
| K 線計算 | Kafka Streams windowed aggregation | **SQL 事後 group by**：做得到但就沒練到 Kafka Streams（spec 明確要求）。 |
| 事件序列化 | JSON（Jackson + Java record） | **Avro + Schema Registry**：正式環境常見，但要多跑一個 Schema Registry、多一層 codegen；學習第一步先用 JSON，之後可獨立升級。 |
| 資料庫 | PostgreSQL + Flyway | **MySQL**：同樣可行，但 Postgres 的 `timestamptz`、`ON CONFLICT` upsert 更順手；**TimescaleDB**：保留期/壓縮很方便，但多一個學習維度且非必要。 |
| 警示判斷 | 普通 `@KafkaListener` + DB 狀態 | **Kafka Streams + state store**：警示由 REST CRUD 管理，要把 DB 的警示同步進 Streams（GlobalKTable/CDC）代價高；普通 consumer 直觀、易測。 |
| Spring Boot 版本 | **3.5.x**（使用者決定，2026-09-29）：教學資源、範例、Stack Overflow 答案最多，適合學習 | **4.1.x**（2026-06 釋出，OSS 支援到 2027-07）：**因教學資源較少而不採用**；另有 Jackson 3（`tools.jackson` package）、starter 模組化等破壞性變更，會增加學習時的干擾。代價：3.5 的 OSS 支援已於 2026-06-30 結束，見 Risks。 |
| 測試用 Kafka/DB | Testcontainers（Kafka + Postgres）+ Kafka Streams `TopologyTestDriver` | **EmbeddedKafka + H2**：不需 Docker，但 H2 和 Postgres 的 SQL/型別差異會讓測試「綠了但上線壞」；Flyway migration 也要能在真 Postgres 上跑。 |

## Architecture / Design Decisions

### 後端 package 結構（維持 `com.currency.demo`）

```
com.currency.demo
├── DemoApplication                 （移除舊的 CommandLineRunner 與多餘的 @ComponentScan）
├── config/     AppProperties（@ConfigurationProperties record，集中所有 app.* 設定）、ClockConfig、KafkaTopicsConfig、RestClientConfig
├── feed/       PriceFeedClient（介面）、WebSocketPriceFeedClient、CoinbaseMessageParser、KrakenMessageParser、FeedManager、FeedStatus、TickPublisher（有界佇列 + publisher thread）、FeedsChaosEndpoint（僅 chaos profile）
├── pricing/    PriceTick（record, Kafka 事件）、TickPersister（listener）、PriceTickRepository、PriceQueryService、RetentionJob
├── candle/     CandleTopology（Kafka Streams）、Candle（record）、CandlePersister、CandleRepository
├── fx/         ExchangeRateClient、FxRateRefresher（@Scheduled）、FxRate（entity）、ConversionService
├── currency/   Currency（entity，沿用）、CurrencyRepository、CurrencyService、CurrencyController、DTO records
├── alert/      PriceAlert、AlertEvent（entities）、AlertEvaluator（listener）、AlertService、AlertController
├── stream/     SseBroadcaster（listener → SseEmitter）、StreamController
└── health/     KafkaHealthIndicator、KafkaStreamsHealthIndicator
```

- **建構子注入**、DTO/事件一律 `record`、金額一律 `BigDecimal`、時間一律 `Instant`（DB `timestamptz`）。不再用 `Map<String,Object>` 當回傳型別。
- **Feature 開關**（全部可用環境變數覆寫）：`app.ingest.enabled`、`app.streams.enabled`、`app.persist.enabled`、`app.alerts.enabled`。預設全開；測試可以只開需要的部分；之後 K8s 可讓 ingest 只跑 1 個副本。
- **Kafka topics**（啟動時由 `NewTopic` bean 建立，單 broker 所以 replication=1、可設定）：
  - `btc.price.ticks`（key = `BTC-USD`，保證同幣對同 partition 有序；3 partitions）
  - `btc.candles`（key = `BTC-USD|1m|<openTime>`）
  - `btc.alerts.triggered`
  - `btc.feed.status`（cleanup.policy=compact）
- **交付語意**：producer 開 idempotence；consumer at-least-once。tick 帶 `eventId`(UUID) 並設 DB unique，重複寫入用 `ON CONFLICT DO NOTHING`；candle 以 `(pair, interval, open_time)` upsert；重複 tick 不影響 OHLC 的 high/low，open/close 依事件時間也不受重複影響。Streams 用 at_least_once（exactly_once_v2 在單 broker 需額外設定 transaction log replication，留作之後的練習）。
- **大量寫入**：Coinbase 高峰可能每秒數十筆；`TickPersister` 使用 batch listener + JDBC batch insert（`JdbcTemplate.batchUpdate`），不用 JPA 逐筆 save。
- **健康檢查**：開啟 `management.endpoint.health.probes.enabled`。
  - liveness：只有 `livenessState`（不含外部依賴，避免 Kafka 掛掉導致 K8s 狂重啟 pod）
  - readiness：`readinessState` + `db` + 自訂 `kafka`（`AdminClient.describeCluster()`，3 秒 timeout）+ 自訂 `kafkaStreams`（Streams 狀態為 `RUNNING`/`REBALANCING` 才 UP；`ERROR`/`NOT_RUNNING` 為 DOWN，避免 K 線靜默停止；`app.streams.enabled=false` 時不註冊）
  - Actuator 只暴露 `health,info`（移除現在的 `include=*`）；`chaos` profile 額外暴露 `feeds` 端點（見下）。
- **故障注入端點（僅供手動驗收，預設關閉）**：自訂 Actuator endpoint `feeds`，只在 `SPRING_PROFILES_ACTIVE` 含 `chaos` 時註冊。`POST /actuator/feeds/{source}/block` 讓該來源的真實 WebSocket client 主動斷線、且在 unblock 前每次重連都視為失敗（仍走真實的退避邏輯）；另支援 `?mode=silent`：連線保持開啟、但 client 丟棄所有收到的訊息（模擬 half-open），用來驗證 idle watchdog 會 abort 並重連。`POST /actuator/feeds/{source}/unblock` 解除後，client 用正常重連流程連回真實交易所，`FeedManager` 自動切回。用途是在**不重啟**的情況下驗證 AC6 的自動切換與切回。預設 profile 與 compose 預設設定都不包含 `chaos`。
- **設定**：`application.yml` 用 `${ENV:default}` 形式；提供 `docker` profile 給 compose。移除現在 `logging.level...=TRACE` 的大量除錯設定。
- **SSE**：每個連線一個 `SseEmitter`（timeout 0 + 每 15 秒 heartbeat comment）；price 事件節流：每連線最多每 250ms 推一次最新價，避免高峰塞爆瀏覽器。

### 前端（`frontend/`）

- 頁面（單頁，分區塊/分頁）：即時價格卡（價格、來源、狀態燈、最後更新時間）、多幣別換算表（中文名、價格、匯率、匯率更新時間，旁附「Rates By Exchange Rate API」attribution 連結）、圖表（1m / 5m K 線切換 + 最近 N 分鐘走勢折線，走勢資料來自 `/api/prices/trend` 的降採樣結果，之後以 SSE 價格即時延伸）、幣別管理（表格 + 新增/編輯/刪除）、警示管理（新增 高於/低於、刪除、未讀警示清單與「標記已讀」/「全部已讀」）、toast 通知（依上方「已讀」定義自動標記）。
- 資料取得：`useEventSource` hook 處理 SSE 與前端自有的延遲偵測；REST 用 `fetch` 包一層小 API client（不引入 Redux，React state + context 足夠）。
- 介面文字繁體中文。

### 部署 / 建置

- **版本基準**：Java 21、Spring Boot 3.5.x、Kafka broker image `apache/kafka:3.9.x`（與 kafka-clients 3.9 同系列）、PostgreSQL 17；實際 patch 版本在第一個 task 決定並固定。
- **Maven Wrapper**（`mvnw`）加入 repo，不需全域安裝 Maven。
- **單一多架構 Dockerfile**：builder 用 `FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk`（jar 與架構無關，builder 用本機架構跑比 QEMU 模擬快很多），runtime `eclipse-temurin:21-jre`（官方有 amd64/arm64），非 root 使用者、`HEALTHCHECK` 不寫死（交給 compose / K8s probe）。刪除 `Dockerfile.amd64`、`Dockerfile.silicon`、`start-silicon.sh`、`stop-silicon.sh`。
- `frontend/Dockerfile`：node build → nginx:alpine。
- `docker-compose.yml`：`kafka`（`apache/kafka` 官方 image，KRaft 單節點，**具名 volume** 保存 log / offset / Streams changelog）、`postgres`（具名 volume）、`backend`（Streams `state.dir` 也掛具名 volume）、`frontend`；`depends_on` + `condition: service_healthy`。所有 image tag 固定版本，不用 `latest`。
- **compose 網路拆成三個**，讓「斷外網」可以只斷價格來源而不影響內部通訊：
  - `internal`（`internal: true`，無對外網路）：kafka、postgres、backend、frontend 互連
  - `egress`（一般 bridge）：只有 backend，用於連 Coinbase / Kraken / open.er-api，以及對 host 公開 8080
  - `public`（一般 bridge）：只有 frontend，用於對 host 公開 3000
  - AC2 手動驗收：`docker network disconnect <project>_egress <backend>` → 後端仍連得到 Kafka/DB、前端仍連得到後端，只有價格來源斷線；`docker network connect` 接回。
- **離線測試準備**（AC13）：README 列出 `./mvnw dependency:go-offline`、`npm ci`，以及要事先 `docker pull` 的固定版本 image：`apache/kafka:<ver>`、`postgres:<ver>`、`testcontainers/ryuk:<ver>`（測試若用 Toxiproxy 則不需要，本 plan 不使用）。QA 以 `./mvnw -o verify` 在斷網下驗收。
- **多架構建置**（AC14）：README 註明需先建立支援多平台的 builder（`docker buildx create --use --driver docker-container`，或啟用 Docker Desktop 的 containerd image store）。

## Affected Files & Modules

**修改**
- `pom.xml` — Java 21、`spring-boot-starter-parent` **3.5.x**（最新 patch），版本由 Boot BOM 管理、不自行指定：`spring-boot-starter-web`、`-actuator`、`-data-jpa`（已含 JDBC / `JdbcTemplate`）、`-validation`、`spring-kafka`（3.3.x）、`kafka-streams`（3.9.x）、`flyway-core` + `flyway-database-postgresql`、`postgresql`；測試：`spring-boot-starter-test`、`spring-boot-testcontainers`、`org.testcontainers:junit-jupiter` / `kafka` / `postgresql`（1.x 且**必須 ≥ 1.21.4**：本機 Docker 29.x 對舊版會報「client version 1.32 is too old」；第一個 task 用 `./mvnw dependency:tree` 確認，不足就覆寫 `testcontainers.version` 屬性；`org.testcontainers.kafka.KafkaContainer` 搭配 `apache/kafka` image）、`kafka-streams-test-utils`（TopologyTestDriver；不用 `spring-kafka-test`，避免帶入用不到的 broker/zookeeper）、`awaitility`、`org.java-websocket:Java-WebSocket`；移除 h2
- `src/main/java/com/currency/demo/DemoApplication.java` — 移除 seed runner 與多餘 annotation，加 `@EnableScheduling`、`@ConfigurationPropertiesScan`
- `Currency` / `CurrencyRepository` / `CurrencyService` / `CurrencyController` — 搬到 `currency/` package，改 DTO record + Bean Validation、`Optional` 回傳、正確的 404/409/204
- `src/main/resources/application.properties` → 改為 `application.yml`
- `docker-compose.yml`、`Dockerfile`、`README.md`
- `src/test/java/.../CurrencyControllerTest.java` — 改用 Testcontainers Postgres

**新增**
- `mvnw`、`mvnw.cmd`、`.mvn/wrapper/`
- `.gitignore`（`target/`、`node_modules/`、`frontend/dist/`、IDE 檔）
- `src/main/resources/db/migration/V1__currency.sql`、`V2__seed_currencies.sql`、`V3__price_tick.sql`、`V4__candle.sql`、`V5__fx_rate.sql`、`V6__alerts.sql`
- 上述 `feed/ pricing/ candle/ fx/ alert/ stream/ health/ config/` 下的類別
- `src/test/...` — parser 單元測試（真實訊息 JSON 當 fixture，含 Kraken 一則多筆）、`FeedManager` 切換邏輯（假 client + 可控 Clock）、`WebSocketPriceFeedClient` 對本機假 WS server 的整合測試（測試用 WS server 以 test scope 的 `org.java-websocket:Java-WebSocket` 實作）、`TickPublisher`（Kafka 不可用時不阻塞、丟最舊）、`CandleTopology`（TopologyTestDriver）、`AlertEvaluator` 冷卻邏輯、`ExchangeRateClient`（`MockRestServiceServer`）、Retention、Repository/Controller 整合測試（Testcontainers）、一條端到端測試（假 feed → Kafka → DB/K 線/警示）
- `frontend/`（Vite 專案、元件、hooks、測試、`Dockerfile`、`nginx.conf`）
- `docker-compose.chaos.yml`（只加上 `SPRING_PROFILES_ACTIVE=chaos`，手動驗收 AC6 用）

**刪除**
- `target/`（整個目錄從 git 移除）
- `Dockerfile.amd64`、`Dockerfile.silicon`、`start-silicon.sh`、`stop-silicon.sh`
- `CoindeskController`、`CoindeskService`、`CoindeskResponse`、`RestTemplateConfig` 以及對應測試（Coindesk 已停用；以新的 pricing/fx API 取代）
- `src/test/resources/application-test.properties`（H2 設定）

## Data Model / API Changes

### 資料表（Flyway，PostgreSQL）

| 表 | 欄位 | 備註 |
|---|---|---|
| `currency` | id bigserial PK, code varchar(3) unique, name varchar(50), created_at, updated_at timestamptz | 沿用現有形狀；V2 seed：USD 美元、EUR 歐元、GBP 英鎊、TWD 新台幣、JPY 日圓（`ON CONFLICT DO NOTHING`） |
| `price_tick` | id bigserial PK, event_id uuid unique, pair varchar(16), price numeric(20,8), source varchar(16), event_time timestamptz, received_at timestamptz | index `(pair, event_time)`；30 天清除 |
| `candle` | pair, interval varchar(4) ('1m'/'5m'), open_time timestamptz, close_time, open, high, low, close numeric(20,8), tick_count int | PK `(pair, interval, open_time)`；不清除 |
| `fx_rate` | code varchar(3) PK, rate_per_usd numeric(20,8), provider_updated_at timestamptz, fetched_at timestamptz | 只存最新值 |
| `price_alert` | id bigserial PK, pair, direction varchar(5) ('ABOVE'/'BELOW'), threshold numeric(20,8), created_at, last_triggered_at nullable | |
| `alert_event` | id bigserial PK, alert_id FK → price_alert ON DELETE CASCADE, direction, threshold, triggered_price, triggered_at, read_at nullable | 刪除警示時一併刪除其紀錄 |

### Kafka 事件（JSON）

- `PriceTick(UUID eventId, String pair, BigDecimal price, String source, Instant eventTime, Instant receivedAt)`
- `Candle(String pair, String interval, Instant openTime, Instant closeTime, BigDecimal open, high, low, close, int tickCount)`
- `AlertTriggered(long eventId, long alertId, String direction, BigDecimal threshold, BigDecimal price, Instant triggeredAt)`
- `FeedStatus(String activeSource, State state, Instant lastTickAt, Instant reportedAt)`

### REST API（全部重新設計；`/api/bitcoin/price/original` 移除）

| Method | Path | 說明 |
|---|---|---|
| GET | `/api/prices/latest` | 最新 BTC-USD 價格 + 來源 + 狀態 |
| GET | `/api/prices/converted` | 各幣別：code、中文名、價格、匯率、匯率更新時間（無匯率的幣別標示 `rate: null`） |
| GET | `/api/prices/history?from&to&limit&cursor` | 逐筆歷史，**keyset 分頁**：依 `(event_time, id)` 排序，`limit` 預設 1,000、上限 5,000，回應帶 `nextCursor`（沒有下一頁時為 null）。任何區間都不會因筆數多而回 400；只有 `from > to` 或 `limit` 超過上限才回 400。 |
| GET | `/api/prices/trend?from&to&points` | 走勢圖用的**伺服器端降採樣**：以 PostgreSQL `date_bin` 把區間切成最多 `points`（預設 300、上限 1,000）個 bucket，每個 bucket 回傳最後一筆價格（依 `(event_time, received_at, event_id)`）；前端走勢線用這支，不用逐筆 API。 |
| GET | `/api/candles?interval=1m\|5m&from&to` | K 線 |
| GET | `/api/stream` | SSE：`price`、`status`、`alert` |
| GET/POST/PUT/DELETE | `/api/currencies[/{id}]` | 幣別 CRUD（code 驗證為 3 個大寫字母；重複 code → 409） |
| GET/POST/DELETE | `/api/alerts[/{id}]` | 警示（無 PUT：spec 只要求新增/刪除） |
| GET | `/api/alert-events?unread=true` | 未讀（或全部）觸發紀錄 |
| POST | `/api/alert-events/{id}/read`、`/api/alert-events/read-all` | 標記已讀 |
| POST | `/actuator/feeds/{source}/block`、`/unblock`；GET `/actuator/feeds` | **僅 `chaos` profile**：手動驗收用的故障注入 |
| GET | `/actuator/health/liveness`、`/actuator/health/readiness` | 探針 |

錯誤回應採 Spring 內建 RFC 9457 `ProblemDetail`。

### 環境變數（節錄）

`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`SPRING_KAFKA_BOOTSTRAP_SERVERS`、`APP_FEED_PRIMARY_URL`、`APP_FEED_BACKUP_URL`、`APP_FEED_STALE_THRESHOLD`(10s)、`APP_FEED_RECOVERY_PERIOD`(15s)、`APP_FX_URL`、`APP_FX_REFRESH_INTERVAL`(30m)、`APP_RETENTION_TICKS`(P30D)、`APP_ALERT_COOLDOWN`(5m)、各 feature 開關。

## Testing Strategy

- **每個 task 自帶測試**，不集中到最後。
- 單元：parser（Coinbase/Kraken 真實訊息樣本，含 Kraken 一則多筆、格式錯誤訊息）、FeedManager 切換狀態機、AlertEvaluator 冷卻、ConversionService（誤差計算）、TickPublisher、Retention。
- **時間相關測試一律用可變 `Clock` 直接呼叫方法，不用 `Thread.sleep`**，並涵蓋邊界：
  - FeedManager：剛好 10 秒 → stale；9.999 秒 → 不 stale；recovery 期間主來源又 stale → 維持備援、計時歸零；兩個都斷 → `DISCONNECTED`；備援先恢復 → 用備援，主來源再健康滿 15 秒 → 切回主。
  - AlertEvaluator：剛好 5:00 → 可再觸發；4:59.999 → 不觸發；冷卻後條件不成立 → 不觸發；ABOVE 剛好等於門檻 → 不觸發。
  - Retention：剛好等於保留期邊界的資料不刪；資料量超過一批（例如 25,000 筆、批次 10,000）→ 一次執行全部刪完；candle 數量不變。
- Kafka Streams：`TopologyTestDriver`（不需 broker），**輸入的 record timestamp 刻意和 payload 的 `eventTime` 不同**，證明用的是事件時間 extractor；最後送一筆更晚的 tick 推進 stream time 才斷言 suppress 的輸出。驗證 1m/5m OHLC、亂序、同 `eventTime` 的 tie-break、剛好落在 window 邊界的 tick、超過 grace 的遲到 tick 被丟棄、壞訊息被略過而 topology 繼續運作。
- WebSocket client：`WebSocketPriceFeedClient` 對本機假 WS server 的整合測試——(a) 連線收 tick → server 主動關閉 → client 依退避重連 → 再收到 tick；(b) **half-open**：server 保持連線但停止送任何訊息 → 超過 idle-timeout 後 client abort 並重連 → server 恢復送訊息後再收到 tick；(c) block / block(mode=silent) / unblock 行為。
- 整合：`@SpringBootTest` + Testcontainers（`@ServiceConnection` 的 Kafka、Postgres），外部 HTTP 用 `MockRestServiceServer`（含 429 → 保留舊匯率），price feed 用假 `PriceFeedClient`。
  - **Readiness（AC12）**：用 docker `pause` / `unpause`（`container.getDockerClient().pauseContainerCmd(...)`）讓 Kafka 失聯再恢復——pause 不會換 port，`@ServiceConnection` 的 bootstrap servers 仍然有效；斷言 readiness `DOWN`（HTTP 503）→ unpause 後在期限內回到 `UP`（用 Awaitility 輪詢，不用固定 sleep）。不用 `stop()`/`start()`，因為重啟後 host port 會改變。
- 前端：Vitest + RTL + MSW；SSE 用假 EventSource；涵蓋「頁面可見時 toast → 呼叫標記已讀」「背景分頁 → 不標記」、15 秒無事件 → 顯示延遲、attribution 連結存在。
- 手動驗收（AC1/2/3/4/6/7 需要真實網路與時間）：README〈驗收步驟〉逐條寫出指令。

## Acceptance Criteria 驗證方式

「自動」＝ `./mvnw verify` 或 `npm test` 內的測試；「手動」＝ README〈驗收步驟〉中寫明的操作（需要真網路或真時間）。

| AC | 自動測試 | 手動驗收 |
|---|---|---|
| AC1 compose 起來 30 秒內看到跳動價格 | 整合：假 feed → Kafka → SSE 端點收到 `price` 事件；前端：收到 SSE 後畫面更新 | `docker compose up` → 開 `http://localhost:3000` 計時 |
| AC2 斷網 30 秒內顯示斷線，恢復自動更新 | FeedManager：兩來源皆無 tick 超過門檻 → `DISCONNECTED`，恢復 → `LIVE`；前端：15 秒無事件 → 顯示延遲 | `docker network disconnect <project>_egress <backend container>`（只斷價格來源，內部通訊不受影響）→ 30 秒內前端顯示斷線；`docker network connect` 接回 → 不重啟自動恢復 |
| AC3 換算誤差 < 0.5%，顯示匯率時間 | ConversionService 單元測試（已知匯率 → 精確值）；API 回傳含 `rateUpdatedAt`；前端顯示該欄位 | 比對畫面 TWD 價格 ÷ USD 價格 與 `curl open.er-api.com` 的 TWD 匯率 |
| AC4 10 分鐘後有歷史、≥10 根 1m、≥2 根 5m，OHLC 一致 | TopologyTestDriver：用 10+ 分鐘的合成 tick，斷言根數與 OHLC＝同批 tick 手算；整合：tick/candle 落 DB 後以 SQL 對照 | 跑 10 分鐘後：`GET /api/candles` 取 K 線、`GET /api/prices/history` 分頁取完該時段逐筆（或直接執行 README 的 SQL，排序鍵同 `(event_time, received_at, event_id)`），逐根比對 OHLC；若不一致先查 Streams `dropped-records` metric |
| AC5 重啟後資料仍在 | 整合：寫入後重建 Spring context（同一 Postgres container）仍查得到 | `docker compose restart backend postgres` 後查 API |
| AC6 主來源封鎖 30 秒內切備援、恢復切回 | FeedManager：可控 Clock 模擬主來源 stale → 切 backup、恢復 15 秒 → 切回；status 事件含 `activeSource` | (a) 以 `chaos` profile 啟動（`docker compose -f docker-compose.yml -f docker-compose.chaos.yml up`）→ `POST /actuator/feeds/coinbase/block` → 30 秒內前端顯示來源為 Kraken → `POST .../unblock` → 不重啟，約 15～45 秒內（退避 + recovery period）切回 Coinbase。(b) 另外用 `APP_FEED_PRIMARY_URL=wss://invalid.example` 啟動，確認「主來源一開始就連不上」時也會用備援。 |
| AC7 警示通知 + 5 分鐘冷卻 + 冷卻後再通知 | AlertEvaluator：可控時間的 tick 序列，驗證 觸發 / 冷卻內不觸發 / 冷卻後仍成立再觸發；前端：收到 `alert` 事件出現 toast | 設門檻於現價附近；先用 `APP_ALERT_COOLDOWN=30s` 快速觀察，**再用預設 5 分鐘實跑至少一次** |
| AC8 關閉前端時觸發的警示重開顯示未讀 | 整合：無 SSE 連線時觸發 → `/api/alert-events?unread=true` 有資料；前端：載入時呼叫並顯示 | 關分頁 → 等觸發 → 重開 |
| AC9 超過保留期自動清除、K 線不受影響 | RetentionJob 整合測試：可控 Clock + 縮短保留期，舊 tick 被刪、candle 數量不變 | `APP_RETENTION_TICKS=PT5M` 跑 10 分鐘後查 |
| AC10 全新 DB 有 5 個預設幣別 | Flyway + Testcontainers：乾淨 DB 啟動後查 `/api/currencies` | `docker compose down -v && up` 後查 |
| AC11 前端幣別 CRUD，重新整理仍在 | 後端 Controller 整合測試；前端 MSW 元件測試 | 在頁面新增/修改/刪除後 F5 |
| AC12 停 Kafka readiness 失敗，恢復後自動恢復 | 整合：`pause` Kafka container → readiness DOWN(503)；`unpause` → Awaitility 等到 `UP`（見 Testing Strategy） | `docker compose stop kafka` / `start kafka` 並 `curl /actuator/health/readiness`（compose 內 Kafka 走服務名稱，重啟後位址不變） |
| AC13 離線、無預啟 Kafka 下測試全過 | 所有測試用 Testcontainers / 假 client / MockRestServiceServer；不含任何真實外部 URL | 先 `./mvnw dependency:go-offline`、`npm ci`、`docker pull` 固定版本的 kafka / postgres / ryuk image → 斷網執行 `./mvnw -o verify` 與 `npm test` |
| AC14 buildx 多架構 | — | 先建立多平台 builder（README 說明），再 `docker buildx build --platform linux/amd64,linux/arm64 .` |
| AC15 repo 無建置產物 | — | `git ls-files \| grep -E '(^\|/)(target\|node_modules\|dist)/'` 應為空 |

## Risks & Mitigations

| 風險 | 緩解 |
|---|---|
| **本機工具鏈缺**：沒有 JDK、Docker daemon 未啟動 | 第一個 task 前請使用者執行 `brew install openjdk@21` 並開啟 Docker Desktop；Maven 用 `mvnw`，不需安裝。 |
| **AC13「沒有網路連外」與 Testcontainers**：Testcontainers 需要 Docker daemon，第一次要拉 image | **已確認的解讀（使用者接受，2026-09-29）**：事先 `docker pull` 固定 tag 的 `apache/kafka`、`postgres`、`testcontainers/ryuk` image，並執行 `./mvnw dependency:go-offline` 與 `npm ci`；之後在**斷網**狀態下 `./mvnw -o verify` 與 `npm test` 全部通過，即達成 AC13。README 列出清單與指令。 |
| **Spring Boot 3.5 已結束 OSS 支援**（2026-06-30 起不再有免費的安全性修補） | 使用者已知悉並選擇 3.5 以利學習。使用 3.5 系列最新 patch；程式避免使用 3.5 已標記 deprecated 的 API（例如用 `RestClient` 而非 `RestTemplate`、用 `@MockitoBean` 而非 `@MockBean`），讓之後升級 4.x 的改動最小。升級 4.x 另開 spec。 |
| 外部 WebSocket 格式改版或被限流 | parser 與 client 分離、URL 可設定、有備援；parser 以真實樣本測試，格式錯誤只記 log 不中斷連線。 |
| 匯率來源每天才更新一次（我們每 30 分鐘抓，但資料本身日更） | 符合「抓取頻率 ≥ 每小時」；前端顯示來源提供的更新時間讓使用者知道新鮮度。AC3 比對的是同一來源，誤差來自四捨五入，遠小於 0.5%。 |
| 逐筆資料量：30 天可能上千萬筆 | batch insert、`(pair, event_time)` 索引、每小時執行、每批 10k 筆迴圈刪到沒有為止；歷史 API 用 keyset 分頁、走勢用 `date_bin` 降採樣，不會一次撈大量資料。之後若需要可改 Postgres 分區表。 |
| Kafka 斷線時 `send()` 阻塞、拖垮 WS thread 或誤判來源 stale | 有界佇列 + 獨立 publisher thread、`max.block.ms=5s`；stale 判斷只看 WS 收到的時間（Approach 第 9 點），並有對應測試。 |
| Kafka Streams thread 因壞訊息死掉，K 線靜默停止 | `LogAndContinue` 例外處理 + Streams 狀態納入 readiness。 |
| 故障注入端點被誤開在正式環境 | 只有 `chaos` profile 才註冊 bean；compose 預設與之後的 K8s 設定都不帶此 profile；有測試斷言預設 profile 下 `/actuator/feeds` 為 404。 |
| open.er-api.com 條款與限流 | 前端 attribution 連結；30 分鐘抓一次遠低於限制；429 時保留舊匯率。 |
| K 線「定稿」延遲：suppress 需等 window 結束 + grace 且有新事件推進 stream time | grace 只設 5 秒；來源全斷時最後一根 K 線會晚到，屬可接受（spec 無即時未完成 K 線需求）。 |
| 主備同時連線導致重複 tick | 只轉發 active 來源；切換瞬間可能有少量不同交易所價格並存，屬真實行為，記錄於 `source` 欄位。 |
| 多副本時重複 ingest | 目前單副本；`app.ingest.enabled` 開關預留給 K8s spec 決定（例如 ingest 獨立 1 副本 Deployment 或 leader election）。 |
| 網路擋 Coinbase/Kraken（公司網路、VPN） | 可經環境變數改 URL；Readme 註明。 |

## QA 意見與處理（cube-qa，2026-09-29，verdict: CONCERN）

| # | QA 意見 | 處理 |
|---|---|---|
| M1 | AC12 用 Testcontainers stop/start 會換 port，驗不到恢復 | **採納**：改用 docker `pause`/`unpause` + Awaitility（Testing Strategy） |
| M2 | compose 單一網路，`network disconnect` 會假陽性 | **採納**：拆成 `internal`（internal:true）/`egress`/`public` 三個網路，只斷 `egress` |
| M3b | 真實斷網是 half-open，client 不會發現斷線，主來源恢復後切不回 | **採納**：idle watchdog（訂閱 heartbeat，10 秒無任何訊息 → abort 重連）；假 WS server 的 half-open 測試案例；chaos 端點加 `mode=silent` |
| M3 | AC6 沒驗到「不重啟自動切回」；真實 WS client 重連無測試 | **採納目標、改用不同手段**：新增本機假 WS server 整合測試（斷線→退避重連→再收 tick）。手動驗收**不採用 toxiproxy**：Coinbase/Kraken 是 WSS，TCP proxy 會讓 TLS SNI/主機名稱驗證失敗，要繞過得關閉驗證或改 DNS，很脆弱。改為 `chaos` profile 才有的 Actuator `feeds` block/unblock 端點，讓真實 client 斷線並拒絕重連，解除後走真實重連流程。 |
| M4 | 歷史 API 10k 上限會讓 AC4 與走勢圖拿到 400 | **採納**：`/history` 改 keyset 分頁；新增 `/trend` 用 `date_bin` 伺服器端降採樣給走勢圖 |
| S1 | Kafka 斷線時 send 阻塞、Streams poison pill | **採納**：有界佇列 + publisher thread、`max.block.ms=5s`、丟最舊 + metric；`LogAndContinue`；Streams 狀態納入 readiness |
| S2 | open.er-api attribution、429 | **採納** |
| S3 | OHLC tie-break、late tick、Kraken snapshot/多筆 | **採納**：排序鍵 `(eventTime, receivedAt, eventId)`，`[start,end)`，dropped-records metric，`snapshot:false`，多筆 parser 測試 |
| S4 | toast 算不算已讀 | **採納並定義**：頁面可見時顯示 toast 即標記已讀；背景分頁不標記；另有「全部已讀」 |
| S5 | 時間測試邊界 | **採納**：邊界案例逐條列在 Testing Strategy |
| S6 | 刪除要迴圈到清空 | **採納** |
| S7 | Kafka volume、state.dir、AC7 實跑 5 分鐘、buildx builder、SSE offset latest | **全部採納** |
| C1–C3 | （Boot 3.5 覆審）Testcontainers ≥ 1.21.4、直接宣告 kafka-streams-test-utils、pom 不重複列 jdbc | **全部採納**，C1 在第一個 task 驗證 |
| AC13 | 解讀可接受，但需含 Ryuk、固定 tag、`-o verify` | **採納**；使用者已接受此解讀 |

## Non-Goals Reaffirmed

- 不做 K8s manifests / Helm、GitHub Actions CI/CD（只確保容器化、env 設定、探針已就緒）
- 不做使用者帳號、登入、權限；單一使用者
- 只支援 BTC（pair 欄位存在只是為了事件自描述，不做多幣種）
- 不做交易、下單、錢包等金流
- 不做 Email / 推播 / LINE 等站外通知
- 不保證舊 API 相容；`/api/bitcoin/price/original` 移除
- 不回補歷史資料
- 不做高可用 / 多 broker Kafka、不做 Schema Registry、不做 exactly-once（僅記為未來練習）
