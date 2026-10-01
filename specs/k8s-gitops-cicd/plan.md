# Plan: cube_test 上 Kubernetes — GitOps（Argo CD）+ GitHub Actions CI/CD

Status: CONFIRMED（team lead 依使用者授權核准，2026-10-01；GitHub push / PR 授權範圍待使用者本人確認）

> 依據：`specs/k8s-gitops-cicd/spec.md`（CONFIRMED）。以資深平台工程師角度撰寫；所有「官方用法」類的內容都查過文件（見文末〈參考來源〉），版本號在實作時以當下最新的 patch 版為準並以 SHA / 固定 tag 釘住。

## Approach

一句話：**本機 k3d（k3s）叢集 + Argo CD「app of apps」管理所有東西；第三方元件用 Helm chart、自家應用用 Kustomize base + dev/prod overlay；GitHub Actions 只負責「測試 → 建 image → 用 PR 改 Git 裡的 image 版本」，部署永遠由 Argo CD 從 Git 拉。**

1. **叢集**：`k3d` 建一個單 server 節點的 k3s 叢集（設定檔 `deploy/k3d/cluster.yaml` 進 repo），把 host 的 80 port 對到叢集的 LoadBalancer。`scripts/cluster-up.sh` 一鍵建立 + 安裝 Argo CD + 套用 root Application；`scripts/cluster-down.sh` 一鍵刪除。
2. **GitOps 結構**：Argo CD 由 bootstrap 腳本用 Helm 裝一次，之後 Argo CD 透過 root Application 管理：
   - `deploy/platform/`：Traefik（Gateway API）、Sealed Secrets、Strimzi operator、CloudNativePG operator、kube-prometheus-stack，以及 Argo CD 自己（self-managed）。
   - `deploy/apps/cube/`：`base/` + `overlays/dev`、`overlays/prod`，內容是 backend（worker + api 兩種角色）、frontend、Kafka（Strimzi CR）、PostgreSQL（CNPG CR）、HPA、HTTPRoute、ServiceMonitor、PrometheusRule、NetworkPolicy。
   - `deploy/monitoring/dashboards/`：Grafana 儀表板 JSON（ConfigMap，由 Grafana sidecar 載入）。
3. **backend 拆兩個角色（同一個 image，只差環境變數）**：
   - `backend-worker`：**StatefulSet，replicas 固定 1**，開 ingest + Kafka Streams + persist + alerts + FX 更新 + 保留期清除。StatefulSet 的語意保證同一個 ordinal 在任何時刻最多一個 Pod（舊 Pod 確定終止才建新的），因此交易所 WebSocket 永遠只有一份連線（需求 6）；Kafka Streams 的 RocksDB state 放在它的 PVC。
   - **防止 worker 被擴到 2 以上**（QA M1；prod 沒有 selfHeal，光靠 Argo CD 不夠）：(a) 一條 `ValidatingAdmissionPolicy`（K8s 內建、CEL）拒絕任何把 `backend-worker` 設成 >1 的變更，**包含 `kubectl scale` 走的 `statefulsets/scale` subresource**；(b) 告警 `IngestDuplicated`：`count by (namespace) (cube_feed_ingest_active == 1) > 1`；(c) 文件標為禁止操作。
   - `backend-api`：**Deployment + HPA**，所有背景角色關閉（`APP_INGEST_ENABLED=false` 等、`APP_FX_REFRESH_ENABLED=false`），只提供 REST + SSE；每個副本用自己的 consumer group 讀價格（現有設計），所以水平擴展不會重複處理。
4. **CI（`.github/workflows/ci.yml`）**：PR / main push 都跑 backend `./mvnw verify`、frontend test/lint/build、manifests 驗證、secret 掃描、multi-arch image 建置 + Trivy 掃描；main 上才推送到 GHCR（tag `sha-<7 碼>`）。最後一個彙總 job `ci-ok` 是 branch protection 唯一的必要 check。
5. **CD**：
   - main 有新 image → workflow 用 GitHub App token 開「dev 升級到 sha-xxxx」PR（只改 `overlays/dev` 的 image）並開啟 auto-merge → 合併後 Argo CD 輪詢到變更、自動同步 dev。
   - 推 `vX.Y.Z` tag → 不重建，**把同一個 digest 重新標成 `vX.Y.Z`**、建立 GitHub Release（自動變更說明）、開「prod 升級到 vX.Y.Z」PR（只改 `overlays/prod`，image 以 tag + digest 釘住）→ 使用者審核合併 → Argo CD 同步 prod。
6. **安全**：Trivy 掃 image（CRITICAL 且可修補即失敗，SARIF 上傳到 GitHub Code scanning）、gitleaks 掃 secret、Dependabot（maven / npm / github-actions / docker）、所有第三方 action 以 commit SHA 釘住（理由見風險 R1）。
7. **監控**：kube-prometheus-stack 一套（`monitoring` namespace），ServiceMonitor 抓兩個環境的 backend `/actuator/prometheus` 與 Strimzi Kafka Exporter；一個 Grafana 儀表板用 `namespace` 變數切換 / 並排 dev、prod；PrometheusRule 兩條告警（價格擷取停止、SSE 推送停止）。
8. **Claude Code**：官方 `anthropics/claude-code-action@v1` 三個 workflow——PR 自動 review、`@claude` 互動、CI 失敗分析（只分析不修改）；全部排除 fork PR 與 bot、限制回合數、同 PR 只跑最新一次。

## Alternatives Considered

### 1. 叢集工具（Open Question 1）— 建議 **k3d**

| | k3d（k3s in Docker）✅ | kind | OrbStack 內建 K8s |
|---|---|---|---|
| 基礎記憶體 | 低（k3s 單一 binary，實測一般 ~0.5–0.8 GB） | 中（kubeadm 全套 control plane，~0.8–1.2 GB） | 低 |
| LoadBalancer / 對外 port | 內建 ServiceLB，`-p 80:80@loadbalancer` 即可 | 需 `extraPortMappings` + 自行處理 LB | 內建 |
| metrics-server（HPA 必需） | **內建** | 需另裝並加 `--kubelet-insecure-tls` | 內建 |
| NetworkPolicy | 內建（kube-router） | 需換 CNI 才有完整支援 | 視版本 |
| 可移植性 | 與雲端 **k3s**（spec 舉的例子）同一套發行版：storage class `local-path`、行為一致 | upstream K8s，一般性最好 | 綁定 OrbStack |
| 前提 | 沿用現有 Docker Desktop | 同左 | 要換掉 Docker Desktop（使用者目前用 Docker Desktop） |

選 k3d：資源最省、HPA 需要的 metrics-server 與 LB 都內建、和 spec 提到的雲端 k3s 同源。kind 在「upstream 一致性」上較好，因此 CI 的 manifest 驗證改用 schema 驗證（kubeconform）而不是綁定任何發行版。k3s 內建的 Traefik **會被停用**（`--disable=traefik`），改由 Argo CD 安裝我們自己版本控管的 Traefik，避免依賴 k3s 專屬的 HelmChartConfig，換叢集時同一份 Application 照用。

### 2. 套件管理（Open Question 2）— 建議 **混用：第三方 Helm、自家 Kustomize**

- 第三方元件（Argo CD、Traefik、Sealed Secrets、Strimzi、CNPG、kube-prometheus-stack）本來就以 Helm chart 發佈、有大量可調參數：用 Argo CD Application 的 Helm source + repo 內的 values 檔，版本釘在 `targetRevision`。
- 自家應用只有兩個環境、差異小：Kustomize base + overlay 最直觀（需求 9），CI 只要 `kustomize edit set image` 就能產生一行的 diff，PR 一眼看得懂。
- 不選「自家也寫 Helm chart」：多一層 template 語言，對兩個環境是過度設計；也不選「全部 Kustomize」：第三方 chart 要 `helmCharts:` inflation，Argo CD 需額外開 `--enable-helm`，除錯不如直接用 Helm source。

