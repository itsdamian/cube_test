# QA Review Log — realtime-btc-kafka-react

Owner: cube-qa（此檔由 QA 維護；工程師請勿編輯）
Spec: `specs/realtime-btc-kafka-react/spec.md`（Status: CONFIRMED）
驗證方式：每個 task 在獨立 worktree `../cube_test-qa` 於工程師指定的 commit 上實際 build / test。

Verdict 定義：
- **PASS** — 已實際執行並符合 done-when / spec
- **FAIL** — 有具體證據顯示未完成、壞掉或違反 spec / Non-Goals
- **CONCERN** — 可接受但有風險或改善建議（不阻擋）

---

## Acceptance Criteria 追蹤表

| # | Acceptance Criterion（摘要） | 對應 task | 狀態（QA 獨立驗證，2026-09-30） | 證據 |
|---|---|---|---|---|
| AC1 | `docker compose up` 後免 API key，30 秒內前端看到跳動的 BTC-USD 價格 | 4,6,7,16,18,21,26 | ✅ HTTP/SSE 層；✅ 畫面（team lead 目視） | QA 堆疊：經 nginx 的 SSE 10 秒 15 筆 price；頁面 `<title>比特幣即時價格</title>` |
| AC2 | 切斷價格來源網路 30 秒內顯示斷線/延遲；恢復後自動更新 | 6,7,21,26 | ✅ 後端/SSE；⏳ 畫面（變淡/警告）待目視 | egress disconnect → +10s kraken、+11s STALE、+13s DISCONNECTED；+46s 接回 → +73s coinbase/LIVE，未重啟 |
| AC3 | 幣別換算與匯率來源誤差 < 0.5%，顯示匯率更新時間 | 14,22 | ✅ | 5 幣別換算匯率與 open.er-api 完全一致（0.00000%），rateUpdatedAt=provider 時間；attribution 連結（前端測試） |
| AC4 | 跑 10 分鐘後有歷史、≥10 根 1m、≥2 根 5m，OHLC 與逐筆一致 | 9–12,18 | ✅ | 12 根 1m + 2 根 5m，README SQL 14 列全部 `ohlc_matches = t`（期間經歷 46s 斷網與 backend 重建） |
| AC5 | 重啟後端與 DB 後歷史價格與 K 線仍在 | 3,12,26 | ✅ | `restart backend postgres`：candles 14→14、ticks 2539→2557、幣別改名仍在 |
| AC6 | 主要來源被封鎖 30 秒內切備援、前端顯示來源；主要恢復後切回 | 6–8,16,18,21 | ✅ 後端/SSE；⏳ 畫面待目視 | chaos block → 1s kraken；unblock → 18s coinbase；錯誤網址 → kraken；還原後 `/actuator/feeds` 404 |
| AC7 | 警示越過門檻通知；5 分鐘冷卻；冷卻後條件成立再通知 | 15,16,25 | ✅ 後端/SSE（預設 5m）；⏳ toast 待使用者目視 | 預設冷卻：兩次觸發間隔 **300.363s**；30s 冷卻：30.003s / 30.759s |
| AC8 | 前端關閉時觸發的警示，重開頁面顯示為未讀 | 15,25 | ✅ 後端；✅ 未讀清單 + F5（team lead 目視） | 無瀏覽器時觸發的 2 筆 `readAt=null`；read 後從未讀清單消失 |
| AC9 | 逐筆價格超過 30 天自動清除（可縮短保留期驗證）；K 線不受影響 | 13 | ✅ | PT5M/PT30S：最舊 tick 年齡 5:17–5:18，candles 28→29 未被刪，log 每 30s 刪除 |
| AC10 | 全新 DB 已有 USD/EUR/GBP/TWD/JPY 與中文名稱 | 3 | ✅ | `down -v` → EUR 歐元、GBP 英鎊、JPY 日圓、TWD 新台幣、USD 美元 |
| AC11 | 前端幣別 CRUD，重新整理後仍在 | 3,24 | ✅ API + 持久化；✅ 畫面 CRUD+409+F5（team lead 目視） | API 新增 CHF / 改名 / 刪除；重啟後仍在；換算表立即出現 CHF；前端 36 tests |
| AC12 | 停 Kafka 後 readiness 失敗；Kafka 恢復後自動恢復 | 17 | ✅ | `compose stop kafka` → 立即 503（kafka DOWN），liveness 200；start → 2s 回 200 |
| AC13 | `mvn verify` + 前端測試在無外網、無預啟 Kafka 環境全過 | 全部 | ✅ Maven 離線模式；⏳ 實際斷網待使用者 | `./mvnw -o clean verify` 152/152、外部 host 0；只用 postgres:17.11-alpine / apache/kafka:3.9.2 / ryuk:0.12.0；npm test 46/46；random order 4/4 |
| AC14 | `docker buildx build --platform linux/amd64,linux/arm64` 成功 | 19 | ✅ | `--no-cache` 3:57 成功，amd64/arm64 都在，uid 1001 |
| AC15 | repo 內無 `target/`、`node_modules/` 等建置產物 | 1,20 | ✅ | `git ls-files \| grep -E '(^\|/)(target\|node_modules\|dist)/'` → clean |

## Non-Goals 守門清單（出現即視為 scope drift）

- K8s manifests / Helm / GitHub Actions CI/CD
- 使用者帳號、登入、權限
- BTC 以外的加密貨幣
- 交易、下單、錢包等金流
- Email / 推播 / LINE 等站外通知
- 回補歷史資料
- 多 broker / HA Kafka 叢集

---

## Review Entries

### 2026-09-29 — QA 就緒 / 環境檢查
- **Reviewed**: spec.md（全文）、`.claude/agents/qa-engineer.md`
- **Verdict**: N/A（尚無交付物）
- **Findings**:
  - 基準 commit：`main` @ `9665ca1`；`git worktree list` 只有主 worktree，QA worktree 尚未建立（收到第一個 commit 時建立）。
  - 工具鏈現況（`java -version` / `mvn -v` / `docker info`）：
    - JDK：**未安裝**（"Unable to locate a Java Runtime"）→ 需要 JDK 21（例：`brew install openjdk@21`）
    - Maven：**未安裝** → 若工程師加入 `mvnw` 則不需全域安裝
    - Docker：CLI 29.8.0 已安裝，但 **daemon 未啟動** → 需開啟 Docker Desktop（Testcontainers、compose、buildx 都需要）
    - Node：v26.4.0 已安裝
  - 在上述工具就緒前，QA 無法對實作 task 執行 build/test，只能審 plan.md / tasks.md 文件。

### 2026-09-29 — check（使用者觸發）
- **Reviewed**: `progress.md`、分支與 commit 狀態
- **Verdict**: N/A（尚無交付物）
- **Findings**:
  - `progress.md` 不存在；`plan.md` / `tasks.md` 也尚未產生。
  - 工程師已建立分支 `feat/realtime-btc-kafka-react`（目前 checked out），但尚無新 commit（`git log --all` 最新仍為 `9665ca1`）。
  - `cube-java-engineer` session 已上線（busy），推測正在撰寫 plan。
  - 工具鏈仍未就緒：JDK 未安裝、Docker daemon 未啟動。

### 2026-09-29 — Stage: plan.md DRAFT 審查
- **Reviewed**: `specs/realtime-btc-kafka-react/plan.md`（DRAFT，未 commit，branch `feat/realtime-btc-kafka-react`）、`progress.md` 13:53 條目
- **Verdict**: **CONCERN** — 方向正確、無 scope drift，15 條 AC 全部有對應的驗證方式；但有 4 個驗證設計在實際執行時會失效或誤判（M1–M4），請在寫 tasks.md 前修正 plan。另有 7 項建議（S1–S7）。
- **查證**：
  - `git ls-files | grep -c '^target/'` → 18（證實 AC15 需要從 repo 移除建置產物）；`start-silicon.sh`、`docker-compose.yml` 只服務 `Dockerfile.silicon`/`amd64` + H2，刪除合理。
  - Spring Boot 4.1.0 已於 2026-06-10 GA、4.1.1 於 2026-08-20 釋出（spring.io blog），plan 的版本描述正確。
  - Kraken WS v2 `trade`：`snapshot` 預設 `false`，一則訊息可能包含多筆成交（docs.kraken.com/api/docs/websocket-v2/trade）。
  - open.er-api.com 服務條款：**要求在使用匯率的頁面放上 attribution 連結**「Rates By Exchange Rate API」；資料每日更新一次，每小時抓一次不會被限流，被限流時回 429，20 分鐘後解除（exchangerate-api.com/docs/free）。

#### 對工程師 4 個問題的回覆
1. **AC 驗證是否可執行** → 大致可執行；AC2、AC4、AC6、AC12 有缺陷，見 M1–M4。
2. **Scope drift** → 無。Spring Boot 4.1.x 屬於 spec 授權 plan 決定的範圍（Constraints：「版本升級由 plan 決定」）；刪除 Coindesk（spec Non-Goals 明示可移除）、`Dockerfile.amd64/silicon`（需求 17 明示取代）、`start/stop-silicon.sh`（只包裝 `app-silicon` service）都符合 spec。
3. **AC13 解讀** → QA 可接受：事先 pull image、之後斷網執行，字面上就是「沒有網路連外」，也符合需求 18 的動機（CI runner 位於美國 / Binance 地區限制）。但仍由使用者拍板，並加上以下條件，QA 會照這個方式驗收：
   - 離線清單要包含 Testcontainers 的 **Ryuk image**（`testcontainers/ryuk`），而不只 kafka/postgres；所有 image tag 要固定版本，不用 `latest`。
   - Maven 依賴要能離線使用：先跑 `./mvnw dependency:go-offline`（或先跑一次 verify），QA 會用 `./mvnw -o verify` 在斷網狀態下驗收；前端先 `npm ci` 再斷網跑 `npm test`。
4. **時間相關測試** → 方向正確（注入 Clock、TopologyTestDriver、冷卻以事件時間計算）。需要補的細節見 S5。

#### 必修（M）
- **M1 AC12 自動測試照 plan 的寫法做不到「恢復」**：plan:164/185「停掉 Kafka container → 重啟 → UP」。Testcontainers 的 `stop()` 會移除 container，重新 start 後拿到的是**新的隨機 host port**，而 `@ServiceConnection` 注入的 bootstrap servers 已經固定，因此「恢復後自動 UP」永遠驗證不到。改用 `ToxiproxyContainer` 切斷連線，或對 container 做 docker `pause`/`unpause`（port 不變）。
- **M2 AC2 手動步驟會誤判**：plan:175「`docker network disconnect` backend 的對外網路」。compose 預設只有一個 network，後端和 Kafka/Postgres/nginx 都在上面；disconnect 之後前端是因為 SSE 斷掉才顯示斷線（**假陽性**），並沒有驗到「價格來源斷線偵測」。compose 要把網路拆成 `internal`（kafka/postgres/frontend↔backend）和 `egress` 兩個，只 disconnect `egress`；或改用關 Wi-Fi 的方式。
- **M3 AC6 手動步驟「改回 URL 後重啟 ingest 觀察切回」沒有驗到自動切回**：plan:179。AC6 要求主來源恢復後在**不重啟**的情況下自動切回，這也是 plan:23「指數退避重連」要驗證的行為。需要一種可以在執行中封鎖 / 解封主來源的方式，例如 QA 用的 compose override 加 toxiproxy，把 `APP_FEED_PRIMARY_URL` 指向 proxy，執行中關閉 / 開啟 proxy。自動測試方面，目前只測了 `FeedManager` + 假 client，**真正的 `WebSocketPriceFeedClient` 重連與退避沒有任何測試**；請加一個對本機假 WS server 的整合測試（斷線 → 重連 → 再收到 tick）。
- **M4 歷史 API 的上限與 AC4 / 需求 11 衝突**：plan:144 限制「最多 10,000 筆，超過回 400」。Coinbase 的 ticker 每秒數筆到數十筆（plan:73），10 分鐘就可能超過 10k 筆，也就是 AC4「可以查到該時段的歷史價格」本身就會拿到 400，前端「最近 N 分鐘走勢」也一樣。請改成分頁 / cursor、伺服器端降採樣，或讓走勢線改用 1m K 線的 close，並寫清楚 AC4 要用哪一種查詢來驗。

#### 建議（S）
- **S1 Kafka 故障模式沒寫**（Risks 表中沒有）：Kafka 斷線時 `producer.send()` 會在 `max.block.ms`（預設 60 秒）內阻塞；如果是在 WS listener thread 裡呼叫，會卡住 feed，甚至讓 FeedManager 誤判來源 stale。請定義 `max.block.ms` / `delivery.timeout.ms`、Kafka 斷線期間 tick 要丟棄還是緩衝、恢復後的行為，並對應一個測試。另外 Kafka Streams thread 可能因為 poison pill 死掉，K 線就會靜默停止 → 設定 `DeserializationExceptionHandler`（LogAndContinue），並考慮把 Streams 的狀態（RUNNING/ERROR）納入 readiness。
- **S2 open.er-api.com attribution**：前端要放「Rates By Exchange Rate API」連結（條款要求）。30 分鐘抓一次符合「不會被限流」，但請處理 429（保留上次的匯率並記 log）。
- **S3 OHLC 一致性的判定規則**：同一個 `event_time` 有多筆 tick 時（Kraken 會批次送出、同一時間戳多筆成交），open/close 要有確定的 tie-break（例如 `event_time, received_at, event_id`），README 的 AC4 SQL 也要用同樣的排序；超過 grace 5 秒的 late tick 會被 Streams 丟掉，但仍會寫進 DB，造成不一致 → 請記錄 dropped-late 的 metric/log，AC4 比對也要說明怎麼處理。Kraken 訂閱時請**明確**帶 `snapshot:false`，parser 要測一則訊息含多筆成交的情況。
- **S4 未讀的定義**：前端開著時透過 SSE 跳出的 toast，算不算「已看過」？如果不算，重新整理後這些警示會再以未讀出現。需求 14a 說的是「上次離開後觸發過、還沒看過的」，請在 plan 裡寫清楚（例如 toast 顯示時自動標記已讀，或需要手動標記），並寫一條對應的測試。
- **S5 時間相關測試的細節**：(a) FeedManager 以可變的 Clock 直接呼叫 `check()`，不要用 `Thread.sleep`；(b) 測邊界：剛好 10 秒 stale、剛好 5:00 冷卻（`≥` 還是 `>`）、剛好落在 window 邊界的 tick；(c) recovery period 內主來源又斷線 → 維持在備援；兩個都斷、備援先恢復 → 備援，接著主來源恢復滿 15 秒 → 切回；(d) TopologyTestDriver 輸入的 record timestamp 要**刻意和 payload 的 eventTime 不同**，才能證明真的用了事件時間的 TimestampExtractor；suppress 要送一筆更晚的 tick 推進 stream time 才會輸出。
- **S6 保留期批次刪除**：plan:199「每小時分批刪除（每批 10k 筆）」要寫清楚是在同一次執行中**迴圈刪到沒有資料為止**；只刪一批的話，每小時的寫入量（每秒 10 筆就是 36k）大於刪除量，表會無限成長。
- **S7 其他**：Kafka 在 compose 裡也要用具名 volume（`down`/`up` 後 Streams 的 changelog 和 offset 才不會遺失）；Streams 的 `state.dir` 要可以設定；AC7 除了用 30 秒冷卻觀察之外，至少要用預設 5 分鐘實跑一次；AC14 的 README 要註明需要支援多平台的 buildx builder（containerd image store 或 `docker-container` driver）；SSE 每個實例唯一的 group id 要設 `auto.offset.reset=latest`。

