# 比特幣即時價格 — 畫面重新設計提案

> 範圍：只做設計，不改 `frontend/`。工程師之後依選定的方向實作。
> 日期：2026-09-30 ・ 角色：UI/UX 設計

## 0. 檔案一覽

| 檔案 | 內容 |
|---|---|
| `direction-a.html` | 方向 A「交易終端」靜態 mockup（單檔，無外部請求） |
| `direction-b.html` | 方向 B「沉穩金融」靜態 mockup |
| `direction-{a,b}-desktop-dark.png` / `-desktop-light.png` | 1440×1000 截圖 |
| `direction-{a,b}-mobile-dark.png` | 375×812 @2x 截圖 |
| `direction-{a,b}-desktop-dark-states.png` | 額外：已斷線＋toast＋刪除確認狀態 |
| `direction-{a,b}-mobile-dark-currency.png` | 額外：手機版幣別管理（drawer / bottom sheet） |
| `src/tokens.mjs` | 兩個方向的色彩 token（唯一來源）＋ WCAG 對比度計算（`node src/tokens.mjs`） |
| `src/build.mjs`、`src/*.src.html`、`src/mock.js`、`src/mock.css`、`src/data.js` | mockup 原始碼；`node src/build.mjs` 產生兩個 HTML |
| `src/redesign.src.md`、`src/doc.mjs` | 本文件的原稿；`node src/doc.mjs` 從 `tokens.mjs` 填入 token 與對比度表格後產生 `redesign.md` |
| `screenshot.mjs` | `scripts/screenshot.mjs` 的複本，多了 URL 參數，並會印出水平溢出檢查結果 |

**Mockup 使用方式**：直接用瀏覽器開啟 HTML。左下角紫色虛線框是「Mockup 控制列」（不屬於設計），可切換 主題（自動/深色/淺色）、狀態（即時/延遲/已斷線/載入中）、觸發 toast、開啟幣別管理。價格每 0.65 秒模擬跳動一次，閃爍規則與正式程式相同。也可用 URL 參數：`?theme=light&state=stale&toast=1&dialog=1&confirm=1&bar=0&shot=1`（`shot=1` 停止跳動以便截圖）。刪除確認、編輯、新增、標記已讀、表單驗證（含「代碼已存在」）都可以實際點。

圖表資料是 2026-09-30 從執行中的 app（`/api/candles`、`/api/prices/trend`）抓下來的真實 K 線；圖表本身是模仿 lightweight-charts 的 SVG 示意。

---

## 1. 現況問題（對照 `docs/images/dashboard-*.png`）

1. **整個大價格數字隨每一筆成交變紅/變綠。** 一秒好幾筆，最顯眼的元素一直換顏色，比閃爍本身更吵；而且紅色大數字在深色底上容易被讀成「錯誤」。（dark 截圖是紅色、light 截圖是綠色，只差幾秒。）
2. **價格卡片右半邊是空的。** 780px 寬的卡片只放一個數字和一行 meta，資訊密度低，看起來像還沒做完。
3. **連線狀態和價格分離。** 狀態 pill 在頁首最右邊，離價格很遠；手機版 pill 自己佔一整行（mobile 截圖）。
4. **兩欄底部不齊。** 左欄到 y≈735，右欄到 y≈650，中間留下一塊空白，讓版面顯得鬆散。
5. **圖表細節**：Y 軸 `83600.00` 沒有千分位也多了無意義的 `.00`；TradingView 標誌壓在 K 線區左下角；手機版 Y 軸佔掉約 1/4 寬度。
6. **換算表在手機上被截斷**：表頭「匯率（1 USD =）」被切成「匯率（1 USD :」，表格要在卡片內橫向捲動。
7. **警示卡片層次不清**：「沒有未讀警示」字級比標題還大；表單 placeholder `90000` 看起來像已填的值；「尚未設定任何警示」只是一行灰字。
8. **幣別管理佔滿整個頁面寬度、放在最下方**，每列很高（編輯/刪除上下疊，另案修正中）。一個低頻管理功能成了頁面上面積最大的區塊。
9. **區塊標題**：`text-transform: uppercase` + `letter-spacing: .08em` 對中文沒有作用，只讓中文字距變鬆。所有卡片同一權重，沒有主次。
10. **缺少作品感**：沒有頁尾、沒有資料來源說明、沒有品牌識別，看起來像預設樣板。