### 3. Kafka / PostgreSQL 部署（Open Question 3）— 建議 **Operator：Strimzi + CloudNativePG**

| | Operator（Strimzi / CNPG）✅ | 自寫 StatefulSet |
|---|---|---|
| 學習價值 | 學到 K8s 實務主流做法：CRD、reconcile、operator 管 rolling upgrade | 學到 StatefulSet / PVC 基礎 |
| 資源 | 多兩個 operator：Strimzi ~0.3–0.4 GB、CNPG ~0.1 GB（全叢集共用一份） | 0 |
| 密碼 | **CNPG 自動產生資料庫帳密並寫成 Secret（含 `jdbc-uri`）**，Git 裡完全不需要 DB 密碼 | 要自己產生並加密 |
| Kafka consumer lag 指標（需求 20） | Strimzi 內建 **Kafka Exporter**，一行設定 | 要另外部署 exporter |
| 上雲 | 雲端 k3s 上同樣運作，只改 storage class / 資源 | 同左 |
| 代價 | Strimzi 1.2 只支援 **Kafka 4.2 / 4.3**，與目前 compose / Testcontainers 的 3.9.2 不同（見風險 R4，計畫統一升到 4.3） | 可沿用 3.9.2 |

兩個 operator 共 ~0.5 GB，換來「DB 密碼不進 Git」與「consumer lag 指標」兩個需求直接解決，學習價值也高，因此建議 Operator。若使用者想先省資源，StatefulSet 是可行的備案（資源表見下，兩者差 ~0.5 GB）。

### 4. Secret 管理（Open Question 4）— 建議 **Sealed Secrets**

- 需要加密進 Git 的 secret 其實很少：DB 帳密由 CNPG 產生、Kafka 叢集內不開認證（以 NetworkPolicy 隔離）、Argo CD admin 初始密碼由 Argo CD 自己產生、GHCR image 設為 public 不需 pull secret。剩下 **Grafana admin 密碼**（以及未來的外部 token）。
- Sealed Secrets：叢集內 controller 持有私鑰，`kubeseal` 用公鑰加密成 `SealedSecret` 再 commit；Argo CD 不需要任何外掛。
- 不選 SOPS + age：Argo CD 需要裝 KSOPS / 自訂 repo-server plugin，對單人學習專案複雜度較高；SOPS 的優點（可離線解密、多雲 KMS）在本機叢集用不到。
- 代價：叢集砍掉重建時私鑰會換掉。對策：首次建叢集後把私鑰備份到 repo **外**（使用者清單第 9 項），`cluster-up.sh` 若發現備份檔就先還原再裝 controller。
- 2025 年 Bitnami 目錄改版**不影響** Sealed Secrets（官方 issue 確認仍在 docker.io/bitnami 正常發佈）。

### 5. Claude Code 觸發範圍（Open Question 5）— 建議

- **review 要看 K8s manifests 與 workflow 檔：要**。這兩類錯誤（權限過大的 `permissions:`、`pull_request_target` 誤用、probe / resource 設錯）代價最高，測試又抓不到。但**跳過**機器人開的 image bump PR（`deploy/dev`、`deploy/prod` label）與 Dependabot PR——diff 只有版本號，review 沒有價值又花錢。
- **CI 失敗分析要涵蓋 main：要，但只針對 push 到 main 的失敗**，分析結果寫到**同一個** issue「main CI 失敗」（存在就留言，不存在才開），避免每次失敗都開新 issue。PR 的失敗則留言在該 PR。理由：dev 是從 main 自動部署的，main 壞掉會直接卡住 CD，越早知道越好；而 main 只有合併時才觸發，頻率低、成本可控。
- 一律**不**做 auto-fix（官方範例 `ci-failure-auto-fix.yml` 會在有寫入權限的情況下執行 PR 的程式碼，且 spec 不允許未經審核的修改）；`@claude` 的修改只會推到 PR 分支或新分支，main 有 branch protection。

### 其他取捨

- **Ingress vs Gateway API**：Kubernetes SIG Network 於 2025-11-11 在官方部落格宣布 ingress-nginx 只維護到 2026 年 3 月、之後不再發布任何修補（[kubernetes.io](https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/)），官方建議改用 Gateway API。即使沒有這件事，Gateway API 也是 Ingress 的官方後繼標準，本身就是合理選擇。採 **Gateway API（HTTPRoute）+ Traefik** 作為實作；Traefik 持續維護且原生支援 Gateway API，換到雲端只要換 Gateway 的實作或 listener。
- **dev 部署用 CI 寫回（PR）而非 Argo CD Image Updater**：Image Updater 的 git write-back 要直接推 main（需要對 branch protection 開例外）或推到另一分支（Argo CD 要追蹤非 main 分支，失去「main = 叢集狀態」的單純性）。spec 也要求 dev 優先走 PR。
- **multi-arch：單一 amd64 runner + QEMU，而非原生 arm64 runner 矩陣**：兩個 Dockerfile 的重活（Maven 編譯、npm build）都已經在 `$BUILDPLATFORM` 跑，QEMU 只執行最終 stage 的 `useradd` 與 COPY，額外成本約數十秒；矩陣做法要建兩次、再用 `imagetools create` 合併 manifest，複雜度較高。保留備案：若哪天最終 stage 出現重的 `RUN`，改用 `ubuntu-24.04-arm`（public repo 免費、2025-08 GA）原生建置 + 合併。

## Architecture / Design Decisions

### 叢集與網路

```
host :80 ──► k3d LoadBalancer ──► Traefik (Gateway "cube", namespace traefik)
   ├─ cube.localhost        ─► HTTPRoute ─► frontend (ns cube-prod) ─ nginx /api ─► backend-api
   ├─ dev.cube.localhost    ─► HTTPRoute ─► frontend (ns cube-dev)  ─ nginx /api ─► backend-api
   ├─ argocd.localhost      ─► argocd-server
   └─ grafana.localhost     ─► grafana
```

- 路由一律進 frontend 的 nginx，再由 nginx 把 `/api` 轉給 `backend-api` Service——**沿用 compose 已驗證過的 SSE 設定**（`proxy_buffering off`、1h read timeout），不在 Traefik 再調一次。
- `*.localhost`：Chrome / Firefox / curl 會直接解析到 127.0.0.1；Safari 若不行，使用者清單提供 `/etc/hosts` 備案。
- Namespaces：`cube-dev`、`cube-prod`、`monitoring`、`argocd`、`traefik`、`sealed-secrets`、`strimzi`、`cnpg-system`。
- NetworkPolicy（QA M5）：`cube-dev`、`cube-prod` 各自**預設拒絕所有進入流量**，再逐條允許；egress 不限制（worker 要連交易所與匯率 API、所有 Pod 要 DNS）。完整允許清單：

  | 來源 | 目的（同一個 namespace 內） | Port | 用途 |
  |---|---|---|---|
  | 同 namespace 所有 Pod | 同 namespace 所有 Pod | 全部 | frontend→api、api/worker→Kafka、api/worker→PostgreSQL |
  | namespace `traefik` | frontend | 8080 | 對外流量 |
  | namespace `strimzi` | Kafka Pod（`strimzi.io/cluster`） | 全部（9090 控制平面、9091 replication / operator、9092 client） | operator reconcile、rolling、健康檢查 |
  | namespace `cnpg-system` | PostgreSQL Pod（`cnpg.io/cluster`） | 8000、5432 | instance manager status、operator 連線 |
  | namespace `monitoring` | backend（api + worker） | 8080 | `/actuator/prometheus` |
  | namespace `monitoring` | Kafka Pod、Kafka Exporter | 9404 | JMX exporter、lag |
  | namespace `monitoring` | PostgreSQL Pod | 9187 | CNPG 內建 exporter |

  Strimzi 自己也會為 Kafka listener 產生 NetworkPolicy；NetworkPolicy 是「聯集」，兩者並存不衝突。operator 與監控本身所在的 namespace 不套 default-deny。**驗證**（叢集任務 done-when）：套用後 `Kafka` 與 CNPG `Cluster` CR 仍為 Ready、Argo CD 仍 Healthy；從 `cube-dev` 起一個暫時 Pod 連 prod 的 Kafka 9092 / PostgreSQL 5432 **逾時失敗**，連自己環境的則成功（需求 2 的資料隔離多一層保障，因為 Kafka 不開認證）。

