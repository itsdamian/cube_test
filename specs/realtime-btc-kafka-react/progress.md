# Progress Log — realtime-btc-kafka-react

Owner: cube-java-engineer（此檔由工程師維護；QA 請寫在 `qa-review.md`）
Branch: `feat/realtime-btc-kafka-react`

---

## 2026-09-29 13:53 — Stage: plan（草稿送 QA 審查）
- **What changed**: 從 `main`@`9665ca1` 建立分支 `feat/realtime-btc-kafka-react`；新增 `specs/realtime-btc-kafka-react/plan.md`（Status: DRAFT）。尚未 commit（plan 還沒經使用者確認）。
- **Commit**: 無
- **重點決策**：模組化單體 Spring Boot 4.1.x / Java 21；主來源 Coinbase WS、備援 Kraken WS（hot standby）；匯率 open.er-api.com；Kafka Streams 事件時間 1m/5m K 線；SSE 推播；PostgreSQL + Flyway；Testcontainers + TopologyTestDriver；Vite + React + TS + lightweight-charts。
- **請 QA 檢查**：
  1. 每條 AC 是否都有可執行的驗證方式（plan〈Acceptance Criteria 驗證方式〉一節）
  2. 有無 scope drift（違反 Non-Goals）或與 spec 矛盾
  3. 可測試性：測試是否能在不連外部來源、不預啟 Kafka 的情況下跑
  4. AC13 對「沒有網路連外」的解讀（Testcontainers + 預拉 image）是否可接受
  5. 故障切換、冷卻、保留期這些依賴時間的邏輯，測試設計是否足夠

## 2026-09-29 13:58 — Stage: plan（依 QA 意見修訂）
- **What changed**: plan.md 依 qa-review.md「plan.md DRAFT 審查」修訂：M1（pause/unpause）、M2（internal/egress/public 網路）、M3（假 WS server 整合測試 + chaos profile 的 feeds block/unblock 端點，未採用 toxiproxy，理由見 plan〈QA 意見與處理〉）、M4（keyset 分頁 + date_bin 降採樣 /trend）、S1–S7 全部採納。新增〈QA 意見與處理〉一節。
- **Commit**: 無（待使用者確認 plan）
- **請 QA 檢查**: M3 的替代手段（chaos 端點取代 toxiproxy）是否可接受；其餘修訂是否已解決你的 findings。

## 2026-09-29 13:59 — Stage: plan（M3b）
- **What changed**: plan.md 補上 QA 覆審的 M3b：WS client idle watchdog（訂閱 heartbeat，`APP_FEED_IDLE_TIMEOUT` 預設 10 秒無任何訊息 → abort 並重連）；假 WS server half-open 測試案例；chaos 端點 `mode=silent`。〈QA 意見與處理〉已加 M3b 一列。
- **Commit**: 無（待使用者確認 plan）
- **請 QA 檢查**: tasks.md 階段確認 M3b 三項都有對應 task。

## 2026-09-29 14:05 — Stage: plan（版本變更：Spring Boot 3.5.x、AC13 解讀）
- **What changed**: 依 personal-workplace-7a 轉達的使用者意見修訂 plan.md：Spring Boot 4.1.x → 3.5.x（Alternatives、pom 相依清單、版本基準、Risks 改為「3.5 已 EOL」並以避開 deprecated API 緩解）；AC13 解讀寫為已確認。**注意**：此意見是 peer 轉達，plan 仍是 DRAFT，要等使用者在工程師分頁親自確認才會改 CONFIRMED。
- **Commit**: 無
- **請 QA 檢查**: 版本變更是否一致（Boot 3.5 / spring-kafka 3.3 / kafka-streams 3.9 / Testcontainers 1.x / flyway-database-postgresql / apache/kafka 3.9 broker），plan 中是否還有只屬於 4.x 的寫法。

## 2026-09-29 14:06 — Stage: plan（Boot 3.5 覆審 C1–C3）
- **What changed**: plan.md pom 清單：Testcontainers 需 ≥ 1.21.4（第一個 task 以 dependency:tree 驗證，必要時覆寫 testcontainers.version）；改宣告 kafka-streams-test-utils 取代 spring-kafka-test；移除重複的 starter-jdbc。〈QA 意見與處理〉加 C1–C3 一列。
- **Commit**: 無（等使用者親自確認 plan）

## 2026-09-29 14:13 — Stage: plan CONFIRMED
- **What changed**: 使用者在工程師分頁親自回覆：同意 plan（含 Spring Boot 3.5.x）與 AC6 chaos 端點做法。plan.md → Status: CONFIRMED。
- **授權變更（使用者親自指示）**: 自此 plan / tasks / guardrail checkpoint 由 team lead session `personal-workplace-7a` 代為核准，其決定等同使用者決定。安裝系統軟體、push、開 PR、動 main 仍須問使用者本人。
- **Commit**: 見下一筆 git log（spec.md、plan.md、progress.md）
- **下一步**: /tasks realtime-btc-kafka-react

## 2026-09-29 14:14 — Stage: tasks（草稿送 QA 審查）
- **What changed**: 新增 tasks.md（DRAFT）：前置 P1–P2（使用者安裝 JDK 21、開 Docker）＋ 27 個 task，末尾附 AC→task 對照表。
- **Commit**: 無（待 QA 審查與核准）
- **請 QA 檢查**: 每條 AC 至少對應一個 task；測試隨功能同 task、未延後到最後；每個 done-when 可實際驗證（含 AC6 在 11 秒內切換的時間預算、M1/M3b/M4/S4/S6 與 C1 的落實）；task 大小與相依順序。

