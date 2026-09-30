# 驗收步驟（Acceptance Criteria）

[← 回到 README](../README.md) · [設定](configuration.md) · [API](api.md) · [測試](testing.md) · [驗收步驟](acceptance.md)

對應 [`specs/realtime-btc-kafka-react/spec.md`](../specs/realtime-btc-kafka-react/spec.md) 的 AC1–AC15。環境變數見 [configuration.md](configuration.md)。


以下假設已執行 `docker compose up -d --build --wait`，網頁在 http://localhost:3000，後端在 http://localhost:8080。

> **如果你改過 port 或專案名稱**，請先換掉下面指令中的對應部分，否則指令會打到別的堆疊：
> - 網頁／後端 port：`3000` → 你的 `FRONTEND_PORT`，`8080` → 你的 `BACKEND_PORT`。
> - 網路與容器名稱以專案名稱開頭：預設是 `currency_egress`、`currency-backend-1`；用 `docker compose -p <名稱>` 時會變成 `<名稱>_egress`、`<名稱>-backend-1`（用 `docker compose ps` 與 `docker network ls` 確認）。
> - 用 `APP_*=... docker compose up -d backend` 重建 backend 時，也要一起帶上 `FRONTEND_PORT` / `BACKEND_PORT`（例如 `BACKEND_PORT=8081 APP_ALERT_COOLDOWN=30s docker compose up -d backend`），否則會回到預設 port。

**AC1　不需 API key，30 秒內看到跳動價格**
1. 開 http://localhost:3000。30 秒內「即時價格」出現數字、狀態為「即時」、目前來源 Coinbase，價格會持續變化。
2. 命令列確認：`curl -sN --max-time 10 localhost:3000/api/stream | grep -c '^event:price'`（應 > 0）。

**AC2　斷線 30 秒內顯示，恢復自動更新**
1. 只切斷 backend 的對外網路（Kafka、DB、瀏覽器連線不受影響）：`docker network disconnect currency_egress currency-backend-1`
2. 30 秒內網頁狀態變成「已斷線」或「資料延遲」，價格變淡並出現警告文字。
3. 接回：`docker network connect currency_egress currency-backend-1`。不重啟任何服務，約 10–40 秒後恢復「即時」、價格繼續跳動。

**AC3　換算誤差 < 0.5%，顯示匯率更新時間**
1. 網頁「多幣別換算」每列都有「匯率更新時間」，下方有 “Rates By Exchange Rate API” 連結。
2. 比對：
   ```bash
   curl -s localhost:3000/api/prices/converted | python3 -c "import json,sys;d=json.load(sys.stdin);[print(i['code'],i['price']/d['usdPrice'],i['rateUpdatedAt']) for i in d['items'] if i['rate']]"
   curl -s https://open.er-api.com/v6/latest/USD | python3 -c "import json,sys;r=json.load(sys.stdin)['rates'];print({k:r[k] for k in ['EUR','GBP','JPY','TWD','USD']})"
   ```
   同一幣別的兩個數字差距應遠小於 0.5%（匯率來源每天更新一次；若剛好跨過更新時間，等下一次 30 分鐘刷新）。

**AC4　10 分鐘後有歷史、≥10 根 1m、≥2 根 5m，OHLC 與逐筆一致**
1. 系統啟動後等 10 分鐘以上（最早與最晚那根可能是不完整的時段，屬正常）。
2. 數量：
   ```bash
   docker compose exec -T postgres psql -U currency -d currency -c \
     "SELECT interval_code, count(*) FROM candle WHERE open_time >= now() - interval '15 minutes' GROUP BY 1;"
   curl -s "localhost:3000/api/prices/history?limit=5" | python3 -m json.tool | head -20
   ```
3. OHLC 一致性（每一列的 `ohlc_matches` 應為 `t`）：
   `docker compose exec -T postgres psql -U currency -d currency < docs/acceptance/ac4-ohlc-check.sql`
   規則：open／close 依 `(event_time, received_at, event_id)` 排序，與 Kafka Streams 相同。`tick_count` 不比對：若交易所重送同一筆成交，Streams 會多算、資料庫會去重，但 OHLC 不受影響。

**AC5　重啟後資料仍在**
1. 記下 K 線數量（AC4 第 2 步的 SQL）。
2. `docker compose restart backend postgres && docker compose up -d --wait`
3. 再查一次：數量只增不減，網頁圖表仍顯示先前的 K 線。