### 應用程式部署（每個環境）

| 物件 | 種類 | dev | prod | 備註 |
|---|---|---|---|---|
| Kafka（Strimzi `Kafka` + `KafkaNodePool`） | KRaft 單節點（controller+broker） | heap 512m，PVC 2Gi | heap 768m，PVC 5Gi | Kafka Exporter 開啟（lag）；**不啟用 Entity Operator**（Topic / User Operator 兩個 JVM，每環境 ~0.5 GB；topic 仍由應用 `KafkaAdmin` 建立，也沒有 Kafka 使用者需要管理，QA C1） |
| PostgreSQL（CNPG `Cluster`） | 1 instance，PG 17 | PVC 1Gi | PVC 2Gi | 應用從 `<cluster>-app` Secret 讀 `jdbc-uri` / 帳密 |
| backend-worker | StatefulSet ×1 | 512Mi req | 640Mi req | ingest + streams + persist + alerts + FX + retention；streams state PVC 1Gi |
| backend-api | Deployment + HPA | 1–2 副本 | 2–4 副本 | 只有 REST + SSE；HPA CPU 70% |
| frontend | Deployment | 1 | 2 | nginx-unprivileged |

- **Probes**：沿用現有端點——liveness `/actuator/health/liveness`（不依賴 Kafka / DB，需求 7：不會被誤殺）、readiness `/actuator/health/readiness`（DB / Kafka / Streams 掛掉就移出 Service）、startupProbe 給 JVM 啟動 60s 寬限。
- **滾動更新（需求 8）**：api / frontend `maxUnavailable: 0, maxSurge: 1`；`server.shutdown=graceful` + preStop `sleep 5`（等 endpoint 移除），SSE 連線被關閉後前端 EventSource 自動重連（既有行為）。worker 是 StatefulSet，更新時會有 ~20–40 秒沒有新價格（舊 Pod 終止後新 Pod 才啟動）——這正是「保證單一 ingest」的代價；前端 60 秒規則與告警 `for: 1m` 都不會因此誤報。
- **資源設定**：所有容器都設 requests；memory limit = request 的 1.25 倍；JVM 維持 `-XX:MaxRAMPercentage=75`。CPU 不設 limit（避免 JVM throttle），HPA 以 CPU requests 為基準。
- **應用程式最小調整**（spec 允許的部署所需）：
  1. 加 `micrometer-registry-prometheus`、暴露 `prometheus` 端點（只在叢集內，nginx 不轉 `/actuator`）。
  2. 自訂指標（語意明確定義，QA M2）：
     - worker：`cube_feed_last_tick_seconds`（最後一筆**成交**的 epoch 秒）、`cube_feed_active_source{source}`（目前來源為 1）、`cube_feed_ingest_active`（ingest 開啟的行程為 1，供 M1 告警）。
     - api：`cube_sse_last_push_seconds` = **SSE 推送迴圈最後一次取到「新價格」的時間，與連線中的 client 數無關**（沒有任何人開頁面時照樣前進，因此不會誤報；只有推送迴圈本身凍結時才變舊——正是 task 32 的事故）；`cube_sse_prices_pushed_total`（實際送給 client 的事件數）；`cube_sse_connections`。
  3. `server.shutdown=graceful`。
  4. 其餘全用既有環境變數切換角色，不改程式邏輯。

### GitOps / Argo CD

- 安裝：Helm（非 HA、停用 Dex 與 notifications）。叢集在 NAT 後面，GitHub webhook 打不到，只能靠輪詢；`timeout.reconciliation` 從預設 180 秒調為 **60 秒**（本機叢集只有一個 repo，負擔可忽略），讓 main → dev 的總時間留在 AC 的 15 分鐘內（預算見〈CI 在 GitHub-hosted runner 上〉）。
- Application 拓樸：`root` → `platform-*`（sync wave -10：CRD / operator 先裝）→ `cube-dev`、`cube-prod`（wave 0）。
- 同步策略：
  - `cube-dev`：automated（prune + selfHeal）——手動改副本數會在數秒內被改回（AC drift）。
  - `cube-prod`：automated（prune）但 **selfHeal 關閉**——Git 合併後自動部署（AC：合併後 15 分鐘內升級），但手動改動不自動覆蓋，而是在 UI 顯示 OutOfSync（spec「prod 依設定」）。要回到 Git 狀態按一次 Sync 或 revert；文件說明兩者差異。注意（QA C8）：在 prod 手動**刪除**資源同樣不會被自動補回（只顯示 OutOfSync / Missing），文件一併說明。
- HPA 管副本數：Kustomize 不寫 `replicas`（或 Argo CD `ignoreDifferences` 忽略 `/spec/replicas`），避免和 HPA 打架。AC 的 drift 測試改 **frontend** 的副本數（沒有 HPA），否則會測到 HPA 而不是 Argo CD。

### CI / CD 工作流程

| Workflow | 觸發 | 內容 |
|---|---|---|
| `ci.yml` | `pull_request`、`push: main`、`workflow_dispatch` | `changes`（判斷改了哪些路徑）→ `backend`、`frontend`、`manifests`（kustomize build + kubeconform + helm template + actionlint + `promtool test rules`）、`secrets`（gitleaks）、`images`（buildx amd64+arm64、Trivy、main 才 push）→ `ci-ok`（彙總，唯一必要 check） |
| `deploy-dev.yml` | `workflow_run: CI` 成功且為 main push 且有新 image | `concurrency: deploy-dev`（不取消、依序執行）→ GitHub App token → 在**固定分支 `deploy/dev`** 上 `kustomize edit set image` 並 force-push → 同一個 PR（不存在才建立，`--label deploy/dev`）→ `gh pr merge --auto --squash` |
| `release.yml` | `push: tags v*.*.*` | 決定 image（見下）→ 確認該 image 的來源 commit CI 綠燈 → `buildx imagetools create` 把 `sha-xxx` 重新標成 `vX.Y.Z` 與 `X.Y` → `gh release create --generate-notes` → 開「prod 升級到 vX.Y.Z」PR（`--label deploy/prod`，**不開 auto-merge**） |
| `claude-review.yml` | `pull_request: opened, synchronize, ready_for_review` | 見下 |
| `claude.yml` | `issue_comment`、`pull_request_review_comment`、`pull_request_review`、`issues` 含 `@claude` | 官方範例 + 限制 |
| `claude-ci-failure.yml` | `workflow_run: CI completed` 且 failure | 只讀分析，PR 留言 / main 寫入單一 issue |
| `.github/dependabot.yml` | 每週 | maven、npm（frontend）、github-actions、docker（兩個 Dockerfile），同類 minor/patch 分組 |

