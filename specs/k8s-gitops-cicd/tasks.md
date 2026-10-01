# Tasks: cube_test 上 Kubernetes — GitOps（Argo CD）+ GitHub Actions CI/CD

Status: CONFIRMED（team lead 依使用者授權核准，2026-10-01）

依據 `spec.md`（CONFIRMED）與 `plan.md`（CONFIRMED）。每個 task 完成後 commit，交 QA 驗證，**QA PASS 後才打勾**。

標記說明：
- 🔒 **需使用者授權 push / 開 PR**（plan 使用者清單第 1 項；確認前不得 push）
- 👤 **需使用者本人先完成的步驟**（括號內為 plan 使用者清單編號）
- 🤝 **需與 QA 協調**（共用叢集協議，plan R10）：破壞性操作或改變版本前先通知 QA、等回覆，結束後恢復並通知
- 🏁 **workflow 必須先在 main 上**：GitHub 對 `workflow_run`（`deploy-dev.yml`、`claude-ci-failure.yml`）與 `issue_comment` / `pull_request_review_comment` / `pull_request_review`（`claude.yml` 的 `@claude`）一律使用**預設分支（main）上的 workflow 檔**；只放在 feature 分支或 PR 中永遠不會觸發。`ci.yml`、`claude-review.yml`（`pull_request`）與 `release.yml`（`push: tags`）不受此限制（QA M1）

順序原則：
- Phase A–C **不需要 GitHub**：叢集先用本機建置、`k3d image import` 匯入的 image 驗證，所以等待 push 授權期間仍能前進。
- Phase D 才接上 GitHub、GHCR 與 Argo CD 的 GitOps。
- 測試與驗證寫在每個 task 的 done-when 裡，不集中到最後；文件也隨 task 增量撰寫，最後一個 task 只負責整併。

## Phase A — 應用程式的部署準備（本機，不需叢集）

- [x] 1. **Kafka 升到 4.3.x，讓測試、compose、叢集版本一致**（plan R4；team lead 核准，附 QA C5 的條件）：`docker-compose.yml` 與 `IntegrationTest.KAFKA_IMAGE` 改為 `apache/kafka:4.3.x`，**不改應用程式碼** — done when: `./mvnw clean verify` 連續 3 次 `-Dsurefire.runOrder=random` 全部通過；compose 回歸冒煙（`docker compose up -d --build --wait` → SSE 有 price、1 分鐘後 `/api/candles?interval=1m` 有新 K 線、`/actuator/health/readiness` 為 UP）；`docs/testing.md`〈離線跑測試〉與 `docs/configuration.md` 中的 Kafka image 清單同步更新（前一份 spec 的 AC13 斷網測試會用到，QA C2）；如果有任何不相容，停下來回報 team lead，不修改應用程式碼
- [x] 2. **Prometheus 指標與 graceful shutdown**：加 `micrometer-registry-prometheus`、暴露 `prometheus` 端點、`server.shutdown=graceful`；新增 plan 定義的指標，語意如下：
  - `cube_feed_last_tick_seconds`、`cube_feed_active_source{source}`、`cube_feed_ingest_active`
  - `cube_sse_last_push_seconds`：與 client 數無關
  - `cube_sse_prices_pushed_total`、`cube_sse_connections`

  — done when: 單元測試（`SimpleMeterRegistry`）涵蓋每個指標，包括「0 個 SSE client 時 `cube_sse_last_push_seconds` 仍會前進」與「ingest 關閉時 `cube_feed_ingest_active`=0」；`./mvnw clean verify` 通過；compose 上 `curl /actuator/prometheus` 看得到全部指標，nginx（:3001）**不**對外提供 `/actuator`
- [x] 3. **api 角色（所有背景功能關閉）的設定驗證**：新增整合測試，用 api 的環境變數組合（ingest / streams / persist / alerts / FX refresh 全關）啟動 context — done when: context 能啟動、readiness 為 UP（沒有 kafkaStreams 也不報錯）、REST 與 SSE 可用、沒有建立任何交易所連線、`cube_feed_ingest_active` 不存在或為 0；`./mvnw clean verify` 通過

