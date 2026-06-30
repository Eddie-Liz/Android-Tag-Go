執行 Android release build，產生 APK 與 AAB，並複製到版本目錄。

## 步驟

1. 從 `app/build.gradle.kts` 確認 `versionName`
2. 用 tmux 執行 `.claude/commands/build_release.sh`：
   - socket: `${TMPDIR:-/tmp}/claude-tmux-sockets/claude.sock`
   - session 名稱: `build-release`
   - 預設（不進版號）: `bash .claude/commands/build_release.sh`
   - 使用者明確要求進版號時才加旗標: `bash .claude/commands/build_release.sh --bump`（自動遞增 `versionCode`，build 失敗會還原）
3. 每 60 秒用 `tmux capture-pane` 檢查進度，直到出現 `Done.` 或錯誤
4. 完成後回報：
   - 版本號
   - APK / AAB 輸出路徑
   - 若失敗，貼出最後 50 行 log 並指出錯誤原因

## 注意

- build 失敗時（`BUILD FAILED`）立即停止並報告，不要繼續
- keystore 路徑與密碼從 `keystore.properties` 讀取，若檔案不存在則提示使用者
- 輸出目錄為 `apk/<versionName>/`