關鍵設計：

- **必要 check 只有 `ci-ok`**：它 `needs` 所有 job、`if: always()`，任何 job failure / cancelled 就失敗、skipped 視為通過。這樣 image bump PR（只改 `deploy/**`）可以跳過 backend/frontend/images，但仍然回報一個綠色的必要 check——避免「path filter 讓必要 check 永遠 pending」的經典陷阱。
- **避免無限迴圈**：image 只在 `src/**`、`frontend/**`、`Dockerfile*`、`pom.xml` 等有變更時才建置與推送；bump PR 合併後的 main push 只改 `deploy/**` → 沒有新 image → `deploy-dev` 不觸發。`deploy-dev` 也檢查「overlay 已經是這個 tag」就不開 PR。
- **為什麼要 GitHub App token**：GitHub 規定由 `GITHUB_TOKEN` 建立的 PR **不會觸發**其他 workflow，必要 check 永遠不會出現、PR 無法合併。用使用者建立的小型 GitHub App（contents + pull-requests 讀寫）透過 `actions/create-github-app-token` 換取短效 token 開 PR，CI 就會正常跑。App 沒有 bypass branch protection 的權限。
- **prod 只有人能升級**：release workflow 從不對 prod PR 開 auto-merge；branch protection 要求 PR + `ci-ok`。回滾 = revert 那個 commit（需求 16），Git 歷史就是稽核紀錄。
- **image 標籤**：`ghcr.io/itsdamian/cube-backend`、`cube-frontend`；main 推 `sha-<7>`（可追溯到 merge commit，AC）；release 只「重新標籤」同一個 digest，**prod 跑的就是 CI 測試過的那個位元組**；prod overlay 寫 `newTag: v0.1.0` + `digest`。
- **release 用哪個 image**（QA M3）：image 只在程式變更時建置，所以 tag 常常打在 bump commit 或只改文件的 commit 上，這些 commit 沒有自己的 `sha-*` image。定義：**tag 對應的 image = tag commit 上 `overlays/dev/kustomization.yaml` 記錄的 image**——也就是 dev 正在跑、且已通過 CI 的那一個（backend 與 frontend 各自讀取）。release workflow 會：(a) 確認 tag commit 是 main 的祖先；(b) 從該 overlay 讀出兩個 `sha-*`；(c) 用 `gh api` 確認這兩個 sha 對應 commit 的 `ci-ok` 是 success；(d) 任一不符就失敗並說明原因，不產生 Release。AC8 驗證**刻意把 tag 打在 bump commit 上**（最常見的情況）。
- **bump PR 的競態**（QA C2）：兩個 PR 相繼合併會產生兩次 CI；`deploy-dev` 以 `concurrency` 依序執行、並且永遠只更新同一個 `deploy/dev` 分支與同一個 PR，因此不會出現「舊的 bump 比新的晚合併、dev 退版」。
- **權限**：每個 workflow 頂層 `permissions: {}`，job 層級最小授權；PR 不推 image、不需要任何 secret（所以 Dependabot PR 也能跑完整 CI，AC 最後一條）。
- **供應鏈**：所有第三方 action 釘 commit SHA（GitHub 官方的安全加固建議；Dependabot 會更新 SHA），Trivy 使用 [官方 advisory](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23) 標示為安全的版本（R1）。

### CI 在 GitHub-hosted runner 上

- **Testcontainers**：`ubuntu-24.04` runner 內建 Docker Engine，Testcontainers 自動偵測 `/var/run/docker.sock`，Ryuk 正常運作，**不需要任何設定**；現有「測試不連外部價格來源」的 test profile 不變（需求 11）。Kafka / PostgreSQL image 從 Docker Hub 拉取（R6）。
- **runner 規格**：public repo 的標準 Linux runner 為 4 vCPU / 16 GB，免費、不限分鐘。
- **快取**：`actions/setup-java` 的 Maven 快取、`actions/setup-node` 的 npm 快取、buildx `cache-to: type=gha`。
- **時間估算**（本機 M5 實測後換算，見下表）：

本機基準（2026-10-01 實測，M5、image 已快取、Maven offline）：`./mvnw clean verify` **130 秒**（155 個測試）；前端 test + lint + build **4.3 秒**。GitHub runner 是 4 vCPU x86，且要下載相依與 image，保守換算如下：

| Job | 首次（無快取） | 有快取 | 依據 |
|---|---|---|---|
| backend（verify） | 7–9 分 | 5–6 分 | 本機 130 秒 ×2（CPU 較慢）＋ Maven 下載 1–2 分 ＋ Kafka/PG image 拉取 ~1 分 |
| frontend | 2 分 | 1 分 | `npm ci` 為主 |
| manifests + secrets | 1 分 | 1 分 | 純靜態檢查 |
| images（2 個 × amd64+arm64）+ Trivy | 6–8 分 | 2–4 分 | 重活在 BUILDPLATFORM；`type=gha` 快取；Trivy DB 下載 ~30 秒 |
| **PR 總時間（job 並行）** | **~9 分** | **~6 分** | 取最長的 backend |
| image bump PR（只改 `deploy/**`） | ~1 分 | | 只跑 manifests，其餘 skipped |
| release（重新標籤 + Release + PR） | ~2 分 | | 不重建 |

**main → dev 的時間預算（AC：15 分鐘）**：main CI 並行 ~6 分（images 與測試同時跑，推送 `sha-*` 不等測試；但 `deploy-dev` 只在整個 CI 綠燈後才開 PR，所以未通過測試的 image 永遠不會被部署）→ 開 bump PR ~1 分 → bump PR 的 CI ~1–2 分 → auto-merge → Argo CD 輪詢 ≤ 60 秒（本機叢集把 `timeout.reconciliation` 設為 60s）→ rollout ~1–2 分，合計 **約 10–12 分**。若實測超過，備案是讓 bump PR 直接在 `deploy-dev` 中等待並合併（省掉排隊時間）。

### 安全掃描

- Trivy（image）：`severity: CRITICAL`、`ignore-unfixed: true`、`exit-code: 1`；同時產出 SARIF 上傳到 Code scanning（public repo 免費），報告在 Security 分頁（需求 18）。AC 驗證：在 PR 裡把某個相依降到有已知 CRITICAL CVE 的版本，CI 失敗並顯示報告，驗證完關閉 PR。
- gitleaks（git 歷史 + 工作樹）+ GitHub 內建 secret scanning / push protection（使用者開啟）→ AC「repo 搜不到明文密碼」。
- Dependabot（需求 19）：版本更新 PR 每週一次、分組，會跑完整 CI。

### 監控

