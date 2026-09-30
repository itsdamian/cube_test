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

## 2026-09-30 13:50 — Stage: task 28（完整驗收）— 自動可驗部分完成，待瀏覽器目視與 QA
環境：本機 docker compose（前端 FRONTEND_PORT=3001，因 3000 被使用者的 wms Vite 占用），真實 Coinbase / Kraken / open.er-api。時間為 UTC。

| AC | 結果 | 證據 |
|---|---|---|
| AC1 | ✅（HTTP）/ 待目視 | 經 nginx 的 SSE：10 秒 16 筆 price；全新重建的堆疊 10 秒 15 筆 |
| AC2 | ✅ | `docker network disconnect currency_egress`：10–12 秒內瀏覽器串流收到 STALE / DISCONNECTED；`connect` 後 14 秒恢復 LIVE、5 秒內 10 筆新價格，未重啟服務（第一次腳本量測恢復時有 bug，已以修正指令重跑） |
| AC3 | ✅ | EUR/GBP/JPY/TWD/USD 換算匯率與 open.er-api 完全相同（誤差 0.00000%），rateUpdatedAt=2026-09-30T00:02:31Z |
| AC4 | ✅ | 運行約 12 分鐘：11 根 1m、3 根 5m、2,328 筆逐筆；`docs/acceptance/ac4-ohlc-check.sql` 14 根全部 ohlc_matches = t；history 可取回全部 2,328 筆 |
| AC5 | ✅ | `restart backend postgres` 前 14 candles / 2,328 ticks，後 15 / 2,338 |
| AC6 | ✅ | chaos 封鎖 coinbase → 1 秒切 Kraken（API 顯示 kraken），解封 → 17 秒切回；主來源網址錯誤 → Kraken LIVE；還原後回到 coinbase |
| AC7 | ✅（後端＋SSE）/ 待目視 | 預設 5 分鐘冷卻：05:25:05 與 05:30:06 各觸發一次（間隔 300.7 秒），兩筆都經 SSE 推播 |
| AC8 | ✅（後端）/ 待目視 | 無瀏覽器開啟時兩筆皆為未讀（/api/alert-events?unread=true） |
| AC9 | ✅ | PT5M 保留期、每 30 秒清除：10 分鐘後最舊 tick 5 分 03 秒；candle 27 → 39 未被刪；log 每 30 秒 "Retention: deleted N ticks" |
| AC10 | ✅ | `down -v` 後全新 DB：EUR 歐元、GBP 英鎊、JPY 日圓、TWD 新台幣、USD 美元 |
| AC11 | 自動測試 ✅ / 待目視 | 後端 CurrencyApiTest、前端 CurrencyManager 測試；瀏覽器 CRUD + F5 需目視 |
| AC12 | ✅ | stop kafka → 6 秒 readiness 503（kafka DOWN），liveness 200；start → 2 秒回 200 |
| AC13 | ✅（Maven 離線模式）/ 待真斷網 | `./mvnw -o clean verify` 152/152、0 次下載、log 無外部 host；`npm test` 46/46。實際關閉網路需使用者操作 |
| AC14 | ✅ | task 19：buildx amd64+arm64，QA 以 --no-cache 重跑驗證 |
| AC15 | ✅ | `git ls-files` 無 target/node_modules/dist |

- **限制**: 本環境無瀏覽器自動化工具，畫面相關（價格跳動顯示、K 線/走勢圖、幣別 CRUD + F5、警示 toast、未讀清單）請使用者目視確認；AC13 的真斷網執行請使用者關閉網路後執行。
- **task 28 暫不勾選**：待 QA 驗證與使用者目視確認。