兩個方向都會解決以上所有問題；差異在「要呈現成什麼樣的產品」。

---

## 2. 方向 A — 交易終端（Trading Terminal）

![A desktop dark](direction-a-desktop-dark.png)

### 概念
把畫面當成一個「看盤工具」，而不是網頁。整頁在桌機上剛好填滿視窗、不需捲動；面板之間用 1px 細線分隔（像 Bloomberg / TradingView 的工作區），數字一律等寬字體、靠右對齊、字級小但清楚。價格本身保持中性色，方向只由旁邊的小方塊（▲/▼）表示，閃爍也只發生在那個小方塊上。幣別管理收進右側 drawer，警示建立縮成一行。適合想展示「這是一個即時資料系統」的作品集。

### 各斷點版面
- **桌機 ≥1100px**：頂列 48px（品牌、`BTC-USD`、狀態 pill、右側「⚙ 幣別管理」）→ 報價列（大價格＋5 個統計格）→ 主區兩欄：左 `1fr` 圖表（佔滿剩餘高度），右 400px rail（上：換算表；下：警示，內部可捲動）→ 頁尾一行。`.app { height: 100vh; min-height: 680px }`。
- **平板 720–1099px**：取消固定高度，圖表全寬（高 380px），rail 變成換算｜警示兩欄並排。統計格換到價格下方一整列。
- **手機 ≤560px**：頂列只剩 ₿、標題、pill、⚙ 圖示按鈕；統計格改 2 欄格線；OHLC 圖例換行在分頁下方、「區間高/低」隱藏；圖表 280px；換算表中文名稱放到代碼下方、表頭只寫「匯率」；控制項高度 36px；幣別管理 drawer 變成全螢幕。

### 關鍵設計決策
- **價格不變色**：只讓 28×28 方向小方塊著色＋閃爍（`box-shadow` 光環 0.8s），大數字永遠是 `--text`。
- **等寬數字**（`ui-monospace, SF Mono…`）：每秒跳動時數字寬度不變、不會左右抖動。
- **一頁不捲動**：價格、圖表、換算、警示一眼看完，符合「看盤」的使用情境。
- **細線網格取代卡片**：`gap: 1px; background: var(--line)`，資訊密度高但不雜亂。
- **狀態明確**：延遲/斷線時價格改成 `--muted`（仍 ≥4.5:1）、方向方塊換成「資料延遲」/「已斷線」標籤、報價列下方出現整條橫幅（沿用現有文案）。
- **幣別管理 → 右側 drawer**（`<dialog>`），警示建立 → 一行（高於/低於 segmented + 門檻 input + 新增）。

### 優缺點
- 優點：資訊密度高、非常「專業工具」；桌機一頁看完；數字對齊漂亮；和 Kafka 即時串流的主題最契合。
- 缺點：字級偏小（12–14px），對不熟看盤的人較有壓迫感；桌機固定高度需要處理矮螢幕（已設 `min-height: 680px`，低於則整頁捲動）；等寬字在中文環境看起來較「工程」。

---

## 3. 方向 B — 沉穩金融（Calm Fintech）

![B desktop dark](direction-b-desktop-dark.png)

### 概念
像一個現代金融 App（Revolut、Wise 這一類）：大圓角卡片、柔和陰影、寬鬆留白、比較大的字。主角是一張「價格＋圖表」合一的主卡：64px 的大價格（小數用次要色，讓整數更突出）、下面一行 meta，再往下就是圖表。換算改成 5 張幣別卡片，警示在右欄，建立表單預設收合。整體的感覺是「平靜、可信、好讀」。適合想展示設計品味與 UX 的作品集。