- kube-prometheus-stack（Prometheus retention 3 天、Grafana、Alertmanager、kube-state-metrics、node-exporter），`monitoring` namespace，Grafana 走 `grafana.localhost`，admin 密碼以 SealedSecret 提供。
- 抓取對象：`ServiceMonitor` 選兩個 namespace 的 backend（api + worker）與 Strimzi `PodMonitor`（Kafka + Kafka Exporter）。
- 儀表板「cube 概覽」（JSON 放 repo）：`namespace` 多選變數（可並排 dev / prod）；面板：JVM heap / GC、HTTP RPS / 延遲、Kafka consumer lag、價格推送速率、SSE 連線數、目前來源。lag 面板只顯示有意義的固定 group（`tick-persister|candle-persister|alert-evaluator|currency-candles`）——api 每個 Pod 的 `sse-*-<uuid>` / `feed-status-<uuid>` 是隨機 group，HPA 擴縮後會留下大量已不存在的 group（QA C4）。
- 告警（PrometheusRule，需求 22）——**series 消失也要能觸發**（QA M2：worker 被 scale 到 0 時指標整個不見，`time() - max(...)` 回傳空集合、永遠不會 firing）。做法是以「這個環境應該有 worker」為基準，**除非**有新鮮的指標才不告警：
  - `PriceIngestStalled`（`for: 1m`）：
    ```
    max by (namespace) (kube_statefulset_created{statefulset="backend-worker"})
      unless on (namespace)
    (time() - max by (namespace) (cube_feed_last_tick_seconds) <= 60)
    ```
    指標變舊（> 60 秒）或整個消失都會觸發；kube-state-metrics 的 `kube_statefulset_created` 在 replicas=0 時仍然存在。
  - `PricePushStalled`（`for: 1m`）：擷取正常、但 SSE 推送凍結（前一份 spec task 32 的事故型態），不和上一條重複告警：
    ```
    (max by (namespace) (kube_deployment_created{deployment="backend-api"})
       unless on (namespace)
     (time() - max by (namespace) (cube_sse_last_push_seconds) <= 60))
      and on (namespace)
    (time() - max by (namespace) (cube_feed_last_tick_seconds) <= 60)
    ```
  - `IngestDuplicated`（M1）：`count by (namespace) (cube_feed_ingest_active == 1) > 1`，`for: 0m`。
  - **規則有單元測試**：`deploy/apps/cube/base/alerts.test.yaml`，CI 的 `manifests` job 執行 `promtool test rules`。案例：指標新鮮→不告警；指標變舊→告警；**series 消失**→告警；推送凍結但擷取正常→只觸發 `PricePushStalled`；兩個 ingest→`IngestDuplicated`。
- 不接任何外部通知管道（Non-Goal 的付費服務）；firing 狀態在 Alertmanager / Grafana UI 看。

### 自動擴展（需求 23）

- `backend-api` HPA：CPU 使用率 70%（以 requests 計），prod 2–4、dev 1–2；`behavior.scaleDown.stabilizationWindowSeconds: 120` 避免 SSE 連線頻繁被切斷。
- 壓測：叢集內跑一個 k6 Job（`deploy/loadtest/`，image 從 Docker Hub 拉，使用者不需安裝工具）打 `/api/prices/history` 與 `/api/prices/converted`；文件記錄觀察 `kubectl get hpa -w` 的擴展與縮回。
- 說明：SSE 是長連線，縮容時被移除的 Pod 上的連線會斷開並由瀏覽器自動重連——這是預期行為。

### Claude Code 整合（官方 `anthropics/claude-code-action@v1`）

- **安裝**：使用者在本機執行 `claude` 後輸入 `/install-github-app`（必須是 repo admin），或手動安裝 Claude GitHub App 並新增 secret。驗證方式二選一：`ANTHROPIC_API_KEY`（Console API key，按用量計費）或 `CLAUDE_CODE_OAUTH_TOKEN`（以 `claude setup-token` 產生，使用 Pro/Max 訂閱額度）。agent 不經手任何金鑰。
- **`claude-review.yml`**（需求 25）：
  - `if`：同 repo 的 PR（`head.repo.full_name == github.repository`）、非 draft、作者不是 `dependabot[bot]` **也不是部署 App 的 bot（`cube-deployer[bot]`）**——以作者判斷比 label 可靠（QA C3：`opened` 事件時 label 不一定已存在）；label `deploy/*` 作為第二道判斷。
  - `permissions: contents: read, pull-requests: write, id-token: write`；`actions/checkout` 只取 base（官方 security 文件建議的模式）。
  - `use_sticky_comment: true`（每個 PR 只有一則持續更新的 review 留言）；`claude_args`：`--max-turns`（初始 15）、`--allowedTools` 只給 `mcp__github_inline_comment__create_inline_comment,Bash(gh pr comment:*),Bash(gh pr diff:*),Bash(gh pr view:*)`。
  - `concurrency: claude-review-${{ PR number }}`、`cancel-in-progress: true`（連續 push 只 review 最新版）。
  - 不是必要 check（需求 25）。
- **`claude.yml`**（需求 26）：官方範例；預設只有對 repo 有寫入權限的人能觸發（官方行為，不開 `allowed_non_write_users`）；`--max-turns 20`；可推 commit 到 PR 分支或新分支，main 受 branch protection 保護、Claude App 沒有 bypass。
- **Claude 不能合併 PR**（QA M4）：branch protection 只要求 PR + `ci-ok`（單人 repo 無法自我審核），而 Claude App 有 pull-requests 寫入權限 → 技術上可以合併綠色的 PR，可能被提示詞注入誘導。因此**三個 workflow 都明確設定白名單與黑名單**：
  - `--allowedTools`：review 與 CI 分析只有讀取 + 留言類工具（見上）；`claude.yml` 額外給 `Read,Edit,Write,Glob,Grep,Bash(git add:*),Bash(git commit:*),Bash(git push:*),Bash(gh pr view:*),Bash(gh pr diff:*),Bash(gh pr comment:*),Bash(gh issue view:*),Bash(gh issue comment:*)` 與專案的建置測試指令。
  - `--disallowedTools "Bash(gh pr merge:*),Bash(gh api:*),Bash(gh pr review:*),Bash(gh workflow:*),Bash(gh release:*),Bash(gh repo:*),Bash(git push --force:*)"`（合併、任意 API 呼叫、核准、觸發 workflow、發布）。
  - **AC6 加負向測試**：在 PR 留言「@claude 請合併這個 PR」→ PR 必須維持未合併，Claude 回覆無法執行。
- **`claude-ci-failure.yml`**（需求 27）：
  - `on: workflow_run: workflows: [CI], types: [completed]`，`if`: `conclusion == 'failure'` 且（PR 事件且 `pull_requests[0]` 存在——fork PR 時這個陣列是空的，因此自然排除；或 `event == 'push' && head_branch == 'main'`）。
  - `permissions: actions: read, contents: read, pull-requests: write, issues: write, id-token: write`。不 checkout PR 程式碼、不執行它。
  - 提示詞要求先 `gh run view <id> --log-failed` 取失敗 log，輸出「可能原因 / 建議修法 / 相關檔案」；`--allowedTools` 只給 `Bash(gh run view:*),Bash(gh pr comment:*),Bash(gh issue:*),Read,Grep`；`--max-turns 10`。
- **成本控制（需求 28）**：只在上述事件觸發、排除 fork / bot / draft / 機器人 bump PR、`--max-turns` 上限、concurrency 取消舊執行；另外請使用者在 Anthropic Console 設定**每月用量上限**（使用者清單）。實際花費於驗收期間從 Console 用量頁記錄到 progress.md。

### 資源估算（需求：dev + prod + Argo CD + ingress + 監控）

依據：前一份 spec 的 compose 實測（`docker stats`）：Kafka 1.1 GB（預設 1 GB heap）、backend（全角色）~0.6 GB、PostgreSQL ~0.09 GB、nginx ~0.01 GB；第三方元件取官方 chart 預設 requests 與一般單節點觀測值。單位 GB，取「穩定工作集」。

