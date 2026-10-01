# Progress: k8s-gitops-cicd

工程師（cube-java-engineer）的交付紀錄。QA 的紀錄在 `qa-review.md`（QA 擁有）。

## 2026-10-01 14:20 — Stage: plan 草稿（交 QA 審查）
- **分支**：從最新的 origin/main（`b3a5873` 之後的 `7c5be88`）建立 `feat/k8s-gitops-cicd`。建立時 git 自動把 upstream 設成 origin/main，**已移除**，避免不帶參數的 `git push` 誤推到 main。spec.md 與 plan.md 尚未 commit（plan 核准後一起 commit）。
- **plan.md（DRAFT）**：五個 Open Question 的建議：
  - k3d；
  - 第三方元件用 Helm、自家應用用 Kustomize；
  - Kafka / PostgreSQL 用 Strimzi + CloudNativePG；
  - Sealed Secrets；
  - Claude review 涵蓋 manifests 與 workflow、CI 失敗分析涵蓋 main（寫入單一 issue）。
- **plan 內也包含**：資源估算（建議 Docker 16 GB，最低 12 GB）、runner 上的 Testcontainers 與 multi-arch 做法、CI 時間估算、使用者步驟清單（12 項）。
- **查證**（不憑記憶）：
  - claude-code-action 官方 README / solutions / security / examples；
  - ingress-nginx 已於 2026-03 停止維護 → 改用 Gateway API + Traefik；
  - public repo 的 arm64 runner 免費且已 GA；
  - trivy-action 2026-03 的 tag 劫持事件 → 所有 action 以 SHA 釘住；
  - Strimzi 1.2 只支援 Kafka 4.2/4.3 → 計畫把 compose 與 Testcontainers 升到 4.3；
  - Sealed Secrets 不受 Bitnami 目錄改版影響。
- **實測基準**：本機 `./mvnw clean verify` 130 秒、前端 test+lint+build 4.3 秒；`docker stats`：Kafka 1.1 GB、backend 0.6 GB、PG 0.09 GB。
- **請 QA 檢查**：
  - 15 條 AC 是否都有驗證方式；
  - 資源估算的合理性；
  - CI 迴圈與必要 check 的設計；
  - Claude workflow 的權限與 fork / 成本限制；
  - 有無 spec 要求被漏掉或超出範圍。

## 2026-10-01 14:50 — Stage: plan 依 QA 意見修訂
- QA 判定 CONCERN（無 scope drift）：5 項必修、8 項建議，**全部採納**，沒有反駁項目。處理對照表在 plan.md〈QA 意見與處理〉。
- 重點修改：
  - AC4 改讀 Kafka 驗證重複，加 ValidatingAdmissionPolicy 擋 worker 擴容；
  - 告警改用 `unless` 寫法並加 promtool 測試；
  - release 改用 tag commit 上 dev overlay 記錄的 image；
  - Claude 三個 workflow 都設黑白名單，並加「請合併」負向測試；
  - NetworkPolicy 列出完整允許清單；
  - 停用 Strimzi Entity Operator；
  - deploy-dev 加 concurrency、固定 bump 分支。
- 下一步：交 team lead 核准。

## 2026-10-01 15:05 — Stage: plan CONFIRMED
- QA：修訂版 PASS（M1–M5、C1–C8 逐行對照）。實作時 QA 會實測四件事：
  - VAP 拒絕 worker 擴容；
  - promtool 測試在 CI 跑，並用錯誤規則做反向驗證；
  - 「@claude 請合併」的負向測試；
  - NetworkPolicy 套用後 operator 仍 Ready、dev→prod 被擋。
- team lead 核准 plan，並核准 Kafka 4.3 升級（照 QA 的條件，tasks 中獨立成早期 task）。
- 依 team lead 要求，在正文就近附上兩個 2026-03 事件的出處（kubernetes.io 官方部落格、Trivy 官方 advisory）；並寫明「改用 Gateway API」與「action 釘 SHA」本身就成立，不依賴這兩個事件。
- **GitHub push / PR 授權（使用者清單第 1 項）**：team lead 已轉給使用者本人，**等待回覆；在使用者明確同意前不 push、不開 PR**。
- compose stack 的停止時間點寫進 tasks（`currency` 由工程師停、`currency-qa` 由 QA 停）。Docker 記憶體調到 16 GB 由 team lead 請使用者處理。

