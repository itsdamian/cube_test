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