## 2026-09-30 13:49 — Stage: QA 結果（task 16 修正、24–27 PASS）
- QA：task 16 修正（71a5a79）、24、25、26、27 PASS，勾 [x]。QA 以隔離堆疊（-p currency-qa，前端 3002）只照 README 第 6 節獨立完成 AC1–AC15，結果與工程師證據一致（AC7 冷卻 300.363 秒、AC4 14/14、AC2 +13 秒 DISCONNECTED）。
- QA CONCERN 22：README 第 6 節加上「改過 port／專案名稱時要換掉的部分」提醒。
- **剩餘**: task 28 待使用者瀏覽器目視確認與實際斷網跑 AC13。
- 更正：task 18 在 QA PASS（a134a40）後漏勾，先前進度報告誤稱已勾選；已補勾 [x]。
- 2026-09-30 13:49 QA：f3a6460 PASS、CONCERN 22 關閉，目前沒有未解決的 QA 意見。task 28 等使用者目視確認與實際斷網跑 AC13，之後通知 QA 做最後確認。

## 2026-09-30 14:56 — Stage: team lead 目視驗收 + bug 修正
- **Team lead 目視驗收（Chrome，localhost:3001）**: AC1、AC3、AC6（來源顯示）、1m/5m K 線與走勢圖、AC11（新增 HKD → 409 代碼已存在 → F5 仍在 → 編輯 → 刪除）、AC7/AC8（觸發後進未讀清單、F5 仍在）皆 OK；測試資料已清除。toast 未在自動化分頁顯示是因為 visibilityState=hidden，符合 S4 設計，將由使用者在自己的瀏覽器確認。
- **Bug（`daa7ea1`）**: 刪除警示後「未讀警示」清單沒有更新（後端 cascade 正確，前端沒有發出 alert-events-changed）。先寫會失敗的測試重現、修正後通過；npm test 47/47（連跑 5 次）；已重建執行中的前端容器。
- **剩餘**: 使用者在自己瀏覽器確認 toast；實際斷網跑 AC13；之後 QA 做 task 28 最後確認。
- **更正（2026-09-30 14:57，QA 指出）**: 上一段寫「AC6（來源顯示）OK」不精確——team lead 確認的是畫面顯示目前來源（Coinbase），**封鎖主來源時畫面切換顯示 Kraken 尚未目視**；AC2 斷線時價格變淡＋警告也尚未目視。兩者後端/串流已驗證，畫面部分維持「待確認」。QA：daa7ea1 PASS（含反向驗證）。

## 2026-09-30 15:05 — Stage: implement task 29（前端視覺美化，範圍擴充）
- **來源**: 使用者要求「整體畫面更美觀」，team lead 依授權核准為 spec 外新增範圍，tasks.md 新增 task 29。push 之後由 team lead 處理（640c09d 已 push）。
- **What changed (`f5d82ad`)**: CSS design tokens（深色為主 + prefers-color-scheme 淺色，所有配色以 WCAG 公式驗證 AA）、header 狀態 pill、桌機兩欄／手機單欄版面（表格在卡片內捲動）、價格漲跌綠紅 + 閃爍（尊重 prefers-reduced-motion）、延遲琥珀／斷線紅並淡化、segmented control、圖表配色讀 token 並跟隨主題切換、toast 右上角、統一控制項與危險操作紅色、focus-visible。
- **Verified**: `npm test` 49/49 連跑 5 次（既有斷言一行未改，新增漲跌與 status pill 測試）、build、lint 0；已重建 localhost:3001 前端容器。
- **限制**: 本環境無瀏覽器，桌機與 375px 手機寬度的實際外觀請 team lead 以 Chrome 目視審查。
- **概念**: design token 讓顏色、間距等決策集中在一處；canvas 圖表無法吃 CSS，就在執行時讀同一組 CSS 變數，讓兩者永遠一致。

