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