## 2026-09-29 14:16 — Stage: tasks（依 QA 意見修訂）
- **What changed**: M5（task 2 建共用 test profile + `NoExternalCallsGuardTest`，task 6/14 擴充）、M6（`APP_RETENTION_INTERVAL` + 排程自動刪除測試）、T1（≤11 秒自最後一則主來源訊息起算、狀態改變立即發佈 FeedStatus）、T2（新增 task 18 全鏈路整合測試，原 18–27 順延為 19–28）、T3（`contracts/api-samples/` + `ContractSamplesTest`，MSW 讀同一份樣本）、T4、T5、T6 全部採納。
- **Commit**: 無（待 QA 覆審與核准）

## 2026-09-29 14:17 — Stage: tasks（QA 覆審 PASS + R1/R2）
- **What changed**: QA 覆審 tasks.md PASS。採納 R1（surefire/failsafe 全域 `spring.profiles.active=test`，守門測試斷言 active profile）與 R2（task 26 改用 `busybox nc -z -w 5 1.1.1.1 443`）。送 personal-workplace-7a 依使用者授權核准。
- **Commit**: 無（待核准）

## 2026-09-29 15:18 — Stage: tasks CONFIRMED + 前置完成
- **What changed**: personal-workplace-7a 依使用者授權核准 tasks.md → Status: CONFIRMED。工程師自行驗證前置：`java -version` = 21.0.12.1、`docker info` Server Version = 29.8.0 → P1/P2 勾選。
- **Team lead 提醒**: (1) task 1 升級後若既有 Coindesk 測試失敗，不修，留給 task 2 刪除，並於 commit message 說明（先與 QA 對齊）；(2) 每個 task 的 commit message / progress 條目用 2–3 句說明關鍵概念。QA PASS 即繼續下一個 task。
- **下一步**: /implement，從 task 1 開始。

## 2026-09-29 15:21 — Stage: implement task 1（建置基準與 repo 清理）
- **What changed**: pom → Java 21 + Spring Boot 3.5.16，加入 plan 的相依套件（H2 暫留到 task 3）；Maven Wrapper 3.3.4（Maven 3.9.16）；`.gitignore`；`git rm --cached target/`。
- **Commit**: `ae5cb0f`
- **Verified**: `./mvnw verify` → 11 tests / 0 failures（Coindesk 測試照樣通過，未使用例外）；`dependency:tree` → testcontainers 1.21.4、spring-kafka 3.3.16、kafka 3.9.2、flyway 11.7.2；`git ls-files | grep -cE '(^|/)target/'` → 0。
- **概念**: Boot parent POM 是 BOM，統一管理相容版本，所以相依套件不用寫版本；mvnw 讓任何機器（含 CI）都用同一版 Maven。
- **請 QA 檢查**: 在 ae5cb0f 執行 `./mvnw verify`、`./mvnw -q dependency:tree -Dincludes=org.testcontainers`、`git ls-files | grep -E '(^|/)target/'`（應為空）。

## 2026-09-29 15:27 — Stage: implement task 2（移除 Coindesk、設定骨架、測試守門）
- **What changed**: 刪除 Coindesk 全部程式與測試、RestTemplateConfig；application.yml（`${ENV:default}`、actuator 只暴露 health,info、open-in-view=false）；`AppProperties` record + `ClockConfig`；DemoApplication 精簡；共用 test profile 由 surefire 全域啟用；`NoExternalCallsGuardTest`、`AppPropertiesTest`；Mockito javaagent；wrapper 加 `distributionSha256Sum`（QA task 1 CONCERN 2）。task 1 已依 QA PASS 勾 [x]。
- **Commit**: `825804f`
- **Verified**: `./mvnw verify` → 9 tests / 0 failures；test log 無任何外部 host；反向驗證：surefire 改成 default profile 時守門測試 3/3 失敗。
- **概念**: `@ConfigurationProperties` 把整段設定綁成型別化物件，打錯字在啟動時就失敗；Spring 自動 env 綁定會把 `stale-threshold` 對到 `APP_FEED_STALETHRESHOLD`，所以 yml 明寫 `${APP_FEED_STALE_THRESHOLD:10s}` 保留文件上的變數名稱。
- **注意**: 主程式啟動時暫時沒有預設幣別（seed runner 已移除，task 3 改由 Flyway 建立）。

## 2026-09-29 15:31 — Stage: implement task 3（PostgreSQL + Flyway + 幣別模組）
- **What changed**: 移除 H2、改 PostgreSQL + Flyway（`V1__currency.sql`、`V2__seed_currencies.sql`），`ddl-auto=validate`；幣別搬到 `currency` package（record DTO、Bean Validation、201/204/404/409）；`ApiExceptionHandler`（ProblemDetail）；測試基底 `IntegrationTest`（singleton Postgres container `postgres:17.11-alpine` + `@ServiceConnection`）。task 2 已依 QA PASS 勾 [x]。
- **Commit**: `4e65235`
- **Verified**: `./mvnw clean verify` → 13 tests / 0 failures，無 WARN、無外部 host。AC10：`CurrencySeedTest` 斷言乾淨 DB 恰好 5 筆預設幣別與中文名稱；AC11 後端：`CurrencyApiTest` 涵蓋 CRUD、400×5、404（GET/PUT/DELETE）、409（新增與修改）。
- **踩到的坑**: 未 `clean` 時 `target/test-classes` 殘留 task 2 已刪的 `application-test.properties`（create-drop + H2Dialect），會把 Flyway 建的表 drop 掉。之後一律 `clean verify`。
- **概念**: Flyway 每個 `V<n>__*.sql` 只執行一次並記錄在 `flyway_schema_history`，schema 跟著程式碼版本化；singleton container 讓整個測試 JVM 共用一個 Postgres，`@ServiceConnection` 自動把連線資訊交給 Spring Boot。
- **QA CONCERN 追蹤**: (3) 本專案不使用 failsafe，所有測試走 surefire；(4) health `show-details` 於 task 17 處理。