### 2026-09-29 — Stage: plan.md 修訂版覆審（progress 13:58）
- **Reviewed**: plan.md 修訂版（〈QA 意見與處理〉、Approach 2/3/5/9、網路拆分、Testing Strategy、AC 驗證表）
- **Verdict**: **CONCERN（可進入使用者確認）** — M1、M2、M4、S1–S7、AC13 的修改都確認已寫進 plan（plan:24-27、29、36、40、89、107-110、164、186-193、203-214、227-231）。M3 的替代做法原則上接受，但還缺一個面向（M3b），請在 plan 補一句，並在 tasks 中落實。
- **M3 替代方案評估**：
  - toxiproxy 的反對理由成立：它是 L4 proxy，client 的 SNI / hostname 會變成 proxy 的名稱，憑證驗證會失敗；要繞過就得關閉驗證或改 DNS alias，都不值得。接受 chaos endpoint + 假 WS server 的組合。
  - chaos endpoint 會加上 profile gate，而且有測試斷言預設 profile 下回 404，風險可以接受。
- **M3b（仍然缺少）靜默斷線（half-open）這條路徑沒有被驗到**：chaos `block` 是 client 主動 close，走的是「乾淨斷線 → onClose → 退避重連」。真實的「擋掉網路」（AC6 舉例的情況、AC2 的 egress disconnect）通常**不會送出 FIN/RST**：socket 仍顯示已連線，只是沒有資料。JDK `java.net.http.WebSocket` 在只收不送的情況下可能很久都不會察覺。FeedManager 會因為 stale 切到備援，但**如果 client 不主動 abort 舊連線並重連，網路恢復後主來源永遠不會回來 → 不會切回**。這正是 AC6「恢復後切回」和 AC2「恢復網路後自動恢復更新，不用重啟」最容易出錯的地方，而目前 plan 的自動測試和 chaos 手動步驟都走不到這條路徑。
  - 要求：(1) plan 明定 client 的 idle watchdog：超過 N 秒沒收到任何訊息（含 Coinbase `heartbeat` channel / Kraken `heartbeat`）→ `abort()` 並走退避重連；(2) 假 WS server 整合測試加一個案例：server **保持連線但停止送訊息**（不 close）→ client 在期限內 abort 並重連 → 恢復送訊息後再收到 tick；(3) （可選）chaos endpoint 加 `mode=silent`（保留 socket、丟棄收到的訊息），讓手動驗收也能走到這條路徑。AC2 的 egress disconnect/connect 手動步驟本身會真實走到這條路徑，QA 驗收時會特別看「接回後不重啟就恢復」。

### 2026-09-29 — Stage: plan.md M3b 覆審
- **Reviewed**: plan.md 中的 M3b 修改（`grep -n -iE "idle|heartbeat|silent|half-open|M3b" plan.md`）
- **Verdict**: **PASS（QA 端）** — plan 已經沒有未解決的 QA findings，接下來由使用者確認。
- **Findings**:
  - plan:29 已加入 idle watchdog：兩個 client 都訂閱 heartbeat，`APP_FEED_IDLE_TIMEOUT`（預設 10 秒）內沒收到任何訊息就 `abort()` 並重連。
  - plan:90 chaos 端點已加入 `?mode=silent`；plan:191 已加入 half-open 整合測試案例 (b)；plan:244 的〈QA 意見與處理〉已記錄。
  - 給 tasks 階段的提醒（不阻擋）：idle-timeout 和 stale-threshold 都是 10 秒，AC6「30 秒內」的時間預算是：偵測約 10 秒 + 狀態發佈最多 5 秒 + 切換，仍在 30 秒內；tasks 的 done-when 要寫出這個時間預算的驗證方式。

### 2026-09-29 — Stage: plan.md 版本變更覆審（Spring Boot 4.1.x → 3.5.x）
- **Reviewed**: plan.md 中和版本相關的段落（`grep -n -iE "4\.1|jackson|starter|3\.5|spring-kafka|flyway|testcontainers|KafkaContainer|MockitoBean|RestClient|apache/kafka|AC13|ServiceConnection|date_bin" plan.md`）
- **Verdict**: **CONCERN（1 項必須在第一個 task 落實；不擋 plan 確認）**
- **沒有 4.x 殘留**：`tools.jackson`、starter 模組化只出現在 plan:56「不採用 4.1 的原因」；其餘寫法都是 3.5 可用的：
  - `@MockitoBean`（Framework 6.2 / Boot 3.4+）、`RestClient`（Boot 3.2+）、`MockRestServiceServer` 可綁定 `RestClient.Builder`
  - `@ServiceConnection` 支援 `org.testcontainers.kafka.KafkaContainer`（Boot 3.4+）
  - Flyway 10+ 需要額外的 `flyway-database-postgresql`，plan:118 已列入；PostgreSQL 17 的 `date_bin` 在 PG14+ 就有
- **版本組合**：Boot 3.5 BOM 管理的是 spring-kafka 3.3.x + kafka-clients/kafka-streams 3.9.x，搭配 `apache/kafka:3.9.x` broker 是同一系列，相容。
- **C1（必須）Testcontainers 版本要 ≥ 1.21.4**：本機 `docker --version` → `Docker version 29.8.0`。Docker Engine 29 把最低 API 版本提高了；Testcontainers 1.21.4 以前內建的 docker-java 預設用 API 1.32，會直接失敗（"client version 1.32 is too old" / "could not find a valid Docker environment"，testcontainers-java issue #11212、#11235；1.21.4 修正）。第一個 task 請用 `./mvnw dependency:tree | grep testcontainers` 確認 BOM 解析出來的版本 ≥ 1.21.4；不夠就在 pom 覆寫 `<testcontainers.version>`。QA 驗收時會檢查。
- **C2（建議）** plan:118 用 `spring-kafka-test` 取得 TopologyTestDriver：已查 spring-kafka-test 3.3.10 的 pom，確實會透過 compile scope 帶入 `kafka-streams-test-utils`，但同時也會帶入 `kafka_2.13`、`kafka-server`、`zookeeper` 等 broker 相依（只在測試 classpath）。既然沒有用 EmbeddedKafka，建議直接宣告 `org.apache.kafka:kafka-streams-test-utils`（test scope，版本由 Boot BOM 管理），比較乾淨。不阻擋。
- **C3（小）** plan:118 同時列了 `-data-jpa` 和 `-jdbc`；data-jpa 本身已經包含 jdbc，重複列出無害。
- **流程備註**：這次版本變更與 AC13 的「使用者已接受」是透過 `personal-workplace-7a` 轉達的；工程師已說明會等使用者在工程師分頁親自確認才改成 CONFIRMED，QA 認同。

### 2026-09-29 — 流程變更（使用者直接指示）
- 從現在起，QA 和工程師經過一輪討論仍有分歧時，交給 team lead session **`personal-workplace-7a`** 決定，QA 照它的決定執行（取代原本「列出雙方立場交給使用者」的規則）。
- QA 的執行方式：送給 team lead 的內容會包含雙方立場與證據；決定結果記錄在此檔。team lead 決定的是「怎麼處理分歧」，**不會改變實際量測到的結果**：測試真的沒過，QA 仍會如實記錄。team lead 決定接受某個已知問題時，QA 會以「已知問題（team lead 接受）」記錄，而不是改寫成 PASS。

### 2026-09-29 — plan CONFIRMED 確認
- **Reviewed**: commit `74df724`（`git show --stat`：spec.md、plan.md、progress.md 三個檔案，+417 行）；plan.md 開頭為 `Status: CONFIRMED`。
- **Verdict**: N/A（紀錄）
- 工程師轉述：使用者已在工程師分頁親自核准 plan，並授權 `personal-workplace-7a` 代為核准之後的 plan / tasks / guardrail checkpoint；安裝系統軟體、push、開 PR、動 main 仍然要由使用者本人決定。這和使用者在 QA 分頁的指示（分歧交給 personal-workplace-7a 決定）一致。
- C1–C3 已寫進 plan（plan:118、plan:255）；C1（Testcontainers ≥ 1.21.4）會在第一個 task 驗收時用 `dependency:tree` 實際檢查。

### 2026-09-29 — Stage: tasks.md DRAFT 審查
- **Reviewed**: `specs/realtime-btc-kafka-react/tasks.md`（DRAFT，未 commit；P1–P2 + 27 個 task + AC 對照表）；另外讀了現有測試 `CoindeskServiceTest` / `CoindeskControllerTest` / `CurrencyControllerTest` 與 `application.properties`
- **Verdict**: **CONCERN** — AC 覆蓋完整、測試和功能放在同一個 task、順序符合依賴關係，先前的 QA 意見也都已落實。但有 2 項 done-when 缺陷（M5、M6）需要修正後再 commit；另有 6 項建議（T1–T6）。

#### 工程師的 5 個問題
1. **AC 覆蓋** → 15/15 條都對應到至少一個有自動測試的 task，而且手動驗收都落在 25/27（對照表 tasks:66-82）。✔
2. **測試是否延後** → 每個 task 的 done-when 都含有自己的測試；task 27 是驗收，不是「補寫測試」。✔
3. **done-when 是否可驗證** → 大部分具體可驗；AC6 的時間預算寫法有歧義，見 T1。
4. **先前意見是否落實** → C1 tasks:18、M3b tasks:26、M1 tasks:43、M4 tasks:33、S6 tasks:36、S4 tasks:56、網路切分 tasks:60；S3 tie-break/grace tasks:34、S1 tasks:24/34/43、S2 tasks:40/53 也都有。✔
5. **大小與順序** → 依賴順序正確（2→4 用 AppProperties，4/7/15 → 16 SSE，11 → 17 Streams health）。task 15、25 比較大，但每一個都有清楚的 done-when，可以接受。前端在後端之後、到 task 25 才第一次實際整合 → T3。

#### 必修（M）
- **M5 從 task 6/7 起，`@SpringBootTest` 預設會連到真實的 Coinbase / Kraken / open.er-api**：plan 的 feature 開關「預設全開」（plan:66），`FxRateRefresher` 是 `@Scheduled`、`WebSocketPriceFeedClient` 啟動就會連線。任何沒有特別關閉的整合測試（例如 task 9、12、15、16、17 的 context）都會在背景連到真實的外部來源。有網路時測試照樣是綠的，**要到 task 27 斷網驗收時才會發現**，而且違反需求 18。要求：
  - 在 task 4（或最早建立測試基底的 task）加一份共用的測試設定：`app.ingest.enabled=false`、FX 排程關閉，所有外部 URL 指向 `ws://127.0.0.1:1` / `http://127.0.0.1:1`（萬一被誤開也只會連線失敗）；
  - done-when 加一個守門測試：斷言預設測試 context 中沒有啟動任何真實 feed client、外部 URL 都不是公網位址；
  - 建議從 task 6 起，每個 task 至少有一次在斷網狀態下跑 `./mvnw verify`（或在 CI-like 指令裡加 `-Dapp...` 驗證），不要等到 task 27。
- **M6 AC9 的手動驗收在 10 分鐘內無法完成**：`RetentionJob` 固定每小時執行一次（tasks:36），但 plan:211 的手動步驟是「`APP_RETENTION_TICKS=PT5M` 跑 10 分鐘後查」，這段時間 job 根本還沒執行。要求把執行間隔也改成可設定（例如 `APP_RETENTION_INTERVAL`，預設 `PT1H`），task 13 的 done-when 加上「間隔設定可以被覆寫」的測試，README（task 26）的 AC9 步驟同時縮短兩個值。

#### 建議（T）
- **T1 AC6 時間預算的寫法**：tasks:27「主 stale 後在 11 秒內切到備援」有歧義，因為 stale 本身就是最後一筆 tick 後 10 秒。建議改成「**最後一筆主來源 tick 後 ≤ 11 秒** active 切換為備援」。另外請確認 `FeedStatus` 是**狀態一變就立即發佈**（再加上每 5 秒的定期發佈），否則最壞情況要再多等 5 秒。完整的預算（偵測 ≤ 11 秒 + 發佈 + Kafka + SSE + 前端 < 30 秒）在 task 27 用 chaos block 計時驗證。
- **T2 plan 承諾的「假 feed → Kafka → DB / K 線 / 警示」端到端測試沒有對應的 task**（plan:130）：task 12 是直接把 tick 送進 Kafka，繞過了 FeedManager + TickPublisher。建議在 task 16 或另開一個小 task：假 `PriceFeedClient` → FeedManager → TickPublisher → Kafka → price_tick / candle / alert_event / SSE `price` 事件，全鏈路走一次。
- **T3 前後端契約漂移**：前端 19–24 全部用 MSW mock，到 task 25 才第一次接上真的後端。建議讓 MSW 的回應直接使用後端整合測試實際輸出的 JSON 樣本（例如 task 10/12/14/15 把回應存到共用的 fixture 目錄），或至少在 task 25 的 done-when 加入「前端每個頁面區塊都能載入真實 API 資料，console 沒有錯誤」。
- **T4 SSE 節流要保證最後一筆會送出**（tasks:42）：「1 秒內 50 筆最多 5 筆」只驗了上限。如果是 leading-edge 節流，最後一個價格可能被丟掉，畫面就會停在舊價格。請加上斷言：**最後收到的 price 等於最後送出的 tick**（trailing edge）。
- **T5 AC15 的檢查指令**：tasks:51 用 `git status` 不夠，因為 .gitignore 生效後 `git status` 本來就看不到。請改成 `git ls-files | grep -E '(^|/)(node_modules|dist)/'` 為空，和 plan 的 AC15 指令一致。
- **T6 task 25「kafka 無法連外」要寫出可以執行的指令**：`apache/kafka` image 不一定有 curl/nc。請寫明實際指令（例如 `docker compose exec kafka bash -c 'timeout 3 bash -c "</dev/tcp/1.1.1.1/443"'` 預期失敗，backend 內同一個指令預期成功），並確認 backend 也連得到 `internal`。
- 備註：task 1 的「`./mvnw verify` 通過（既有測試）」—— `CoindeskControllerTest` 是沒有 mock 的 `@SpringBootTest`（CoindeskControllerTest.java:19-25），可能會打到已經停止服務的 Coindesk，要靠 fallback 才會通過；這個測試 task 2 就會刪掉，不阻擋，但 QA 驗 task 1 時如果在斷網下它失敗，不會算 FAIL。