## 2026-09-30 15:08 — Stage: task 29 QA PASS；implement task 30（README 改版）
- **Task 29**: QA PASS（自行由 CSS token 重算 WCAG 對比、測試檔無刪行）→ 勾 [x]。team lead 的 Chrome 目視審查仍可能提出調整。
- **Task 30（範圍擴充，team lead 依使用者授權核准）**: README 改為作品門面（介紹＋截圖、功能亮點、mermaid 架構圖、技術棧、技術重點、快速開始、開發與測試、spec-driven＋雙 agent、Roadmap、專案結構）；原第 1–7 節**原文**搬到 `docs/configuration.md`（工具、服務與網路、環境變數、常見問題）、`docs/api.md`、`docs/testing.md`、`docs/acceptance.md`。逐行比對：180 行中 174 行原封不動，6 行為刻意改寫的交叉引用（改成連結）。新增 `scripts/check_md_links.py`（檢查相對連結與 GitHub 錨點，反向驗證可抓出壞連結／壞錨點）。tasks.md（task 28）、plan.md、CandlePipelineTest 註解改為新路徑。
- **引用更新說明**: 本檔（progress.md）較早段落提到「README 第 X 節」者為當時紀錄，不改寫；現位置：第 3 節→docs/configuration.md、第 4 節→docs/api.md、第 5 節→docs/testing.md、第 6 節→docs/acceptance.md、第 7 節→docs/configuration.md〈常見問題〉。qa-review.md 屬 QA，由 QA 自行更新。
- **待補**: 截圖 `docs/images/dashboard-dark.png`、`dashboard-mobile.png`、`dashboard-light.png`（team lead 以 Chrome 擷取）；mermaid 於 GitHub 的實際 render 待 push 後確認。task 30 暫不勾選。

## 2026-09-30 15:13 — Stage: task 29 team lead 目視審查修正
- **Team lead 審查**: 375px 無水平捲動 ✅、深淺主題圖表配色一致 ✅、閃爍太頻繁 ❌，另列 5 項必修 + 1 項核准的小幅行為新增（形成中 K 線即時更新）。
- **修正（`ed6d612`）**: 閃爍同方向每 2 秒（成交時間）最多一次、透明度 18%；換算表不換行、更新時間改為表格下方一行（取最舊）；版面 1360px、3fr/2fr；狀態只在 header pill（成為 live region），價格卡保留淡化與原因；手機分段按鈕不換行、說明文字可換行；形成中 1m/5m K 線跟隨即時價格、後端定稿覆蓋；幣別表格不撐滿、操作靠右。
- **測試調整說明（交 QA）**: 狀態斷言改讀 header pill（期望文字逐字相同）；匯率時間改讀表格下方（同一 dateTime）；「即時價格不影響 K 線」與核准的第 6 項矛盾，保留原斷言並加入新行為斷言。55/55 連跑 5 次、build、lint 0；前端容器已重建。
- **概念**: 在 render 中只用輸入資料（成交時間）而不讀時鐘，元件就是純函式、可預期也好測試。
- 2026-09-30 15:16 QA：b027563 PASS（反向驗證：拿掉淡化邏輯後 3 個測試失敗），CONCERN 23 關閉，目前沒有未解決的 QA 意見。剩下：task 30 的截圖（team lead）、task 28 的使用者目視確認與實際斷網跑 AC13。

## 2026-09-30 15:22 — Stage: task 29 第二次目視審查修正
- **Team lead 複審**: 6 點 OK；AC2 斷線畫面（淡化、紅字、pill 變紅）也已目視 ✅。新提出：幣別表格按鈕上下堆疊（必修）、閃爍加全域 1 秒下限（小修）。
- **修正（`a766828`）**: 操作欄按鈕容器 inline-flex 不換行；移除操作欄 `width:1%`——它在自動寬度表格中會把整張表撐到全寬，**因此 ed6d612 所稱「幣別表格不撐滿」其實沒有生效，此處更正**；閃爍任兩次至少間隔 1 秒（新測試）。commit `scripts/screenshot.mjs` 並在 docs/testing.md 說明重新截圖方式。
- **Verified**: npm test 57/57、build、lint 0；以 screenshot.mjs 在 1440px 與 375px（DPR 2）自行截圖目視確認。
- **暫緩**: 依 team lead 指示，截圖先不 commit——使用者將請 UI/UX 設計師重新設計（task 31），完成後再拍最終版。