### 各斷點版面
- **桌機 ≥1081px**：內容最大寬 1240px 置中，12 欄格線。第一列：主卡（8 欄：價格＋圖表 340px）｜警示卡（4 欄，高度拉到與主卡齊平，底部放一句規則說明）。第二列：多幣別換算全寬，5 張卡片一列。頁尾。
- **平板 641–1080px**：主卡全寬、警示卡全寬，換算卡片 3 欄。
- **手機 ≤640px**：頁首只留 ₿、標題、pill；價格 40px；圖表分頁拉滿寬度；圖表 260px；換算卡片變成一個清單（左：代碼 badge＋名稱；右：價格＋匯率）；幣別管理變成 bottom sheet；toast 左右滿版。

### 關鍵設計決策
- **價格與圖表合一**：解決現況「價格卡片空一半」與「兩欄底部不齊」。
- **整數/小數分色**：`$83,211` 用 `--text`、`.21` 用 `--muted`，讀價格時眼睛先抓整數。
- **方向只用小 pill**：「▲ 較前一筆」pill 著色並柔和發光 1s，大數字不變色。
- **換算卡片**：每個幣別一張卡，代碼 badge＋中文名稱＋大數字＋「1 USD = …」，比表格更容易掃讀；手機改清單避免橫向捲動。
- **低頻功能收起來**：「管理幣別」放在換算卡片右上角（使用者要管理幣別的時機正是看換算時）→ 開啟置中 modal；「＋ 新增警示」預設收合，展開後是一個小表單（附「填入目前價格」）。
- **沒有未讀時整塊不顯示**，不再出現「沒有未讀警示」這種佔位文字；空清單有圖示＋說明的空狀態。

### 優缺點
- 優點：第一眼最「漂亮」、最像成熟產品；字大好讀；對非交易使用者友善；截圖放在 GitHub README 很上相。
- 缺點：需要捲動才看得到換算（1440×1000 的桌機剛好在第一屏下緣）；資訊密度較低；新增警示多一次點擊；陰影/圓角/漸層背景在淺色主題要小心不要太「甜」。

---

## 4. 建議

**建議採用方向 B（沉穩金融），並吸收 A 的兩個細節：OHLC 圖例（可選）與等寬數字僅用於表格。**

理由：
1. 這是作品集：看的人多半只看 README 的一張截圖，B 的第一印象最好，而且差異感最明顯（現況是「卡片網格」，A 本質上也是網格，只是更密）。
2. 使用情境是「開著頁面看價格、偶爾設警示」，不是專業交易。B 的大字與留白更符合「平靜」這個要求，也更自然地把低頻功能收起來。
3. 實作風險較低：B 是一般文件流版面，不需要 A 的「固定 100vh + 內部捲動」，平板/矮螢幕的邊界情況少很多。
4. 兩個方向的元件、行為變更幾乎一樣（見 §8、§9），選 B 不會多花工程時間。

如果你更想強調「這是即時資料工程專案」、要做出 Bloomberg 感，就選 A — A 的 mockup 同樣是完整可實作的。

---

## 5. 兩個方向共通的規則

### 5.1 資料誠實
只使用後端實際提供的資料。下列「衍生資料」都標為 **選用**，計算方式完全來自既有資料：

| 顯示 | 計算方式 | 出現在 |
|---|---|---|
| 開啟後漲跌（金額、%） | `目前價格 − 本頁開啟後收到的第一筆價格`；% = 差額 ÷ 第一筆價格 | A 統計格、B meta 行 |
| 本次開啟最高 / 最低 | 本頁開啟後所有 SSE 價格的 max / min | A 統計格 |
| 圖表區間 高 / 低 | 目前分頁已載入的 K 線（含正在形成的那根）high 的最大值 / low 的最小值；走勢圖則是所有點 price 的 max/min | A 圖例、B 圖表列 |
| 最新 K 線 開/高/低/收 | 圖表最後一根 K 線（含 live 更新）的 OHLC | A 圖例 |
| 警示「還差 ±X」 | `門檻 − 目前價格` | A、B 警示清單 |