## Phase B — Manifests 與 CI 前置（離線驗證，不需叢集）

- [x] 4. **應用 manifests：Kustomize base + dev / prod overlays**（`deploy/apps/cube/`）：內容包括
  - backend-worker StatefulSet ×1（streams state PVC）、backend-api Deployment + HPA（dev 1–2、prod 2–4，scaleDown stabilization 120 秒）、frontend；
  - Services、HTTPRoute（`dev.cube.localhost`、`cube.localhost`）；
  - Strimzi `Kafka` + `KafkaNodePool`（KRaft 單節點、Kafka Exporter、**不啟用 Entity Operator**）、CNPG `Cluster`（PG 17，應用讀 `<cluster>-app` Secret）；
  - probes（liveness / readiness / startup）、resources（requests + 1.25 倍 memory limit、不設 CPU limit）、rolling 設定、preStop；
  - plan 列出的完整 NetworkPolicy 允許清單、ValidatingAdmissionPolicy + Binding（拒絕 worker >1，包含 `statefulsets/scale`）。

  — done when: `kubectl kustomize overlays/dev`、`overlays/prod` 都能 build；以 kubeconform（docker image 執行）`-strict` 驗證通過，含 Strimzi、CNPG、Gateway API、Prometheus Operator、VAP 的 CRD schema；兩個 overlay 的 diff 只有副本數、資源、網址、image、PVC 大小
- [x] 5. **監控 manifests 與告警單元測試**：
  - ServiceMonitor（api + worker）、Strimzi PodMonitor、CNPG metrics；
  - PrometheusRule：`PriceIngestStalled`、`PricePushStalled`、`IngestDuplicated`，使用 plan 的 `unless` 寫法；
  - `alerts.test.yaml`；
  - Grafana 儀表板「cube 概覽」JSON：`namespace` 變數，lag 面板只顯示固定的 group。

  — done when: `promtool test rules`（docker 執行）通過以下案例：指標新鮮→不告警、指標變舊→告警、**series 消失**→告警、推送凍結但擷取正常→只觸發 `PricePushStalled`、兩個 ingest→`IngestDuplicated`；**反向驗證**：把規則改回 `time() - max(...) > 60` 時，「series 消失」案例會失敗；儀表板 JSON 能被 Grafana schema 解析（在 task 9 實際載入）
- [x] 6. **平台元件與叢集腳本**（`deploy/k3d/cluster.yaml`、`deploy/bootstrap/`、`deploy/platform/`、`scripts/cluster-up.sh`、`cluster-down.sh`、`seal-secret.sh`）：
  - k3d 叢集設定（停用內建 Traefik、80 port 對到 LB）；
  - 各元件的 Application + values：Argo CD（非 HA、無 Dex / notifications、reconciliation 60s）、Traefik + Gateway、Sealed Secrets、Strimzi、CNPG、kube-prometheus-stack（retention 3 天）；
  - `cluster-up.sh`（計時包含下載；原本的 `--prepull` 在 task 7 因 Docker Desktop 的 containerd image store 無法匯入多平台 image 而移除）；有備份就先還原 Sealed Secrets 私鑰；
  - 所有 chart 版本以 `targetRevision` 釘住。

  — done when: 每個 values 檔都能 `helm template`（docker 執行）成功；`shellcheck` 檢查兩支腳本無錯誤；`cluster.yaml` 能通過 `k3d` schema 驗證（task 7 實際建立）

## Phase C — 本機叢集（用本機 image，不依賴 GitHub）

> 開始 task 7 前：👤 Docker 記憶體 16 GB（清單 2）、工具安裝（清單 3）；工程師停掉 `currency` compose stack（`docker compose -p currency down`），🤝 QA 停掉 `currency-qa`。