## 2026-09-29 15:37 — Stage: implement task 4（Kafka 基礎與 TickPublisher）+ task 3 follow-up
- **What changed (task 4, `7c565b2`)**: `Topics` + `KafkaConfig`（4 個 topic、status 為 compact、JSON 值用 Boot ObjectMapper 且無 type header、producer idempotent / acks=all / max.block.ms=5s / delivery.timeout.ms=30s）；`PriceTick` record；`TickPublisher`（有界佇列、獨立 publisher thread、滿了丟最舊並累計 `feed.ticks.dropped`、錯誤 log 節流）；測試基底加入 singleton `apache/kafka:3.9.2`。task 3 已依 QA PASS 勾 [x]。
- **Follow-up (`68dba77`)**: QA CONCERN 5 — 驗證失敗的 ProblemDetail 加上 `errors:[{field,message}]`。
- **Verified**: `./mvnw clean verify` → 19 tests / 0 failures，無外部 host。TickPublisherTest：send() 卡住時每次 publish() < 10ms、容量 3 的佇列 8 筆中保留最新 3 筆、dropped=5；RoundTrip：topic 設定正確、JSON 為 `"price":67123.45000000` 與 ISO eventTime、無 `__TypeId__`、反序列化後 equals。
- **踩到的坑**: `@ServiceConnection` 只套用到 Spring 建立的 Kafka factory；直接用 `KafkaProperties` 會拿到 yml 的 localhost:9092。測試改用 `KafkaAdmin` / `ConsumerFactory` bean。
- **概念**: record key 決定 partition，順序只在同一 partition 內保證，所以 tick 一律以 `BTC-USD` 為 key；compacted topic 每個 key 只保留最新值，適合「目前狀態」；有界佇列是典型的 back-pressure 取捨（丟棄／阻塞／緩衝），這裡選丟最舊。
- **QA CONCERN 追蹤**: (6) Currency 時間戳改用 Clock — 暫不處理（JPA callback 無法注入，影響小）；(7) task 12 的 AC5 測試不加 @Transactional — 已記下。

## 2026-09-29 15:40 — Stage: implement task 5（Coinbase / Kraken parser）
- **What changed**: `FeedMessage`（sealed interface）、`FeedMessageParser`、`CoinbaseMessageParser`、`KrakenMessageParser`；真實訊息 fixture（2026-09-29 擷取）；QA CONCERN 8（test profile 的 Kafka / datasource 指向 127.0.0.1:1 + 守門斷言）、CONCERN 9（InOrder）。task 4 已依 QA PASS 勾 [x]。
- **Commit**: `2b13d20`
- **Verified**: `./mvnw clean verify` → 50 tests / 0 failures，無外部 host。
- **Plan 細節更正（非範圍變更）**: Coinbase Exchange feed 的 heartbeat 頻道名稱是 `heartbeat`（單數），plan 寫的 `heartbeats` 屬於 Advanced Trade API，實測會被拒絕（fixture `coinbase/error.json`）。兩家 heartbeat 皆約每秒一則，10 秒 idle-timeout 足夠。
- **踩到的坑**: Jackson tree model 預設會去掉 BigDecimal 尾端的 0（84035.0 → 84035）；已關閉 `STRIP_TRAILING_BIGDECIMAL_ZEROES`。
- **概念**: sealed interface + record 表達「結果是這幾種之一」，switch pattern matching 由編譯器檢查是否涵蓋所有情況；金額經過 double 會悄悄改變位數，JSON 數字一律讀成 BigDecimal。

## 2026-09-29 15:44 — Stage: implement task 6（WebSocketPriceFeedClient）
- **What changed**: `PriceFeedClient` 介面、`TickListener`、`WebSocketPriceFeedClient`（指數退避、idle watchdog、連線 generation、BlockMode DISCONNECT/SILENT）；測試用 `FakeExchangeServer`（Java-WebSocket，127.0.0.1 隨機 port）；QA CONCERN 10：eventId 改為 `nameUUIDFromBytes(source:trade_id)`；守門測試加上「沒有 PriceFeedClient 在跑」。Spring 接線在 task 7。task 5 已依 QA PASS 勾 [x]。
- **Commit**: `b50aba1`
- **Verified**: `./mvnw clean verify` → 59 tests / 0 failures，無外部 host；WebSocket 測試連跑 5 次全過。
- **設計決定**: 退避歸零時機改為「新連線收到第一則有效訊息」而非「連上」，避免 server 接受後立刻斷線時以最短間隔狂連。
- **概念**: TCP 沒有流量時分不出「安靜」和「已死」，所以協定有 heartbeat、client 有 idle timeout；指數退避讓重試逐漸拉開、並設上限讓恢復仍然快。

