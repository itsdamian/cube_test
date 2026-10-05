# cube_test — 即時比特幣價格儀表板

從 Coinbase / Kraken 的公開 WebSocket 接收 BTC-USD 逐筆成交，經 **Kafka / Kafka Streams** 即時算出 K 線、判斷價格警示，並用 **Server-Sent Events** 推到 React 儀表板；主要來源斷線時自動切換到備援交易所。整套系統 `docker compose up` 一鍵啟動，不需要任何 API key。

![桌機深色主畫面：即時價格、K 線、多幣別換算與價格警示](docs/images/dashboard-dark.png)

| 手機（375px） | 淺色主題 |
|---|---|
| ![手機版單欄畫面](docs/images/dashboard-mobile.png) | ![淺色主題畫面](docs/images/dashboard-light.png) |

## 功能亮點

- **即時價格**：逐筆成交經 SSE 推到瀏覽器，較前一筆的漲跌以綠／紅標示，並顯示開啟頁面後的累計漲跌；資料延遲或斷線時畫面會明確提示，不會把舊價格當成即時價格。
- **雙來源自動故障切換**：Coinbase 為主、Kraken 為 hot standby。主來源失效約 10 秒內切換，恢復並穩定 15 秒後自動切回，不需要重啟。
- **K 線與走勢**：Kafka Streams 以**成交時間**聚合 1 分／5 分 OHLC，與資料庫逐筆價格完全一致；走勢圖由伺服器端降採樣。滑鼠移到 K 線上可看該根的開高低收。
- **多幣別換算**：以真實匯率（open.er-api.com）把 BTC-USD 換算成資料庫裡的每個幣別，並顯示匯率更新時間；幣別可在畫面上新增、修改、刪除。
- **價格警示（含未讀）**：由後端判斷，5 分鐘冷卻；每筆警示顯示距離目前價格還差多少；頁面沒開時觸發的警示會列為「離開期間觸發」，下次打開時顯示。
- **Kubernetes + GitOps**：本機 k3d 叢集上跑 dev 與 prod 兩個環境，由 Argo CD 從 `main` 部署；ingest 固定單一副本、API 由 HPA 自動擴縮，Prometheus / Grafana 監控與告警。
- **CI/CD**：GitHub Actions 跑測試、manifest 驗證、密鑰與漏洞掃描；合併後自動部署到 dev，推送版本 tag 後自動開 prod 升級 PR，合併才升級、revert 就回滾。

## 架構

```mermaid
flowchart LR
    CB["Coinbase WS<br/>（主要）"] --> FM
    KR["Kraken WS<br/>（備援）"] --> FM
    FM["FeedManager<br/>故障切換 + idle watchdog"] -->|btc.price.ticks| K[(Kafka)]
    K --> TP[tick-persister]
    K --> KS["Kafka Streams<br/>1m / 5m K 線"]
    K --> AE[alert-evaluator]
    K --> SSE[SSE broadcaster]
    KS -->|btc.candles| CP[candle-persister]
    TP --> PG[(PostgreSQL)]
    CP --> PG
    AE --> PG
    AE -->|btc.alerts.triggered| SSE
    FX["open.er-api.com<br/>每 30 分鐘"] --> PG
    SSE --> UI["React 儀表板<br/>（nginx）"]
    PG -->|REST| UI
```

- 後端是一個 Spring Boot 模組化單體，各區塊只透過 Kafka topic 溝通；ingest、Streams、寫入、警示都有功能開關，之後可以拆成不同 Deployment。
- 網路分成 `internal`（無法連外）與 `egress`（只有 backend），「哪個服務可以連網際網路」是明確、可測試的設定。

### Kubernetes 與 CI/CD

```mermaid
flowchart LR
    DEVR[開發者] -->|PR| GH[GitHub]
    GH -->|"CI：測試、掃描、<br/>多架構 image"| GHCR[(GHCR)]
    GH -->|"deploy/dev PR<br/>（自動合併）"| MAIN[main]
    GH -->|"tag vX.Y.Z →<br/>prod 升級 PR（人工合併）"| MAIN
    MAIN -->|Argo CD 同步| K8S
    GHCR -->|拉取 image| K8S
    subgraph K8S["k3d 叢集"]
      direction TB
      D["cube-dev<br/>worker ×1 · api（HPA）· frontend<br/>Kafka · PostgreSQL"]
      P["cube-prod<br/>worker ×1 · api（HPA）· frontend<br/>Kafka · PostgreSQL"]
      MON["Prometheus · Grafana"]
    end
```