### 2026-09-29 — Stage: tasks.md 修訂版覆審
- **Reviewed**: tasks.md 修訂版（規則區、task 2/6/7/13/14/16/18/20/21/26–28、AC 對照表）
- **Verdict**: **PASS**（附 2 個小建議，不擋 commit）
- **已確認落實**：M5 規則區 + tasks:21（test profile + `NoExternalCallsGuardTest`）、tasks:28/42 擴充守門測試；M6 tasks:38（`APP_RETENTION_INTERVAL` + PT1S 排程自動刪除測試）；T1 tasks:29（最後一則訊息後 ≤ 11 秒，狀態改變時立即發佈）；T2 新增 task 18 全鏈路（含切換到備援與切回）；T3 規則區 + `contracts/api-samples` + `ContractSamplesTest`；T4 tasks:44（最後一筆一定會送出）；T5 tasks:55；T6 tasks:64。AC 對照表已依新的編號更新，15/15 條都有對應。
- **小建議**：
  - **R1** 「所有 `@SpringBootTest` 使用 test profile」要靠每個測試類別記得加 `@ActiveProfiles("test")`，漏加一個守門測試也抓不到（守門測試只檢查自己的 context）。建議在 pom 的 surefire/failsafe 設定 `<systemPropertyVariables><spring.profiles.active>test</…>` 全域啟用，或所有整合測試都繼承同一個基底類別。
  - **R2** tasks:64 的 `busybox:1.37 wget https://…`：busybox 的 wget 要依賴 `ssl_client` 才能連 HTTPS，官方 image 的某些版本沒有，會讓「egress 應該成功」那一步**因為工具問題而失敗**（假陰性）。建議改用 `nc -z -w 5 1.1.1.1 443`（busybox 內建），或改用 `curlimages/curl`。（QA 本機目前 Docker daemon 沒有開，無法實測，驗收 task 26 時會實際跑。）
- **給使用者 / team lead**：tasks 在 QA 端已經沒有阻擋項目，可以送去核准。實作從 task 1 開始，需要 P1（JDK 21）、P2（Docker Desktop）由使用者本人完成。

### 2026-09-29 — 工具鏈就緒確認
- 通知來源：`personal-workplace-7a`（team lead）。QA 實測：
  - `source ~/.zshrc; java -version` → `openjdk version "21.0.12.1" 2026-08-18`（Homebrew）；`JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/...`
  - `docker info` → `29.8.0 aarch64`；`docker buildx ls` → `desktop-linux*`（BuildKit v0.33.0，含 linux/amd64 + linux/arm64）
- **R2 實測，撤回**：`docker run --rm busybox:1.37 wget -T 5 -q -O /dev/null https://open.er-api.com/...` → `WGET_HTTPS_OK`（只會提示 "TLS certificate validation not implemented"），`nc -z -w 5 1.1.1.1 443` → `NC_OK`。busybox 1.37 的 wget 可以連 HTTPS，task 26 原本的寫法可行，R2 不需要修改。

### 2026-09-29 — tasks CONFIRMED + task 1 done-when 例外對齊
- `42c43b3`：tasks.md `Status: CONFIRMED`（由 personal-workplace-7a 依使用者授權核准）。
- 工程師提議（team lead 建議）：task 1 升級後如果 Coindesk 測試失敗就不修（task 2 會刪除），done-when 改為「Coindesk 以外的測試全過」。**QA 同意**，附帶條件：
  1. 必須能成功 compile（包括 test-compile）；如果 Coindesk 測試連編譯都過不了，其他測試根本不會執行，就不適用這個例外。
  2. 不可以用 `-DskipTests`、`@Disabled` 或刪除來繞過；commit message 要列出失敗測試的名稱和根因。QA 會讀 surefire 報告，確認**失敗的只有 Coindesk\*** 測試，並親自確認失敗的根因是升級或已停止服務的 Coindesk，而不是應用程式 context 本身壞掉（`CurrencyControllerTest` 必須通過）。
  3. 這個例外只適用於 task 1；task 2 的 done-when 維持完整的 `./mvnw verify` 全綠。

### 2026-09-29 15:22 — Task 1（建置基準與 repo 清理）@ `ae5cb0f`
- **Reviewed**: `git diff 42c43b3..ae5cb0f`（pom.xml、.gitignore、mvnw/mvnw.cmd、.mvn/wrapper/maven-wrapper.properties、移除 target/ 追蹤）；在 QA worktree `../cube_test-qa`（detached @ ae5cb0f）實際執行
- **Verdict**: **PASS**
- **Evidence**:
  - `./mvnw -B verify` → `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（CurrencyControllerTest 4、CoindeskControllerTest 2、CoindeskServiceTest 5）。沒有用到 Coindesk 例外。
  - `./mvnw -o -B verify` → 同樣 11/0/0，`BUILD SUCCESS`（Maven 離線模式）。
  - `./mvnw dependency:tree -Dincludes=org.testcontainers` → testcontainers / junit-jupiter / kafka / postgresql 都是 **1.21.4**（C1 ✔）。
  - `dependency:tree -Dincludes=org.apache.kafka,org.springframework.kafka` → spring-kafka 3.3.16、kafka-clients / kafka-streams / kafka-streams-test-utils 3.9.2（和 plan 的 3.9 broker 同一系列 ✔）；沒有引入 spring-kafka-test（C2 ✔）。
  - `git ls-files | grep -cE '(^|/)target/'` → `0`；verify 之後 worktree `git status --short` 是空的（.gitignore 生效）。
  - `unzip -p target/app.jar BOOT-INF/classes/.../DemoApplication.class | xxd` → `cafe babe 0000 0041`（class major 65 = Java 21 ✔）。
  - pom 沒有硬編碼的秘密；Java-WebSocket 1.6.0 是唯一自行指定版本的相依（BOM 不管理，已註解說明）。
- **Findings（不阻擋）**:
  - **CONCERN-1（既有問題，task 2 處理）**：`CoindeskControllerTest` 會真的連到 `https://api.coindesk.com/v1/bpi/currentprice.json`（log：`java.net.UnknownHostException: api.coindesk.com`），靠 fallback mock 才通過。這證實了 tasks 審查時的預測；task 2 刪除它之後，QA 會確認 log 中不再出現任何外部 host。
  - **CONCERN-2（小）**：`maven-wrapper.properties` 沒有 `distributionSha256Sum`，wrapper 下載的 Maven 沒有做完整性驗證。之後要上 CI 時建議補上（`./mvnw wrapper:wrapper -Dmaven=3.9.16` 可以產生）。不阻擋。
  - 原本 pom 的 `<mainClass>` / `<executable>` 已移除；只有一個 main class，repackage 正常（`target/app.jar` 已產生）。

### 2026-09-29 15:35 — Task 2（移除 Coindesk、設定骨架、測試守門）@ `825804f`
- **Reviewed**: `git diff ae5cb0f..825804f`（20 個檔案，+338/−895）；在 QA worktree 實際執行
- **Verdict**: **PASS**
- **Evidence**:
  - `./mvnw -B verify` → `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`；`./mvnw -o -B verify` → 成功。
  - **task 1 CONCERN-1 關閉**：`grep -ciE 'coindesk|coinbase\.com|kraken\.com|er-api|UnknownHost'` 測試 log → `0`。`grep -rniE "coindesk|resttemplate|h2-console" src pom.xml` → 沒有殘留。
  - **守門測試反向驗證（QA 親自做）**：在 QA worktree 把 pom 的 `<spring.profiles.active>` 改成 `default`，執行 `-Dtest=NoExternalCallsGuardTest` → `Tests run: 3, Failures: 3`（profile 沒有生效 / URL 不是 loopback / ingest 為 true，三項都被抓到），之後 `git checkout pom.xml` 還原。守門測試是有效的。
  - **R1**：surefire 的 `systemPropertyVariables` 全域啟用 test profile（pom.xml maven-surefire-plugin），`CurrencyControllerTest` 的 `@ActiveProfiles("test")` 已移除，仍然通過，證明全域設定生效。
  - **task 1 CONCERN-2 關閉**：QA 從 Central 下載 `apache-maven-3.9.16-bin.zip` → `shasum -a 512` 前綴 `ed41650d4248…` 和 Central 的 `.sha512` 一致；`shasum -a 256` = `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`，和 `distributionSha256Sum` 完全一致。
  - `application.yml` 所有 `app.*` 以及 datasource / port 都用 `${ENV:default}`；actuator `include: health,info`；TRACE log 已移除；`open-in-view: false`。`AppProperties` 使用 `@Validated` record，所有 Duration 都有 `@NotNull`。
  - done-when「`APP_RETENTION_TICKS=PT5M` 綁定成 5 分鐘」：AppPropertiesTest$EnvironmentOverrides 通過。
- **Findings（不阻擋）**:
  - **CONCERN-3**：目前只有 surefire 設定 profile。之後如果用 failsafe 跑 `*IT` 類別，那邊不會繼承這個設定，而守門測試只會在 surefire 執行。請在 failsafe 也加同樣的 `systemPropertyVariables`，或約定不使用 failsafe。另外在 IDE 裡直接執行單一測試時不會帶這個 profile（守門測試在 IDE 會失敗，至少能給出訊號），可以在 README 註明。
  - **CONCERN-4（小）**：`management.endpoint.health.show-details: always` 會對外揭露 DB / Kafka 的細節。本機可以接受；task 17 / 上 K8s 時建議改成 `when-authorized` 或只對 probes group 顯示。
  - 備註：`AppPropertiesTest` 用 test properties 模擬環境變數（JVM 內無法設定 env），因為 yml 用的是 `${APP_…}` placeholder，這樣的效果和 OS env 相同。QA 會在 task 26 compose 驗收時用真正的環境變數覆寫再確認一次。
  - 備註：主程式暫時沒有預設幣別（seed runner 已移除），task 3 用 Flyway V2 補上。QA 驗 task 3 時會對照 AC10。

### 2026-09-29 15:40 — Task 3（PostgreSQL + Flyway + 幣別模組）@ `4e65235`
- **Reviewed**: `git diff 825804f..4e65235`（25 個檔案）；在 QA worktree 實際執行，並另外做了一次真實 Postgres 的手動冒煙測試
- **Verdict**: **PASS**
- **Evidence（自動）**:
  - `./mvnw -B clean verify` → `Tests run: 13, Failures: 0, Errors: 0`、`BUILD SUCCESS`；log 中 ` WARN ` 出現 0 次；外部 host 出現 0 次。
  - Testcontainers 實際使用的 image：`postgres:17.11-alpine`、`testcontainers/ryuk:0.12.0`（tag 固定，AC13 離線清單要列入這兩個）。
  - AC10：`CurrencySeedTest` → 剛好 5 筆 EUR/GBP/JPY/TWD/USD，中文名稱正確。AC11 後端：`CurrencyApiTest` 7 個測試（201+Location / 200 / 204→404 / 404×3 / 409 新增+修改 / 400）。
- **Evidence（QA 手動冒煙：`java -jar target/app.jar` + `postgres:17.11-alpine` container，外部來源關閉）**:
  - 全新 DB `GET /api/currencies` → 5 筆預設幣別，中文名稱正確（AC10 ✔）；`flyway_schema_history` → V1 `currency`、V2 `seed currencies` 都是 success。
  - `POST CHF` → `201`，`Location: …/api/currencies/6`；重複 `USD` → `409`；`code":"usd"` → `400` ProblemDetail；壞掉的 JSON → `400`；`/api/currencies/abc` → `400` ProblemDetail；`PUT` TWD 改名 → `200`，`updatedAt` 有更新；`DELETE` JPY → `204`。
  - **應用程式 + DB 都重啟之後**：`[CHF 瑞士法郎, EUR, GBP, TWD 台幣, USD]` —— 新增 / 修改 / 刪除都有真正 commit 並且持久化，被刪除的 JPY 沒有被 V2 重新 seed 回來（Flyway 只執行一次，符合需求 14b「第一次啟動」）。這補上了自動測試因為 `@Transactional` rollback 而沒有涵蓋的 commit 路徑。
  - `/actuator/env` → `404`（只暴露 health,info ✔）；app log 中 WARN/ERROR 0 筆；不需要 Kafka 也能正常啟動。
- **Findings（不阻擋）**:
  - **CONCERN-5**：Bean Validation 失敗時 ProblemDetail 只回 `"detail":"Invalid request content."`，**沒有說明是哪個欄位、哪條規則**。task 24 的前端要顯示「驗證錯誤訊息」時會拿不到資訊。建議覆寫 `handleMethodArgumentNotValid`，加入 `errors: [{field, message}]` 屬性，並寫進 task 16 的契約樣本。
  - **CONCERN-6（小）**：`Currency` 的 `@PrePersist/@PreUpdate` 用的是 `Instant.now()`，而不是注入的 `Clock`，和 plan「時間一律經由 Clock」不一致。這只是稽核時間戳，影響很小，之後需要測試時間時再改。
  - **CONCERN-7（小）**：`CurrencyApiTest` 用 `@Transactional` rollback 隔離，所以「commit 後另一個請求讀得到」這件事沒有自動化測試；本次由 QA 手動冒煙補上驗證。task 12 的 AC5 重建 context 測試要注意**不要**用 `@Transactional`。
  - 備註：`application.yml` 的 datasource 預設帳密 `currency/currency` 只是本機開發預設，compose / K8s 必須用環境變數覆寫。QA 會在 task 26 確認 compose 沒有依賴這個預設值上線。
  - 工程師的回覆已記錄：CONCERN-3 → 本專案不使用 failsafe（關閉）；CONCERN-4 → task 17 處理（追蹤中）。

