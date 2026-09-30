# 開發與測試

[← 回到 README](../README.md) · [設定](configuration.md) · [API](api.md) · [測試](testing.md) · [驗收步驟](acceptance.md)


```bash
./mvnw clean verify                   # 後端全部測試（需 Docker；Testcontainers 會啟動 Kafka 與 PostgreSQL）
cd frontend && npm ci && npm test     # 前端測試（Vitest）
cd frontend && npm run dev            # 前端開發伺服器 :5173，/api 代理到 localhost:8080
```

- 測試**不會**連到任何真實的價格或匯率來源：`test` profile 把所有外部網址指向 `127.0.0.1:1`，並有守門測試（`NoExternalCallsGuardTest`）檢查。
- 修改 API 回應格式後，更新契約樣本：`./mvnw test -Dtest=ContractSamplesTest -Dcontracts.update=true`。

## 離線跑測試（AC13）

有網路時先準備一次：

```bash
./mvnw clean verify                                # 下載所有 Maven 依賴與外掛（dependency:go-offline 抓不全測試外掛）
docker pull postgres:17.11-alpine
docker pull apache/kafka:3.9.2
docker pull testcontainers/ryuk:0.12.0
(cd frontend && npm ci)
```

之後可以**完全斷網**執行：

```bash
./mvnw -o clean verify
(cd frontend && npm test)
```

## 建置多架構 image（AC14）

```bash
docker buildx build --platform linux/amd64,linux/arm64 -t currency-backend:multiarch .
docker image ls --tree currency-backend:multiarch            # 應同時有 linux/amd64 與 linux/arm64
```

若出現 `Multi-platform build is not supported for the docker driver`，先執行 `docker buildx create --use --driver docker-container`（或在 Docker Desktop 開啟「Use containerd for pulling and storing images」）。前端 image：`docker build -f frontend/Dockerfile .`（需在 repo 根目錄執行）。

## 重新產生 README 截圖

`scripts/screenshot.mjs` 用本機的 Google Chrome（headless，暫時的 profile）透過 Chrome DevTools Protocol 開啟 http://localhost:3001，等 7 秒讓即時價格與圖表載入後截圖，不需要安裝任何套件（Node 18+）。先用 `FRONTEND_PORT=3001 docker compose up -d --wait` 啟動系統，再執行：

```bash
node scripts/screenshot.mjs docs/images/dashboard-dark.png   1440 1000 1 dark
node scripts/screenshot.mjs docs/images/dashboard-light.png  1440 1000 1 light
node scripts/screenshot.mjs docs/images/dashboard-mobile.png 375  812  2 dark mobile
```

參數依序為：輸出檔、寬、高、device pixel ratio、`dark` / `light`（`prefers-color-scheme`），最後的 `mobile` 代表模擬手機。其他 port 或非 macOS 的 Chrome 用環境變數指定，例如 `SCREENSHOT_URL=http://localhost:3000/ CHROME_PATH=/usr/bin/google-chrome node scripts/screenshot.mjs ...`。截完後跑 `python3 scripts/check_md_links.py` 確認 README 的圖片路徑都有效。

