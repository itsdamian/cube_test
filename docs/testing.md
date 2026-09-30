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