### 2026-09-29 15:45 — Task 4（Kafka 基礎與 TickPublisher）@ `7c565b2`，以及 CONCERN-5 修正 @ `68dba77`
- **Reviewed**: `git diff 4e65235..7c565b2`（13 個檔案）、`git diff 7c565b2..68dba77`（ApiExceptionHandler + 測試）；QA worktree 兩個 commit 都做了 `clean verify`
- **Verdict**: **PASS**（task 4）；**CONCERN-5 已關閉**
- **Evidence**:
  - `7c565b2`：`./mvnw -B clean verify` → `Tests run: 18, Failures: 0`、`BUILD SUCCESS`；WARN 2 筆，都是 `TickPublisherTest` 刻意製造的（`queue full … dropped=1`、`TimeoutException: metadata not available`），ERROR 0；外部 host 0。
  - `68dba77`：`clean verify` → `Tests run: 19, Failures: 0`（工程師說的 19 是這個 commit 的數字；task 4 本身是 18）。`handleMethodArgumentNotValid` 回傳 `errors:[{field,message}]`，並依欄位排序，輸出穩定。
  - **真實 producer 的有效設定**（取自測試 log 中 Kafka `ProducerConfig` 的輸出）：`acks = -1`（all）、`enable.idempotence = true`、`max.block.ms = 5000`、`delivery.timeout.ms = 30000`、`linger.ms = 5` —— 和 plan Approach 第 9 點一致。
  - Testcontainers 實際使用 `apache/kafka:3.9.2`（和 kafka-clients 3.9.2 同一系列）。**AC13 離線 image 清單目前是**：`postgres:17.11-alpine`、`apache/kafka:3.9.2`、`testcontainers/ryuk:0.12.0`。
  - 測試內容：`TickPublisherTest` 讓 `send()` 卡住 30 秒 → 8 次 `publish()` 每次都 < 10ms，`dropped=5`，只留下最新 3 筆（`containsExactly(later[5..7])`）；send 丟例外之後 publisher 仍在執行。`PriceTickKafkaRoundTripTest` 對真實 broker 斷言：3 個 partition、`cleanup.policy=compact`、key=`BTC-USD`、沒有 `__TypeId__` header、原始 JSON 是 `"price":67123.45000000`、反序列化後 `equals`（BigDecimal scale 和 Instant 奈秒都沒有失真）。
  - 設計檢查：`publish()` 用 `offer` + `poll` 丟最舊的，不會阻塞；`send()` 在專用的 `tick-publisher` thread；log 以每 10 秒一次節流；topic 數量 / replication 可以用 `APP_KAFKA_*` 覆寫。
- **Findings（不阻擋）**:
  - **CONCERN-8（建議儘快處理）**：`application-test.yml` 沒有覆寫 `spring.kafka.bootstrap-servers` 和 `spring.datasource.url`。目前所有 `@SpringBootTest` 都繼承 `IntegrationTest`，所以會被 `@ServiceConnection` 覆寫。但只要將來有一個測試沒有繼承，它就會**悄悄連到開發者本機的 `localhost:9092` Kafka / `localhost:5432` Postgres**：有本機 Kafka 的人測試是綠的，其他人或 CI 則會失敗。這直接違反需求 18「不依賴開發者本機已經啟動的 Kafka」。建議在 test profile 設定 `spring.kafka.bootstrap-servers: 127.0.0.1:1`、datasource 指向 `127.0.0.1:1`（`@ServiceConnection` 的優先權比較高，不影響現有測試），並在 `NoExternalCallsGuardTest` 加上斷言。
  - **CONCERN-9（小）**：`sendsQueuedTicksInOrderOnceKafkaIsAvailable` 的名稱寫「依順序」，但兩個 `verify(timeout)` 並沒有驗證順序；改用 Mockito `inOrder` 就能真正驗到。
  - 備註：應用程式關閉時，佇列中還沒送出的 tick 會遺失（`stop()` 沒有 flush）。這屬於 spec「不回補」的範圍，可以接受。
  - 工程師的回覆：CONCERN-6 不處理 → **QA 接受**（JPA entity callback 無法注入 bean，影響只在稽核時間戳）。CONCERN-7 已記下，task 12 時追蹤。

### 2026-09-29 15:55 — Task 5（Coinbase / Kraken parser）@ `2b13d20`（含 CONCERN-8、9 的修正）
- **Reviewed**: `git diff 68dba77..2b13d20`（23 個檔案）、全部 10 個 fixture 檔案；QA worktree `clean verify`
- **Verdict**: **PASS**；**CONCERN-8、CONCERN-9 關閉**
- **Evidence**:
  - `./mvnw -B clean verify` → `Tests run: 50, Failures: 0`、`BUILD SUCCESS`（Coinbase parser 14、Kraken parser 16）；WARN 2（task 4 刻意製造的那兩筆）、ERROR 0、外部 host 0。
  - done-when：Kraken `trade-multi.json` → 3 個 tick，價格 `84034.9 / 84035.0 / 84036.1`（`toPlainString()=="84035.0"`，scale 保留），eventTime 精確，eventId 不重複；heartbeat → `Heartbeat` 且沒有 tick；兩個 parser 的 `@ValueSource` malformed 輸入 → `Invalid`，不會丟例外。Kraken `type:"snapshot"` → `Control`（忽略舊成交，S3 ✔）；混入 ETH/USD 的訊息只取 BTC/USD。
  - **CONCERN-8**：`application-test.yml` 新增 `spring.kafka.bootstrap-servers: 127.0.0.1:1`、`spring.datasource.url: jdbc:postgresql://127.0.0.1:1/none`；守門測試加上斷言。測試 log 中實際的 `bootstrap.servers = [localhost:53572]`（Testcontainers 的隨機 port）證明 `@ServiceConnection` 的優先權確實比較高。
  - **CONCERN-9**：改用 Mockito `inOrder`。
  - **事實更正 (a) 已查證**：Coinbase Exchange 官方文件（docs.cdp.coinbase.com/exchange/websocket-feed/channels）寫明頻道名稱是單數 `"heartbeat"`，而且「every second」送一次；`fixtures/coinbase/error.json` 是真實的拒絕訊息 `heartbeats is not a valid channel`。10 秒的 idle-timeout 足夠。只是事實更正，沒有 scope 變化。
  - (b) `JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES` 已關閉，並啟用 `USE_BIG_DECIMAL_FOR_FLOATS`（`FeedMessageParser.JSON`），有 `84035.0` 的測試保護。
- **Findings（不阻擋）**:
  - **CONCERN-10（task 9 前請決定）**：`eventId` 在每次 parse 時用 `UUID.randomUUID()` 產生。如果交易所在重新連線 / 重新訂閱後**重送同一筆成交**，就會變成兩個 eventId 不同的 tick。DB 的 `event_id unique` 去重只能擋 Kafka 重送，擋不了交易所重送，結果 `price_tick` 會重複、candle 的 `tick_count` 被灌水（OHLC 不受影響，因為價格和時間相同）。兩家都有 `trade_id`（ticker.json `trade_id`、Kraken `trade_id`），建議改用決定性的 `UUID.nameUUIDFromBytes(source + ":" + trade_id)`，就能讓重連重送也是冪等的。task 6/9 可以用「同一個 fixture 解析兩次 → eventId 相同」來測。
  - 備註：`kraken/trade-snapshot.json` 和 `subscribe-error.json` 是依文件格式合成的，不是實際擷取（README 已註明），可以接受。

### 2026-09-29 16:00 — Task 6（WebSocketPriceFeedClient）@ `b50aba1`（含 CONCERN-10 修正）
- **Reviewed**: `git diff 2b13d20..b50aba1`（13 個檔案），逐行讀了 `WebSocketPriceFeedClient`、`FakeExchangeServer`、`WebSocketPriceFeedClientTest`；QA worktree 實際執行 + **QA 自己對真實交易所做了 live smoke**
- **Verdict**: **PASS**；**CONCERN-10 關閉**
- **Evidence（自動）**:
  - `./mvnw -B clean verify` → `Tests run: 59, Failures: 0`、`BUILD SUCCESS`；ERROR 0、外部 host 0。
  - 穩定性：`WebSocketPriceFeedClientTest` 單獨重跑 **8/8 通過**；另外在 10 核心全部被 `yes` 占滿的情況下重跑 **3/3 通過**，沒有 flaky。
  - done-when (a) server close → 重連 → 收到 tick ✔；(b) `pumping(false)`（連線保持、完全不送訊息）→ 1 秒 watchdog → 第 2 條連線 → 恢復後收到 tick ✔（M3b）；(c) DISCONNECT 期間 800ms 內連線數不增加、unblock 後恢復；SILENT 期間 socket 仍在、沒有 tick、watchdog 仍會重連、unblock 後恢復 ✔。
  - CONCERN-10：兩個 parser 都有「同一筆解析兩次 → eventId 相同」的測試，Kraken 另外測了「同一個 trade_id 在另一家交易所 → 不同 id」。
- **Evidence（QA live smoke，jshell + `target/classes`，連真實的 wss://ws-feed.exchange.coinbase.com 與 wss://ws.kraken.com/v2；不屬於自動測試）**:
  - 15 秒：`counts={kraken=9, coinbase=37}`，兩個都 connected；接收延遲（receivedAt − eventTime）約 `coinbase 73ms`、`kraken 92ms`。→ 真實交易所可以接受我們的訂閱訊息，parser 也能處理真實流量。
  - 對 coinbase 設定 `block(SILENT)` → 10 秒後 log：`connection dropped (no message for 10000 ms (idle watchdog)), reconnecting in 1000 ms` → `unblock` 後 5 秒內收到 16 筆新的 tick。→ 真實環境下 watchdog 和 chaos 路徑都可以運作。
  - **90 秒成交間隔量測**：`ticks={kraken=51, coinbase=288}`、`maxGapMs={kraken=9280, coinbase=1525}`。**Kraken 最大的成交間隔是 9.28 秒，距離 10 秒的 stale 門檻只差 0.7 秒** → 見 task 7 的 CONCERN-12。
- **Findings（不阻擋）**:
  - **CONCERN-11（小）**：「退避在收到第一則有效訊息後才歸零」這個設計決定是正確的，但沒有測試；指數退避的序列（100→200→400 上限）也沒有被直接斷言。建議加一個「server 接受後立刻 close」的測試，斷言在固定時間內的連線次數沒有超過指數退避的預期上限。
  - 備註：`PriceFeedClient` 只暴露 `lastTickAt()`，沒有 `lastMessageAt()`（heartbeat），這和 CONCERN-12 有關。

### 2026-09-29 16:10 — Task 7（FeedManager 故障切換與 FeedStatus）@ `2052a70`
- **Reviewed**: `git diff b50aba1..2052a70`（10 個檔案），逐行讀了 `FeedManager`、`FeedConfig`；QA worktree 實際執行 + **QA 對真實交易所做了 FeedManager live 故障切換測試**
- **Verdict**: **PASS**（附 CONCERN-12，建議在 task 8/9 之間決定）
- **Evidence（自動）**:
  - `./mvnw -B clean verify` → `Tests run: 69, Failures: 0`、`BUILD SUCCESS`；ERROR 0、外部 host 0。FeedManagerTest 9、FeedWiringTest 1、守門測試 5 都通過。
  - done-when 對照：9.999s 不是 stale / 10s 是 stale；最後一筆 tick 在 +0.3s 時 → 切換 ≤ 11s，並且在同一次 check 就發佈（T1 ✔）；+14s 仍在備援 / +15s 切回；recovery 期間中斷一次 → 重新計時；STALE 與 DISCONNECTED 的區分；只轉發 active 來源的 tick；每 5 秒重發一次。`FeedManager` bean 用 `@ConditionalOnProperty(app.ingest.enabled=true)`，守門測試斷言 test profile 下沒有這個 bean。
- **Evidence（QA live：jshell 用真實 `WebSocketPriceFeedClient` 連 Coinbase / Kraken，Kafka 以 Mockito mock 代替，stale 10s、recovery 15s 都是預設值）**:
  ```
  t=1s  coinbase/STALE      （啟動時還沒收到第一筆 tick）
  t=2s  coinbase/LIVE
  t=15s BLOCK coinbase SILENT
  t=25s kraken/LIVE         ← Switching coinbase -> kraken (coinbase is stale)   約 10 秒
  t=50s UNBLOCK coinbase
  t=67s coinbase/LIVE       ← Switching kraken -> coinbase (primary healthy for 15s)   約 17 秒
  ```
  → AC6「30 秒內切到備援、主來源恢復後不重啟自動切回」在真實交易所上成立（封鎖後約 10 秒切換，時間預算還剩 20 秒給發佈 + Kafka + SSE + 前端）。
- **Findings**:
  - **CONCERN-12（建議修改，屬於 plan 層級的細節，需要工程師決定；有分歧時交給 team lead）：市場冷清時會誤判 stale / STALE**。`healthy()` 看的是 `lastTickAt`（成交），不是 `lastMessageAt`（包含每秒一次的 heartbeat）。QA 在 task 6 量到 **Kraken 90 秒內的最大成交間隔是 9,280ms**，距離 10 秒門檻只差 0.7 秒；深夜或週末交易量更少時，一定會出現 ≥ 10 秒沒有成交的時段。後果：(1) 主來源掛掉、跑在 Kraken 時，只要 Kraken 10 秒沒有成交，狀態就在 LIVE ↔ STALE 之間跳動，前端顯示「資料延遲」，但實際上連線健康、價格也仍然是最新的（只是沒人成交），這是**誤報**；(2) 主來源剛好 stale 的那一刻，如果 Kraken 10 秒內沒有成交，就不會切換，要等到 Kraken 下一筆成交。這不違反 AC 字面上的要求，但會讓 AC2 / AC6 手動驗收時出現讓人困惑的狀態。建議：「來源健康」改用 `lastMessageAt`（heartbeat 也算，兩家都是每秒一次）；「價格是否太舊」可以另外用比較寬的門檻（例如 60 秒）決定是否顯示 STALE。`PriceFeedClient` 需要多暴露一個 `lastMessageAt()`。SILENT / DISCONNECT / 真實斷網都會讓 heartbeat 停止，所以故障偵測能力不會變弱。
  - 備註：啟動後第 1 秒會出現一次 `STALE`（還沒收到第一筆 tick）。前端可以把它顯示成「連線中」，不影響 AC。
  - 備註：`FeedConfig.urlFor` 用 parser 的名稱對應 URL，主來源 / 備援寫死是 Coinbase / Kraken（名稱不能用 env 覆寫）；URL 可以覆寫（需求 4 ✔）。

### 2026-09-29 16:20 — Task 8（chaos 故障注入端點）@ `253c111`
- **Reviewed**: `git diff 2052a70..253c111`（4 個檔案）；QA worktree `clean verify` + **QA 照手動驗收流程實跑**（`java -jar` + `postgres:17.11-alpine` + `apache/kafka:3.9.2` container + 真實 Coinbase/Kraken）
- **Verdict**: **PASS**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 73, Failures: 0`、`BUILD SUCCESS`；`FeedsChaosEndpointTest$ChaosProfile` 3、`$DefaultProfile` 1；ERROR 0、外部 host 0。
- **Evidence（QA 手動，完全照 AC6 的手動步驟）**:
  - 預設 profile：`GET /actuator/feeds` → `404`，`POST …/coinbase/block` → `404`（端點不存在 ✔）。
  - `SPRING_PROFILES_ACTIVE=chaos`：GET 回傳 `activeSource/status/clients`；未知 source `binance` → `404`；未知 action → `400`；`mode=foo` → `400`；沒有帶 `Content-Type` → `415`（和工程師說明一致，README 需要寫進去）。
  - AC6 實跑（DISCONNECT 模式）：
    ```
    t=0s  BLOCK coinbase → coinbase connected=False, blockMode=DISCONNECT
    t=1s  active=kraken LIVE        ← 1 秒切換（DISCONNECT 使 isConnected=false，立即不健康）
    t=20s UNBLOCK
    t=21s coinbase connected=True
    t=38s active=coinbase LIVE      ← 解除後約 17 秒切回（第一筆 tick + recovery 15s）
    ```
    （task 7 的 SILENT 模式 live 測試：約 10 秒切換、約 17 秒切回。）
  - **Kafka 實際內容**（從 host 用 kafka-clients 讀 offset）：`btc.price.ticks` end offsets `{p0=131, p1=0, p2=0}`（key 都是 `BTC-USD` → 同一個 partition，符合設計）；`source counts={coinbase=95, kraken=36}`，kraken 只出現在封鎖期間 → **hot standby 只轉發 active 來源**，在真實環境下成立；`btc.feed.status` 共 48 筆。樣本 `eventId":"4531d532-1bc2-310e-…"` 是 v3 name-based UUID → CONCERN-10 的決定性 id 在真實資料上也成立。
  - （QA 自己的工具錯誤紀錄：第一次在 container 內用 `kafka-console-consumer --bootstrap-server localhost:9092` 讀到 0 筆，原因是 advertised listener 是 `localhost:59092`，從 container 內連不到；改從 host 讀之後得到上面的正確結果。這和應用程式無關。）