- [x] 7. **建立叢集與平台元件**：🤝（`cluster-down` 會摧毀共用叢集）先以腳本直接安裝（Argo CD 先裝好，但此階段還不指向 GitHub）— done when:
  - `cluster-down` → `cluster-up` 從零重建成功，記錄總時間（包含下載）；
  - 所有平台 Pod Ready；`argocd.localhost`、`grafana.localhost` 可以從瀏覽器開啟；`kubectl top nodes` 可用（metrics-server）；
  - 記錄 `docker stats` 與 `kubectl top pods -A`，和 plan 的資源表對照（偏差超過 30% 要回報）
- [ ] 8. **部署 dev / prod 兩套應用**：🤝（刪 Pod、擴容、rolling update 前通知 QA）本機 build image → `k3d image import`，用暫時的 image override 套用兩個 overlay（不 commit 本機 tag）— done when:
  - **AC2**：兩個網址在 headless Chrome 截圖中都有跳動的價格，持續 60 秒以上沒有「資料延遲」（R12）；在 dev 新增幣別後，prod 的 `/api/currencies` 不受影響。
  - **AC3**：記錄幣別、警示、`price_tick` 筆數 → 刪除 Kafka 與 PostgreSQL Pod → Ready 後資料仍在。
  - **AC4**：api 擴到 3 副本時，從 Kafka 讀一段時間窗，**record 數 == 不重複 eventId 數**，且 source 只有 active 來源；`cube_feed_ingest_active` 只有一個 Pod 為 1。
  - **M1**：`kubectl scale sts/backend-worker --replicas=2` 被 VAP 拒絕。
  - **M5**：套用 NetworkPolicy 後 Kafka / Cluster CR 仍然 Ready；dev→prod 的 Kafka 9092 與 PG 5432 逾時，同環境連線成功。
  - **需求 8**：rolling update api 時，瀏覽器的 SSE 自動重連，頁面不會停在「已斷線」
- [ ] 9. **監控實際運作**：🤝 暫停 dev auto-sync 前先通知 QA — done when:
  - Grafana「cube 概覽」可以並排顯示 dev / prod 的 JVM、HTTP、固定 group 的 Kafka lag、價格推送速率、SSE 連線數（截圖）。
  - **AC12**：dev worker scale 到 0（series 消失的情況）後約 2 分鐘內 `PriceIngestStalled` firing，恢復後 resolved。
  - 沒有開任何頁面時，dev 與 prod 都不會出現 `PricePushStalled`，觀察至少 10 分鐘；task 21 再檢查 24 小時（含冷清時段）的告警歷史（QA C4）
- [ ] 10. **HPA 壓測**（`deploy/loadtest/` k6 Job）：🤝 事先通知 QA — done when: **AC13**：`kubectl get hpa -w` 的紀錄顯示 api 在設定範圍內擴展，負載停止後於 stabilization 時間後縮回；過程中 SSE 頁面持續更新
- [ ] 11. **Sealed Secrets 與 Grafana admin 密碼**：🤝（重建叢集）`seal-secret.sh`；Grafana admin 改由 SealedSecret 提供；私鑰備份與還原流程 — done when:
  - repo 中只有密文；
  - 按備份流程 `cluster-down` → `cluster-up` 後，Grafana 用同一組密碼登入成功；
  - 沒有備份時，文件說明如何重新產生並重新加密

## Phase D — GitHub、CI/CD 與 GitOps（🔒 需使用者授權 push / 開 PR）

- [ ] 12. **CI workflow（`ci.yml`）**：🔒；👤 清單 4（Actions 權限設為 Read、允許 auto-merge）
  - jobs：`changes`、`backend`、`frontend`、`manifests`（kustomize + kubeconform + helm template + actionlint + promtool）、`secrets`（gitleaks）、`images`（buildx amd64+arm64 + QEMU、Trivy SARIF；PR 不 push）、`ci-ok`（彙總）；
  - 所有 action 釘 commit SHA；頂層 `permissions: {}`。

  — done when:
  - 測試 PR（draft、`test-only` label）上所有 job 綠燈，實際時間記錄並與 plan 的估算對照；
  - 只改 `deploy/**` 的 PR：重 job 為 skipped、`ci-ok` 綠燈、約 1 分鐘；
  - actionlint 無錯誤；
  - **反向驗證**：故意弄壞告警規則，`manifests` job 失敗