## 2026-10-01 15:20 — Stage: tasks.md 草稿（交 QA 審查）
- 21 個 task，分五個 Phase：
  - A：應用程式準備，task 1–3，第一個就是 Kafka 4.3 升級；
  - B：manifests 與離線驗證，4–6；
  - C：本機叢集，7–11，使用 `k3d image import` 的本機 image，**不依賴 GitHub**，等待 push 授權期間仍能前進；
  - D：GitHub / CI/CD / GitOps，12–19，🔒 需要 push 授權；
  - E：文件與總驗收，20–21。
- 每個 task 都標出需要的使用者步驟（👤）與 QA 協調（🤝）；compose stack 的停止時間點寫在 Phase C 開頭。
- tasks.md 最後附 AC1–AC15 → task 對照表；每個 AC 都有離線 / 自動驗證與實測兩層。

## 2026-10-01 15:40 — Stage: tasks.md 依 QA 意見修訂
- QA 判定 CONCERN：AC 15/15 都有對應。2 項必修與 5 項建議**全部採納**：
  - **M1**：`workflow_run`、`issue_comment` 類事件只執行 main 上的 workflow 檔。新增 🏁 標記；task 16 與 19 的前提加上「使用者先把該 workflow 合併到 main」，證據必須由 main 上的版本觸發；並註明 `ci.yml`、`claude-review.yml`、`release.yml` 不受此限制。
  - **M2**：task 21 寫明 AC2–4、10、12、13 必須在 GitOps 叢集上重跑，對照表同步更新。
  - **C1**：task 7、8、11、16、17 補上 🤝。
  - **C2**：task 1 一併更新離線 image 清單（前一份 spec 的 AC13 斷網測試要用）。
  - **C3**：task 14 的 PR 說明要寫清楚。
  - **C4**：task 9 dev / prod 都要觀察，task 21 檢查 24 小時告警歷史。
  - **C5**：記錄每個 Claude run 的耗時與回合數。

## 2026-10-01 14:40 — 使用者授權與署名決定
- **使用者本人在工程師分頁確認**：「同意 k8s-gitops-cicd 的 push 授權範圍（清單第 1 項）」。範圍：
  - 可以 push `feat/k8s-gitops-cicd` 與 `test/*`，可以開 / 關測試 PR（draft + `test-only` label）；
  - 合併到 main、推 tag、合併 prod PR 一律由使用者本人操作；
  - 不得自動合併任何進 main 的 PR（plan 核准的 deploy-dev auto-merge 除外）。
- **署名（使用者經 team lead 轉達）**：從此 commit 不加 `Co-Authored-By: Claude` trailer，PR 描述不加生成標記；改在 README〈開發方式〉署名（task 20）。尚未 push 的 `8ce0331`、`b9bb7cc` 會在第一次 push 前移除 trailer（team lead 指示）。

## 2026-10-01 14:55 — Stage: implement task 1（Kafka 4.3.1）
- **What changed**：
  - `docker-compose.yml`、`IntegrationTest.KAFKA_IMAGE` 改為 `apache/kafka:4.3.1`（Strimzi 1.2 支援的穩定版，4.3.2 目前只有 rc）；
  - `docs/testing.md` 的離線預載清單（前一份 spec AC13 會用到）、README 技術棧；
  - `docs/configuration.md` 常見問題新增「從 3.9 的舊 volume 升級」。
  - **應用程式碼未修改**。
- **發現（給 QA）**：第一次 random 測試有 2 個 error（`CandlePipelineTest`、`EndToEndPipelineTest`）。
  - 根因：Kafka 4（KIP-1030）把 broker 的 `log.message.timestamp.after.max.ms` 預設值從不限制改成 **1 小時**。這兩個測試刻意使用 2032 / 2035 年的成交時間，讓自己的 K 線和共用 broker 中其他測試的資料隔離，因此被拒絕（`InvalidTimestampException … out of range`）。
  - 處理：**只在 Testcontainers 的 broker** 加 `KAFKA_LOG_MESSAGE_TIMESTAMP_AFTER_MAX_MS=Long.MAX_VALUE`，並在程式中註解原因。compose 與叢集保留 Kafka 的預設值，可以擋住時鐘偏差過大的寫入；真實成交時間就是現在，不受影響。
  - 沒有改測試內容：改成過去的時間會撞到保留期清除與其他測試的「最新價格」判斷，牽動範圍更大。
  - 反向驗證：沒有這行設定時兩個測試失敗，加上後通過。