## 2026-09-30 15:29 — Stage: QA 目視驗證 task 28 剩餘畫面；screenshot.mjs 參數化
- **QA**: a766828 + 35c257c PASS（FLASH_MIN_GAP_MS 反向驗證）。QA 以 headless Chrome 親自驗證 task 28 缺的畫面：AC7 toast 出現且未讀同步標記已讀、AC2 斷線時卡片淡化＋紅色警告並於接回後恢復、AC6 375px 下 pill 顯示「即時 · Kraken」並於 unblock 後回到 Coinbase。先前 team lead 已目視 AC1、AC3、AC11、圖表與 AC2 斷線畫面。
- **task 28 剩餘**: 僅使用者實際斷網執行 AC13（`./mvnw -o clean verify`、`npm test`）。
- **screenshot.mjs**: 依 QA 建議以 `SCREENSHOT_URL`、`CHROME_PATH` 環境變數覆寫（預設不變），docs/testing.md 已說明；以預設與覆寫各截一次驗證。

## 2026-09-30 16:15 — Stage: implement task 31（依設計方向 B 重新設計前端）
- **來源**: 設計師產出 `design/`（方向 B）。使用者決定：綠漲紅跌、所有幣別 2 位小數、USD 固定第一、做「開啟後漲跌」與「還差 ±X」、`attributionLogo:false` 但 footer 註明並連結 TradingView。team lead 確認範圍：做圖表區間高低、填入目前價格、skeleton；不做本次最高/最低。
- **小幅行為新增（team lead 核准）— OHLC legend**: 位置在圖表工具列右側，與「圖表區間 高 X · 低 Y」共用；沒有 hover 時顯示區間；1m/5m hover 顯示該根的「開 · 高 · 低 · 收」，移開後恢復；走勢圖只顯示價格與時間；tabular-nums 並固定寬度，所以不會位移；觸控裝置（`hover: none`）只顯示區間。crosshair→文字是純函式 `chart/legend.ts`（有單元測試），`chartAdapter` 只轉發事件。
- **What changed (`0270e05`)**:
  - B 的 tokens 與元件（index.css 全面改寫）；`.app[data-feed]` 集中表達資料狀態。
  - 版面：nav + 12 欄 grid；hero（價格＋圖表）、側欄警示（撐滿高度）、全寬換算 tiles、footer。
  - 價格：大字不變色、小數淡色；方向 pill 帶 1 秒光暈，節流規則 `FLASH_EVERY_MS` / `FLASH_MIN_GAP_MS` 不變；meta 行為開啟後漲跌、來源（主要/備援）、更新時間；延遲/斷線 banner 文字逐字不變。
  - 圖表：AreaSeries 走勢、只保留水平格線、價格軸千分位（整數刻度不帶小數，其餘固定 2 位）、crosshair 用 `--line-strong`；分頁支援方向鍵；載入中遮罩。
  - 幣別管理改為原生 `<dialog>`（Esc、× 按鈕與點背景都能關閉，焦點回到「管理幣別」）；只在開啟時掛載，所以每次開啟都重讀清單。
  - 警示：表單可收合，成功後保持開啟；方向改 radio；「建立」送出；每筆顯示「還差 ±X」（獨立元件，每筆價格只重繪這幾個 span）；「填入目前價格」會四捨五入到整數。
  - 未讀：為空時不 render；標題為「離開期間觸發 n 則」。
  - toast：B 版樣式，底部倒數條（`--toast-ms` = TOAST_MS）。
  - 換算：tiles，USD 固定第一。
  - `check_md_links.py` 改為略過 `*.src.md` 模板。設計師的 `design/src/redesign.src.md` 是 build.mjs 的模板，裡面的圖片路徑要產生到 `design/redesign.md` 後才成立；產生後的檔案有檢查，也通過。
