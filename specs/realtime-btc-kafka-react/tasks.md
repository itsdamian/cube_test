# Tasks: cube_test 改造 — 即時 Bitcoin 價格串流（Kafka + React）

Status: CONFIRMED
Spec: `spec.md`（CONFIRMED）｜Plan: `plan.md`（CONFIRMED）｜Branch: `feat/realtime-btc-kafka-react`

規則：
- 每個 task 自帶測試，「done when」裡的指令要實際跑過才算完成；一個 task 一個 commit。
- `./mvnw verify` 指全部後端測試（Testcontainers 需要 Docker）；`npm test` 指前端測試（在 `frontend/`）。
- 只有 QA PASS 後才把 `[ ]` 勾成 `[x]`。
- **測試不得連外（需求 18）**：自 task 2 起，所有 `@SpringBootTest` 使用共用的 `test` profile（ingest 關閉、FX 排程關閉、所有外部 URL 指向 `ws://127.0.0.1:1` / `http://127.0.0.1:1`），並由守門測試把關（QA M5）。QA 驗收每個 task 時至少以斷網或 `./mvnw -o verify` 跑一次。
- **前後端契約（QA T3）**：後端 API 的回應樣本放在 `contracts/api-samples/*.json`，由後端 `ContractSamplesTest` 以實際序列化結果比對（不一致就失敗）；前端 MSW handler 直接讀這些檔案，不手寫假 JSON。

## 前置（使用者執行，工程師不自行安裝）

- [x] P1. 安裝 JDK 21：`brew install openjdk@21`，並依 brew 提示設定 `JAVA_HOME` — done when: `java -version` 顯示 21
- [x] P2. 開啟 Docker Desktop — done when: `docker info` 顯示 Server Version

## 後端基礎

- [x] 1. **建置基準與 repo 清理**：`git rm -r --cached target/`、新增 `.gitignore`（target/、node_modules/、frontend/dist/、IDE 檔）、加入 Maven Wrapper、pom 升級 Java 21 + Spring Boot 3.5.x（最新 patch）並加入 plan 列出的相依套件（Testcontainers、kafka-streams-test-utils、awaitility、Java-WebSocket 為 test scope） — done when: `git ls-files | grep -E '(^|/)target/'` 為空；`./mvnw -q dependency:tree` 顯示 testcontainers ≥ 1.21.4（不足則覆寫 `testcontainers.version`，QA C1）；`./mvnw verify` 通過（既有測試）
- [x] 2. **移除 Coindesk 與舊設定、建立設定骨架**：刪除 `CoindeskController/Service/Response`、`RestTemplateConfig` 與對應測試；`application.properties` → `application.yml`（`${ENV:default}`、移除 TRACE log、actuator 只暴露 `health,info`）；新增 `AppProperties`（`@ConfigurationProperties` record，含 feed/fx/retention/alert/feature 開關）與 `Clock` bean；`DemoApplication` 移除多餘 annotation 與 seed runner ；建立共用 `test` profile（`application-test.yml`：`app.ingest.enabled=false`、`app.fx.refresh-enabled=false`、外部 URL 指向 `127.0.0.1:1`），並在 pom 的 surefire / failsafe `systemPropertyVariables` 設 `spring.profiles.active=test` **全域啟用**（不靠各測試類別自己加 `@ActiveProfiles`，QA R1），與守門測試 `NoExternalCallsGuardTest` — done when: `./mvnw verify` 通過；`AppPropertiesTest` 驗證預設值與環境變數覆寫（例如 `APP_RETENTION_TICKS=PT5M` 綁定成 5 分鐘）；守門測試斷言「目前 active profile 含 `test`」且所有外部 URL 的 host 都是 `127.0.0.1`（未加任何 `@ActiveProfiles` 的測試類別也成立）（後續 task 6、14 擴充此守門測試，見下）
- [x] 3. **PostgreSQL + Flyway + 幣別模組**：移除 H2、加入 Postgres driver 與 Flyway；`V1__currency.sql`、`V2__seed_currencies.sql`（USD 美元、EUR 歐元、GBP 英鎊、TWD 新台幣、JPY 日圓）；幣別程式搬到 `currency/` package，改 DTO record + Bean Validation（3 個大寫字母）、404 / 409（重複 code）/ 204、`ProblemDetail`；建立共用的 Testcontainers 整合測試基底（`@ServiceConnection` Postgres） — done when: `./mvnw verify` 通過，其中整合測試斷言：乾淨 DB 啟動後 `GET /api/currencies` 恰好回傳 5 筆預設幣別與中文名稱（AC10）；CRUD 全流程 + 驗證錯誤 400 + 重複 409 + 不存在 404（AC11 後端）

