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