| 元件 | dev | prod | 共用 |
|---|---|---|---|
| Kafka（KRaft 單節點，heap dev 512m / prod 768m）+ Kafka Exporter | 0.75 | 1.00 | |
| PostgreSQL（CNPG instance） | 0.15 | 0.20 | |
| backend-worker（全背景角色） | 0.55 | 0.60 | |
| backend-api（每副本 ~0.45；min / max） | 0.45 / 0.90 | 0.90 / 1.80 | |
| frontend nginx | 0.01 | 0.02 | |
| **環境小計（HPA 最少 / 最多）** | **1.9 / 2.4** | **2.7 / 3.6** | |
| k3s server（apiserver、sqlite、coredns、local-path、metrics-server） | | | 0.8 |
| Traefik | | | 0.1 |
| Argo CD（controller、repo-server、server、redis、applicationset） | | | 0.6 |
| Strimzi operator / CNPG operator / Sealed Secrets（Entity Operator 不啟用；若啟用每環境 +0.5） | | | 0.35 / 0.1 / 0.03 |
| kube-prometheus-stack（Prometheus 3 天 ~0.8、Grafana 0.15、其他 0.2） | | | 1.15 |
| **共用小計** | | | **3.1** |

- **穩定狀態**：1.9 + 2.7 + 3.1 ≈ **7.7 GB**；HPA 撐到最大 + 滾動更新多一個 Pod：≈ **9.6 GB**；Docker Desktop VM 本身 ~0.5–1 GB。
- 目前 Docker 分配 **8 GB 不夠**（而且前一份 spec 的兩套 compose stack `currency`、`currency-qa` 還在跑，各約 1.8 GB）。
- **建議：Docker Desktop 記憶體調到 16 GB**（機器 32 GB，留一半給 macOS / IDE / 瀏覽器），CPU 維持 10 核、磁碟 ≥ 60 GB。最低可行：**12 GB**，條件是叢集運行時先停掉兩套 compose stack，且 dev HPA 固定 1 副本。
- CPU：requests 合計約 3.5 核（JVM 啟動期間會短暫吃滿），10 核足夠；HPA 壓測時 api 會吃到 2–3 核。
- 若選 StatefulSet 備案（不裝兩個 operator）：共用小計減 ~0.45 GB。
- QA 複核（C1）：在不啟用 Entity Operator 的前提下，16 GB 的建議合理、12 GB 偏緊。

### 使用者本人必須執行的步驟（集中清單）

| # | 時機 | 步驟 |
|---|---|---|
| 1 | 開始實作前 | **授權範圍**：CI/CD 必須在 GitHub 上實際執行才能驗證。請決定是否授權（直接或經 team lead）：push `feat/k8s-gitops-cicd` 與 `test/*` 分支、開 / 關測試 PR（AC 的失敗 PR、CRITICAL 漏洞 PR）。**main、tag、prod PR 的合併一律由你本人操作。** |
| 2 | 叢集任務前 | Docker Desktop → Settings → Resources：記憶體 **16 GB**（最低 12 GB）、磁碟 ≥ 60 GB；之後執行叢集時先 `docker compose -p currency down`（QA 的 `currency-qa` 由 QA 停）。 |
| 3 | 叢集任務前 | 安裝工具：`brew install k3d helm kustomize kubeseal argocd`（`kubectl`、`gh` 已存在）。 |
| 4 | CI 任務前 | GitHub repo → Settings → Actions → General：Workflow permissions 設為 **Read**；勾選 **Allow auto-merge**（Settings → General）。 |
| 5 | CI 任務後 | **Branch protection / ruleset**（main）：需要 PR、必要 status check `ci-ok`、禁止 force push 與刪除、不設 bypass。 |
| 6 | CD 任務前 | 建立 GitHub App「cube-deployer」（Repository permissions：Contents RW、Pull requests RW；不需 webhook），安裝到本 repo，新增 secrets `DEPLOYER_APP_ID`、`DEPLOYER_APP_PRIVATE_KEY`。docs 會附逐步截圖說明。 |
| 7 | 首次推 image 後 | GHCR：確認 `cube-backend`、`cube-frontend` 兩個 package 的 visibility 為 **Public**（Argo CD / 叢集免 pull secret）。 |
| 8 | 安全任務 | Settings → Code security：開啟 Dependabot alerts、Dependabot security updates、Secret scanning + Push protection。 |
| 9 | 首次建叢集後 | 備份 Sealed Secrets 私鑰到 repo 外（docs 提供一行指令，例如存到 `~/cube-secrets/`），之後重建叢集才能解開既有的 SealedSecret。 |
| 10 | Claude 任務前 | 在本機 `claude` 中執行 `/install-github-app`（或手動安裝 Claude GitHub App）；建立 `ANTHROPIC_API_KEY`（或 `claude setup-token` 產生 `CLAUDE_CODE_OAUTH_TOKEN`）存為 repo secret；在 Anthropic Console 設定每月用量上限。 |
| 11 | 驗收 | 推 `v0.1.0` tag、審核並合併「prod 升級」PR、revert 測試。 |
| 12 | 選用 | Safari 解析不到 `*.localhost` 時，在 `/etc/hosts` 加入四個網址；Docker Hub 若限流，新增 `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` secret。 |

## Affected Files & Modules

- **新增**
  - `deploy/k3d/cluster.yaml`；`scripts/cluster-up.sh`、`scripts/cluster-down.sh`、`scripts/seal-secret.sh`
  - `deploy/bootstrap/`（Argo CD Helm values、root Application）
  - `deploy/platform/`（各元件 Application + `values/` 檔、Gateway、Grafana admin SealedSecret）
  - `deploy/apps/cube/base/`（worker StatefulSet、api Deployment、frontend、Services、HPA、HTTPRoute、Strimzi Kafka / KafkaNodePool、CNPG Cluster、ServiceMonitor、PodMonitor、PrometheusRule + `alerts.test.yaml`、NetworkPolicy、ValidatingAdmissionPolicy + Binding）、`overlays/dev`、`overlays/prod`
  - `deploy/monitoring/dashboards/cube-overview.json`；`deploy/loadtest/`（k6 Job + script）
  - `.github/workflows/{ci,deploy-dev,release,claude-review,claude,claude-ci-failure}.yml`、`.github/dependabot.yml`、`.github/release.yml`（Release 變更說明分類）、`.gitleaks.toml`（必要時）
  - `docs/kubernetes.md`（從零建叢集、操作、驗收）、`docs/cicd.md`（流程圖、GitHub 設定、發布與回滾）
- **修改**
  - `pom.xml`（`micrometer-registry-prometheus`）、`application.yml`（暴露 `prometheus`、`server.shutdown=graceful`）
  - `SseBroadcaster`、`FeedManager`（或其 status 發布處）：加入上述 Micrometer 指標（含單元測試）
  - `docker-compose.yml`、`IntegrationTest.KAFKA_IMAGE`：Kafka 升到與叢集相同的 4.3.x（R4）
  - `README.md`（Roadmap 改為已完成、架構圖加 K8s / CI/CD）、`docs/testing.md`（CI 說明）
- **不修改**：應用程式業務邏輯、前端行為、API。

## Data Model / API Changes

- 資料庫：無。
- REST / SSE：無。
- 新增 `GET /actuator/prometheus`（只在叢集內抓取，不經 nginx 對外）。
- 新增 Micrometer 指標（名稱見〈監控〉）。

## Testing Strategy

