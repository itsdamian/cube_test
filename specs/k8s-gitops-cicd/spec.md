# cube_test 上 Kubernetes — GitOps（Argo CD）+ GitHub Actions CI/CD

Status: CONFIRMED

## Summary

把已完成的即時比特幣價格儀表板（`specs/realtime-btc-kafka-react/`）部署到本機 Kubernetes 叢集，分成 dev 與 prod 兩套環境，並用 GitHub Actions 建立完整的 CI/CD：每個 PR 自動測試與掃描、合併到 main 自動部署 dev、打版本 tag 自動發布並升級到 prod。部署採 GitOps：叢集內的 Argo CD 從 repo 拉取設定並同步，因此本機叢集不需要對外開放。另外加入安全掃描、監控、自動擴展，並把 Claude Code 接進 PR 流程協助 code review 與 CI 失敗分析。這是學習 K8s 與 CI/CD 的個人專案；本機先做，但設定要能之後換到雲端叢集時只改設定、不重寫。

## Goals

- 在本機（Apple Silicon Mac）建立可重建的 Kubernetes 叢集，部署完整系統：backend、frontend、Kafka、PostgreSQL
- dev 與 prod 兩套環境在同一叢集的不同 namespace，彼此資料與設定隔離
- GitOps：Argo CD 監看本 repo，環境設定的變更（含 image 版本）由 Git 驅動，叢集狀態與 Git 一致
- CI（GitHub Actions）：PR 與 main 上自動跑後端、前端測試、lint、建置 multi-arch image、安全掃描
- CD：
  - 合併到 main → 自動更新 dev 的 image 版本 → Argo CD 部署到 dev
  - 打 semver tag（例如 `v1.2.0`）→ 產生 GitHub Release 與版本化 image → prod 升級到該版本
- 安全掃描：image 漏洞掃描、相依套件自動更新提醒
- 監控：Prometheus + Grafana，能看到 JVM、Kafka consumer lag、價格推送狀況
- 自動擴展：負責 API / SSE 的部分依負載自動增減副本；價格擷取（ingest）維持單一副本
- Claude Code 加入 CI/CD：PR 自動 code review、可在 PR / issue 用 `@claude` 互動、CI 失敗時自動分析原因並在 PR 留言
- 文件化：任何人照 docs 能從零建立叢集、裝好 Argo CD、看到兩套環境運作

## Non-Goals

- 雲端叢集、對外公開網址、正式網域與 TLS 憑證（之後另開 spec；本 spec 只要求設定可移植）
- 每個 PR 一套臨時預覽環境（本機叢集 GitHub 連不到；PR 只做自動測試與結果回報）
- Kafka 多 broker / PostgreSQL 高可用、跨區備援、備份還原演練
- Service mesh、多叢集、多租戶
- 改變應用程式功能或 UI（只允許為部署所需的最小調整，例如暴露 metrics 端點、角色開關、設定項）
- Claude Code 自動合併 PR、直接推送到 main 或 prod、或在沒有人審核的情況下修改程式碼
- 付費的第三方服務（Claude API 用量除外，見 Constraints）

## Requirements / User Stories

### 叢集與環境
1. 作為開發者，我能用一個指令（或照 docs 的少數步驟）在本機建立叢集並裝好必要元件（Ingress controller、Argo CD、監控），也能一個指令砍掉重建。
2. dev 與 prod 各自擁有獨立的 backend、frontend、Kafka、PostgreSQL 與設定；在 dev 建立的幣別或警示不會出現在 prod。
3. 兩套環境都能從瀏覽器用不同的本機網址存取（例如 `dev.cube.localhost`、`cube.localhost`），不需要 `kubectl port-forward`。
4. Kafka 與 PostgreSQL 的資料存在持久化 volume，Pod 重啟或重新排程後資料仍在。
5. 所有密碼、token 不以明文 commit 到 Git；做法需能在 GitOps 流程下運作。

### 應用程式部署
6. 價格擷取（ingest：WebSocket 連線 + 故障切換）在每個環境只會有一個副本在運作，即使 API / SSE 擴展到多副本也一樣。
7. backend 使用現有的 liveness / readiness 端點作為 K8s probes；Kafka 或資料庫掛掉時 Pod 不會被誤殺，但會從 Service 移除。
8. 更新版本時採滾動更新，更新過程中前端持續可用（SSE 斷線後會自動重連）。
9. 環境間的差異（副本數、資源、網址、image 版本）用同一份基底設定加上環境覆寫來表達，不複製整份設定。