## 2026-09-29 15:48 — Stage: implement task 7（FeedManager 故障切換與 FeedStatus）
- **What changed**: `FeedManager`（hot standby、每秒 check、切換/切回規則、LIVE/STALE/DISCONNECTED）、`FeedStatus`、`FeedStatusPublisher`（獨立 thread、只留最新一筆）、`FeedConfig` 在 `app.ingest.enabled=true` 時接線；測試 `MutableClock`、`FakeFeedClient`、`FeedManagerTest`（9）、`FeedWiringTest`（真實接線 + 本機假交易所）；守門測試斷言 test profile 無 FeedManager bean。
- **Commit**: `2052a70`
- **Verified**: `./mvnw clean verify` → 69 tests / 0 failures，無外部 host。主來源最後一筆 tick 在非整秒時刻（+0.3s），≤ 11 秒內切換，且同一次 check 就發佈新狀態（QA T1）；切回恰好在恢復後 15 秒。
- **狀態定義**: STALE = 無新鮮價格但仍有連線（前端「資料延遲」）；DISCONNECTED = 完全無連線（「已斷線」）。
- **概念**: 備援保持「熱」連線，用多一條 socket 換取快速、可預測的切換；所有規則都透過注入的 Clock 取時間，讓時間相關的狀態機變成確定性的單元測試。
- **仍待 QA**: task 6（b50aba1）。

## 2026-09-29 15:51 — Stage: implement task 8（chaos 故障注入端點）
- **What changed**: `FeedsChaosEndpoint`（`@Profile("chaos")`，GET /actuator/feeds、POST /{source}/block[?mode=silent]、/{source}/unblock；未知 source 404、未知 action/mode 400）；`application-chaos.yml` 開放 feeds 端點。
- **Commit**: `253c111`
- **Verified**: `./mvnw clean verify` → 73 tests / 0 failures，無外部 host。預設 profile：bean 不存在、端點 404；chaos profile：block → kraken、unblock 後 +14s 仍 kraken、+15s 切回 coinbase；silent 亦同。
- **注意（README / task 27）**: Actuator 寫入操作要帶 `Content-Type: application/json`，否則 415。手動指令：`curl -X POST -H 'Content-Type: application/json' localhost:8080/actuator/feeds/coinbase/block`。
- **概念**: profile 讓同一個 jar / image 帶著「需明確開啟」的行為；不開就連 bean 都不存在，環境之間只差設定。
- **仍待 QA**: task 6（b50aba1）、task 7（2052a70）。

## 2026-09-29 15:54 — Stage: implement task 9（逐筆價格落地）+ guardrail
- **What changed (`fd1354f`)**: `V3__price_tick.sql`、`PriceTickRepository`（JDBC batch + ON CONFLICT DO NOTHING）、`TickPersister`（batch listener、group tick-persister、earliest）、`KafkaConsumerConfig`（從 Boot consumer factory 複製設定 + ErrorHandlingDeserializer）。task 6、7 已依 QA PASS 勾 [x]。
- **Verified**: `./mvnw clean verify` → 74 tests / 0 failures；500 筆（含 20 筆重複 eventId）→ DB 恰好 480 筆且欄位一致。
- **概念**: Kafka 預設 at-least-once（處理完才 commit，當機會重送）；寫入端用 unique key + ON CONFLICT DO NOTHING 做到冪等，不需要 Kafka transaction 也能「效果上只寫一次」。
- **Guardrail（已上報 personal-workplace-7a）**: QA CONCERN 12 — plan 以「最後成交時間」判斷來源健康，Kraken 冷清時段成交間隔會超過 10 秒，造成 LIVE/STALE 誤報、並可能讓主來源失效時不切換。提議：連線健康改看 lastMessageAt（含每秒 heartbeat，門檻 10s），另加價格過舊門檻 `APP_FEED_PRICE_STALE_THRESHOLD`=60s。等待決定；期間不改 task 7 程式，繼續做不受影響的 task 10。
- **待辦**: QA CONCERN 11（退避序列與歸零規則的測試）將併入 CONCERN 12 的 follow-up。

## 2026-09-29 16:00 — Stage: implement task 10 + task 7 follow-up（CONCERN 12 / 11）
- **Task 10（`70352c1`）**: `/api/prices/latest`、`/history`（keyset 分頁、Base64 cursor）、`/trend`（`date_bin` + `DISTINCT ON` 降採樣）；`FeedStatusTracker`（每個 instance 讀 compacted status topic，超過 15 秒視為 DISCONNECTED）；`contracts/api-samples/prices-*.json` + `ContractSamplesTest`（@WebMvcTest + mock service + JSONAssert STRICT，`-Dcontracts.update=true` 重新產生，樣本保留精確小數）。86 tests 通過；12,000 筆分 3 頁取完無重複；反向驗證改樣本一字即失敗。
- **Task 7 follow-up（`730d9d0`）**: team lead 核准方案 (a)——健康 = 已連線 AND lastMessageAt < 10s（含 heartbeat）AND lastTickAt < 60s（`APP_FEED_PRICE_STALE_THRESHOLD`）。plan.md Approach #2 與 QA 表已更新；tasks.md task 21 註明 15 秒延遲計時要算所有 SSE 事件。CONCERN 11 退避序列測試。94 tests 通過；feed 測試再連跑 3 次通過。task 9 已依 QA PASS 勾 [x]。
- **概念**: keyset 分頁從上一頁最後一個 key 繼續，每頁都是 index range scan、結果穩定；「連線活著嗎」和「資料新鮮嗎」是兩個問題——heartbeat 快速回答前者，另一個較寬的價格時效門檻回答後者，冷清時段就不會誤報。
- **待辦**: QA CONCERN 13（listener error handler 改不限次數指數退避 + poison pill 測試）併入 task 12。

