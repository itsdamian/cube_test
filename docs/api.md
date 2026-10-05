# API

[← 回到 README](../README.md) · [設定](configuration.md) · [API](api.md) · [測試](testing.md) · [Kubernetes](kubernetes.md) · [CI/CD](cicd.md) · [驗收步驟](acceptance.md)


| 方法 | 路徑 | 說明 |
|---|---|---|
| GET | `/api/prices/latest` | 最新價格 + 來源狀態 |
| GET | `/api/prices/converted` | 各幣別換算價、匯率、匯率更新時間 |
| GET | `/api/prices/history?from&to&limit&cursor` | 逐筆歷史（keyset 分頁，`limit` ≤ 5000） |
| GET | `/api/prices/trend?from&to&points` | 走勢（伺服器端降採樣，`points` ≤ 1000） |
| GET | `/api/candles?interval=1m\|5m&from&to` | K 線 |
| GET | `/api/stream` | SSE：`price`、`status`、`alert` |
| GET/POST/PUT/DELETE | `/api/currencies[/{id}]` | 幣別 |
| GET/POST/DELETE | `/api/alerts[/{id}]` | 價格警示 |
| GET | `/api/alert-events?unread=true` | 警示觸發紀錄 |
| POST | `/api/alert-events/{id}/read`、`/api/alert-events/read-all` | 標記已讀 |

時間一律為 ISO-8601（UTC），例如 `2026-09-30T08:00:00Z`；錯誤回應為 RFC 9457 ProblemDetail。回應範例見 [`contracts/api-samples/`](../contracts/api-samples/)（由後端測試保證與實際輸出一致，前端測試直接使用）。