## Kafka 與價格來源

- [x] 4. **Kafka 基礎與 TickPublisher**：`KafkaTopicsConfig`（4 個 topic，replication 可設定）、JSON serde、`PriceTick` record；`TickPublisher`（有界佇列 + 獨立 publisher thread、`max.block.ms=5s`、`delivery.timeout.ms=30s`、滿了丟最舊並累計 `feed.ticks.dropped`）；Testcontainers Kafka 加入整合測試基底 — done when: `./mvnw verify` 通過，含：整合測試 publish → consume 得到同一個 `PriceTick`（BigDecimal / Instant 不失真）；單元測試「Kafka 不可用時 `publish()` 在 10ms 內返回、佇列滿時丟最舊並計數」
- [x] 5. **Coinbase / Kraken 訊息 parser**：`CoinbaseMessageParser`（ticker、heartbeat、subscriptions）、`KrakenMessageParser`（v2 trade 一則多筆、heartbeat、status）；以真實訊息樣本當 fixture — done when: `./mvnw verify` 通過，含：Kraken 一則 3 筆成交 → 3 個 tick；heartbeat 被辨識為「有訊息但非 tick」；格式錯誤訊息回傳空結果且不丟例外
- [x] 6. **WebSocketPriceFeedClient**：JDK `java.net.http.WebSocket`、訂閱訊息（Coinbase `ticker` + `heartbeats`；Kraken `trade` + `snapshot:false`）、指數退避重連（1s→30s，成功後歸零）、**idle watchdog**（`idle-timeout` 內無任何訊息 → `abort()` 重連，QA M3b）、`block()` / `block(silent)` / `unblock()` 掛點 — done when: `./mvnw verify` 通過，含對本機假 WS server 的整合測試：(a) server 關閉連線 → 重連 → 再收到 tick；(b) server 保持連線但停送訊息 → idle-timeout（測試設 1 秒）後 abort 並重連 → 恢復後收到 tick；(c) block → 斷線且重連失敗、silent → 連線在但不產生 tick、unblock → 恢復收 tick；`NoExternalCallsGuardTest` 擴充：test profile 的 context 中不存在已啟動的真實 `WebSocketPriceFeedClient`（QA M5）
- [x] 7. **FeedManager 故障切換與 FeedStatus**：狀態機（plan Approach 第 2 點的規則）、只轉發 active 來源的 tick 給 `TickPublisher`、每 5 秒發佈 `FeedStatus` 到 `btc.feed.status`、`app.ingest.enabled` 開關 — done when: `./mvnw verify` 通過，單元測試用可變 `Clock` 直接呼叫 `check()` 驗證：剛好 10 秒 stale / 9.999 秒不 stale；**主來源最後一筆 tick（或最後一則任何訊息）之後 ≤ 11 秒內**切到備援（10 秒門檻 + 1 秒檢查週期；AC6 30 秒預算，QA T1）；狀態或 active 來源改變時**立即**發佈 FeedStatus（不等 5 秒週期）；主恢復滿 15 秒切回、recovery 中又 stale 則歸零；兩者皆 stale → `DISCONNECTED`，備援先恢復 → 用備援，主再健康 15 秒 → 切回；非 active 來源的 tick 不轉發
- [x] 8. **chaos 故障注入端點**：Actuator `feeds` endpoint（GET 狀態、POST `{source}/block?mode=silent`、`{source}/unblock`），僅 `chaos` profile 註冊 — done when: `./mvnw verify` 通過，含：預設 profile 下 `/actuator/feeds` 回 404；`chaos` profile 下 block coinbase（假 client）→ FeedManager 切到 kraken → unblock → 切回（可控 Clock）

## 資料落地、K 線、查詢