- **Findings（不阻擋）**:
  - `btc.price.ticks` 開了 3 個 partition，但 key 固定，所以實際只會用到 1 個。這是 plan 的設計（保證順序），但 3 個 partition 目前沒有作用。記錄下來，不需要修改。

### 2026-09-29 16:20 — CONCERN-12 的決定（team lead `personal-workplace-7a`）
- 採用方案 (a)：健康 = 已連線 + `lastMessageAt` 在 10 秒內（heartbeat 也算）+ `lastTickAt` 在 60 秒內（`APP_FEED_PRICE_STALE_THRESHOLD`）。工程師會用 task 7 的 follow-up commit 實作（同時補上 CONCERN-11 的退避測試）。
- QA 驗收這個 follow-up 時的檢查清單（team lead 指定 + QA 補充）：
  1. 59.999s / 60s 價格門檻的邊界；9.999s / 10s 訊息門檻的邊界
  2. 主來源只剩 heartbeat 超過 60 秒 → 切到備援
  3. 兩邊都只剩 heartbeat → 狀態**不能**是 LIVE
  4. SILENT / DISCONNECT 的故障偵測時間沒有變慢（QA 會重跑 live chaos：SILENT 約 10 秒內切換）
  5. CONCERN-11：退避序列與「收到第一則有效訊息才歸零」的測試
  6. （task 21 時）前端「15 秒無事件」的計時要把 `status` 事件也算進去

### 2026-09-29 16:35 — Task 9（逐筆價格落地）@ `fd1354f`
- **Reviewed**: `git diff 253c111..fd1354f`（7 個檔案）；QA worktree `clean verify` + **QA live：DB 中斷測試**
- **Verdict**: **PASS**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 74, Failures: 0`、`BUILD SUCCESS`；ERROR 0、外部 host 0。`TickPersisterTest`：500 筆（480 筆不同 + 20 筆 eventId 重複）→ DB 剛好 480 筆，之後 `await().during(2s)` 仍是 480，所有欄位都一致。
- **Evidence（QA live：jar + Postgres/Kafka container + 真實交易所，ingest + persist 都開啟）**:
  - 執行 20 秒後 DB 有 90 筆 → `docker stop` Postgres **25 秒** → `docker start` → 再等 20 秒。
  - 結果：Kafka `btc.price.ticks` end offset **263**，DB `price_tick` **263** 筆；以秒為單位檢查 event_time，**沒有 >5 秒的斷層** → 25 秒的 DB 中斷**沒有遺失任何資料**，恢復後自動補寫。
  - 程式碼檢查：`ON CONFLICT (event_id) DO NOTHING` + JDBC batch；`ErrorHandlingDeserializer` 讓壞掉的 record 變成 null，會被過濾並記 WARN；consumer factory 從 Boot 的設定複製，保留 `@ServiceConnection` 的位址。
- **Findings（不阻擋）**:
  - **CONCERN-13**：這次沒有遺失，是因為 Hikari 的 `connectionTimeout`（預設 30 秒）讓 insert **在原地等候**。如果 DB 中斷超過約 30 秒，`insertAll` 會丟例外，Spring Kafka 預設的 `DefaultErrorHandler` 是 `FixedBackOff(0, 9)`：沒有間隔地重試 9 次後就**略過該批次並 commit offset** → 永久遺失（而且 K 線那一路不受影響，造成 AC4 的 OHLC 與逐筆不一致）。建議為 persister（以及之後的 candle / alert consumer）設定帶退避的 `DefaultErrorHandler`（例如 `ExponentialBackOff` 最大間隔 30 秒、不限次數，或至少數分鐘），讓 DB 長時間中斷時 consumer 暫停而不是丟資料。可以在 task 12 一起處理。
  - 小：`TickPersisterTest` 沒有涵蓋「無法反序列化的 record 被略過、後續的 record 仍然寫入」（poison pill）；ErrorHandlingDeserializer 的路徑目前沒有測試。建議補一個。

### 2026-09-29 16:55 — Task 10（價格查詢 API）@ `70352c1`
- **Reviewed**: `git diff fd1354f..70352c1`（18 個檔案），逐行讀了 PriceQueryRepository / Service / Controller / HistoryCursor / FeedStatusTracker；QA worktree `clean verify` + 契約反向驗證 + live API 實測
- **Verdict**: **PASS**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 86, Failures: 0`、`BUILD SUCCESS`；PriceQueryApiTest 5、ContractSamplesTest 3、FeedStatusTrackerTest 4；ERROR 0、外部 host 0。
  - **契約反向驗證（QA 親自做）**：把 `prices-latest.json` 的 `"coinbase"` 改成 `"coinbasX"` → `ContractSamplesTest` 失敗；`git checkout` 還原。契約測試是有效的（T3 ✔）。
- **Evidence（QA live：chaos jar + Postgres/Kafka + 真實交易所，約 70 秒資料）**:
  - `/api/prices/history?limit=50` 依照 `nextCursor` 翻頁 → **4 頁、169 筆 = DB `count(*)` 169**，依 eventTime 排序，沒有重複也沒有遺漏；其中 coinbase 114、kraken 55（SILENT 期間）。
  - `/api/prices/latest` → `{"price":83979.00000000,"source":"coinbase",…,"status":{"activeSource":"coinbase","state":"LIVE",…}}`。
  - `/api/prices/trend?points=30` → `bucketSeconds=30`，3 個點（預設範圍 15 分鐘，資料約 70 秒）。
  - 錯誤參數：`from>to`、`limit=0`、`limit=abc`、`cursor=zzz`、`points=1001` → 全部 `400`。
- **Findings（不阻擋）**:
  - **CONCERN-14**：`/history` 的排序是 `(event_time, id)`，但 plan / S3 定義的 OHLC 標準排序鍵是 `(event_time, received_at, event_id)`，而 `PricePoint` 沒有 `receivedAt` / `eventId`。如果 AC4 的手動驗收用「history 翻頁取完 → 自己算 OHLC」，在**同一個 event_time 有多筆不同價格**時，open/close 的判定可能和 Streams 不一樣（誤判為不一致）。建議二選一：(a) `PricePoint` 加上 `receivedAt`、`eventId`，README 說明要用哪個排序；或 (b) AC4 的手動比對一律用 README 的 SQL（依標準排序鍵），不要用 history API。task 12 / 27 時確認。
  - 小：`FeedStatusTracker` 的 group id 是 `feed-status-${random.uuid}`，每次重啟都會在 Kafka 留下一個孤兒 consumer group（7 天後才會過期）。本機可以接受；上 K8s 前可以考慮 `group.id` 用 pod 名稱，或改用不 commit offset 的 assign 模式。
  - 小：`btc.feed.status` 是 compact topic，但沒有設定 `segment.ms`，active segment 不會被壓縮，所以 `earliest` 在啟動時會重播好幾天的狀態（每 5 秒一筆）。功能上沒問題（取 reportedAt 最新的），只是啟動時多讀一些資料。

### 2026-09-29 16:55 — Task 7 follow-up（CONCERN-12 + CONCERN-11）@ `730d9d0`
- **Reviewed**: `git diff 70352c1..730d9d0`（14 個檔案，含 plan.md / tasks.md 的更新）；QA worktree `clean verify` + 重跑 + live SILENT 故障切換
- **Verdict**: **PASS**；**CONCERN-11、CONCERN-12 關閉**
- **Evidence**:
  - `./mvnw -B clean verify` → `Tests run: 94, Failures: 0`、`BUILD SUCCESS`；`FeedManagerTest,WebSocketPriceFeedClientTest`（14 + 9）單獨重跑 **5/5 通過**。
  - team lead 的檢查清單：`heartbeatsKeepAQuietSourceHealthyForUpToSixtySecondsWithoutTrades`（只有 heartbeat 30 秒仍然 LIVE）✔；`lastMessageBoundaryIsTenSecondsEvenWithRecentTrades`（9.999/10）✔；`priceStaleBoundaryIsSixtySecondsWhileHeartbeatsContinue`（59.999/60）✔；`primaryWithOnlyHeartbeatsForOverSixtySecondsFailsOverToTradingBackup` ✔；`bothWithOnlyHeartbeatsForOverSixtySecondsIsStaleNotLive` ✔。
  - CONCERN-11：`backoffDoublesUpToTheMaximumWhileTheServerIsDown`、`backoffKeepsGrowingWhenServerAcceptsButDropsBeforeAnyMessage`、`backoffResetsOnceANewConnectionDeliversItsFirstMessage` ✔。
  - 程式碼：新增 `lastReceivedAt` 只在**真正收到訊息**時更新；watchdog 用的 `lastMessageAt` 在 connect 時重設，兩者分開 → 剛連上但還沒有收到任何訊息的來源，不會被誤判為健康。✔
  - **Live（QA 檢查清單第 4 點：偵測速度沒有變慢）**：chaos jar + 真實交易所，`block?mode=silent` → **t=10s 切到 kraken**；t=25s unblock → **t=43s 切回 coinbase**（約 18 秒 = 第一則訊息 + 15 秒 recovery）。和修改前（task 7 live：約 10 秒 / 約 17 秒）一樣。
  - plan.md Approach #2 和 tasks.md task 21 的說明（15 秒計時要算所有 SSE 事件）已更新；task 21 時 QA 會確認。

### 2026-09-29 17:10 — Task 11（Kafka Streams K 線 topology）@ `8371673`
- **Reviewed**: `git diff 730d9d0..8371673`（13 個檔案），逐行讀了 CandleTopology / CandleAccumulator / PriceTickTimestampExtractor / TickOrder / CandleStreamsConfig；QA worktree `clean verify` + QA 用 Postgres 查證排序與精度
- **Verdict**: **PASS**
- **Evidence**:
  - `./mvnw -B clean verify` → `Tests run: 100, Failures: 0`、`BUILD SUCCESS`；CandleTopologyTest 5、TickOrderTest 1；ERROR 0；log 中 `StreamThread` 出現 0 次（test profile 預設關閉 Streams ✔）；外部 host 0。
  - done-when 對照（CandleTopologyTest，record timestamp 和 eventTime 刻意差 1 小時 → 證明用的是事件時間）：12 分鐘 → 12 根 1m、2 根 5m，OHLC 等於用 TickOrder 手算的結果；`12:05:00.000` 屬於 12:05 那根；同一個 eventTime 的 tie 依 receivedAt → 無號 eventId 決定；超過 grace 的 tick 被丟棄並計入 `dropped-records-total`；end+grace 前 1 秒沒有輸出、到 end+grace 剛好輸出一根；2 筆壞訊息被略過，之後照常處理（LogAndContinue ✔）。S3 / S5(d) 全部涵蓋。
  - **工程師的發現已查證**：Postgres `ORDER BY uuid` → `00000000…`、`7fffffff…`、`80000000…`（無號 byte 比較）；Java `UUID.compareTo` 對 `7fff…` vs `8000…` 回傳正數（有號）。TickOrder 改成無號比較是正確的，TickOrderTest 有鎖住這個行為。
- **Findings（不阻擋）**:
  - **CONCERN-15（建議，低風險、修改便宜）：Java 與 DB 的時間精度處理方式不同**。QA 實測 pgjdbc 會把奈秒**四捨五入**到微秒：寫入 `2026-09-29T12:04:59.999999600Z` → DB 存成 `2026-09-29T12:05:00Z`。但 `TickOrder` 是**截斷**到微秒（→ `12:04:59.999999`），Streams 的 extractor 則是截斷到毫秒（→ 12:04 那根）。所以一筆奈秒精度的 tick，在 Streams 屬於 12:04 那根，在 DB 查詢卻屬於 12:05 那根 → AC4 的 OHLC 會不一致。目前交易所給的 eventTime 都只到微秒，所以實際上不會發生；但 `receivedAt = clock.instant()`，在 Linux（Docker/K8s）上 `Clock.systemUTC()` 可能有奈秒精度（macOS 實測只到微秒：`…26.726175Z`），這會影響 tie-break。建議在 `PriceTick` 的 compact constructor 把 `eventTime` / `receivedAt` 統一 `truncatedTo(MICROS)`，從源頭消除這一整類問題（再加一個測試）。
  - 小：tickCount 在 Streams 和 DB 可能不同。交易所重連後重送的成交（eventId 相同），DB 會用 `ON CONFLICT` 去重，但 Streams 會算兩次；OHLC 不受影響（價格和時間相同），AC4 只要求 OHLC 一致。記錄下來，README 的 AC4 比對不要比 tick_count，或者要說明原因。
  - 備註：Boot 實際接線（`@EnableKafkaStreams`、state-dir、replication）的測試在 task 12 的端到端測試中，QA 屆時會確認真的有 StreamThread 在跑並輸出到 `btc.candles`。

### 2026-09-30 11:20 — Task 12（K 線落地與查詢 API）@ `fa7f3c3`（含 CONCERN-7/13/14/15 的修正）
- **Reviewed**: `git diff 8371673..fa7f3c3`（18 個檔案），讀了 CandleRepository / Persister / Controller / KafkaConsumerConfig / CandlePipelineTest；QA worktree `clean verify` + **兩輪 live 實跑（真實交易所、Streams 開啟、DB 中斷）**
- **Verdict**: **PASS**；**CONCERN-7、13、14、15 關閉**
- **Evidence（自動）**:
  - `./mvnw -B clean verify` → `Tests run: 107, Failures: 0`、`BUILD SUCCESS`；外部 host 0。唯一一筆 ERROR 是 `PersistingErrorHandlerTest` 刻意對照 Spring 預設 `FixedBackOff(0,9)` 產生的 `Records discarded`（預期中）。
  - Boot 接線的 Streams 確實有啟動：log 出現 `CREATED→REBALANCING→RUNNING`、`PARTITIONS_ASSIGNED→RUNNING`。
  - `CandlePipelineTest`（真實 Kafka + Streams + Postgres，沒有 `@Transactional` → CONCERN-7 ✔）：12 分鐘 → 12 根 1m、2 根 5m；每根 OHLC 以及 tick_count 都等於 `price_tick` 依 `(event_time, received_at, event_id)` 的 SQL 聚合；tie tick 刻意使用最高位元為 1 的 UUID（`0xF000…`）和 `0x0F00…` → 驗證了無號排序。之後啟動一個全新的 app instance（同一個 DB），查得到 12/2 根 candle 以及 168 筆 tick（AC5 的自動部分）。
  - CONCERN-14：`PricePoint` 新增 `receivedAt`、`eventId`，契約樣本已重新產生。CONCERN-15：`PriceTick` 的 compact constructor 會 `truncatedTo(MICROS)`，`PriceTickTest` 測 `12:04:59.9999996` 仍然落在 12:04。