### CI（GitHub Actions）
10. 每個 PR 自動執行：後端 `./mvnw verify`、前端 `npm ci && npm test && npm run lint && npm run build`、backend 與 frontend image 建置（amd64 + arm64），結果以 status check 呈現在 PR 上。
11. CI 不連真實的外部價格來源（沿用現有測試策略），在 GitHub-hosted runner 上穩定通過。
12. main 設定 branch protection：必要的 status checks 未通過不能合併。
13. 合併到 main 後，image 推送到 GHCR，標籤可追溯到 commit（例如 commit SHA）。

### CD（GitOps）
14. 合併到 main 後，CI 自動更新 dev 環境設定中的 image 版本（以 commit 方式寫回 repo），Argo CD 偵測到後自動同步 dev。
15. 推送 semver tag 後：建立 GitHub Release（含自動產生的變更說明）、推送以版本號標記的 image，並**自動開一個「prod 升級到 vX.Y.Z」的 PR**（diff 只有 prod 的 image 版本）。使用者審核並合併後，Argo CD 才同步 prod。prod 不會在沒有人工核准的情況下變更。
16. prod 的版本變更可以從 Git 歷史查到「誰、何時、升到哪個版本」；要回滾時，revert 那個 commit 即可，不需要手動操作叢集。
17. 叢集內實際狀態若被手動改動（drift），Argo CD 能偵測並（dev 自動、prod 依設定）修正回 Git 的狀態。

### 安全掃描
18. CI 對兩個 image 做漏洞掃描；有可修補的 CRITICAL 漏洞時 CI 失敗，掃描報告可在 GitHub 上查看。
19. 啟用相依套件自動更新提醒（Maven、npm、GitHub Actions、Docker base image），以 PR 形式提出。

### 監控
20. Prometheus 收集 backend 的 metrics（JVM、HTTP、Kafka consumer lag、應用自訂指標：已推送價格數、目前 active 來源、SSE 連線數）。
21. 監控為兩個環境共用一套（獨立的 monitoring namespace），以 namespace 區分 dev / prod。Grafana 提供至少一個預先建好的儀表板，可切換或並排比較兩個環境；儀表板設定以檔案形式存在 repo（GitOps 管理）。
22. 至少一條告警規則：價格停止推送超過 60 秒（呼應前一份 spec task 32 的事故）。

### 自動擴展
23. API / SSE 的部分設定 HPA，依 CPU（或可說明的指標）在設定的最小與最大副本之間調整；能用壓測或模擬負載觀察到擴展與縮回。

### 版本發布
24. 版本號遵循 semver；每個 Release 有自動產生的變更說明（依 PR 標題或 conventional commits）。

### Claude Code 整合
25. PR 開啟或更新時，Claude Code 自動做 code review，以 PR 留言或 review comments 呈現；review 是輔助意見，不作為必要 status check。
26. 在 PR 或 issue 留言 `@claude` 可以請 Claude Code 回答問題或提出修改建議；任何程式碼修改都以新 commit 推到該 PR 分支或開新 PR，由人審核後才合併，不能直接推 main。
27. CI 失敗時，Claude Code 自動分析失敗的 log，在 PR 留言說明可能原因與修法建議。
28. Claude Code 的觸發有成本控制：只在指定事件觸發、限制每次執行的回合數，fork 來的 PR 不觸發（避免 secret 外洩與濫用）。

## Acceptance Criteria

- [ ] 在乾淨的 Mac 上照 docs 執行，30 分鐘內（不含下載時間）建好叢集；Argo CD UI 顯示 dev 與 prod 兩個應用都是 Synced / Healthy
- [ ] 瀏覽器開啟 dev 與 prod 兩個網址都能看到即時跳動的價格；在 dev 新增一個幣別後，prod 的幣別清單不受影響
- [ ] 刪除 Kafka 與 PostgreSQL 的 Pod，重建後先前的幣別、警示、歷史價格仍在
- [ ] 把 backend 擴到 3 個副本後，Kafka 中價格事件沒有重複寫入（逐筆價格筆數和單副本時同一時段相符），且只有一個 Pod 持有交易所 WebSocket 連線
- [ ] 開一個故意讓後端測試失敗的 PR：status check 顯示失敗、無法合併，Claude Code 在 PR 留言分析失敗原因
- [ ] 開一個正常的 PR：所有 check 通過，Claude Code 自動留下 review；在 PR 留言 `@claude` 提問能得到回覆
- [ ] 合併到 main 後 15 分鐘內，dev 跑的是新的 image（Pod 的 image tag 對得上 merge commit），prod 不變
- [ ] 推送 `v0.1.0` tag 後：GitHub Release 出現、GHCR 有 `v0.1.0` image、自動開出「prod 升級到 v0.1.0」的 PR，且 prod 在 PR 合併前**不變**；合併後 15 分鐘內 prod 升級到 `v0.1.0`
- [ ] revert 「升級 prod」的 commit 後，prod 回到前一版
- [ ] 用 `kubectl` 手動把 dev 的副本數改掉，Argo CD 在設定的時間內改回 Git 的值
- [ ] 在 image 中刻意引入一個已知有 CRITICAL 漏洞的套件版本，CI 失敗並在 GitHub 顯示掃描報告
- [ ] Grafana 儀表板能看到兩個環境的 JVM、Kafka lag、價格推送速率；停掉 dev 的價格擷取 60 秒以上，告警變成 firing
- [ ] 對 API / SSE 施加負載，HPA 在設定範圍內增加副本，負載停止後縮回
- [ ] repo 中搜尋不到任何明文密碼或 token（以 secret 掃描工具或 grep 驗證）
- [ ] Dependabot（或等效工具）在啟用後一週內至少開出一個相依更新 PR，且該 PR 會跑完整 CI