不顯示 24h 漲跌、成交量、市值、掛單簿等後端沒有的資料。頁面上這些欄位都沒有冠上「24h」之類的字眼，避免誤導。

### 5.2 價格閃爍與方向（比現在更安靜）
- 大價格數字**永遠不變色**（現況是整個數字變紅/綠）。
- 方向只由小元件表示（A：28px 方塊；B：「較前一筆」pill），顏色為 `--up`/`--down`，並附 `.sr` 文字「較前一筆上漲/下跌」。
- 閃爍只在該小元件上，用 `box-shadow` 光環淡出（A 0.8s、B 1s），不閃背景、不動版面。
- 節流規則**沿用現有常數**：同方向最多每 `FLASH_EVERY_MS = 2000` 一次，任兩次至少間隔 `FLASH_MIN_GAP_MS = 1000`。
- `prefers-reduced-motion: reduce` 時所有動畫變成 0.01ms（現有做法），toast 倒數條隱藏。

### 5.3 連線狀態
| 狀態 | pill | 價格 | 其他 |
|---|---|---|---|
| 即時 LIVE | `--up` 文字 + `--up-soft` 底，圓點 2.4–2.6s 脈動 | `--text` | — |
| 資料延遲 STALE | `--warn` / `--warn-soft` | 改 `--muted`，方向元件隱藏 | 價格下方橫幅（`--warn-soft` 底、`--text` 字、警告圖示），衍生數字與換算金額也改 `--muted` |
| 已斷線 DISCONNECTED | `--down` / `--down-soft` | 同上 | 橫幅改 `--down-soft`、✕ 圖示 |
| 連線中 connecting | `--muted` / `--panel-2` | skeleton | 換算金額 skeleton、圖表顯示「載入圖表中…」 |

橫幅文案沿用現有 LivePrice 的兩句話；狀態用顏色＋文字＋圖示三重表示，不只靠顏色。

### 5.4 無障礙
- 所有文字 ≥4.5:1、UI 元件邊框與 focus ring ≥3:1（§6.1、§7.1 的表格是用 `src/tokens.mjs` 算出來的，不是估的）。
- 沒有用 `opacity` 讓舊價格變淡（現況用 `opacity: .45`，會讓對比掉到 4.5 以下），改用 `--muted` 色。
- `:focus-visible` 2px `--accent` 外框；B 的 input 另加 3px 半透明光暈。
- 圖表分頁是 `role="tablist"`，支援左右方向鍵；高於/低於是 `radiogroup`（原生 radio）。
- 幣別管理用原生 `<dialog>.showModal()`：Esc 關閉、焦點鎖定、背景 inert 都是瀏覽器內建。
- 觸控目標：手機上控制項 36px（A）/ 40px（B），行高列 ≥44px。

---

## 6. 方向 A 實作規格

### 6.1 色彩 token（深色預設、淺色由 `prefers-color-scheme: light` 切換）

| Token | 深色 | 淺色 | 用途 |
|---|---|---|---|
<!--TOKENS_A-->

<!--CONTRAST_A-->

### 6.2 其他 token
| 類別 | 值 |
|---|---|
| 字體 | `--font-sans: system-ui, -apple-system, "PingFang TC", "Noto Sans TC", "Microsoft JhengHei", "Segoe UI", sans-serif`；`--font-num: ui-monospace, "SF Mono", "JetBrains Mono", Menlo, Consolas, "PingFang TC", monospace`（所有數字 + `tabular-nums`） |
| 字級 | 11（badge/軸）/ 12（標籤、表頭）/ 13（內文基準）/ 14（統計值）/ 16 / 20 / 價格 `clamp(32px, 4.2vw, 44px)` 700、`letter-spacing: -.03em` |
| 間距 | 2 / 4 / 8 / 12 / 16 / 24 px（`--sp-1`…`--sp-6`） |
| 圓角 | 2 / 4 / 6 px（小方塊、按鈕、pill 都用 4px；不使用膠囊形） |
| 尺寸 | 控制項 28px（手機 36px）、面板標頭 40px、頂列 48px、rail 400px |
| 陰影 | 只有浮層用：`0 12px 32px rgba(0,0,0,.5), 0 0 0 1px var(--line)`（淺色 `rgba(13,20,28,.18)`）；面板本身不用陰影 |
| 動態 | `--ease: cubic-bezier(.2,.7,.2,1)`；hover 120ms；方向閃爍 800ms；toast 進場 200ms；drawer 220ms |