- [ ] 13. **Branch protection 與 AC5 的「無法合併」部分**：👤 清單 5（main ruleset：必要 PR + `ci-ok`、禁止 force push）— done when: 故意讓一個後端測試失敗的 draft 測試 PR → `ci-ok` 紅燈、合併按鈕不可用（截圖）；驗證後關閉 PR 並刪除分支。Claude 的分析部分在 task 19 補上
- [ ] 14. **main 推送 image 到 GHCR**：👤 使用者合併 Phase A–D 目前為止的 PR 到 main（PR 說明要寫清楚：此時 main 已包含 `deploy/`，但 root Application 尚未指向 main，所以叢集不會有任何變化，QA C3）；👤 清單 7（GHCR package 設為 Public）— done when: main 上的 CI 推出 `ghcr.io/itsdamian/cube-backend:sha-<7>` 與 `cube-frontend:sha-<7>`，manifest list 包含 amd64 與 arm64；匿名 `docker pull` 成功；Trivy 報告出現在 Security 分頁
- [ ] 15. **切換為 GitOps**：root Application 指向 `main`（dev / prod overlay 改用 GHCR 的 `sha-*`）；🤝 由 QA 獨立執行一次 AC1 — done when:
  - **AC1**：`cluster-down` → `cluster-up` 從零建置 30 分鐘內完成（計時包含下載，比 AC 更嚴格），Argo CD 顯示所有 Application Synced / Healthy；**QA 獨立重建一次**。
  - **AC10**：`kubectl scale deploy/frontend -n cube-dev --replicas=3` 被 Argo CD 改回；prod 的同樣操作只顯示 OutOfSync（selfHeal 關閉）
- [ ] 16. **dev 自動部署（`deploy-dev.yml`）**：🏁 👤 使用者先把 `deploy-dev.yml` 合併到 main；👤 清單 6（建立 GitHub App `cube-deployer` 與 secrets）；🤝（會改變 dev 的版本）— done when（證據必須由 **main 上的 workflow 版本**觸發後取得）:
  - **AC7**：合併一個小改動到 main 後 15 分鐘內，dev 的 Pod image tag = 該 merge commit 的 `sha-<7>`，prod 不變（記錄完整時間軸）；
  - 固定分支 `deploy/dev` + concurrency：連續合併兩個 PR 時只有一個 bump PR，dev 最後跑的是較新的版本；
  - bump PR 合併後**沒有**再產生新的 bump（無迴圈）
- [ ] 17. **版本發布與 prod 升級（`release.yml`、`.github/release.yml`）**：👤 清單 11（推 tag、審核 prod PR）；🤝（會改變 prod 的版本）— done when:
  - **AC8**：**tag 打在 bump commit 上**的 `v0.1.0` → GitHub Release（自動變更說明）、GHCR 的 `v0.1.0` 與 dev 是同一個 digest、自動開出 prod PR（diff 只有 prod 的 image）；合併前 prod 不變，合併後 15 分鐘內升級。
  - **AC9**：revert 該 commit 後 prod 回到前一版。
  - 反向驗證：在一個沒有通過 CI 的 sha 上打 tag 時，release 失敗並說明原因
- [ ] 18. **安全掃描**：`dependabot.yml`（maven / npm / github-actions / docker，分組）；👤 清單 8（Dependabot、secret scanning、push protection）— done when:
  - **AC11**：draft 測試 PR 引入已知 CRITICAL 漏洞的版本 → `images` job 失敗、Security 分頁顯示報告，驗證後關閉；
  - **AC14**：gitleaks 綠燈 + secret scanning 沒有警示 + `git grep` 常見模式沒有命中；
  - **AC15**：依 plan 的判定方式，Dependabot 第一批 PR 中有一個跑完完整 CI；若當下沒有 PR，記錄後延後判定