- **Evidence（QA live #1，約 6.5 分鐘：jar + Postgres/Kafka + 真實交易所，Streams 與 persist 都開啟；中途 `docker stop` Postgres 70 秒）**:
  - Kafka `btc.price.ticks` end offset **1276** = DB `price_tick` **1276**；event_time 以秒為單位沒有 >5 秒的斷層。
  - **真實市場資料的 AC4 對照**（對每根 candle 以 SQL 從 price_tick 重算）：
    ```
    iv | t     | o_ok | h_ok | l_ok | c_ok | tick_count | db_n
    1m | 03:07 ~ 03:13（7 根）| 全部 t | 全部 t | 全部 t | 全部 t | 38/218/239/217/138/193/176 = db_n
    5m | 03:05 | t    | t    | t    | t    |        495 |  495
    ```
    → 在跨越 70 秒 DB 中斷的情況下，7 根 1m + 1 根 5m 的 OHLC 與 tick_count **全部一致**（AC4 的核心在真實資料上成立；完整的「10 分鐘、≥10 根 1m、≥2 根 5m」留到 task 28 的 compose 驗收）。
  - 限制：Hikari `connectionTimeout`=30 秒，每次寫入都在原地等候，所以這輪**無法區分**新策略和預設策略（和工程師遇到的情況一樣）。
- **Evidence（QA live #2：刻意讓 DB 真的失敗而不是卡住）**：設定 `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=1000`，並 `docker stop` Postgres **60 秒**（預設 `FixedBackOff(0,9)` 在約 10 次 × 1 秒之後就會丟掉批次）：
  - Kafka end offset **789** = DB **789**，沒有斷層，log 中沒有 `Records discarded` / `exhausted` → **新的不限次數指數退避策略撐過了 60 秒的真實失敗**，CONCERN-13 關閉。
  - 誠實說明：`DefaultErrorHandler` 預設不會把每次重試寫進 log，所以 log 中看不到失敗次數（`grep` 為 0），無法從 log 直接證明「失敗發生了幾次」；但 1 秒 timeout × 60 秒中斷在物理上必然會失敗，並且 `PersistingErrorHandlerTest` 已經用真實 container 證明預設策略在第 10 次失敗後會丟資料。
- **Findings（不阻擋）**:
  - **CONCERN-16**：(a) 不限次數重試也包含**非暫時性錯誤**（例如數值超出 `numeric(20,8)`、欄位太長、NOT NULL）→ 同一批會永遠重試，**整條 persister 卡死**（至少不會丟資料，但後續資料也寫不進去）。建議 `DefaultErrorHandler.addNotRetryableExceptions(DataIntegrityViolationException.class, …)`，對這類錯誤只略過該筆並記錄 ERROR（或送到 DLT）。(b) 重試過程沒有 log，DB 中斷時完全看不出 consumer 正在退避；建議 `setLogLevel(WARN)` 或加一個 `RetryListener`，每次失敗記一行節流的 WARN。readiness（task 17）本來就會因為 DB 顯示 DOWN，所以 (b) 是可觀察性問題。

### 2026-09-30 11:25 — Task 13（逐筆保留期清除）@ `d58af4d`
- **Reviewed**: `git show d58af4d`（RetentionJob + 2 個測試）；QA worktree `clean verify` + live 縮短保留期
- **Verdict**: **PASS**
- **Evidence**:
  - `./mvnw -B clean verify` → `Tests run: 109, Failures: 0`；唯一的 ERROR 是 PersistingErrorHandlerTest 刻意對照產生的（預期中）。RetentionJobTest（獨立 database + Flyway）：25,001 筆過期（含 cutoff 前 1µs）全部刪除，batches=4；剛好等於 cutoff 的那筆和 100 筆未過期的都保留；candle 2 筆不變；再跑一次刪 0 筆（S6 ✔）。RetentionScheduleTest：`APP_RETENTION_INTERVAL=PT1S`、沒有手動呼叫 → `scheduling-1` 執行緒 log `Retention: deleted 1 ticks …`（M6 ✔）。
  - **QA live（AC9 的手動路徑）**：`APP_RETENTION_TICKS=PT20S`、`APP_RETENTION_INTERVAL=PT10S`，接真實交易所 → 每 10 秒 log `Retention: deleted 19 / 39 ticks older than …`；`price_tick` 最舊資料的年齡連續 3 次量測都是 25 / 26 / 25 秒（= 20 秒保留期 + 最多 10 秒間隔）→ 自動清除在真實環境下成立。
- **Findings（不阻擋）**:
  - **CONCERN-17**：`@Scheduled(initialDelayString = "${app.retention.interval}")` → 預設**啟動後 1 小時才第一次執行**。如果之後在 K8s 上 pod 重啟 / rolling deploy 的間隔 < 1 小時，清除就永遠不會執行，表會無限成長。建議 `initialDelay` 改成固定的短延遲（例如 1 分鐘），`fixedDelay` 維持 interval。
  - 小（效能）：刪除用的子查詢 `WHERE event_time < ?` 沒有帶 `pair`，而索引是 `(pair, event_time)`；PG 17 沒有 skip scan，所以「沒有東西要刪」的那次最後檢查會做全表 seq scan（30 天約數千萬筆，每小時一次）。加上 `pair = 'BTC-USD'` 就能使用索引。之後資料量大時再處理即可。

### 2026-09-30 11:25 — Task 14（匯率與多幣別換算）@ `7c18fe2`
- **Reviewed**: `git show 7c18fe2`（17 個檔案）；QA worktree `clean verify` + **live 對照 open.er-api.com（AC3）**
- **Verdict**: **PASS**
- **Evidence**:
  - `./mvnw -B clean verify` → `Tests run: 116, Failures: 0`；ExchangeRateClientTest 4、ConversionTest 2、ContractSamplesTest 5、守門測試 5（test profile 下沒有 FxRateRefresher bean）；外部 host 0（`open.er-api.com` 只出現在 fixture 的 README 說明，沒有連線）。
  - **QA live AC3**（jar 以預設設定從真實 open.er-api.com 抓匯率：`Exchange rates refreshed: 166 currencies, provider time 2026-09-30T00:02:31Z`），將 `/api/prices/converted` 的 `price / usdPrice` 和同時間 `curl https://open.er-api.com/v6/latest/USD` 比對：
    ```
    EUR 歐元   implied=0.881520   er-api=0.88152    err=0.00000%
    GBP 英鎊   implied=0.755961   er-api=0.755961   err=0.00000%
    JPY 日圓   implied=157.389062 er-api=157.389062 err=0.00000%
    TWD 新台幣 implied=31.840710  er-api=31.84071   err=0.00000%
    USD 美元   implied=1.000000   er-api=1          err=0.00000%
    rateUpdatedAt=2026-09-30T00:02:31Z = provider time_last_update_utc "Wed, 30 Sep 2026 00:02:31 +0000"
    ```
    → **AC3 後端部分成立**（誤差 0 < 0.5%，並有匯率更新時間）；`rateSource` = "Rates By Exchange Rate API (https://www.exchangerate-api.com)"（S2；前端的連結在 task 22 驗收）。
- **Findings（不阻擋）**:
  - **CONCERN-18**：失敗後要等下一個完整的 `refresh-interval`（30 分鐘）才會重試。全新環境第一次啟動時如果剛好遇到網路暫時不通或 429，前端會有 **30 分鐘**全部顯示「無匯率」→ AC1/AC3 的 compose 驗收可能會踩到。建議失敗時改用較短的重試間隔（例如 1 分鐘，遇到 429 則依條款等 20 分鐘）。
  - 小：`fx_rate.rate_per_usd numeric(20,8)`：匯率 < 1e-8 的幣別會被四捨五入成 0（open.er-api 目前沒有這種幣別），不影響 5 個預設幣別。

### 2026-09-30 11:30 — Task 15（價格警示與未讀紀錄）@ `d8040cd`
- **Reviewed**: `git diff 8ddbec5..d8040cd`（16 個檔案），讀了 V6、AlertRule、AlertEvaluator、`AlertRepository.claimTrigger`；QA worktree `clean verify` + **live AC7/AC8**
- **Verdict**: **PASS**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 128, Failures: 0`；AlertRuleTest 5、AlertEvaluationTest 1（真實 Kafka、沒有瀏覽器：T、T+4:59.999、T+5:00、T+11:00(不成立) → 剛好 2 筆未讀）、AlertApiTest 4；唯一的 ERROR 是 PersistingErrorHandlerTest 的對照組；外部 host 0。
  - 程式碼：冷卻以 `tick.eventTime` 計算，`>= cooldown`；`claimTrigger` 是條件式 `UPDATE … WHERE last_triggered_at IS NULL OR last_triggered_at <= at - cooldown`，由 DB 原子地搶到觸發權 → 重送 / 重試 / 多個 instance 都不會重複觸發 ✔。同一批中的本地副本會同步更新 last_triggered_at ✔。`ON DELETE CASCADE` + 未讀的 partial index ✔。
- **Evidence（QA live：jar + 真實交易所，`APP_ALERT_COOLDOWN=30s`）**:
  - 當時價格 83233 → 建立 `ABOVE 81233`（持續成立）與 `BELOW 81233`（不會成立）；錯誤 body → `400`；`PUT /api/alerts/1` → `405`（依 spec 沒有 PUT）。
  - 75 秒後 `GET /api/alert-events?unread=true` → **3 筆**，全部屬於 alert 1，`readAt=null`；觸發間隔 **30.003s、30.759s**（≥ 冷卻期，且冷卻後條件仍成立會再觸發 ✔）；BELOW 那個警示 0 筆 ✔。
  - `POST /api/alert-events/3/read` → `204`，未讀剩 2、全部 3 ✔（AC8 後端）。
  - Kafka `btc.alerts.triggered` end offset = **3**（和 DB 一致，task 16 SSE 會用到）。
  - → AC7 的「觸發 + 冷卻 + 冷卻後再觸發」後端在真實資料上成立（以 30s 冷卻期觀察；預設 5 分鐘的實跑留到 task 28，照 S7）。
- **Findings（不阻擋）**:
  - 小：DB 交易 commit 之後才 `kafka.send`；如果 send 失敗，事件已經在 DB（下次開頁面會以未讀顯示），但當下不會跳出 toast。這是合理的取捨，記錄下來。
  - 小：`auto.offset.reset=latest` 只影響「全新」的 consumer group；已經 commit 過的 group 重啟後會處理停機期間累積的 tick，舊的觸發會以過去的 `triggeredAt` 補上，這正好符合 14a「沒開頁面期間觸發的也要記錄」。

### 2026-09-30 11:55 — Task 16（SSE 即時推播）@ `44c3523`
- **Reviewed**: `git diff d8040cd..44c3523`（14 個檔案），逐行讀了 SseBroadcaster；QA worktree `clean verify` + 重跑 + **live SSE（curl 串流 40 秒 + chaos 切換）**
- **Verdict**: **PASS**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 134, Failures: 0`；SseStreamTest 3、FeedWiringTest 1、ContractSamplesTest 10；`SseStreamTest,FeedWiringTest` 重跑 **3/3 通過**；唯一的 ERROR 是對照組；外部 host 0。
- **Evidence（QA live：chaos jar + 真實交易所，`curl -N /api/stream` 40 秒，第 12 秒 block coinbase）**:
  - 連線後**第一、二個事件**就是 `status`（coinbase/LIVE）和 `price`（83275.49）→ 新連線會立即補送狀態和最新價格 ✔。
  - `price` 32 筆（coinbase 21 筆 / 前 12 秒；kraken 11 筆），相鄰事件的**最小間隔 0.215s**（≥ 約 250ms 節流，考量排程誤差）✔。
  - block 後 **+12.7s 出現 `status {"activeSource":"kraken","state":"LIVE"}`**（block 在 +12s，約 0.7 秒就推到瀏覽器端）→ AC6「前端顯示目前來源」的後端推播路徑成立。
  - `:keepalive` comment 在 +6.2s、+21.1s、+36.0s（每 15 秒）✔。
- **Findings（不阻擋）**:
  - **CONCERN-20（小）**：`status` 事件實際上**約每秒一筆**（40 秒內 23 筆），不是 plan 說的「狀態改變時 + 每 5 秒」。原因應該是 `FeedStatus.sameAs` 把 `lastTickAt` 也算進「是否改變」，每次有新成交就算改變。功能上沒問題（前端的 15 秒計時反而更穩），但 compact topic `btc.feed.status` 每天約 8.6 萬筆。可以讓 `sameAs` 只比較 `activeSource + state`，或者接受現況並更新 plan 的描述。
  - 小：所有連線共用單一 `sse-sender` 執行緒同步寫出。遇到很慢的 client（TCP 緩衝區滿）時，會卡住其他連線（head-of-line blocking）。單一使用者的情境可以接受。

### 2026-09-30 11:55 — CONCERN-16/17/18 修正 @ `23eba43`
- **Verdict**: **PASS**；**CONCERN-16、17、18 關閉**
- **Evidence**: `./mvnw -B clean verify` → `Tests run: 141, Failures: 0`。
  - 16a：`addNotRetryableExceptions(DataIntegrityViolationException)`，被拒絕的 record 會記 `Skipping record … that can never be stored`；`PriceTickRepository` / `CandleRepository` 批次失敗時改為逐筆寫入，只丟棄被拒絕的那一筆並記 `Dropping tick …`（PersisterResilienceTest 3：溢位價格被丟棄，另外 2 筆寫入）。16b：`RetryListener` 每次失敗記 WARN（log 中可以看到 `Writing a batch of 1 record(s) to the database failed (attempt 1..5), will retry: …`）。
  - 17：`@Scheduled(initialDelayString = "${app.retention.initial-delay}")`，預設 `PT1M`；DELETE 加上 `pair = ?`。
  - 18：`FxRateRefresherTest` 4 個測試：失敗 +1 分鐘、429 +20 分鐘；不會重複排程；成功時取消待執行的重試。