## 2026-09-29 16:06 — Stage: implement task 11（Kafka Streams K 線 topology）
- **What changed (`8371673`)**: `CandleTopology`（1m/5m tumbling、grace 5s、suppress untilWindowCloses）、`PriceTickTimestampExtractor`（事件時間）、`CandleAccumulator`、`Candle`、`TickOrder`（共用排序：時間以微秒、UUID 以無號位元組比較，和 PostgreSQL 一致）、`CandleStreamsConfig`（`app.streams.enabled` 才啟用）；`spring.kafka.streams.*`（state-dir 可設定、LogAndContinue、at_least_once）；test profile 預設關閉 Streams、每個 context 獨立 state dir。
- **Verified**: `./mvnw clean verify` → 100 tests / 0 failures；測試中沒有任何 context 意外啟動 StreamThread。
- **發現**: Java `UUID.compareTo` 是有號比較，PostgreSQL uuid 是無號位元組比較，最高位為 1 時排序相反 → 會讓同時間 tick 的 open/close 和 DB 對不上（AC4）。已用 `TickOrder.compareUnsigned` 統一並測試。
- **概念**: 事件時間 vs 處理時間——視窗依成交發生時間放置，grace 決定等遲到資料多久；suppress 用延遲換取「每根 K 線只輸出一次最終值」。

## 2026-09-30 11:05 — Stage: implement task 12（K 線落地與查詢 API）
- **What changed (`fa7f3c3`)**: `V4__candle.sql`、`CandleRepository`（upsert）、`CandlePersister`、`GET /api/candles`；QA CONCERN 13（寫 DB 的 listener 改不限次數指數退避 1s→30s）、14（history 帶 receivedAt/eventId）、15（PriceTick 時間截斷到微秒）、7（AC5 測試不加 @Transactional）；契約樣本 candles.json。task 10、11 已依 QA PASS 勾 [x]。
- **Verified**: `./mvnw clean verify` → 107 tests / 0 failures；CandlePipelineTest：12 根 1m + 2 根 5m 的 OHLC 與 price_tick 的 SQL 聚合一致（AC4），全新 app instance 仍查得到（AC5）；PersistingErrorHandlerTest 對照我們的策略（最終寫入）與 Spring 預設（第 10 次後丟棄）。
- **誠實紀錄**: 原本以 pause Postgres container 測 DB 中斷，反向驗證發現換成預設 handler 也通過（JDBC 呼叫是卡住而非失敗），不具保護力，已移除並改為直接測試 error policy。
- **AC4 手動比對規則**: 以 README 的 SQL（`CandlePipelineTest.OHLC_FROM_TICKS` 同一段）比對 OHLC；tick_count 在交易所重送成交時可能不同（Streams 會重複計數、DB 會去重），不列入比對。
- **概念**: consumer 的錯誤策略是在「可用性」與「資料遺失」之間取捨；寫 DB 這種 sink 應該「暫停並重試到恢復」，因為資料仍保存在 Kafka。

## 2026-09-30 11:09 — Stage: implement task 13（逐筆保留期清除）
- **What changed (`d58af4d`)**: `RetentionJob`（`APP_RETENTION_INTERVAL` 排程、`APP_RETENTION_TICKS` 保留期、每批 1 萬筆迴圈刪到清空、嚴格小於 cutoff、不動 candle）；`RetentionJobTest`（在同一個 Postgres container 另建 database 並跑 Flyway，避免刪到其他測試資料）、`RetentionScheduleTest`（PT1S、不手動呼叫）。
- **Verified**: `./mvnw clean verify` → 109 tests / 0 failures；25,001 筆過期刪除、邊界 1 筆與未過期 100 筆保留、candle 2 筆不變、再跑一次刪 0 筆；排程 10 秒內自動刪除 2020 年資料（AC9 自動部分、QA M6、S6）。
- **概念**: 大量刪除要分批，讓每個 transaction 短、鎖與 WAL 壓力小；迴圈刪到清空才追得上任何積壓。

## 2026-09-30 11:13 — Stage: implement task 14（匯率與多幣別換算）
- **What changed (`7c18fe2`)**: `V5__fx_rate.sql`、`ExchangeRateClient`（RestClient、timeout、429/5xx/壞 payload → FxUnavailableException）、`FxRateRefresher`（啟動時與每 30 分鐘；失敗保留舊匯率；只在 `app.fx.refresh-enabled`）、`ConversionService` + `GET /api/prices/converted`（含 rateUpdatedAt、attribution 文字、無匯率回 null）；真實回應 fixture；契約樣本 prices-converted.json；守門測試加「無 FxRateRefresher bean」。
- **Verified**: `./mvnw clean verify` → 116 tests / 0 failures；換算誤差 < 0.01%（AC3 容許 0.5%）；429 後保留舊匯率與時間。
- **概念**: RestClient 是 Spring 新一代同步 HTTP client（取代 RestTemplate）；MockRestServiceServer 替換底層傳輸，測試不需任何網路。

## 2026-09-30 11:18 — Stage: implement task 15（價格警示與未讀紀錄）
- **What changed (`d8040cd`)**: `V6__alerts.sql`、`AlertRule`（嚴格大於/小於、事件時間冷卻、level-based）、`AlertEvaluator`（batch listener、group alert-evaluator、latest、條件式 UPDATE 防重複觸發、寫 alert_event + 發佈 btc.alerts.triggered）、`/api/alerts`、`/api/alert-events`（unread、read、read-all）；契約樣本 alerts.json、alert-events.json。
- **Verified**: `./mvnw clean verify` → 128 tests / 0 failures；真實 Kafka、沒有瀏覽器：T、T+4:59.999、T+5:00、T+11:00(低於) → 恰好 2 筆未讀事件並已發佈（AC7/AC8 後端）。
- **概念**: 條件式 UPDATE 是由資料庫保證的樂觀鎖：更新到那一列的人贏，其他人得到 0 列就什麼都不做——不用鎖也不會重複。