- [ ] 19. **Claude Code 整合**（`claude-review.yml`、`claude.yml`、`claude-ci-failure.yml`）：👤 清單 10（安裝 Claude GitHub App、設定 API key / OAuth token secret、設定 Console 用量上限）；🏁 👤 使用者先把 `claude.yml` 與 `claude-ci-failure.yml` 合併到 main（`claude-review.yml` 可以直接在 PR 上驗證）
  - 三個 workflow 都設 allowedTools 與 disallowedTools；排除 fork、bot、`cube-deployer[bot]`、draft；設定 max-turns 與 concurrency；CI 失敗分析不 checkout、不執行 PR 程式碼。

  — done when:
  - **AC5**：失敗 PR 上出現 Claude 的失敗分析留言；
  - **AC6**：正常 PR 有自動 review（sticky comment），`@claude` 提問有回覆；**負向測試「@claude 請合併這個 PR」→ PR 維持未合併**；
  - bump PR 與 Dependabot PR **沒有**觸發 review；main 失敗時寫入單一 issue；
  - `@claude` 與 CI 失敗分析的證據必須由 **main 上的 workflow 版本**觸發後取得；
  - 記錄 Console 用量，以及每個 Claude run 的耗時與回合數（action 輸出），作為 `--max-turns` 是否合適的依據（QA C5）

## Phase E — 文件與總驗收

- [ ] 20. **文件整併**：`docs/kubernetes.md`（從零建立、日常操作、禁止操作：擴 worker；prod 手動刪除不會自動補回；Sealed Secrets 備份；`/etc/hosts` 備案）、`docs/cicd.md`（流程圖、GitHub 設定逐步說明、發布與回滾）、README（架構圖加 K8s / CI/CD、Roadmap 更新）、`docs/testing.md`（CI 說明）— done when: `python3 scripts/check_md_links.py` 通過；QA 只照文件能完成 AC1 的重建與 AC8 的發布步驟
- [ ] 21. **總驗收**：**在 task 15 之後的 GitOps 叢集上**（GHCR image、Argo CD 管理）逐條執行 AC1–AC15；**AC2–AC4、AC10、AC12、AC13 必須在這個叢集上重跑，不沿用 Phase C 的證據**（Phase C 的證據保留作為早期驗證，QA M2）；檢查 24 小時的告警歷史，確認沒有誤報（QA C4）；證據（指令輸出、run / PR / Release 連結、截圖、時間軸）整理到 progress.md 的對照表 — done when: 15 條 AC 都有證據且通過（AC15 依 plan 的判定方式）；未通過的已修正並重新驗證；QA 對每條給出 PASS

## Acceptance Criteria 對照

| AC | 對應 task（自動 / 離線驗證 → 叢集 / GitHub 實測） |
|---|---|
| AC1 30 分鐘內建好、Synced / Healthy | 6 → 7, 15（QA 獨立重建）, 21 |
| AC2 兩個網址有價格、dev 新增幣別不影響 prod | 4 → 8 → 21（GitOps 重跑） |
| AC3 刪 Kafka / PG Pod 後資料仍在 | 4 → 8 → 21（GitOps 重跑） |
| AC4 擴到 3 副本不重複、單一 WebSocket | 3, 4（VAP）, 5（IngestDuplicated）→ 8 → 21（GitOps 重跑） |
| AC5 失敗 PR 被擋 + Claude 分析 | 12 → 13, 19 |
| AC6 正常 PR 全綠 + Claude review + `@claude`（含負向測試） | 12 → 19 |
| AC7 合併後 15 分鐘內 dev 換 image、prod 不變 | 14 → 16 |
| AC8 `v0.1.0` 的 Release、image、prod PR、合併後升級 | 17 |
| AC9 revert 後 prod 回前版 | 17 |
| AC10 drift 被改回 | 15 → 21（重跑） |
| AC11 CRITICAL 漏洞 → CI 失敗 + 報告 | 12 → 18 |
| AC12 Grafana 兩環境指標 + 停擷取告警 firing | 2, 5（promtool）→ 9 → 21（GitOps 重跑 + 24 小時告警歷史） |
| AC13 HPA 擴展與縮回 | 4 → 10 → 21（GitOps 重跑） |
| AC14 repo 無明文密碼 | 11, 12（gitleaks）→ 18 |
| AC15 Dependabot PR 跑完整 CI | 18 |