### 6.3 元件與狀態
| 元件 | 規格 / 狀態 |
|---|---|
| 價格 hero（報價列） | 標籤 12px muted「BTC-USD 最新成交價」；價格 mono 44px；單位 `USD` 12px；方向方塊 28×28；右側 `<dl>` 統計格以 1px 左框分隔。狀態：live / stale / disconnected（見 §5.3）/ connecting（skeleton） |
| 狀態 pill | 高 24px、圓角 4px、1px 邊框（狀態色 55% 透明）、圓點 7px；內容「即時 · Coinbase」 |
| Segmented control | 外框 `--bg` 底 + `--line` 邊；項目高 22px（手機 30px）；選中 `--panel-2` 底 + inset 1px `--line-strong`；警示方向選中時用 `--up`/`--down` 文字與框 |
| 表格（換算） | 表頭 12px muted、列高約 36px、1px `--line` 分隔、數字靠右 mono；hover 列 `--panel-2` 70%；代碼 mono 700 + 中文名稱 12px muted（手機換到下一行） |
| 表單控制 | input 高 28px、`--bg` 底、`--line-strong` 邊；hover 邊框 `--muted`；focus 2px `--accent`；錯誤 `aria-invalid` → `--down` 邊框 + 下方 12px 錯誤文字；門檻 input 內含 `USD` 後綴 |
| 按鈕 | 預設（`--panel-2` 底 + `--line-strong` 框）、primary（`--accent` 底 / `--on-accent` 字）、ghost（透明）、danger（`--danger` 底 / 白字）、link（無框 `--accent`）、icon（正方形）；hover 邊框 `--accent`；active 下移 1px；disabled `opacity .5` |
| 警示清單 | 每列：方向方塊 24px｜「高於 83,500.00」＋第二行（上次觸發 / 尚未觸發、還差 ±X）｜刪除（ghost）。刪除確認：同位置換成「確定刪除？」（danger）＋「取消」 |
| 未讀區 | `--warn-soft` 底、`--warn` 45% 邊框；標頭「未讀觸發（n）」＋「全部標記已讀」（link）；每筆兩行＋「已讀」按鈕；n=0 時整塊與 badge 隱藏 |
| Toast | 右上（頂列下方 8px），寬 360px；`--panel-2` 底、左側 3px `--warn`；標題「價格警示觸發」＋時間；底部 2px 倒數條（10s，對應 `TOAST_MS`）；× 關閉 |
| Drawer（幣別管理） | 右側 440px、全高；標頭 48px；清單列 44px（代碼 mono｜名稱｜編輯 / 刪除並排）；編輯時該列變成兩個 input＋儲存/取消；底部固定「新增幣別」表單；手機全螢幕 |
| 空狀態 | 置中兩行：粗體「尚未設定警示」＋說明 |
| Loading | `body[data-feed=connecting]`：`.sk` 元素文字透明、`--panel-2` shimmer 1.4s；圖表區顯示「載入圖表中…」 |

---

## 7. 方向 B 實作規格

### 7.1 色彩 token

| Token | 深色 | 淺色 | 用途 |
|---|---|---|---|
<!--TOKENS_B-->

<!--CONTRAST_B-->