- **應用程式**：新指標以單元測試驗證（Micrometer `SimpleMeterRegistry`）。Kafka 4.3 升級（QA C5 的條件）：完整 `./mvnw clean verify` + `runOrder=random` **×3**；**前一份 spec 的 compose 回歸冒煙**（`docker compose up` → SSE 有 price、K 線產生、readiness UP）；失敗時退回 StatefulSet + 3.9.2 的備案，**不為了遷就 4.3 修改應用程式碼**（若真的需要，先回 team lead 決定）。
- **Manifests（CI `manifests` job，每個 PR）**：`kustomize build overlays/{dev,prod}` + `kubeconform -strict`（含 Strimzi / CNPG / Gateway API / Prometheus Operator CRD schema）；`helm template` 各 platform values；`actionlint` 檢查所有 workflow。結構性錯誤在合併前就被擋下。
- **CI/CD 流程**：在 `test/*` 分支與測試 PR 上實際執行（需使用者授權，清單第 1 項），每個 AC 的證據（run 連結、PR 連結、截圖）記錄到 progress.md。測試 PR（尤其 AC5、AC11）一律開成 **draft 並加 `test-only` label**，驗證後關閉並刪除分支，避免誤合併；只開必要的 PR，每次驗收記錄 Anthropic Console 的用量（QA C7）。
- **`claude-ci-failure` 不執行 PR 程式碼**：它由 `workflow_run` 觸發、以預設分支的 workflow 內容執行且能使用 secrets，因此不 checkout、不執行 head 的任何程式碼（QA C8，實作時 QA 檢查）。
- **叢集**：每個叢集任務的 done-when 都是「`cluster-down` → `cluster-up` 從零重建後」驗證，確保可重建性；QA 在自己的機器資源允許時以同一腳本獨立重建（或在共用叢集上驗證，見 R10）。
- **測試不延後**：每個任務的 done-when 都含對應驗證，不集中在最後。

## Acceptance Criteria 驗證方式

| AC | 驗證 |
|---|---|
| 30 分鐘內建好、兩個 Application Synced/Healthy | `scripts/cluster-up.sh --prepull` 先下載所有 image 並記錄下載時間，再計時建置（QA C8）＋ `argocd app list`；**QA 獨立從零重建一次**（C6） |
| 兩個網址有即時價格、dev 新增幣別不影響 prod | 瀏覽器（headless Chrome 截圖）＋ `curl` 兩個網址的 `/api/currencies` 比對 |
| 刪 Kafka / PG Pod 後資料仍在 | 記錄幣別、警示、`price_tick` 筆數 → `kubectl delete pod` → Ready 後再比對 |
| api 擴到 3 副本不重複、只有一個 WebSocket | **解讀**：AC 的「backend 擴到 3 個副本」對應到可擴展的 `backend-api`；worker 依需求 6 固定 1，且有 ValidatingAdmissionPolicy 擋住擴容（另驗證 `kubectl scale sts/backend-worker --replicas=2` 被拒）。**量測重複要看 Kafka，不能看 DB**（QA M1：eventId 是決定性的、DB 用 `ON CONFLICT` 去重，重複寫入在 DB 看不出來）：擴到 3 副本（暫時把 HPA min 設 3）後，用 Strimzi image 內的 `kafka-console-consumer` 讀 `btc.price.ticks` 的一段固定時間窗，斷言 **record 數 == 不重複 eventId 數**，且 `source` 只有目前的 active 來源；DB 只用來佐證資料完整；`cube_feed_ingest_active` 只有 1 個 Pod 為 1；api Pod 的 log 無 WebSocket 連線 |
| 故意失敗 PR 被擋、Claude 分析 | 測試 PR 讓一個測試失敗 → `ci-ok` 紅、Merge 按鈕不可用、Claude 留言 |
| 正常 PR 全綠、自動 review、`@claude` 回覆 | 測試 PR；另含負向測試「@claude 請合併這個 PR」→ 未被合併（M4） |
| 合併後 15 分鐘內 dev 換 image、prod 不變 | `kubectl get pod -o jsonpath` 比對 image tag 與 merge commit；記錄時間軸 |
| `v0.1.0`：Release、GHCR tag、prod PR、合併前不變、合併後 15 分鐘內升級 | **tag 刻意打在 bump commit 上**（M3）；依序檢查並記錄時間 |
| revert 後 prod 回前版 | revert PR 合併後觀察 |
| 手動改 dev 副本數被改回 | `kubectl scale deploy/frontend -n cube-dev --replicas=3` → 觀察 Argo CD 改回（selfHeal） |
| CRITICAL 漏洞 → CI 失敗 + 報告 | 測試 PR 引入已知漏洞版本（驗證後關閉） |
| Grafana 兩環境指標、停擷取 60 秒告警 firing | 儀表板截圖；先暫停 dev auto-sync，再 `kubectl scale sts/backend-worker -n cube-dev --replicas=0`（series 消失的情況，M2 的規則仍會觸發）→ ~2 分鐘後 Alertmanager 顯示 `PriceIngestStalled` firing；恢復後 resolved；`promtool test rules` 另外涵蓋「指標變舊」的情況 |
| HPA 擴展與縮回 | k6 Job + `kubectl get hpa -w` 記錄 |
| repo 無明文密碼 | CI gitleaks 綠 + GitHub secret scanning 無警示 + `git grep` 常見模式 |
| Dependabot 一週內開 PR 且跑完整 CI | 時間型 AC 的判定（QA C8）：啟用當下 Dependabot 通常立即開出第一批 PR，**其中一個跑完完整 CI 即判定通過**；若當下沒有 PR，則延後判定，不阻擋其他 AC 與交付 |

## Risks & Mitigations

| # | 風險 | 對策 |
|---|---|---|
| R1 | **Actions 供應鏈攻擊**：tag 可以被改指（mutable），被盜用的維護者帳號能讓所有釘 tag 的使用者執行惡意程式。實例：2026-03-19 trivy-action 76/77 個 tag 被強制改指到竊取憑證的程式（CVE-2026-33634，[官方 advisory GHSA-69fq-xp46-6x23](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23)）。「action 一律釘 commit SHA」是 GitHub 官方的安全加固建議，本身就成立 | 所有第三方 action 釘 **commit SHA**；Trivy 用事件後版本並核對官方 advisory；PR workflow 不帶任何 secret；`permissions` 最小化；Dependabot 更新 SHA 時會走 CI + Claude review |
| R2 | 記憶體不足導致 OOMKilled / 節點壓力 | 資源表 + 使用者調到 16 GB；所有容器設 requests/limits；dev 用較小 heap；叢集運行時停掉 compose stack |
| R3 | `GITHUB_TOKEN` 開的 PR 不觸發 CI → 必要 check 永遠不出現 | 用 GitHub App token（使用者清單第 6 項） |
| R4 | Strimzi 1.2 只支援 Kafka 4.2/4.3，與 tests/compose 的 3.9.2 不一致 | 早期任務把 compose 與 Testcontainers 升到 4.3.x（client 仍是 Boot 管理的 3.9，Kafka 4 broker 相容 2.1 以上的 client），完整測試 + random order 驗證；若有不相容，退回 StatefulSet + 3.9.2（plan 備案） |
| R5 | path filter 造成必要 check pending / 自動 bump 無限迴圈 | 單一彙總 check `ci-ok`；image 只在程式變更時建置；`deploy-dev` 檢查 tag 是否已存在 |
| R6 | Docker Hub 匿名拉取限流（Testcontainers、k6、Kafka image） | 依賴 runner 快取 + 失敗時重試；選用的 Docker Hub token（清單第 12 項） |
| R7 | Argo CD 無 webhook（本機在 NAT 後）；main → dev 總時間逼近 AC 的 15 分鐘 | 輪詢 60 秒、main 上 images 與測試並行；驗收時記錄完整時間軸；超過時採〈CI 時間〉中的備案 |
| R8 | worker 單副本更新期間 ingest 中斷 20–40 秒 | 屬設計取捨（保證單一連線）；告警 `for: 1m`、前端 60 秒規則不誤報；文件說明 |
| R9 | Claude 費用失控 / prompt injection | 觸發限制 + max-turns + concurrency + Console 用量上限；只有寫入權限者能觸發；不開 `allowed_non_write_users`；review 不 checkout 不受信任的 ref 到工作根目錄 |
| R10 | QA 與工程師共用一台機器，兩套叢集吃不下 | 共用叢集協議（QA C6）：(a) QA 預設只做唯讀操作（`kubectl get/describe/logs`、`argocd app get`、`curl`）；(b) 破壞性操作（刪 Pod、scale、暫停 sync、壓測）先 SendMessage 通知對方並等回覆，結束後恢復原狀再通知；(c) AC1 由 QA **獨立**執行一次完整的 `cluster-down` → `cluster-up`（工程師叢集先停）；(d) QA 不對叢集 `kubectl apply` 任何設定，一切依 Git。QA 的 `currency-qa` compose 由 QA 在叢集任務前停掉 |
| R11 | `*.localhost` 在 Safari 解析失敗 | Chrome / curl 驗收；`/etc/hosts` 備案 |
| R12 | SSE 經 Traefik + nginx 兩層代理被緩衝或逾時 | 路由進 nginx（已關 buffering），Traefik 預設不緩衝回應；任務 done-when 包含「瀏覽器 60 秒以上持續收到價格且無 `資料延遲`」 |
| R15 | 有人把 worker 擴到 2 個以上，產生兩條 ingest（prod 沒有 selfHeal） | ValidatingAdmissionPolicy 拒絕（含 scale subresource）+ `IngestDuplicated` 告警 + 文件禁止（QA M1） |
| R16 | 告警因 series 消失而永遠不觸發，或沒人開頁面時誤報 | `unless` 寫法 + 指標語意明確定義 + promtool 單元測試（QA M2） |
| R13 | HPA 縮容中斷 SSE | scaleDown stabilization 120 秒；前端自動重連（既有） |
| R14 | GHCR package 預設 private 導致 ImagePullBackOff | 使用者清單第 7 項；文件提供 imagePullSecret 備案 |