### 2026-09-30 11:55 — Task 17（健康檢查與 readiness）@ `1091f92`
- **Reviewed**: `git diff 23eba43..1091f92`（7 個檔案）；QA worktree `clean verify` + **live AC12（停掉 Kafka / DB）**
- **Verdict**: **PASS**；**CONCERN-4 關閉**
- **Evidence（自動）**: `./mvnw -B clean verify` → `Tests run: 150, Failures: 0`；ReadinessTest 2（docker pause/unpause，M1 ✔）、KafkaStreamsHealthIndicatorTest 7、CandlePipelineTest 斷言 readiness/kafkaStreams 為 UP。
- **Evidence（QA live：jar + 真實交易所，Streams 開啟）**:
  - 正常時 `/actuator/health/readiness` → `{"status":"UP","components":{"db":UP,"kafka":UP,"kafkaStreams":UP,"readinessState":UP}}`（只有 UP/DOWN、沒有細節）；`/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`（CONCERN-4 ✔）。
  - **AC12**：`docker stop` Kafka → **t=1s** `ready=503 live=200`，`kafka: DOWN`；t=41s `docker start` → **t=46s** `ready=200`，而且 `kafkaStreams` 仍然是 UP（Streams 在 40 秒的中斷後自己恢復）。liveness 全程維持 200 ✔。
  - DB：`docker stop` Postgres → readiness **`503`，但每次都要 `30.0s` 才回應**（兩次量測 30.037s / 30.016s）；liveness `200`、`0.003s`。
- **Findings（不阻擋）**:
  - **CONCERN-19**：DB 斷線時 readiness 的回應時間是 **30 秒**（Hikari `connectionTimeout` 預設 30 秒，`db` health 會卡在取得連線）。需求 15「DB 連不上 readiness 要回報失敗」最終是成立的；K8s probe 預設 `timeoutSeconds=1`，逾時也算失敗，所以不影響 K8s 判定。但 (a) 手動驗收 `curl` 時看起來像當掉；(b) 每次探測都會卡住一條 Tomcat 執行緒 30 秒。建議把 `spring.datasource.hikari.connection-timeout` 設成約 3–5 秒（可用環境變數覆寫）。有了 CONCERN-13 的無限退避，寫入端也能承受快速失敗（QA 已用 1 秒 timeout 實證過不會丟資料）。
  - 備註：Kafka Streams 如果遇到超過 `task.timeout.ms`（預設 5 分鐘）的長時間中斷，StreamThread 可能會進入 ERROR；目前 readiness 會正確變成 DOWN，但沒有機制自動恢復（liveness 仍然是 UP，K8s 不會重啟）。建議設定 `StreamsUncaughtExceptionHandler` 使用 `REPLACE_THREAD`。QA 這次只測了 40 秒的中斷，沒有測到這種情況。

### 2026-09-30 12:10 — Task 18（後端全鏈路整合測試）@ `68a431f`
- **Reviewed**: `git diff 1091f92..68a431f`（EndToEndPipelineTest、test profile 的 Streams 隔離）；QA worktree `clean verify` + **不同測試順序**
- **Verdict**: **FAIL**（測試套件相依於執行順序，已穩定重現）
- **Evidence**:
  - 預設順序 `./mvnw -B clean verify` → `Tests run: 151, Failures: 0`（EndToEndPipelineTest 23s、CandlePipelineTest 33s）；`-Dsurefire.runOrder=reversealphabetical` → 通過；`EndToEndPipelineTest,CandlePipelineTest` 單獨重跑 3/3 通過。
  - **`-Dsurefire.runOrder=random` → `Tests run: 151, Failures: 0, Errors: 2`**：
    ```
    AlertEvaluationTest.firesWithCooldownStoresUnreadEventsAndPublishesThem  <<< ERROR (62.6s)
      Expecting actual: [9000002.00000000, 9000001.00000000]
      to contain exactly in any order: [9000001, 9000003]
    EndToEndPipelineTest.ticksFlowToDatabaseCandlesAlertsAndBrowserAndFailoverIsPushed  <<< ERROR (121.2s)
      expected: 2  but was: 16   (EndToEndPipelineTest.java:135，alert_event 的數量)
    ```
  - **最小重現（3/3 失敗）**：`./mvnw -o test -Dtest='AppPropertiesTest*,AlertEvaluationTest' -Dsurefire.runOrder=reversealphabetical` → 每次都得到 `[9000002, 9000001]`。
  - **根因**：`AlertEvaluator` 的 `@KafkaListener(groupId = "alert-evaluator")`（AlertEvaluator.java:49）是寫死的 group id。Spring 的測試 context cache 讓同一個 JVM 裡同時存在多個 app context，**每個 context 都有一個 AlertEvaluator 加入同一個 consumer group**。`AppPropertiesTest$EnvironmentOverrides` 的 context 設定了 `APP_ALERT_COOLDOWN=30s`（AppPropertiesTest.java:61）。當 `btc.price.ticks` 唯一使用的 partition 被分配給那個 context，就會以 30 秒冷卻期判斷 → T+4:59.999 的 tick（9000002）觸發；EndToEnd 的 12 分鐘資料則變成每 30 秒觸發一次 → 16 筆。`tick-persister` / `candle-persister` 也是寫死的 group，但它們的行為和設定無關，所以目前沒有出現症狀。
  - **為什麼重要**：surefire 預設的 `runOrder=filesystem` 在 Linux（之後的 GitHub Actions / 任何 CI）和 macOS 上的順序不同；這個套件在 CI 上有可能一開始就是紅的（AC13）。另外 task 18 的訊息說隔離問題「已修正、反向順序也通過」，但實際上還有另一個隔離漏洞。
- **修正建議（擇一或併用）**：
  1. group id 加上可設定的前綴：`groupId = "${app.kafka.group-prefix:}alert-evaluator"`（persister 也一樣），test profile 設定 `app.kafka.group-prefix: ${random.uuid}-`，每個 context 各自獨立（和 Streams application-id 的做法一致）；正式環境前綴為空，行為不變。
  2. 不需要警示的測試 context（例如 AppPropertiesTest）關閉 `app.alerts.enabled` / `app.persist.enabled`。這只能治標，建議至少做到 1。
  - 完成條件：`-Dsurefire.runOrder=random` 連跑 3 次全部通過，加上上面的最小重現指令通過。QA 會用同樣的指令驗收。

### 2026-09-30 12:20 — CONCERN-19/20 + Streams REPLACE_THREAD 修正 @ `6b9cbf2`
- **Verdict**: **PASS**（針對這個 commit 的修改內容）；**CONCERN-19、20 關閉**。注意：這個 commit 仍然包含 task 18 FAIL 的順序相依問題（兩者沒有關係）。
- **Evidence**:
  - `./mvnw -B clean verify`（預設順序）→ `Tests run: 152, Failures: 0`。
  - **Live CONCERN-19**：jar + Postgres/Kafka，`docker stop` Postgres → readiness `503`，**5.041s / 5.015s** 就回應（原本 30 秒）；`docker start` 之後回到 `200`。
  - **Live CONCERN-20**：啟動後約 15 秒內 `btc.feed.status` end offset = **4**（約每 5 秒一筆，原本約每秒一筆）。
  - `CandleStreamsConfig` 用 `static` 的 `StreamsBuilderFactoryBeanConfigurer` 設定 `REPLACE_THREAD`，CandlePipelineTest 斷言 Boot 接線的 factory 確實使用它。

### 2026-09-30 12:35 — Task 19（多架構後端 Dockerfile）@ `adfd8b2`
- **Reviewed**: `git show adfd8b2`（Dockerfile、.dockerignore、maven-wrapper.properties；刪除 Dockerfile.amd64 / .silicon / start|stop-silicon.sh）；QA 在 worktree 實際建置
- **Verdict**: **PASS**（AC14 ✔）
- **Evidence**:
  - `git ls-files | grep -E "Dockerfile|silicon"` → 只剩 `Dockerfile`。
  - 第一次 `docker buildx build --platform linux/amd64,linux/arm64` 只花 3.3 秒（用到了 BuildKit 快取，不能當作證據）→ QA 另外跑 **`--no-cache`**：`exit=0`，**3:56.77**，log 中可以看到 `[linux/arm64 build 6/8] … mvnw -B dependency:go-offline`、`[8/8] … package`；`docker image ls --tree` → `linux/amd64`（231MB）、`linux/arm64`（229MB，content size）。
  - `docker run --platform linux/amd64|arm64 --entrypoint sh … -c 'id -u; uname -m; java -version'` → `1001 / x86_64 / openjdk 21.0.12.1` 與 `1001 / aarch64 / openjdk 21.0.12.1`；`/app` 內是 Spring Boot layered 的 `BOOT-INF META-INF org`。
  - 設計：build stage `--platform=$BUILDPLATFORM`（不經 QEMU 編譯）、`~/.m2` cache mount、runtime 用 noble（glibc，RocksDB 需要）、非 root、`-XX:MaxRAMPercentage=75`、`APP_STREAMS_STATE_DIR` 指向 volume 路徑。沒有寫死秘密。
- **Findings（不阻擋）**:
  - 工程師發現 mvnw 在沒有 `unzip` 的環境會改下載 `.tar.gz`，導致固定的 `.zip` SHA-256 驗證失敗：這是 task 1 CONCERN-2 修正的副作用，已在 Dockerfile 安裝 unzip 並加上註解。QA 驗證環境（macOS）有 unzip，不受影響；README 的先決條件請寫上 unzip。
  - 目前的 `docker-compose.yml` 仍然引用已刪除的檔案（task 26 會重寫），這段期間 compose 是壞的，屬於已知狀況。

### 2026-09-30 12:50 — Task 20（前端骨架）@ `8e8e1be`、Task 21（即時價格與連線狀態）@ `e7df6d1`、Task 22（多幣別換算表）@ `c725638`
- **Reviewed**: 三個 commit 的 frontend 變更；在 QA worktree 的**每個 commit** 各自執行 `npm ci && vitest run && npm run build && npm run lint`（Node v26.4.0、npm 11.17.0）
- **Verdict**: 三個都 **PASS**
- **Evidence**:
  - `8e8e1be`：`Tests 6 passed (6)`、build-ok、lint-ok；`git ls-files | grep -cE '(^|/)(node_modules|dist|target)/'` → **0**（AC15 前端部分 ✔）；build 後 `git status` 沒有未追蹤的檔案（.gitignore 生效）。
  - `e7df6d1`：`Tests 13 passed (13)`、build-ok、lint-ok。程式碼：`price` / `status` / `alert` 三種事件都會呼叫 `heard()` 重設 15 秒計時（LiveStreamProvider.tsx:41-60）→ **team lead 要求的「15 秒計時要把 status 事件算進去」✔**；`computeDisplay` 取三個訊號中最差的（連線錯誤 → disconnected，15 秒沒事件 → delayed，server STALE/DISCONNECTED）。
  - `c725638`：`Tests 18 passed (18)`、build-ok、lint-ok；ConvertedPrices.tsx:83 `<a href="https://www.exchangerate-api.com" …>Rates By Exchange Rate API</a>`（S2 ✔）。工程師主動修正了 task 14 手打算錯的契約樣本價格（EUR/TWD 現在等於 usdPrice × rate），很好。
  - `npm ci` 的 `allow-scripts` 警告（msw postinstall 被 npm 11 預設封鎖）不影響測試（MSW 在 Node 模式下不需要 service worker 腳本）。
- **Findings（不阻擋）**:
  - **CONCERN-21（小）**：`computeDisplay` 在 `lastEventAt === null` 時永遠回傳 `connecting`，**沒有逾時**。如果 SSE 連上了但從來沒有收到任何事件，畫面會一直停在「連線中」，同時顯示 `/api/prices/latest` 從 DB 拿到的（可能很舊的）價格。實務上後端在新連線建立時會補送 status（FeedStatusTracker 超過 15 秒會回報 DISCONNECTED），所以只有「全新系統、status topic 還是空的」才會遇到。建議：連線建立後 15 秒仍然沒有事件也視為 `delayed`。真實瀏覽器驗收時（task 26/28）QA 會特別看「後端重啟、交易所被擋」時畫面的狀態。

### 2026-09-30 12:50 — Task 23（K 線與走勢圖）@ `2bbd673`
- **Verdict**: **FAIL**（偶發失敗的測試，根因是程式中取了兩次「現在時間」）
- **Evidence**:
  - 第一次 `npx vitest run` → `Tests 1 failed | 27 passed (28)`：
    ```
    PriceCharts.test … shows the trend from /api/prices/trend and extends it with live prices
    expect(Date.parse(to) - Date.parse(from)).toBe(60 * 60_000)   ← 失敗
    ```
    接著重跑 5 次 → 5/5 通過 → **偶發失敗（6 次中 1 次）**。
  - 根因 `frontend/src/chart/PriceCharts.tsx:51`：
    `api.trend(new Date(Date.now() - TREND_MINUTES * 60_000).toISOString(), new Date().toISOString(), TREND_POINTS)` —— `from` 和 `to` 分別取了兩次時鐘，跨過毫秒邊界時兩者相差 60 分鐘 + 1ms。產品上的影響很小（多 1ms），但這讓 `npm test` 在 CI 上會隨機變紅（AC13）。
  - 其餘：build-ok、lint-ok；契約樣本與 series 轉換測試都有。
- **修正**：`const now = Date.now()` 只取一次，再算出 `from = now - 60min`、`to = now`。完成條件：`npx vitest run` 連跑 10 次都通過（QA 會用 `for i in $(seq 10)` 驗證）。

### 2026-09-30 13:45 — Task 18 修正覆審 @ `a134a40`
- **Verdict**: **PASS**（task 18 的 FAIL 關閉）
- **Evidence**:
  - diff：`tick-persister` / `candle-persister` / `alert-evaluator` 的 group 都改成 `${app.kafka.group-prefix}<name>`，`application.yml` 的 `group-prefix: ${APP_KAFKA_GROUP_PREFIX:}`（正式環境為空 → group 名稱和原本一樣）；test profile 設定 `group-prefix: test-${random.uuid}-`、`app.alerts.enabled` 預設 false、hikari pool 為 4。工程師指出「只加前綴還不夠，因為 evaluator 共用同一個 DB」，這個判斷正確。
  - QA 的最小重現：`-Dtest='AppPropertiesTest*,AlertEvaluationTest' -Dsurefire.runOrder=reversealphabetical` → **PASS**（修正前 3/3 FAIL）。
  - `./mvnw -o verify -Dsurefire.runOrder=random` × 3 → **152/152 × 3**（seed 3083793679586666、3083915343095125、3084034322751916）。
  - 另外用工程師提到「修 Postgres 連線問題之前唯一失敗」的 seed `3082923979307916` 重跑 3 次：2 次 PASS、**1 次 ERROR → `SseStreamTest.disconnectedClientIsRemoved`**（和 task 18 的根因無關，見下方 task 16 重新開啟）；log 中 `too many clients` 出現 0 次（連線池問題已解決）。