- [x] 9. **逐筆價格落地**：`V3__price_tick.sql`（`event_id` unique、`(pair, event_time)` index）、`TickPersister`（batch listener + `JdbcTemplate.batchUpdate` + `ON CONFLICT DO NOTHING`）、`app.persist.enabled` — done when: `./mvnw verify` 通過，整合測試：送 500 筆（含 20 筆重複 eventId）到 Kafka → DB 恰好 480 筆、欄位值一致
- [x] 10. **價格查詢 API**：`/api/prices/latest`、`/api/prices/history`（keyset 分頁，`limit` 預設 1,000 上限 5,000、`nextCursor`）、`/api/prices/trend`（`date_bin` 降採樣，`points` 預設 300 上限 1,000，排序鍵 `(event_time, received_at, event_id)`） — done when: `./mvnw verify` 通過，整合測試：12,000 筆 10 分鐘資料以分頁取完恰好 12,000 筆、無重複無遺漏（QA M4）；`from > to`、`limit > 5000` 回 400；trend 回傳 ≤ points 筆且每筆為該 bucket 最後一筆價格；`contracts/api-samples/` 新增 latest / history / trend 樣本並由 `ContractSamplesTest` 比對
- [x] 11. **Kafka Streams K 線 topology**：`CandleTopology`（事件時間 extractor、1m/5m tumbling、grace 5s 可設定、`suppress(untilWindowCloses)`、排序鍵 tie-break、`LogAndContinue`、`state.dir` 可設定、`app.streams.enabled`） — done when: `./mvnw verify` 通過，`TopologyTestDriver` 測試：record timestamp 刻意與 `eventTime` 不同；12 分鐘合成 tick → 推進 stream time 後輸出 ≥ 10 根 1m、≥ 2 根 5m，OHLC 等於同批 tick 依排序鍵手算的值；亂序、同 `eventTime` tie-break、剛好 `12:01:00.000` 的 tick 歸 12:01 那根、超過 grace 的 tick 不計入、壞訊息被略過且後續仍正常輸出
- [x] 12. **K 線落地與查詢 API**：`V4__candle.sql`、`CandlePersister`（upsert）、`GET /api/candles?interval&from&to` — done when: `./mvnw verify` 通過，端到端整合測試（Kafka + Streams + Postgres）：送入 12 分鐘事件時間的 tick → DB 有 ≥ 10 根 1m、≥ 2 根 5m，且每根 OHLC 與 `price_tick` 同時段依排序鍵 SQL 查詢結果一致（AC4）；關閉並重建 Spring context（同一 Postgres container）後 tick 與 candle 仍查得到（AC5）；candles 契約樣本
- [x] 13. **逐筆保留期清除**：`RetentionJob`（執行間隔 `APP_RETENTION_INTERVAL` 預設 `PT1H`、每批 10,000 筆迴圈刪到清空、可控 `Clock`、保留期 `APP_RETENTION_TICKS`） — done when: `./mvnw verify` 通過，整合測試：25,000 筆過期 + 100 筆未過期 + 剛好等於邊界的資料 → 一次執行後只剩未過期與邊界資料；candle 筆數不變（AC9、QA S6）；另一個整合測試設 `APP_RETENTION_INTERVAL=PT1S`，不手動呼叫 job，Awaitility 10 秒內觀察到過期資料被排程自動刪除（QA M6）

## 匯率、警示、推播、健康檢查