- **不變**: SSE、`computeDisplay`、閃爍規則、所有 API 呼叫。
- **測試調整（57 → 69，沒有刪任何測試）**:
  - 漲跌斷言從價格 class 改成讀方向 pill 的 `data-dir`，另外斷言大字沒有顏色 class。
  - 閃爍的兩個測試改看 pill 的元素 identity 與 `flash-up/down`，時間點與期望次數一行未改。
  - 「沒有未讀警示」文字改為斷言整塊不存在（§9 核准）；「未讀警示（1）」改為 heading「離開期間觸發 1 則」。
  - 新增警示改成先展開表單（並斷言 `aria-expanded`），再點 radio「價格低於」和「建立」；成功後斷言表單仍開啟、輸入已清空。空值驗證另外補了 -5 的情況。
  - toast 文字從「BTC-USD 高於」改為「BTC-USD 已高於 US$90,000.00」（B 的文案）。
  - App：`幣別管理` 改為 dialog「管理幣別」，並新增 Esc／×關閉與焦點回歸的測試；region 從只數數量改為逐一以名稱斷言。
  - 新增測試：legend 單元測試 5 個、價格軸格式、hover 往返、方向鍵、開啟後漲跌、還差／填入、USD 排序。
  - jsdom 沒有 `showModal`，`test/setup.ts` 補了最小的替代實作：open 屬性、close/cancel 事件、Esc。
- **Verified**:
  - `npm test` 69/69、lint 0 warnings、build 通過；localhost:3001 已重建。
  - screenshot.mjs 自我檢查 1440（深/淺）、768、375 DPR2：與 direction-b-*.png 對照一致，都沒有水平捲動。
  - 已拍攝 README 三張最終截圖。截圖用的示範警示建在我自己的 :3001 本機資料庫：一筆立即觸發（產生「離開期間觸發」），截完已刪除。
- **與設計稿的差異（請審查）**:
  - 美元符號沿用 `US$`，設計稿是 `$`。原因是 zh-TW 的 TWD 也顯示成 `$`，同一頁會混淆。
  - lightweight-charts 的時間軸在邊緣的標籤偶爾被裁切（例如手機的「16:0」），這是函式庫的行為。
  - 狀態/dialog 的視覺（stale banner、modal）有測試涵蓋，但沒有截圖。screenshot.mjs 沒辦法點擊，建議 QA 或 team lead 用 Chrome 目視。
- **發現的後端問題（不在 task 31 範圍，未修改，已回報 team lead）**:
  - 現象：約 08:02:39 起，:3001 的 SSE 不再推 `price`，但 `status` 照常推送，tick 也照常寫入 DB（REST latest 是新的）。新連線只收到 08:02:39 的舊價格。
  - backend log 沒有任何例外。重啟 backend 後恢復。
  - 用 6 個 curl 強制中斷、以及經 nginx 同時跑 4 個 headless Chrome，都重現不了。
  - 推測：`SseBroadcaster` 的 `scheduleAtFixedRate(pushLatestPrice)` 丟出 unchecked exception 後，週期任務被靜默取消。`status` 走的是 `execute`，所以不受影響。
  - 建議另開 task：週期任務包 try/catch 並記 log，加上一個會丟例外的測試。

## 2026-09-30 16:25 — Stage: task 31 審查修正；implement task 32（SSE 價格凍結）
- **Task 31 team lead 審查**: 在 Chrome 目視通過（hover 圖例、dialog、Esc 關閉並回到觸發按鈕、表單、淺色）。
  - 決定：USD 保留 `US$`，TWD 明確顯示為 `NT$`；時間軸邊緣裁切可接受，能簡單改善就做；`check_md_links.py` 略過 `*.src.md` 同意；`design/` 要 commit。
  - `2aa29a0`：`formatMoney` 以 formatToParts 只替換貨幣符號，TWD 顯示 NT$，新增 `format.test.ts`。timeScale `rightOffset: 3`，最新的時間標籤不再被價格軸裁切（左緣仍可能被裁切，已接受）。
  - `92c9e1b`：commit 設計師的產出（`design/`，原樣未改）。