### 7.2 其他 token
| 類別 | 值 |
|---|---|
| 字體 | `--font-sans`（同 A），全部數字 `font-variant-numeric: tabular-nums`，不使用等寬字 |
| 字級 | 12 / 13 / 15（內文基準）/ 18（卡片標題 650）/ 22（換算金額、modal 標題）/ 價格 `clamp(40px, 5.2vw, 64px)` 620、`letter-spacing: -.025em` |
| 間距 | 4 / 8 / 12 / 16 / 24 / 32 / 48 px（`--sp-1`…`--sp-7`） |
| 圓角 | 10（input）/ 14（內層區塊、tile、toast）/ 20（卡片、modal）/ 999（按鈕、pill、segmented） |
| 尺寸 | 控制項 40px（小按鈕 32px）、頁首 76px（手機 64px）、內容最大寬 1240px |
| 陰影 | 卡片深色 `inset 0 1px 0 rgba(255,255,255,.03), 0 1px 2px rgba(0,0,0,.35), 0 8px 24px rgba(0,0,0,.22)`；淺色 `0 1px 2px rgba(18,21,28,.06), 0 8px 24px rgba(18,21,28,.06)`；浮層 `0 24px 64px rgba(0,0,0,.55)`（淺色 `.2`） |
| 背景 | `body` 左上一層 `--accent` 12% 的 radial glow，其餘純色 |
| 動態 | `--ease: cubic-bezier(.2,.8,.2,1)`；hover 150ms；方向光暈 1s；toast 300ms；modal 250ms |

### 7.3 元件與狀態
| 元件 | 規格 / 狀態 |
|---|---|
| 價格 hero（主卡上半） | 幣別標籤「比特幣 BTC ／ 美元 USD」；價格 64px（小數 `--muted` 500）；「▲ 較前一筆」pill 28px；meta 行：開啟後漲跌（選用）· 成交來源 Coinbase（主要/備援）· 更新於 HH:MM:SS；延遲/斷線橫幅在 meta 下方，圓角 14px |
| 狀態 pill | 高 32px 膠囊；live 時 `--panel` 底 + `--line` 框 + `--up` 字；stale/disconnected 改成對應 soft 底、無框 |
| Segmented control | 膠囊形 `--panel-2` 軌道、項目高 32px；選中為 `--panel` 底＋細陰影；手機拉滿寬度平均分配 |
| 圖表 | 高 340px（手機 260px）；無邊框、只有水平/垂直淡格線；K 線陽 `--up` 陰 `--down`；走勢改用 area（`--accent` 28% → 0 漸層）；右側最新價標籤圓角 6px |
| 換算卡片 | 5 欄 grid（平板 3 欄）；`--panel-2` 底、圓角 14px；代碼 badge（膠囊、`--panel` 底）＋中文名；金額 22px；「1 USD = 匯率」12px muted；無匯率時金額位置顯示「無匯率」muted；手機變成清單列 |
| 表單控制 | input 40px、圓角 10px、`--panel-2` 底、`--line-strong` 框；focus `--accent` 框 + 3px 35% 光暈；錯誤 `--down` 框 + 前面帶圓形「!」的 12px 錯誤文字 |
| 按鈕 | 全部膠囊形：default（`--line-strong` 框、透明底）、primary、danger、quiet（無框 muted，用於「刪除」這種次要破壞動作的第一步）、link、icon；active `scale(.98)` |
| 警示卡 | 標頭「價格警示」＋「＋ 新增警示」（`aria-expanded`）；收合表單區塊 `--panel-2` 底：方向 segmented、門檻 input（USD 後綴）＋「建立」、提示「目前 $83,211.21 · 填入目前價格」；清單每列為圓角框：36px 圓形方向圖示｜條件＋「還差 ±X · 上次觸發」｜刪除（quiet）；確認時整列紅框、按鈕換行靠右 |
| 未讀區 | 標題「離開期間觸發 n 則」＋「全部標記已讀」；n=0 時不渲染 |
| Toast | 右上（頁首下方），380px，`--panel` 底、圓角 14px、36px 圓形鈴鐺圖示、底部 3px 倒數條 |
| Modal（管理幣別） | 置中 560px、圓角 20px、背景 60% 黑＋3px 模糊；清單列 56px（代碼 badge｜名稱｜編輯／刪除）；底部 `--panel-2` 新增表單；手機為 bottom sheet（最高 88vh） |
| 空狀態 | 虛線框、44px 圓形圖示、粗體標題＋說明 |
| Loading | 同 A，shimmer 1.6s、圓角 10px |