- [x] 14. **匯率與多幣別換算**：`V5__fx_rate.sql`、`ExchangeRateClient`（`RestClient`，URL 可設定）、`FxRateRefresher`（每 30 分鐘，可設定；429/5xx/timeout 保留舊值 + warn）、`ConversionService`、`GET /api/prices/converted`（含 `rate`、`rateUpdatedAt`，無匯率的幣別 `rate: null`） — done when: `./mvnw verify` 通過，含：`MockRestServiceServer` 以 open.er-api 真實回應樣本解析；429 後舊匯率保留；換算值與「USD 價 × 匯率」誤差 < 0.01%（遠低於 AC3 的 0.5%）；`provider_updated_at` 正確映射為 `rateUpdatedAt`；`NoExternalCallsGuardTest` 擴充：test profile 下 FX 排程未啟動；converted 契約樣本
- [x] 15. **價格警示與未讀紀錄**：`V6__alerts.sql`、`/api/alerts`（GET/POST/DELETE）、`AlertEvaluator`（共用 group、cooldown 以事件時間、`APP_ALERT_COOLDOWN`）、`alert_event` 寫入 + 發佈 `btc.alerts.triggered`、`/api/alert-events?unread=`、`/{id}/read`、`/read-all`、`app.alerts.enabled` — done when: `./mvnw verify` 通過，單元測試：ABOVE 剛好等於門檻不觸發、越過觸發；4:59.999 內不再觸發、剛好 5:00 且仍成立再觸發、冷卻後不成立不觸發；整合測試：沒有任何 SSE 連線時觸發 → `GET /api/alert-events?unread=true` 可查到，read 後消失；刪除警示連帶刪除紀錄（AC7、AC8 後端）；alerts / alert-events 契約樣本
- [x] 16. **SSE 即時推播**：`StreamController`（`/api/stream`）、`SseBroadcaster`（每實例唯一 group id + `auto.offset.reset=latest`、price 每連線 250ms 節流、15 秒 heartbeat、斷線清理） — done when: `./mvnw verify` 通過，整合測試：以 HTTP client 連 `/api/stream`，送 tick / FeedStatus / AlertTriggered 到 Kafka 後 5 秒內依序收到 `price`、`status`、`alert` 事件；1 秒內送 50 筆 tick 最多收到 5 筆 price，且**最後收到的 price 一定是第 50 筆**（節流不丟最後一筆，QA T4）；client 斷線後 emitter 被移除；SSE 三種事件 payload 契約樣本；幣別 API 契約樣本也在此補齊
- [x] 17. **健康檢查與 readiness**：probes 啟用、`KafkaHealthIndicator`（`describeCluster`，3 秒 timeout）、`KafkaStreamsHealthIndicator`、readiness group = `readinessState,db,kafka,kafkaStreams`、liveness 只含 `livenessState` — done when: `./mvnw verify` 通過，整合測試：正常時 readiness 200；docker `pause` Kafka container → 10 秒內 readiness 503、liveness 仍 200；`unpause` → Awaitility 在 60 秒內等到 readiness 200（AC12，QA M1）；Streams 進入 ERROR 時 readiness 503

- [x] 18. **後端全鏈路整合測試**（QA T2）：啟用 ingest 但以**假 `PriceFeedClient`**（主/備各一，由測試控制）取代真實 client，Testcontainers Kafka + Postgres，Streams / persist / alerts / SSE 全開 — done when: `./mvnw verify` 通過，單一測試驗證：假主來源送出 12 分鐘事件時間的 tick → `price_tick` 有資料、`candle` 有 ≥ 10 根 1m 與 ≥ 2 根 5m、預先建立的警示被觸發並寫入 `alert_event`、SSE client 收到 `price` / `alert`；接著讓假主來源停送 → SSE 收到 `status`（activeSource=備援）→ 恢復 → 切回主來源

## 容器化（後端）

- [x] 19. **多架構後端 Dockerfile**：單一 `Dockerfile`（builder `--platform=$BUILDPLATFORM` + mvnw、runtime `eclipse-temurin:21-jre`、非 root），刪除 `Dockerfile.amd64`、`Dockerfile.silicon`、`start-silicon.sh`、`stop-silicon.sh` — done when: `docker buildx build --platform linux/amd64,linux/arm64 .` 成功（AC14）；`docker run` 該 image 以 `id -u` 非 0 執行

## 前端