**AC6　主來源被封鎖 30 秒內切換到備援，恢復後切回**
- 方法 A（執行中封鎖／解封，驗證「不重啟自動切回」）：
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d --wait backend
  curl -s -X POST -H 'Content-Type: application/json' localhost:8080/actuator/feeds/coinbase/block
  # 30 秒內網頁「目前來源」變成 Kraken（實測約 1–11 秒）
  curl -s localhost:8080/actuator/feeds | python3 -m json.tool
  curl -s -X POST -H 'Content-Type: application/json' localhost:8080/actuator/feeds/coinbase/unblock
  # 主來源連續健康 15 秒後切回 Coinbase（約 15–45 秒）
  ```
  也可用 `.../coinbase/block?mode=silent` 模擬「連線還在但收不到訊息」。**注意**：這支端點一定要帶 `Content-Type: application/json`，否則回 415。驗收完用 `docker compose up -d --wait backend` 回到一般設定。
- 方法 B（主來源網址錯誤）：`APP_FEED_PRIMARY_URL=wss://invalid.example docker compose up -d --wait backend`，網頁目前來源應為 Kraken；還原：`docker compose up -d --force-recreate --wait backend`。

**AC7　警示通知與 5 分鐘冷卻**
1. 在網頁「價格警示」新增一個門檻：例如目前價格 83,200 時，新增「價格高於 83,000」。
2. 下一筆成交後，右下角跳出「價格警示」通知。
3. 5 分鐘內不會再通知同一個警示；5 分鐘後若價格仍高於門檻，會再通知一次（「上次觸發」時間會更新）。
4. 快速觀察可用 `APP_ALERT_COOLDOWN=30s docker compose up -d --wait backend`，但請至少用預設 5 分鐘實跑一次。驗收後刪除警示，以免持續觸發。

**AC8　頁面關閉時觸發的警示，重新打開顯示為未讀**
1. 新增一個會很快觸發的警示（同 AC7），然後**關閉網頁分頁**。
2. 等待觸發：`curl -s "localhost:8080/api/alert-events?unread=true"` 出現新紀錄即可。
3. 重新打開 http://localhost:3000，「未讀警示」列出這筆，可逐筆或全部標記已讀。

**AC9　逐筆價格超過保留期自動清除，K 線不受影響**
1. 縮短保留期重啟 backend：
   `APP_RETENTION_TICKS=PT5M APP_RETENTION_INTERVAL=PT30S APP_RETENTION_INITIAL_DELAY=PT30S docker compose up -d --wait backend`
2. 10 分鐘後：
   ```bash
   docker compose exec -T postgres psql -U currency -d currency -c \
     "SELECT now() - min(event_time) AS oldest_tick_age, (SELECT count(*) FROM candle) AS candles FROM price_tick;"
   ```
   `oldest_tick_age` 約 5 分鐘多一點（不超過 5 分 30 秒左右），`candles` 持續增加、沒有被刪。
3. 還原：`docker compose up -d --force-recreate --wait backend`

**AC10　全新資料庫已有 5 個預設幣別**
```bash
docker compose down -v && docker compose up -d --wait
curl -s localhost:3000/api/currencies | python3 -c "import json,sys;print([(c['code'],c['name']) for c in json.load(sys.stdin)])"
```
應為 EUR 歐元、GBP 英鎊、JPY 日圓、TWD 新台幣、USD 美元（`down -v` 會清掉所有資料）。

**AC11　網頁完成幣別新增、修改、刪除，重新整理後仍在**
在「幣別管理」新增（例如 CHF／瑞士法郎）、編輯名稱、刪除（按「刪除」後再按「確定刪除？」），每一步後按 F5，結果都保留；換算表也會跟著更新。

**AC12　停掉 Kafka 後 readiness 失敗，恢復後自動恢復**
```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health/readiness   # 200
docker compose stop kafka
curl -s localhost:8080/actuator/health/readiness                                     # 503，kafka DOWN
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health/liveness     # 仍是 200
docker compose start kafka
# 約 5–30 秒後 readiness 回到 200
```

**AC13　離線、未預啟 Kafka 下測試全過** — 見 [testing.md〈離線跑測試（AC13）〉](testing.md#離線跑測試ac13)。

**AC14　多架構 image** — 見 [testing.md〈建置多架構 image（AC14）〉](testing.md#建置多架構-imageac14)。

**AC15　repo 沒有建置產物**
```bash
git ls-files | grep -E '(^|/)(target|node_modules|dist)/' || echo "clean"
```