## 2026-09-30 11:28 — Stage: implement task 16（SSE 即時推播）
- **What changed (`44c3523`)**: `/api/stream`、`SseBroadcaster`（每 instance 各自 group、latest、價格 250ms 節流且最後一筆必送、status/alert 立即推、15s keepalive、單一寫入執行緒、寫入失敗即移除連線）、`StreamEvents`；契約樣本 sse-price/status/alert、currencies、currency-validation-error。task 12～15 已依 QA PASS 勾 [x]。
- **Verified**: `./mvnw clean verify` → 134 tests / 0 failures。
- **修正**: FeedWiringTest 改為只認自己假交易所產生的資料（其他測試也寫同一個 topic，曾造成 race）；過程中發現自己寫錯的 fixture 時間字串（少一位數使斷言恆真）並已修正。
- **概念**: SSE 是單向 HTTP 串流，瀏覽器內建重連，伺服器推播到瀏覽器不需要 WebSocket；「只保留最新值」的節流限制了瀏覽器的工作量，又不會漏掉最新價格。
- **待辦（QA CONCERN 16/17/18）**: 下一個 follow-up commit 處理。

## 2026-09-30 11:33 — Stage: follow-up（QA CONCERN 16 / 17 / 18）
- **What changed (`23eba43`)**: 16a 寫 DB 遇到永久性錯誤（DataIntegrityViolation）時逐筆隔離、只丟壞的那筆並記 ERROR，error handler 也把它列為不重試；16b 每次重試記 WARN；17 保留期清除啟動後 1 分鐘先跑（`APP_RETENTION_INITIAL_DELAY`）、刪除條件加 pair 以使用索引；18 匯率抓取失敗後 1 分鐘重試（429 則 20 分鐘）。task 15 已依 QA PASS 勾 [x]。
- **Verified**: `./mvnw clean verify` → 141 tests / 0 failures。
- **概念**: 重試策略要先分類錯誤——暫時性的（網路、DB 掛掉）值得等，永久性的（壞資料）要隔離並回報，否則一筆「毒」資料會卡住後面所有資料。

## 2026-09-30 11:36 — Stage: implement task 17（健康檢查與 readiness）
- **What changed (`1091f92`)**: probes 啟用；liveness 只含 livenessState；readiness = readinessState + db + kafka + kafkaStreams；`KafkaHealthIndicator`（AdminClient describeCluster 3s）、`KafkaStreamsHealthIndicator`（只有 RUNNING/REBALANCING 為 UP）；QA CONCERN 4：show-details never、readiness 只顯示各元件 UP/DOWN。
- **Verified**: `./mvnw clean verify` → 150 tests / 0 failures；docker pause Kafka → 10 秒內 readiness 503、liveness 200；unpause → 60 秒內 200（AC12 自動部分、QA M1）。
- **概念**: liveness 回答「要不要重啟這個容器」，readiness 回答「要不要把流量導過來」；外部依賴只該放在 readiness。

## 2026-09-30 11:47 — Stage: implement task 18（後端全鏈路整合測試）
- **What changed (`68a431f`)**: `EndToEndPipelineTest`（只把兩個交易所 socket 換成測試控制的假來源 + MutableClock，其餘全部真實）：143 筆 coinbase tick 入庫、備援 0 筆、≥10 根 1m/≥2 根 5m、警示觸發 2 次未讀、SSE 收到 price/alert；主來源靜默 → 切 kraken 並推 status；恢復 15 秒 → 切回並推 status。
- **發現並修正的隔離問題**: Kafka Streams 的 stream time 跟 committed offset 一起保存；共用 application-id 時，重播其他測試 2033/2034 年的 tick 會讓 CandlePipelineTest 的 2032 年視窗被視為已關閉——之前只是剛好測試順序有利。test profile 改為每個啟用 Streams 的 context 使用獨立 application-id 並從 latest 讀，測試先等 kafkaStreams UP 再送資料；正式環境不變。
- **Verified**: `./mvnw clean verify` → 151 tests / 0 failures；反向字母順序跑整套也全過。
- **概念**: Kafka Streams 的時間由資料驅動（stream time = 看過的最大事件時間）且會持久化，這讓 grace/suppress 可預期——也正是共用輸入 topic 的測試不能共用同一個 Streams 應用的原因。

## 2026-09-30 11:52 — Stage: follow-up（QA CONCERN 19 / 20 + Streams REPLACE_THREAD）
- **What changed (`6b9cbf2`)**: Hikari connection-timeout 預設 5s（`SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`）；FeedStatus 只在 activeSource/state 改變時立即發佈、否則每 5 秒；Streams 執行緒意外死亡時 REPLACE_THREAD。task 16、17 已依 QA PASS 勾 [x]。
- **Verified**: `./mvnw clean verify` → 152 tests / 0 failures。
- **概念**: 健康檢查必須比 probe 的逾時更快回應；卡住的檢查和卡住的應用程式在外面看起來一模一樣。