- [x] 20. **前端骨架**：`frontend/` Vite + React + TypeScript、Vitest + React Testing Library + MSW、型別化 API client、繁中版面骨架 — done when: `cd frontend && npm ci && npm test && npm run build` 全過；`git ls-files | grep -E '(^|/)(node_modules|dist)/'` 為空（AC15 前端部分，QA T5）；MSW handler 讀取 `contracts/api-samples/`
- [x] 21. **即時價格與連線狀態**：`useEventSource` hook（SSE、自動重連、15 秒無事件 → 延遲）、價格卡（價格、來源、狀態燈、最後更新時間） — done when: `npm test` 通過，含：假 EventSource 送 `price` → 畫面更新（AC1）；`status` = STALE/DISCONNECTED 或 15 秒（fake timers）無**任何** SSE 事件（price、status、heartbeat 都算，不能只看 price；冷清時段 price 會暫停但 status 每 5 秒仍會送）→ 顯示「資料延遲／已斷線」（AC2）；`status.activeSource` = kraken → 顯示目前來源 Kraken（AC6）
- [x] 22. **多幣別換算表**：中文名、價格、匯率、匯率更新時間、無匯率顯示「無匯率」、「Rates By Exchange Rate API」attribution 連結 — done when: `npm test` 通過（MSW 模擬 `/api/prices/converted`），斷言上述欄位與連結存在（AC3 前端）
- [x] 23. **K 線與走勢圖**：lightweight-charts；1m / 5m 切換呼叫 `/api/candles`；走勢線用 `/api/prices/trend` 並以 SSE 價格延伸 — done when: `npm test` 通過，斷言切換 interval 時以正確參數呼叫 API，資料轉換函式（API → chart series）有單元測試（需求 11）
- [x] 24. **幣別管理介面**：列表、新增、編輯、刪除、驗證與 409 錯誤訊息 — done when: `npm test` 通過（MSW），斷言新增/編輯/刪除後列表更新並呼叫正確 API、重複代碼顯示錯誤（AC11 前端）
- [x] 25. **警示管理與通知**：新增（高於/低於）、刪除、未讀警示清單（逐筆/全部標記已讀）、SSE `alert` → toast；頁面可見時顯示 toast 即呼叫 read，背景分頁不標記、切回可見後才標記（QA S4） — done when: `npm test` 通過，含：載入時顯示 `/api/alert-events?unread=true` 的未讀清單（AC8）；收到 `alert` 事件出現 toast（AC7）；visible → 呼叫 read、hidden → 不呼叫、切回 visible → 呼叫

## 整合與驗收

- [x] 26. **前端容器與完整 docker compose**：`frontend/Dockerfile`（node build → nginx）、`nginx.conf`（`/api` 反向代理、SSE `proxy_buffering off`）；`docker-compose.yml`：kafka / postgres / backend / frontend，固定 image tag、具名 volume（kafka、postgres、streams state）、healthcheck + `depends_on: service_healthy`、`internal`(internal:true) / `egress` / `public` 三個網路；`docker-compose.chaos.yml` — done when: 乾淨環境 `docker compose up -d` 後所有服務 healthy；`http://localhost:3000` 30 秒內看到持續跳動的 BTC-USD 價格（AC1 smoke）；網路切分正確（QA T6）：`docker run --rm --network <project>_internal busybox:1.37 nc -z -w 5 1.1.1.1 443` **失敗**，同指令改用 `<project>_egress` 網路**成功**；且 backend 容器 `docker compose exec backend` 可解析並連到 `kafka:9092`
- [x] 27. **README 與驗收步驟**（task 30 起：操作細節移至 `docs/`）：啟動方式、環境變數表、離線測試準備（`dependency:go-offline`、`npm ci`、固定 tag 的 kafka/postgres/ryuk image 清單）、buildx 多平台 builder、每條手動 AC 的逐步指令（含 AC4 對照 SQL、AC2 egress disconnect、AC6 chaos block/unblock 與錯誤 URL、AC7 30 秒與預設 5 分鐘、AC9 縮短保留期） — done when: QA 能只照 README 完成 task 28 的所有手動步驟，不需問工程師
- [ ] 28. **完整驗收**：照 [docs/acceptance.md](../../docs/acceptance.md)（原 README 第 6 節，task 30 搬移）在 compose 環境逐條執行 AC1–AC15，並在斷網狀態執行 `./mvnw -o verify` 與 `npm test`（AC13），把每條結果與證據（指令輸出、截圖）記入 progress.md — done when: 15 條 AC 全部有證據且通過；未通過的已修正並重新驗證

## 範圍擴充（team lead 依使用者授權核准，2026-09-30）