- **Verified**：
  - `./mvnw clean verify -Dsurefire.runOrder=random` 連續 3 次通過（每次 155 tests，0 failures / 0 errors）。
  - compose 回歸（沿用舊 volume 升級）：Kafka 4.3.1 healthy、readiness UP（db / kafka / kafkaStreams）、SSE 5 秒內 11 筆 price、升級後產生新的 1m K 線、Kafka log 無 ERROR。`metadata.version` 停在 3.9-IV0，文件中的升級指令實測可以升到 4.3-IV0，之後 SSE 仍正常。
  - 全新 volume（臨時 project `currency-k43fresh`）：readiness UP、SSE 有 price、1m K 線產生、`metadata.version` 4.3-IV0；驗證後已 `down -v` 刪除。
  - `check_md_links.py` 通過。

## 2026-10-01 15:05 — 移除尚未 push 的 commit 中的 Claude trailer（team lead 指示）
- 範圍 `origin/main..HEAD`，用 `git commit-tree` 逐一重建，沒有動到工作樹或 index；QA 尚未 commit 的 `qa-review.md` 保持原樣。
- 新舊對照：`8ce0331 → 9a75e3f`（spec + plan）、`b9bb7cc → c24168a`（tasks）、`feb0003 → 7a95750`（task 1）。本檔較早段落提到的舊 hash 請依此對照。
- 驗證：每個 commit 的 tree 與原本相同、作者 / 日期保留（itsdamian）、`git log origin/main..HEAD --format=%B | grep -c Claude` = 0、message 結尾只有一個換行。

## 2026-10-01 15:35 — Stage: implement task 2（Prometheus 指標 + graceful shutdown）
- **What changed**：
  - `pom.xml` 加 `micrometer-registry-prometheus`（版本由 Boot 管理）；`application.yml` 暴露 `prometheus`、`server.shutdown: graceful`。
  - **新增 `FeedMetrics`**：
    - `cube_feed_ingest_active` 在每個行程都會註冊（FeedManager 執行中為 1，否則為 0）。
    - 有 ingest 的行程才有 `cube_feed_last_tick_seconds`（尚未收到時為 NaN）與 `cube_feed_active_source{source}`。
  - **`FeedManager`**：記錄 `lastTickPublishedAt`。
  - **`SseBroadcaster`**：
    - `cube_sse_last_push_seconds`：推送迴圈取到新價格就更新，**與連線數無關**；
    - `cube_sse_prices_pushed_total`：實際寫給瀏覽器的 price 事件數；
    - `cube_sse_connections`。
- **指標語意的微調（給 QA）**：plan 寫「最後一筆成交的時間」，實作改為**本機收到並發布 active 來源成交的時間**（本機時鐘）。原因：用交易所的成交時間時，交易所時鐘若有偏差，可能讓擷取停止被掩蓋或誤報；用本機接收時間判斷「擷取是否停止」更直接。
- **測試**（新增 8 個，共 163）：
  - `FeedMetricsTest`（4）：ingest 關閉時只有 `ingest_active=0`；ingest_active 隨 start / stop 變化；last_tick 在 active 來源成交前為 NaN、備援來源的成交不更新、之後為本機時間；failover 後 active_source 跟著切換。
  - `SseBroadcasterMetricsTest`（3）：**0 個 client 時 last_push 仍隨新價格前進**、沒有新價格時不前進、pushed 計數與 connections。
  - `PrometheusEndpointTest`（1，`@AutoConfigureObservability`）：端點輸出 `cube_*` 與 JVM 指標；test profile（ingest 關閉）下 `cube_feed_ingest_active 0.0`，且沒有 ingest 專屬指標。
- **既有測試的修正**：`SseBroadcasterResilienceTest` 中兩處 `connectionCount()==1` 的斷言有競態。同一輪廣播中，正常的 emitter 可能先收到價格，失敗的那個才被移除；原本在收到價格的當下立刻斷言，改為 await。測試意圖不變；修正後 5 次重跑全數通過。
- **Verified**：`./mvnw clean verify` 163 tests 0 failures；compose 重建 backend 後，`:8080/actuator/prometheus` 有全部 7 個 `cube_*` 指標（ingest_active 1、coinbase 1 / kraken 0、last_tick 與 last_push 都是當下時間）；經 nginx 的 `:3001/actuator/prometheus` 只回前端 index.html（SPA fallback），**不含任何指標**。