- **Task 32（team lead 核准）— `652d4cc`**:
  - **根因**（QA 獨立調查得到同一結論，並將 task 16 判為 FAIL，由本 task 修正）：`scheduleAtFixedRate(pushLatestPrice)` 只要有一次執行丟出 unchecked exception，之後的執行就被靜默取消。`send()` 只 catch IOException / IllegalStateException；client 在序列化途中斷線會丟 HttpMessageNotWritableException，catch 區塊裡的 `completeWithError` 也可能再丟例外。status 走 `execute`，所以照常推送。前端因為每 5 秒收到 status，15 秒的靜默規則一直被重設，結果畫面顯示「即時」但價格凍結，違反 AC2。
  - **後端**：`send()`、heartbeat 改 catch `IOException | RuntimeException`，只移除出錯的 emitter；`completeWithError` 另外包起來；兩個週期任務都經過 `guarded()`（log.warn 後繼續）。
  - **後端測試**：`SseBroadcasterResilienceTest`（不用 Spring 和 Kafka，用真的 scheduler），3 個：
    - unchecked 例外的 emitter 不會讓後續週期停止推送；
    - 連 close 都失敗的 emitter 也一樣；
    - `guarded()` 會保住失敗過一次的任務。
    - **反向驗證**：拿掉修正後 3 個都 ConditionTimeout 失敗。
  - **前端防線**：status 是 LIVE，但距離最後一筆 SSE price（還沒收到過就從開啟頁面算）≥ 60 秒 → 顯示「資料延遲」，banner 說明「價格更新中斷」。這對應後端的 price-stale 60 秒規則。fake timers 測試 59 秒仍是即時、60 秒轉為延遲、價格恢復後回到即時；另測沒有任何價格的情況。**反向驗證**：門檻改成 600 秒時兩個測試都失敗。
  - **警示距離**：條件成立時顯示「已高於 X／已低於 X」，未成立時顯示「還差 X」（不帶正負號）。比較規則與後端 `Direction` 相同（嚴格 > / <），所以剛好等於門檻時顯示「還差 0.00」。
  - **Verified**：`./mvnw -o clean verify` 155/155；`npm test` 74/74、lint 0、build 通過；已重建 :3001 的 backend 與 frontend，SSE 有持續推送 price。
- **截圖**：示範用的「已成立」警示在觸發後 93 ms 就被一個開著的 :3001 頁面標為已讀（不是 team lead 的分頁）。我原本打算直接在 DB 把 `read_at` 設回 NULL，被權限機制擋下，所以沒有做，也沒有繞過。改用 API：刪掉舊警示、在沒有頁面開著時重建，事件維持未讀。

## 2026-09-30 16:35 — Stage: task 31 QA FAIL 修正；task 32 最終截圖
- **QA FAIL（task 31）**：375/390px 頁面可以水平捲動（scrollWidth 473）。原因是 `.card__title { min-width: max-content }` 讓換算卡的標題不能換行。我先前寫「無水平捲動」只是看截圖判斷，但截圖腳本會隱藏捲軸，**那個說法不正確，此處更正**。
  - **修正 `cbe54af`**：標題改為 flex-wrap、min-width 0，副標題可在任意位置換行。
  - **驗證**：用 CDP 探測 `documentElement.scrollWidth <= clientWidth`，並列出超出畫面的元素。修正前可重現（467 > 375）；修正後 320、375、390（mobile 模擬）、768、1440 都通過。
- **`5da0885`**：改成 NT$ 之後，1440px 的新台幣卡片被截斷成省略號。卡片金額的字級改用 container query 單位，跟著卡片寬度縮放，最大仍是 22px；手機維持 15px 的清單版面。四種寬度重新探測，都沒有溢出。
- **最終截圖（task 30/31/32）**：`docs/images/dashboard-{dark,light,mobile}.png`。
  - 示範狀態：一則離開期間觸發（未讀）、一則「已高於」（已成立）、一則「還差」（未成立）；換算卡片顯示 NT$。
  - `check_md_links.py`（不加 allow）通過。
  - 示範警示 8（已成立）和 7 留在我的 :3001 DB；8 每 5 分鐘會再觸發一次。