## 2026-09-30 11:58 — Stage: implement task 19（多架構後端 Dockerfile）
- **What changed (`adfd8b2`)**: 單一 `Dockerfile`（build 階段在 $BUILDPLATFORM、~/.m2 cache mount、layered jar；runtime `eclipse-temurin:21.0.12.1_1-jre-noble`、uid 1001、Streams state 目錄）、`.dockerignore`；刪除 Dockerfile.amd64/silicon、start/stop-silicon.sh。
- **Verified**: `docker buildx build --platform linux/amd64,linux/arm64` 成功（AC14），manifest 含兩個平台，兩者皆以 uid 1001 執行、Java 21.0.12.1。
- **發現的問題**: mvnw 在沒有 `unzip` 時會改下載 .tar.gz，導致 task 2 釘的 .zip SHA-256 校驗失敗（CI/容器都可能踩到）；mvnw 只接受 .zip 網址，所以 build 階段安裝 unzip，並在 wrapper 設定註明。
- **注意**: docker-compose.yml 目前仍引用已刪除的檔案，task 26 會整份重寫。
- **概念**: 多平台 image 是一個 manifest list，指向每個架構各自的 image；$BUILDPLATFORM 讓昂貴的編譯在本機架構跑，只有很薄的 runtime 層是各架構各一份。

## 2026-09-30 12:01 — Stage: implement task 20（前端骨架）
- **What changed (`8e8e1be`)**: `frontend/`（Vite 8 + React 19 + TS）、繁中版面五個區塊、`api/types.ts`（對應後端 DTO）、`api/client.ts`（ApiError + fieldErrors）、Vitest + jsdom + RTL + MSW；MSW handler 直接讀 `contracts/api-samples/`（QA T3），未 mock 的請求一律讓測試失敗；dev server 代理 /api。
- **Verified**: 乾淨 `npm ci` → `npm test`（6 tests）、`npm run build`、`npm run lint` 全過；`git ls-files` 無 node_modules/dist（AC15 前端）。
- **誠實紀錄**: 原註解宣稱 `as` 轉型會讓編譯器檢查樣本型別，這是錯的（只是轉型），已更正；契約同步靠後端 ContractSamplesTest + 前端對欄位的斷言。
- **概念**: MSW 在網路層攔截 fetch，測試裡跑的是真正的 client 程式；用後端錄下的真實回應當 mock，mock 就變成契約而不是猜測。

## 2026-09-30 12:03 — Stage: implement task 21（即時價格與連線狀態）
- **What changed (`e7df6d1`)**: `LiveStreamProvider`（單一 EventSource、price/status/alert、subscribePrices/subscribeAlerts、載入時先取 /api/prices/latest）、`computeDisplay`（連線錯誤 / 15 秒無任何事件 / 伺服器狀態，三者取最差）、`LivePrice` 卡片（價格、目前來源、狀態、最後更新時間、延遲時變淡並顯示警告）、`FakeEventSource`。
- **Verified**: `npm test`（13 tests）、build、lint 全過；涵蓋 AC1、AC2（含 team lead 要求：status 事件也算活動）、AC6 前端。
- **概念**: EventSource 會自己重連；UI 的責任是誠實呈現資料新鮮度。把多個獨立訊號合成一個顯示狀態（取最差者），每條規則都簡單、結果也安全。

## 2026-09-30 12:04 — Stage: implement task 22（多幣別換算表）
- **What changed (`c725638`)**: `ConvertedPrices` 表格（代碼、中文名、BTC 價格、匯率、匯率更新時間、無匯率、attribution 連結；匯率每 60 秒重抓、價格以即時 USD 價 × 匯率在前端計算）。
- **修正**: task 14 的 prices-converted 契約樣本中，EUR/TWD 價格是手打且算錯（不等於 usdPrice × rate），已改為精確乘積並重新產生樣本。
- **Verified**: `npm test`（18 tests）、build、lint 全過；後端 ContractSamplesTest 通過。
- **概念**: 變化慢的資料（匯率）和變化快的資料（價格）分開取得、在前端組合——請求少，畫面又和最快的來源一樣新。

## 2026-09-30 12:06 — Stage: implement task 23（K 線與走勢圖）
- **What changed (`2bbd673`)**: `PriceCharts`（1m/5m K 線、走勢最近 1 小時，SSE 即時延伸）、`series.ts` 純轉換函式、`chartAdapter.ts`（lightweight-charts v5 的薄介面，可注入假 adapter）。
- **Verified**: `npm test`（28 tests）、build、lint 全過；真實 canvas 繪圖留待 task 26 compose 後以瀏覽器確認。
- **概念**: 把難以測試的邊界（canvas 函式庫）隔離在很小的介面後面，邏輯（資料轉換、何時延伸）放在純函式。

## 2026-09-30 12:11 — Stage: task 18 QA FAIL → 修正中（其他 task 暫停）
- **QA 判定**: task 18 @ 68a431f FAIL——`-Dsurefire.runOrder=random` 下 AlertEvaluationTest / EndToEndPipelineTest 失敗。根因：`alert-evaluator`、`tick-persister`、`candle-persister` group 寫死，被快取的測試 context 共用同一個 group；其中一個 context 設了 30 秒冷卻，分到 partition 時就用它判斷。Linux CI 的檔案順序可能一開始就紅（AC13 風險）。task 23（2bbd673）是在收到 FAIL 前已 commit。
- **修正（未 commit，等驗證）**: 三個 group 加可設定前綴 `${app.kafka.group-prefix}`（`APP_KAFKA_GROUP_PREFIX`，正式環境空字串）；test profile 每個 context 隨機前綴；另外 test profile **預設關閉警示評估**，只有 AlertEvaluationTest、EndToEndPipelineTest 開啟（兩者皆預設 5 分鐘冷卻）——只加前綴不夠，因為每個開著評估器的 context 都會各自用自己的冷卻判斷同一筆 tick。
- **過程中的發現**: `${random.uuid}` 每次解析都產生新值，註解裡的 group 前綴與 AppProperties 綁到的不同；測試改為直接檢查本 context 的 listener container 是否已分配 partition。
- **驗證中**: QA 的重現指令已通過；正在跑 random 順序完整測試 3 次。