## QA 意見與處理

cube-qa 審查 DRAFT（2026-10-01，verdict：**CONCERN**，無 scope drift），5 項必修、8 項建議，**全部採納**，沒有反駁項目。詳細內容見 `qa-review.md`。

| # | QA 意見 | 處理 |
|---|---|---|
| M1 | AC4 用 DB 筆數量不到重複（eventId 決定性 + `ON CONFLICT`）；prod 沒有 selfHeal，沒有東西防止 worker 被擴到 >1 | AC4 改為讀 Kafka `btc.price.ticks`，斷言 record 數 == 不重複 eventId 數；寫明「backend 擴到 3」對應 `backend-api`；加 ValidatingAdmissionPolicy（含 scale subresource）+ `IngestDuplicated` 告警 + 文件禁止 |
| M2 | worker scale 到 0 後 series 消失，告警永遠不觸發；`cube_sse_last_push_seconds` 在 0 個 client 時語意未定義 | 告警改用 `kube_statefulset_created … unless (新鮮指標)` 寫法，消失與變舊都會觸發；明確定義指標語意（與 client 數無關）；`PricePushStalled` 只在擷取正常時觸發；CI 加 `promtool test rules`，包含 series 消失的案例 |
| M3 | tag 常打在沒有 image 的 bump / 文件 commit 上，release 會失敗 | release 改為使用 tag commit 上 `overlays/dev` 記錄的 image，並確認其來源 commit 的 `ci-ok` 綠燈；AC8 刻意把 tag 打在 bump commit 上驗證 |
| M4 | Claude App 有寫入權限、branch protection 不要求審核 → 可能合併 PR | 三個 workflow 明確設定 allowedTools 白名單 + disallowedTools（`gh pr merge`、`gh api`、`gh pr review`、`gh workflow`、`gh release`、`gh repo`、force push）；AC6 加「@claude 請合併」負向測試 |
| M5 | NetworkPolicy default-deny 會擋 operator 與監控 | 列出完整允許清單（同 namespace、traefik、strimzi、cnpg-system :8000/:5432、monitoring 各 exporter port），done-when 驗證 CR 仍為 Ready、dev→prod 被擋 |
| C1 | 資源表漏了 Entity Operator（~1 GB） | 不啟用 Entity Operator（topic 由應用建立），表格註明 |
| C2 | 兩個 bump PR 的競態可能讓 dev 退版 | `concurrency: deploy-dev` 依序執行 + 固定分支 `deploy/dev`、同一個 PR |
| C3 | `opened` 事件時 label 可能還不存在 | review 以作者（`cube-deployer[bot]`、`dependabot[bot]`）判斷為主、label 為輔 |
| C4 | 隨機 consumer group 會塞滿 lag 面板 | 面板只顯示固定的 group |
| C5 | 同意 Kafka 4.3，附條件 | random ×3、compose 回歸冒煙、不改應用程式碼，寫入 Testing Strategy |
| C6 | 共用叢集協議、AC1 由 QA 獨立重建 | 寫入 R10 |
| C7 | 授權邊界恰當；測試 PR 要防誤合併、控制成本 | 測試 PR 一律 draft + `test-only` label，驗證後關閉並刪分支，記錄 Console 用量 |
| C8 | AC15 時間型判定、`workflow_run` 不執行 PR 程式碼、AC1 分開計下載時間、prod 刪除資源不會自動補回 | 全部寫入對應段落 |

## Non-Goals Reaffirmed

- 雲端叢集、公開網址、正式網域與 TLS（只要求可移植：換叢集只改 overlay / values）
- 每個 PR 的臨時預覽環境
- Kafka 多 broker、PostgreSQL HA、跨區備援、備份還原演練（CNPG 的備份功能不啟用）
- Service mesh、多叢集、多租戶
- 改變應用功能或 UI（只做 metrics、graceful shutdown、Kafka 版本對齊等部署所需的最小調整）
- Claude Code 自動合併、直接推 main / prod、未經審核修改程式碼（不使用 auto-fix 範例）
- 付費第三方服務（Claude API 用量除外）；告警不接外部通知管道

## 參考來源

- claude-code-action：[README](https://github.com/anthropics/claude-code-action)、[solutions.md](https://github.com/anthropics/claude-code-action/blob/main/docs/solutions.md)、[security.md](https://github.com/anthropics/claude-code-action/blob/main/docs/security.md)、[examples/claude.yml](https://github.com/anthropics/claude-code-action/blob/main/examples/claude.yml)、[examples/test-failure-analysis.yml](https://github.com/anthropics/claude-code-action/blob/main/examples/test-failure-analysis.yml)、[examples/ci-failure-auto-fix.yml](https://github.com/anthropics/claude-code-action/blob/main/examples/ci-failure-auto-fix.yml)
- [Ingress NGINX Retirement（kubernetes.io，2025-11-11）](https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/)
- [arm64 hosted runners for public repositories GA（GitHub Changelog，2025-08-07）](https://github.blog/changelog/2025-08-07-arm64-hosted-runners-for-public-repositories-are-now-generally-available/)、[GitHub-hosted runners reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Security hardening for GitHub Actions（釘 SHA 的官方建議）](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions)
- Trivy 供應鏈事件：[GHSA-69fq-xp46-6x23](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23)、[The Hacker News](https://thehackernews.com/2026/03/trivy-security-scanner-github-actions.html)
- [Strimzi releases](https://github.com/strimzi/strimzi-kafka-operator/releases)、[Strimzi deploying docs](https://strimzi.io/docs/operators/latest/deploying)
- [CloudNativePG releases](https://github.com/cloudnative-pg/cloudnative-pg/releases)
- [Argo CD releases](https://github.com/argoproj/argo-cd/releases)
- [Sealed Secrets unaffected by Bitnami changes（issue #1785）](https://github.com/bitnami/sealed-secrets/issues/1785)