- [x] 29. **前端視覺美化**：純 CSS + CSS 變數（design tokens），不引入 UI 框架；深色為主的金融儀表板風格並支援淺色（prefers-color-scheme），圖表配色跟隨主題；header（標題 + 狀態 pill + 目前來源）、桌機兩欄（左：價格卡 + 圖表，右：換算 + 警示）、幣別管理在下方、375px 單欄且無水平捲動；價格漲跌綠/紅並短暫閃爍（尊重 prefers-reduced-motion，只用現有資料）；延遲琥珀色/斷線紅色並淡化價格；toast 右上角；1m/5m/走勢為 segmented control；按鈕/輸入框/表格統一、危險操作紅色；WCAG AA 對比、清楚的 focus ring、保留所有 aria/role；不改行為或 API — done when: 上述完成、`npm test`/`build`/`lint` 全過（行為斷言不變）、localhost:3001 前端容器已重建，交 QA 並通知 team lead 目視審查
- [ ] 30. **README 改版（作品門面）**：README 改為給 GitHub 讀者的作品介紹（一句話介紹＋截圖、功能亮點、mermaid 架構圖、技術棧、技術重點、10 行內快速開始、精簡開發與測試、spec-driven＋雙 agent 開發方式、Roadmap）；原第 3–7 節（環境變數、API、離線測試、buildx、AC 驗收步驟、常見問題）原文搬到 `docs/configuration.md`、`docs/api.md`、`docs/testing.md`、`docs/acceptance.md`，修正所有互相連結；截圖由 team lead 以 Chrome 擷取後放入 `docs/images/` — done when: README 在 GitHub 正常顯示（含 mermaid）、`python3 scripts/check_md_links.py` 通過（截圖到位前以 `--allow-missing-images` 標示待補）、QA 能照 docs/acceptance.md 重跑任一條 AC
- [ ] 31. **依設計方向 B 重新設計前端**（設計：`design/redesign.md` §7–§9、`direction-b.html`）：B 的 design tokens 與元件；nav + 12 欄 grid（hero = 價格＋圖表、側欄警示、全寬換算 tiles）；價格大字不變色、方向 pill 閃爍（節流規則不變）；「開啟後」漲跌、警示「還差 ±X」、圖表區間高低＋滑鼠 OHLC legend（team lead 核准）、填入目前價格、skeleton；幣別管理改原生 `<dialog>`；警示表單可收合；未讀為空時不顯示；USD 固定第一；圖表 `attributionLogo:false`、footer 註明 TradingView 並連結；不改 SSE、狀態計算、閃爍規則與 API — done when: `npm test`/`build`/`lint` 全過且測試數不減少；375/768/1440 無水平捲動；以 screenshot.mjs 對照 direction-b-*.png（深淺色）；`<dialog>` Esc 關閉且焦點回到觸發按鈕；localhost:3001 已重建；README 三張最終截圖入庫、`check_md_links.py`（不加 `--allow-missing-images`）通過；交 QA 並通知 team lead 審查

## Acceptance Criteria 對照

| AC | 對應 task（自動測試 → 手動驗收） |
|---|---|
| AC1 compose 起來 30 秒內看到跳動價格 | 4, 6, 7, 16, 18, 21 → 26, 28 |
| AC2 斷網 30 秒內顯示斷線，恢復自動更新 | 6(idle watchdog), 7, 21 → 26(網路切分), 28 |
| AC3 換算誤差 < 0.5%、顯示匯率時間 | 14, 22 → 28 |
| AC4 10 分鐘後有歷史、≥10 根 1m、≥2 根 5m、OHLC 一致 | 9, 10, 11, 12, 18 → 28 |
| AC5 重啟後資料仍在 | 12 → 26(具名 volume), 28 |
| AC6 主來源封鎖 30 秒內切備援、恢復切回 | 6, 7, 8, 18, 21 → 26(chaos override), 28 |
| AC7 警示通知 + 5 分鐘冷卻 | 15, 16, 18, 25 → 28 |
| AC8 關閉前端時觸發的警示重開顯示未讀 | 15, 25 → 28 |
| AC9 超過保留期自動清除、K 線不受影響 | 13 → 28 |
| AC10 全新 DB 有 5 個預設幣別 | 3 → 28 |
| AC11 前端幣別 CRUD、重新整理仍在 | 3, 24 → 28 |
| AC12 停 Kafka readiness 失敗、恢復自動恢復 | 17 → 28 |
| AC13 離線、無預啟 Kafka 下測試全過 | 1–25 全部測試皆不連外 → 27(準備清單), 28 |
| AC14 buildx 多架構 | 19 → 28 |
| AC15 repo 無建置產物 | 1, 20 → 28 |