---

## 8. React 元件對應表

| 元件 | 方向 A | 方向 B |
|---|---|---|
| `index.css` | 換成 §6 token 與元件樣式（舊的 `.card`、`.tabs`、`.table` 等改寫）。token 名稱建議直接用本文件的名稱，`chartAdapter.ts` 讀的 `--chart-*`、`--up`、`--down` 保留 | 換成 §7 token 與樣式 |
| `App.tsx` | 版面改為 `topbar / quote / grid(chart, rail) / footer`；`StatusPill` 移入 topbar；新增「⚙ 幣別管理」按鈕與 `<dialog>` 包住 `CurrencyManager`；新增 footer | 版面改為 `nav / page(hero, side, fx) / footer`；`LivePrice` 與 `PriceCharts` 放同一張卡；「管理幣別」按鈕放在 `ConvertedPrices` 標頭（由 App 傳 `onManage` 或在 App 渲染標頭） |
| `live/StatusPill.tsx` | 只改 class / 結構（dot + 標籤 + `· 來源`），邏輯不變 | 同左 |
| `live/LivePrice.tsx` | 價格不再套 `--up/--down` 顏色類別（CSS 移除即可）；方向 `▲▼` 換成 `.tick` 方塊，flash class 改掛在方塊上；非 live 時顯示狀態標籤；`<dl>` 統計格（選用衍生資料） | 價格拆成整數 / 小數兩個 span（純字串切割 `formatUsd`）；方向改「較前一筆」pill；meta 行重排；橫幅樣式 |
| `chart/PriceCharts.tsx` | 分頁文字改「1 分 K 線 / 5 分 K 線 / 1 小時走勢」；標頭加 OHLC 圖例＋區間高/低（選用） | 分頁同左；右側「圖表區間 高/低」（選用）；空資料訊息樣式 |
| `chart/chartAdapter.ts` | `localization.priceFormatter` 改千分位（`Intl.NumberFormat('zh-TW',{maximumFractionDigits:2})`）；`layout.attributionLogo: false` 並在 footer 註明 TradingView Lightweight Charts™（授權要求的 attribution 移到 footer）；`crosshair` 線色改用 `--line-strong` | 同左；另外走勢改用 `AreaSeries`（`lineColor: --accent`、`topColor: accent 28%`、`bottomColor: 透明`），網格只留淡色水平線、`rightPriceScale.borderVisible: false` |
| `converted/ConvertedPrices.tsx` | 表格三欄：「幣別（代碼＋名稱）/ 1 BTC / 匯率（1 USD =）」；「匯率更新於」與 attribution 放同一行面板 footer | `<table>` 改 `<ul>` 卡片（每張：代碼、名稱、金額、`1 USD = rate`）；標頭加「匯率更新於」與「管理幣別」；attribution 在卡片下方 |
| `alert/AlertManager.tsx` | 表單改一行：方向 radio segmented＋門檻 input（USD 後綴）＋「新增」；清單 `<table>` 改 `<ul>`；刪除確認流程不變 | 同上，但表單包在可收合區塊內；清單列加「還差 ±X」（選用）與「填入目前價格」（選用） |
| `alert/UnreadAlerts.tsx` | 0 筆時回傳 `null`（不再顯示「沒有未讀警示」）；每筆改兩行；badge 顯示於警示面板標頭 | 同左，標題文案「離開期間觸發 n 則」 |
| `alert/AlertToasts.tsx` | markup：圖示＋標題＋時間＋內文＋倒數條；倒數條 `animation-duration` 用 inline style `--toast-ms: ${toastMs}ms` | 同左 |
| `currency/CurrencyManager.tsx` | `<table>` 改 `<ul>` 清單（編輯/刪除並排，順便解掉現在按鈕上下疊的問題）；新增表單移到 drawer 底部；錯誤訊息文案不變 | 同左，放在 modal 裡 |