- 後端在叢集裡拆成兩個角色：**worker**（StatefulSet，固定 1 個副本：交易所連線、Kafka Streams、寫入、警示）與 **api**（Deployment + HPA：REST、SSE）。worker 不能擴充（admission policy 會拒絕），避免重複寫入價格。
- 每個環境各自一套 Kafka（Strimzi）與 PostgreSQL（CloudNativePG）；NetworkPolicy 預設拒絕，dev 連不到 prod。
- 詳細說明：[docs/kubernetes.md](docs/kubernetes.md)（建立叢集、操作、禁止事項）、[docs/cicd.md](docs/cicd.md)（流程、GitHub 設定、發布與回滾）。

## 技術棧

| 層 | 技術 |
|---|---|
| 後端 | Java 21、Spring Boot 3.5、Spring Kafka、Kafka Streams、JDK `java.net.http.WebSocket` |
| 資料 | Apache Kafka 4.3（KRaft 單節點；client 3.9）、PostgreSQL 17 + Flyway |
| 前端 | React 19 + TypeScript + Vite、[TradingView Lightweight Charts™](https://www.tradingview.com/)、純 CSS design tokens |
| 測試 | JUnit 5、Testcontainers（Kafka / PostgreSQL）、Kafka Streams `TopologyTestDriver`、Vitest + Testing Library + MSW |
| 部署 | Docker multi-arch（amd64 / arm64）、docker compose、nginx（unprivileged）、k3d / Kubernetes、Argo CD、Gateway API + Traefik、Strimzi、CloudNativePG、Sealed Secrets |
| 維運 | GitHub Actions、GHCR、Trivy、gitleaks、Dependabot、kube-prometheus-stack（Prometheus / Grafana）、Claude Code（PR review） |

## 技術重點與學到的東西

- **事件時間 + grace + suppress**：K 線依交易所成交時間（而不是 Kafka 收到的時間）切視窗，遲到 5 秒內仍計入；`suppress(untilWindowCloses)` 讓每根 K 線只輸出一次定稿值。open／close 的排序規則 `(event_time, received_at, event_id)` 與 SQL 相同——實作時還發現 Java 的 `UUID.compareTo` 是**有號**比較、PostgreSQL 是**無號**，不統一就會對不上。
- **Hot-standby 故障切換與 idle watchdog**：兩條連線同時保持；健康 = 10 秒內有任何訊息（含 heartbeat）且 60 秒內有成交。真實斷網常是 half-open（socket 看起來還開著），所以 client 在沒有任何訊息時主動中斷重連，並用指數退避。
- **SSE 節流**：每個 instance 用自己的 consumer group 從 latest 讀；價格只保留最新值、每 250 ms 推一次，一批成交的最後一筆一定會送出。
- **冪等寫入與不遺失**：逐筆以 `ON CONFLICT (event_id) DO NOTHING` 寫入（event id 由 `source + trade_id` 決定）；寫 DB 的 consumer 在資料庫中斷時無限指數退避重試，壞資料則逐筆隔離不卡住後面。
- **Keyset 分頁**：歷史價格以 `(event_time, id)` 續查，每頁成本相同、結果穩定；走勢用 PostgreSQL `date_bin` 在伺服器端降採樣。
- **離線可跑的測試策略**：測試不連任何真實外部來源（`test` profile + 守門測試）；Kafka / PostgreSQL 用 Testcontainers，Streams 用 `TopologyTestDriver`，WebSocket 用本機假交易所；前後端以 [`contracts/api-samples/`](contracts/api-samples/) 契約樣本互相約束；測試以 random order 反覆驗證不相依執行順序。

## 快速開始

```bash
git clone <repo-url> && cd cube_test
docker compose up -d --build --wait      # 第一次約 5 分鐘
open http://localhost:3000               # 30 秒內可看到跳動的 BTC-USD 價格
docker compose down                      # 停止（保留資料；加 -v 會清除）
```

需要 Docker Desktop。port 被占用時用 `FRONTEND_PORT=3001 BACKEND_PORT=8081 docker compose up -d --wait`。完整說明見 [docs/configuration.md](docs/configuration.md)。

在本機 Kubernetes 上跑 dev 與 prod（Docker 記憶體需 16 GB，另需 k3d、helm）：

```bash
scripts/cluster-up.sh                    # 約 15 分鐘；完成後開 http://cube.localhost 與 http://dev.cube.localhost
```

見 [docs/kubernetes.md](docs/kubernetes.md)。

## 開發與測試

```bash
./mvnw clean verify                      # 後端（需 JDK 21 + Docker；不需安裝 Maven）
cd frontend && npm ci && npm test        # 前端
```

- [docs/testing.md](docs/testing.md)：開發方式、離線跑測試、多架構 image 建置
- [docs/configuration.md](docs/configuration.md)：需要的工具、服務與網路、所有環境變數、常見問題
- [docs/api.md](docs/api.md)：REST 與 SSE API
- [docs/kubernetes.md](docs/kubernetes.md)：本機 Kubernetes 叢集、Argo CD、日常操作與禁止事項
- [docs/cicd.md](docs/cicd.md)：CI/CD 流程、GitHub 設定、發布與回滾
- [docs/acceptance.md](docs/acceptance.md)：第一份 spec 的 AC1–AC15 逐條驗收步驟

## 開發方式：spec-driven + 工程師／QA 雙 agent

這個專案用 spec-driven 流程開發：**specify → plan → tasks → implement**，每一步都要使用者（或其授權的 team lead）確認才往下走。本專案以 [Claude Code](https://claude.com/claude-code) 協助開發（spec-driven + 工程師／QA 雙 agent）。

| 文件 | [即時儀表板](specs/realtime-btc-kafka-react/) | [Kubernetes + CI/CD](specs/k8s-gitops-cicd/) |
|---|---|---|
| 需求與 Acceptance Criteria | [spec.md](specs/realtime-btc-kafka-react/spec.md) | [spec.md](specs/k8s-gitops-cicd/spec.md) |
| 技術方案、替代方案、風險 | [plan.md](specs/realtime-btc-kafka-react/plan.md) | [plan.md](specs/k8s-gitops-cicd/plan.md) |
| 可驗證的 task 與 done-when | [tasks.md](specs/realtime-btc-kafka-react/tasks.md) | [tasks.md](specs/k8s-gitops-cicd/tasks.md) |
| 工程師的交付紀錄 | [progress.md](specs/realtime-btc-kafka-react/progress.md) | [progress.md](specs/k8s-gitops-cicd/progress.md) |
| QA 的審查紀錄 | [qa-review.md](specs/realtime-btc-kafka-react/qa-review.md) | （spec 結案時加入） |

實作由兩個 Claude agent 互相驗證：工程師 agent 每完成一個 task 就 commit 並交給 QA agent；QA 在獨立 worktree 實際 build / test、做反向驗證，判定 PASS / FAIL / CONCERN。FAIL 必須先修好才能做下一個 task；偏離 spec / plan 的改動要回到使用者決定。

所有合併到 main、推送 tag 與 prod 升級都由使用者本人操作。dev 部署 PR 由 cube-developer[bot]（本專案的 GitHub App）自動建立並合併，相依更新由 Dependabot 提出。PR 上的 Claude Code 只留言（review、回答 `@claude`、分析 CI 失敗），不會 commit。

## Roadmap

- ~~Kubernetes 部署~~、~~GitHub Actions CI/CD~~：已完成（[spec k8s-gitops-cicd](specs/k8s-gitops-cicd/spec.md)）
- **雲端叢集**：把 k3d 換成雲端的 k3s / 受管 Kubernetes，只改環境覆寫（網址、storage class、資源）
- **大版本升級**：Spring Boot 4、Node 26、TypeScript 7 等，各自另開 spec 處理（Dependabot 只開 minor / patch 與安全更新）

## 專案結構

```
src/main/java/com/currency/demo/
  feed/      交易所 WebSocket client、訊息解析、FeedManager（主備切換）、故障注入端點
  pricing/   逐筆價格寫入與查詢、保留期清除、來源狀態追蹤
  candle/    Kafka Streams K 線 topology、K 線寫入與查詢
  fx/        匯率抓取與多幣別換算
  alert/     價格警示規則、判斷、API
  stream/    SSE 推播
  currency/  幣別 CRUD
  health/    Kafka / Kafka Streams 健康檢查
  config/    設定、Kafka topics、consumer 設定
src/main/resources/db/migration/   Flyway schema（V1–V6）
frontend/                          React + TypeScript（Vite）、nginx 設定
contracts/api-samples/             API 回應契約樣本
deploy/                            k3d 設定、Argo CD、平台元件、dev / prod（kustomize）、監控、負載測試
.github/                           GitHub Actions workflow、Dependabot、Release 變更說明設定
scripts/                           建立 / 刪除叢集、Sealed Secrets、manifest 驗證、部署與發布檢查
docs/                              設定、API、測試、Kubernetes、CI/CD、驗收步驟與驗收用 SQL
specs/                             兩份 spec 的規格、計畫、任務、進度與 QA 紀錄
```