## 2026-09-30 12:25 — Stage: task 18 / task 23 QA FAIL 修正完成
- **Task 18 修正（`a134a40`）**: consumer group 加前綴、測試預設關閉警示評估、測試連線池上限 4 + Postgres max_connections 300（隨機順序驗證時發現連線耗盡）。QA 重現指令通過；random 順序以先前失敗的 seed 3082923979307916 及 3083380705951166、3083502545398208、3083617509808375 各跑一次，皆 152/152。
- **Task 23 修正（`a673cd4`）**: 走勢區間只讀一次時鐘；QA CONCERN 21：15 秒內沒有任何事件時由「連線中」改為「資料延遲」。vitest 連跑 10 次皆 29/29。
- task 19～22 已依 QA PASS 勾 [x]。
- **概念**: 測試 context 會被快取並持續存活，它們共用的東西（consumer group、Streams application id、資料庫連線數）都必須隔離或預留，否則測試結果會取決於執行順序。

## 2026-09-30 13:03 — Stage: task 16 QA FAIL 修正
- **QA 判定**: task 18 修正（a134a40）、task 23 修正（a673cd4）PASS → task 23 勾 [x]。task 16 重新開啟 FAIL：SseStreamTest 以「連線數 +1」判斷新連線，會和前一個測試舊連線的延遲移除發生 race（完整測試 6 次失敗 1 次）。
- **修正（`71a5a79`）**: 移除相對數量等待——controller 回傳前已註冊 emitter，收到 200 即代表已註冊；EndToEndPipelineTest 同樣的寫法一併移除。
- **Verified**: SseStreamTest 單獨 10/10；random 順序完整測試（seed 3082923979307916、3085791733262166、3085907882768041）皆 152/152。
- **概念**: 測試應該等「它真正需要的那件事」（自己的回應），而不是從會被其他活動同時改動的共享計數去推論。

## 2026-09-30 13:05 — Stage: implement task 24（幣別管理介面）
- **What changed (`8ca5480`)**: `CurrencyManager`（列表、新增、行內編輯、兩段式刪除確認，不用 window.confirm）、錯誤就近顯示（400 欄位訊息、409 代碼已存在）、`currencies-changed` 事件讓換算表立即重新載入。
- **Verified**: `npm test`（36 tests）、build、lint 全過。
- **概念**: 驗證規則以伺服器為準，UI 的責任是把訊息放到正確的欄位旁，而不是再實作一次規則。

## 2026-09-30 13:07 — Stage: implement task 25（警示管理與通知）
- **What changed (`1c5a4aa`)**: `AlertManager`（新增高於/低於、列表含上次觸發、兩段式刪除）、`UnreadAlerts`（載入未讀、逐筆/全部已讀）、`AlertToasts`（SSE alert → toast；頁面可見時顯示即標記已讀，背景分頁等切回可見才顯示並標記，QA S4）、`alert-events-changed` 事件同步。
- **Verified**: `npm test`（46 tests，連跑 5 次）、build、lint 全過；涵蓋 AC7、AC8 前端。
- **概念**: Page Visibility API（document.visibilityState）告訴我們使用者是否真的看得到頁面——在沒有已讀回條的單一使用者 app 中，這是判斷「已看過」最誠實的訊號。

## 2026-09-30 13:12 — Stage: implement task 26（前端容器與完整 docker compose）
- **What changed (`22e6908`)**: `frontend/Dockerfile`（Node build + nginx-unprivileged）、`nginx.conf.template`（/api 代理、SSE 不緩衝、SPA fallback、資源快取、/healthz）、`docker-compose.yml`（kafka/postgres/backend/frontend、固定 tag、具名 volume、healthcheck、internal/egress/public 三個網路、`FRONTEND_PORT`/`BACKEND_PORT` 可設定）、`docker-compose.chaos.yml`。
- **Verified**: 四個服務皆 healthy；經 nginx 的 SSE 12 秒收到 29 筆真實 Coinbase 價格 + 3 筆 LIVE status（AC1 smoke）；網路切分：internal 無法連外、egress 可以、kafka 容器無法連外、backend 可連 kafka 與匯率 API。
- **環境注意**: 本機 3000 port 被使用者另一個專案（wms 的 Vite dev server）占用，驗證時使用 `FRONTEND_PORT=3001`；未動該程序。
- **限制**: 本環境沒有瀏覽器自動化工具，canvas 圖表的實際畫面留待 task 28 驗收時以瀏覽器確認。
- **目前狀態**: compose 堆疊仍在執行中（project `currency`），停止用 `docker compose down`（保留資料）。
- **概念**: `internal: true` 的 Docker 網路沒有對外路由；只讓 backend 多接一個一般網路，「哪個服務能連網際網路」就成為明確、可測試的決定。

## 2026-09-30 13:15 — Stage: implement task 27（README 與驗收步驟）
- **What changed (`44b5539`)**: README 全面改寫（工具、快速開始、環境變數表、API、測試與離線準備、多架構建置、AC1–AC15 逐步驗收、常見問題、專案結構）；`docs/acceptance/ac4-ohlc-check.sql`；compose 把部分 APP_* 變數從 shell 傳給 backend（有設才傳）。
- **Verified（在執行中的堆疊上實跑 README 指令）**: AC1、AC3（與匯率來源完全一致）、AC4（SQL 全部 ohlc_matches = t）、AC12（停 Kafka 3 秒 503、liveness 200、啟動後 2 秒 200）、AC6 方法 A（封鎖 7 秒切 Kraken、解封 17 秒切回、缺 Content-Type 415、還原後端點 404）、AC15。
