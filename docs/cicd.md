# CI/CD

[← 回到 README](../README.md) · [設定](configuration.md) · [API](api.md) · [測試](testing.md) · [驗收步驟](acceptance.md)

> 這份文件會在 spec k8s-gitops-cicd 的 task 20 補齊（流程圖、GitHub 設定、發布與回滾）。目前先寫好「歷史改寫後的恢復步驟」。

## 歷史改寫後的恢復步驟

改寫 main 的歷史（例如清除 commit trailer）並 force push 之後，所有舊的 commit hash 都會改變。dev / prod overlay 裡的 image tag `sha-<7>` 指向的 commit 已經不存在了。image 本身還在 GHCR 上，而且是用 digest 指定的，所以執行中的 dev / prod **不受影響**。但有兩件事會卡住：

- **發布會失敗**：`release.yml` 會確認 dev overlay 裡的 image 是從哪個 commit 建置的，以及那個 commit 的 `ci-ok` 是否通過。commit 找不到時，會出現錯誤 `commit … is not in the repository's history (rewritten?)`。
- **只改文件的 commit 不會觸發重建**：只動到 `deploy/`、`docs/`、`specs/`、`*.md` 時，CI 不會建置 image，dev 也就不會更新。

恢復順序如下（不能跳步驟）：

1. **重新啟用 main 的 ruleset**（改寫期間暫時停用的那份）。
2. **在 main 上手動執行 CI**：GitHub → Actions → CI → Run workflow，分支選 `main`。也可以用指令：
   ```bash
   gh workflow run ci.yml --ref main
   ```
   手動執行時，CI 一律視為有程式變更，會跑完整的測試，並建置與掃描（Trivy）image，再推送 `sha-<HEAD 的 7 碼>`。在其他分支手動執行時不會推送。
3. **等 dev 自動更新**：CI 綠燈後，`Deploy dev` 會由 workflow_run 觸發。因為 overlay 裡舊的 sha 已經不在歷史中，防降版檢查（`scripts/dev-bump-decision.sh`）會判定為 `bump-unknown-current` 並允許更新。bump PR 會註明 `current sha … not found in the history of main (rewritten?); allowing bump`，接著自動合併，Argo CD 再同步 dev。
4. **確認 dev 已換成新的 sha**：
   ```bash
   kubectl -n cube-dev get pods -o jsonpath='{range .items[*]}{.metadata.name} {.spec.containers[0].image}{"\n"}{end}'
   ```
5. **之後才能發布**：在 bump commit 上打 `vX.Y.Z` tag。這時 dev overlay 裡的 sha 是新的、存在於歷史中、`ci-ok` 已通過，release 會成功。
6. **prod**：overlay 仍然指向舊的 tag，但有 digest，所以照常運作。下一次發布時，透過「prod 升級到 vX.Y.Z」PR 升級即可，不需要另外處理。

注意：image 和測試是並行執行的，所以 GHCR 上有 `sha-*` 不代表那個版本通過了 CI。能不能部署，以該 commit 的 `ci-ok` 為準：`Deploy dev` 只在整個 CI 成功時才更新 dev，`release.yml` 也會檢查 `ci-ok`。

另外，改寫前留下的 GitHub Actions run，按「Re-run」也不會讓 dev 退版。這些 run 的 commit 已不在 main 上，防降版檢查會跳過，run 仍顯示成功，原因寫在 job summary。