---

## 9. 需要「行為變更」的項目（非純 CSS / markup）

必要（兩個方向都需要，已盡量縮小）：
1. **幣別管理放進 `<dialog>`**：App（或一個小的 `CurrencyDialog` 包裝元件）加一個 `ref` 呼叫 `showModal()` / `close()`。`CurrencyManager` 內部邏輯完全不變；dialog 關閉時不需要重設狀態。
2. **方向 B only — 警示表單收合**：`AlertManager` 加一個 `formOpen` 布林 state 與切換按鈕（`aria-expanded` / `aria-controls`）；新增成功後維持展開（方便連續新增）。
3. **UnreadAlerts 0 筆時不渲染**：`return null` 取代 `<p>沒有未讀警示</p>`（未讀數需顯示在面板標頭時，把 `events.length` 往上提或用既有 `ALERT_EVENTS_CHANGED` 事件）。現有測試若有檢查「沒有未讀警示」文字需要一起改。

選用（衍生資料 / 小功能，可分開做、也可以不做）：
4. **開啟後漲跌、本次開啟最高/最低**：`LivePrice` 以同樣「render 期間更新 state」的模式多記 `first`、`high`、`low` 三個值。
5. **圖表區間高/低、OHLC 圖例**：`PriceCharts` 在 `load()` 與 live 更新時計算，用 state 傳給標頭；live 更新時節流（≤1 次/秒）避免每筆重新 render。
6. **警示「還差 ±X」**：做成一個獨立的 `<Distance threshold>` 小元件自己 `useLiveStream()`，避免整個 AlertManager 每筆價格都重新 render。
7. **方向 B「填入目前價格」**：按鈕把 `Math.round(price)` 填進 threshold input。
8. **Loading skeleton**：`LivePrice` 沒有價格時、`ConvertedPrices` 載入中時改渲染 skeleton 元素（取代「尚無價格資料」/「載入中…」文字；文字仍保留在 `.sr` 或 `aria-busy` 供螢幕閱讀器）。

不需要變更：SSE、節流常數、狀態計算（`computeDisplay`）、API 呼叫、錯誤處理、toast 已讀規則、所有文案中的資料意義。

---

## 10. 驗證紀錄
- 對比度：`node src/tokens.mjs` 計算 WCAG 2.x 相對亮度對比，兩個方向 × 兩個主題 × 31 組配色，**0 項未達標**（最低值：B 淺色輸入框邊框對頁面底色 3.17:1，門檻 3:1）。
- 版面：`screenshot.mjs` 在截圖前檢查 `scrollWidth` 與所有超出視窗的元素。375px（含 drawer / modal 開啟）、768px、1440px 兩個方向都是 `scrollWidth == clientWidth`、沒有溢出元素。
- 另外人工檢視了：延遲 / 斷線 / 載入中狀態、toast、刪除確認、手機幣別管理。

---

## 11. 待決問題
1. **漲跌配色**：台灣股市慣例是「紅漲綠跌」，國際加密貨幣交易所多為「綠漲紅跌」。mockup 用綠漲紅跌；token 是語意化的（`--up`/`--down`），要改成紅漲只需對調兩組色值（對比度已知都達標）。要改嗎？
2. **JPY / TWD 小數位**：目前所有幣別固定 2 位小數（`¥13,096,534.29`）。要不要改成各幣別慣例（JPY 0 位）？這是 `format.ts` 的一行修改，但會改變目前刻意的設計。
3. **換算幣別排序**：目前依 API 回傳順序（EUR, GBP, JPY, TWD, USD）。要不要把 TWD 固定排第一？（需要前端排序 = 小行為變更。）
4. **選用衍生資料**（§9 的 4–8）要做哪些？建議至少做「開啟後漲跌」與「還差 ±X」，成本低、畫面最有感。
5. **TradingView 標誌**：建議關掉圖內 logo、改在頁尾註明（符合 lightweight-charts 授權的 attribution 要求）。可以嗎？