## Constraints & Assumptions

- **叢集**：本機、Apple Silicon（Apple M5、32GB RAM、10 核；Docker 目前分配 8GB）。dev + prod 兩套（各含 Kafka 與 PostgreSQL）+ Argo CD + Prometheus/Grafana 的記憶體需求可能超過 8GB；plan 必須估算資源，必要時要求使用者調高 Docker 記憶體，或縮小 dev 的資源配置。
- **可移植**：之後換到雲端叢集（例如 k3s）時，應只需要改環境覆寫設定（網址、storage class、資源），不改基底設定與 CI。
- **GitHub**：repo `itsdamian/cube_test` 為 public；image 放 GHCR（`ghcr.io/itsdamian/...`）。GitHub-hosted runner 位於美國，沿用「測試不連外」策略即可，不受 Binance 地區限制影響（目前也沒有用 Binance）。
- **Argo CD 拉取 public repo**：不需要額外憑證；GHCR image 若為 public 也不需要 pull secret（plan 決定 public 或 private + pull secret）。
- **CI 寫回 repo**：CD 需要 CI 更新環境設定中的 image 版本。prod 一律走自動 PR（使用者合併）；dev 也優先採自動 PR（可自動合併），讓 main 的 branch protection 不需要對 CI 開任何例外。必須避免「寫回的變更又觸發 CI」的無限迴圈。
- **ingest 單一副本**：現有 backend 已有 `app.ingest.enabled` 等功能開關，可拆成不同角色的 Deployment；plan 決定拆法。
- **Claude Code**：使用 Anthropic 官方的 GitHub 整合（Claude GitHub App / claude-code-action）。API key 或 OAuth token 由使用者本人建立並存成 GitHub secret，agent 不經手任何金鑰。Claude API 會產生用量費用，需有觸發限制（需求 28）。
- **使用者本人必須執行的步驟**：建立 API key、設定 GitHub secrets、安裝 GitHub App、開啟 branch protection、調整 Docker 記憶體、安裝系統工具（kind/k3d/helm 等）。agent 只提供指令與說明。
- **本 spec 開始前的前提**：前一份 spec 的 task 28（AC13 斷網測試）由使用者另行完成，不阻擋本 spec。
- **commit 身份**：本 repo 的 commit 一律使用 `itsdamian <greetinitsdamian@gmail.com>`（已設定 repo local config）。

## Decisions（2026-10-01 使用者確認）

- 叢集：本機先做，設定要可移植到雲端
- CD：GitOps / Argo CD
- 環境：dev + prod 兩套（同一叢集、不同 namespace）
- prod 升級：tag 後自動開 PR，使用者合併才升級（dev 自動部署）
- 監控：兩個環境共用一套 Prometheus + Grafana（monitoring namespace），以 namespace 區分
- 加碼：安全掃描、監控、HPA、版本發布、Claude Code 整合全部納入

## Open Questions

- 本機叢集工具：kind、k3d、還是 OrbStack 內建 K8s？（建議 plan 依資源與 Ingress / LoadBalancer 支援度決定）
- 套件管理方式：Kustomize、Helm，還是混用（第三方元件用 Helm，自家應用用 Kustomize）？
- Kafka / PostgreSQL 的部署方式：Operator（例如 Strimzi、CloudNativePG）還是單純的 StatefulSet？學習價值與資源消耗的取捨由 plan 提出建議。
- Secret 管理工具：Sealed Secrets、SOPS，或其他？
- Claude Code 的 review 要不要也看 K8s manifests 與 workflow 檔？CI 失敗分析要不要涵蓋 main 分支（不只 PR）？