### 2026-09-30 13:45 — Task 23 修正覆審 @ `a673cd4`（含 CONCERN-21）
- **Verdict**: **PASS**（task 23 的 FAIL 關閉；**CONCERN-21 關閉**）
- **Evidence**: 在 QA 的 scratch 目錄用 `git archive a673cd4 frontend contracts` 匯出（當時 QA worktree 正被 Maven 使用）→ `npm ci` → `npx vitest run` × 10 → **`10  Tests 29 passed (29)`**；build-ok、lint-ok。diff：`const now = Date.now()` 只讀一次；`computeDisplay` 在沒有任何事件時 `now - startedAt >= 15s → delayed`，並有對應的新測試。

### 2026-09-30 13:45 — Task 16 重新開啟：`SseStreamTest` 偶發失敗
- **Verdict**: **FAIL**（偶發失敗的測試；和 task 23 採用同一個標準）
- **Evidence**:
  - `./mvnw -o verify -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=3082923979307916` → `Tests run: 152, Failures: 0, Errors: 1`：
    ```
    SseStreamTest.disconnectedClientIsRemoved <<< ERROR (5.094s)
    ConditionTimeoutException … not fulfilled within 5 seconds
      at SseStreamTest.connect(SseStreamTest.java:78)
    ```
    完整測試共 6 次（random × 3 + 同一個 seed × 3）中失敗 1 次；`SseStreamTest` 單獨跑 10/10 通過 → 只在完整測試的某些時序下才會出現。
  - **根因（讀程式碼）**：`@BeforeEach connect()` 先記下 `connectionsBefore = broadcaster.connectionCount()`，連線後等待 `connectionCount() == connectionsBefore + 1`（SseStreamTest.java:69-78）。前一個測試已關閉的 emitter 要等到**下一次寫入失敗**才會被移除（SseBroadcaster 的 send/heartbeat）。如果這次移除剛好發生在「記錄 before」和「新連線加入」之間，數量就是 `before - 1 + 1 = before`，永遠不會等於 `before + 1` → 5 秒逾時。其他測試類別（例如 EndToEnd / FeedWiring）送出的 status/price 會觸發 broadcast 寫入，正好造成這個時序。這是測試的競爭條件，不是產品的 bug。
- **修正建議**：不要用相對數量判斷。改成等「這條新連線自己收到的第一個事件」（例如先送一筆唯一的 FeedStatus，等 reader 收到），或者在 `@AfterEach` 就同步移除已關閉的連線（例如送一個 keepalive 讓它寫入失敗，再等 `connectionCount()` 下降）。完成條件：`SseStreamTest` 單獨 10 次通過 + 完整測試 `runOrder=random` 3 次通過（包含 seed 3082923979307916）。

### 2026-09-30 14:40 — Task 16 修正覆審（SSE 測試競爭）@ `71a5a79`
- **Verdict**: **PASS**（task 16 的 FAIL 關閉）
- **Evidence**:
  - diff：`SseStreamTest.connect()` 和 `EndToEndPipelineTest.openBrowserStream()` 拿掉相對數量的等待，改成以收到 `200` 為準（`StreamController` 在回傳前就已經註冊 emitter，Spring 要等回傳後才會送出狀態碼和 header → 收到 response 就表示連線已經註冊）。這個推論正確。`disconnectedClientIsRemoved` 仍然斷言 `connectionCount() == 0`（持續送 status 讓寫入失敗），測試的檢查力道沒有變弱。
  - `SseStreamTest` 單獨 **10/10**；`./mvnw -o verify -Dsurefire.runOrder=random` × 4 → **152/152 × 4**（seed 3082923979307916 × 2、3086822858011666、3086942526431166）。
  - 後端測試套件累計：random order 完整執行 7 次（a134a40 × 3、71a5a79 × 4）中，修正後 4/4 全部通過。

### 2026-09-30 15:00 — Task 24（幣別管理介面）@ `8ca5480`、Task 25（警示管理與通知）@ `1c5a4aa`
- **Verdict**: 兩個都 **PASS**
- **Evidence**: 在 QA worktree 各自的 commit 執行 `npm ci`，並連跑 `vitest run` **5 次** → `8ca5480: 5 × Tests 36 passed (36)`、`1c5a4aa: 5 × Tests 46 passed (46)`；兩者都 build-ok、lint-ok。`grep window.confirm` → 沒有使用（兩段式確認，瀏覽器自動化不會被卡住）；AlertToasts.tsx:31-48 依 `document.visibilityState` 和 `visibilitychange` 實作 S4 的已讀規則。
- 限制：畫面上的互動（toast 實際外觀、F5 後資料還在）需要真實瀏覽器，QA 這個 session 沒有瀏覽器自動化工具 → 列入 task 28 請使用者目視確認（見 AC 總表）。

### 2026-09-30 15:00 — Task 26（前端容器與完整 docker compose）@ `22e6908`（在 `44b5539` 上實跑，兩者之間 compose 只多了 APP_* 的傳遞設定）
- **環境隔離**：工程師的 `currency` 堆疊仍在執行，QA 用 `docker compose -p currency-qa`、`FRONTEND_PORT=3002 BACKEND_PORT=8082`，並在 QA scratch 目錄加一個 override，把 image 改名為 `currency-{backend,frontend}:qa`，避免覆蓋工程師的 `:local` image。
- **Verdict**: **PASS**
- **Evidence**:
  - `up -d --build --wait` → exit 0，4 個服務都 **healthy**（kafka / postgres / backend / frontend）；3 個具名 volume（`kafka-data`、`postgres-data`、`streams-state`）；backend `id -u` = 1001，`APP_STREAMS_STATE_DIR=/var/lib/currency/streams`。
  - **AC1 smoke**：`curl -sN --max-time 10 localhost:3002/api/stream | grep -c '^event:price'` → **15**（經過 nginx 代理，全部是真實資料）；`/api/prices/latest` → coinbase / LIVE。
  - nginx：`<html lang="zh-Hant-TW">`、`<title>比特幣即時價格</title>`；深層連結 fallback 為 200 + `no-cache`；`/assets/*.js` 為 `max-age=31536000, immutable`；`/healthz` → `ok`；SSE 的 `ttfb=0.002s`（沒有被 buffer）。
  - **網路隔離（T6 / M2）**：`busybox nc` 在 `currency-qa_internal` 上 **BLOCKED**，在 `_egress` 上 reachable；kafka 只在 `internal`，`/dev/tcp/1.1.1.1/443` 被擋；backend 在 `internal + egress`，可以連到 `kafka:9092` 和 `open.er-api.com:443`；frontend 在 `internal + public`。
  - 觀察：在 Linux 容器內，`FeedStatus.lastTickAt` 帶有**奈秒**（`…05:22:50.000714718Z`）→ 證實 CONCERN-15 的前提（Linux 的 `Clock` 有奈秒精度）；PriceTick 已經在源頭截斷，這個欄位只用於顯示，不影響功能。

### 2026-09-30 15:40 — Task 27（README 與驗收步驟）@ `44b5539`
- **Verdict**: **PASS**
- **方式**：QA **只照 README 第 6 節**，在自己的隔離堆疊（`-p currency-qa`、3002/8082）上逐條執行 AC1–AC15，**沒有向工程師詢問任何步驟**。所有指令都可以照做並得到 README 描述的結果（見上方 AC 總表與下方 task 28 條目）。`docs/acceptance/ac4-ohlc-check.sql` 可以直接使用。
- **Findings（不阻擋，文件小修）**:
  - **CONCERN-22**：第 6 節的指令寫死了 `currency_egress`、`currency-backend-1`（依賴 compose 的 `name: currency`）；AC6 / AC12 寫死 `localhost:8080`。一般使用者照預設操作不會有問題；但如果用 `-p` 另外起一個堆疊，或是改了 `BACKEND_PORT`，照抄就會打到**另一個堆疊**（QA 平行測試時就遇到這個狀況）。建議在第 6 節開頭加一句「若使用 `-p <name>` 或改了 port，請把 `currency_`/`currency-` 前綴與 8080 換掉」，並提醒 `APP_*` 覆寫重建 backend 時也要帶上 `BACKEND_PORT`，否則會回到預設的 8080。
  - 小：`POST /api/currencies` 回傳的 `createdAt` 帶有奈秒（`…07.249344129Z`），之後 `GET` 只會有微秒（DB 精度）→ 同一筆資料前後兩次讀到的值不同（CONCERN-6 的延伸，影響很小）。

### 2026-09-30 15:40 — Task 28（完整驗收）— QA 獨立 AC 走查
- **環境**：QA 自己的 compose 堆疊（`44b5539`，`-p currency-qa`、前端 3002、後端 8082、image `:qa`），真實 Coinbase / Kraken / open.er-api；與工程師的 `currency` 堆疊並行執行，互不干擾（最後確認工程師的 4 個容器全程 healthy）。
- **結果**：見檔案開頭的 **AC 總表**。**15 條 AC 在後端 / API / SSE / 資料層全部成立**；工程師在 `c8400fd` 的 progress 證據與 QA 的獨立結果一致（例如 AC7 300.7s vs QA 300.363s、AC4 14/14 vs QA 14/14）。
- **QA 自己的操作錯誤紀錄**（誠實記錄）：第一次 AC12 使用 `P="docker compose -p currency-qa"; $P stop kafka`，zsh 不會對變數做 word-split → 指令根本沒有執行（exit 127），readiness 當然維持 200；改用 shell 函式重跑後得到正確結果（立即 503 / 2s 回 200）。另外有一次 psql 引號寫錯導致查詢失敗，改用 API 補查。
- **尚待使用者完成（QA 這個 session 沒有瀏覽器自動化工具，無法代為確認）**：
  1. 畫面目視：AC1 價格跳動與「即時」狀態、K 線 / 走勢圖實際畫面、AC2 斷線時價格變淡與警告文字、AC6「目前來源 Kraken」、AC7 toast、AC8 未讀清單、AC11 幣別 CRUD + F5。
  2. AC13 實際斷網：依 README 5.1 準備好後關閉 Wi-Fi，執行 `./mvnw -o clean verify` 與 `(cd frontend && npm test)`。
- **Verdict（task 28）**：**CONCERN** —— 自動可驗證的部分全部 PASS；目視和實際斷網這兩項由使用者確認後即可結案。
- **所有 task 的 QA 狀態**：1–27 全部 PASS（18、23 曾經 FAIL → 已修正後 PASS；16 曾經重新開啟 FAIL → 已修正後 PASS）；開放中的 CONCERN：22（README 文件）。

### 2026-09-30 15:55 — CONCERN-22 修正 @ `f3a6460`
- **Verdict**: **PASS**；**CONCERN-22 關閉**
- **Evidence**: `git show f3a6460 -- README.md`：第 6 節開頭新增提醒，涵蓋 (1) 3000/8080 → FRONTEND_PORT/BACKEND_PORT；(2) `-p <名稱>` 時網路和容器的前綴會變成 `<名稱>_egress`、`<名稱>-backend-1`；(3) 用 `APP_*` 重建 backend 時要一起帶上 port 變數（附範例）。這三點正是 QA 平行測試時實際遇到的問題。只修改文件，不需要重跑測試。
- **目前狀態**：沒有開放中的 CONCERN。task 28 等使用者完成 (1) 畫面目視確認（localhost:3002 或 3001）和 (2) AC13 實際斷網執行後，再由 QA 做最後確認。

### 2026-09-30 16:10 — Task 28 目視部分（證據來源：**team lead 目視**，`personal-workplace-7a` 用 Chrome 在工程師的堆疊 localhost:3001 操作）
- 由 team lead 回報、QA 記錄（QA 沒有親自目視，以下列為第二手證據）：價格即時跳動並顯示「即時」、目前來源 Coinbase；換算表與 attribution 連結；1m / 5m K 線、走勢圖都有畫出來；幣別 CRUD（包含 409 錯誤訊息，F5 後資料仍在）→ AC1 畫面、AC11 畫面 ✅；警示觸發後未讀清單有顯示且 F5 後仍在 → AC8 畫面 ✅。AC 總表已更新。
- **仍待確認**：
  - (a) AC7 toast：自動化分頁的 `visibilityState=hidden`，依 S4 的設計不會顯示 toast（這是符合設計的行為），需要使用者在自己的前景分頁確認。
  - (b) AC2 斷線時價格變淡 / 警告文字、AC6 前端顯示「目前來源 Kraken」：需要 egress disconnect / chaos 操作，還沒有人目視過（後端與 SSE 的部分 QA 已實測）。
  - (c) AC13 實際斷網執行：由使用者操作。
- **新發現的 bug（team lead 發現，已交給工程師）**：刪除警示後，前端的未讀清單沒有即時更新（後端 `ON DELETE CASCADE` 正確，F5 後就會消失）。QA 會驗收修正，驗收方式：前端測試「刪除警示 → 未讀清單不再顯示該警示的事件」，並確認 `alert-events-changed` 事件有從 AlertManager 的刪除動作發出。

### 2026-09-30 16:25 — 前端 bug 修正（刪除警示後未讀清單沒更新）@ `daa7ea1`
- **Verdict**: **PASS**
- **Evidence**:
  - diff：`AlertManager` 刪除成功後呼叫 `notifyAlertEventsChanged()`；新增整合測試（有狀態的 MSW 假後端，刪除 alert 3 會 cascade 刪除事件 17）。
  - `npx vitest run` × 5 → `5 × Tests 47 passed (47)`；build-ok、lint-ok。
  - **反向驗證（QA 親自做）**：把修正那一行註解掉 → 新測試失敗（`× deleting an alert that has unread events removes them from the unread list`，`1 failed | 10 passed`）；還原之後 47/47。→ 這個測試確實能抓到這個 bug。
  - QA 的 `currency-qa` 堆疊已重建 frontend（localhost:3002 使用修正後的版本），方便使用者目視確認。
- **說法不一致（記錄）**：工程師的訊息說 team lead 目視「AC6 OK」，但 team lead 給 QA 的訊息明確寫 AC6 前端顯示 Kraken「沒有做」。QA 在總表中維持 **AC6 畫面：待確認**，直到有人實際在封鎖主來源時看過畫面。

### 2026-09-30 16:35 — AC6 目視說法更正（已對齊）
- 工程師確認：team lead 目視的是「畫面有顯示目前來源 Coinbase」，並沒有看過封鎖主來源時畫面切換成 Kraken；AC2 的變淡 + 警告也還沒目視。progress.md 已加上更正紀錄。雙方看法一致，不需要交給 team lead 裁決。
- **task 28 最後確認前的待辦（由使用者執行）**：(1) AC7 toast（在前景分頁）；(2) AC2 變淡 / 警告、AC6 顯示 Kraken（在 localhost:3002 用 egress disconnect / chaos 操作）；(3) AC13 實際斷網。
