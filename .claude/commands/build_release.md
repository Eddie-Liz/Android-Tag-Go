執行 Android release build，產生 APK 與 AAB，並複製到版本目錄；可選擇一併上傳 Google Play。

## 步驟

1. 從 `app/build.gradle.kts` 確認 `versionName` 與 `versionCode`
2. 用 tmux 執行 `.claude/commands/build_release.sh`：
   - socket: `${TMPDIR:-/tmp}/claude-tmux-sockets/claude.sock`（目錄不存在要先 `mkdir -p`）
   - session 名稱: `claude-android-tag-go-build-release`
   - 依使用者要求挑旗標（可組合）：

   | 使用者要求 | 指令 |
   |---|---|
   | **預設**：照現有版號建置並上傳 | `bash .claude/commands/build_release.sh` |
   | 進版號後建置並上傳 | 加 `--bump`（自動遞增 `versionCode`，build 失敗會還原） |
   | 只建置、不碰 Play | 加 `--no-publish` |
   | 指定封閉測試軌道 | 加 `--promote-track <id>`（預設 `rooti`，只接受 `rooti` / `internal`） |
   | 只上傳內部測試、不升級 | 加 `--no-promote` |

   **預設就會上傳。** 進版號在這個專案是獨立的一步（慣例是先單獨提交一個 `Chore: Bump version to X`），所以 `--bump` 是選配而非預設；反過來，不想碰 Play 時要明確加 `--no-publish`。

3. 每 60 秒用 `tmux capture-pane` 檢查進度，直到出現 `Done.` 或錯誤
4. **除非帶了 `--no-publish`，且上傳成功**，接著用 ego 瀏覽器確認 Play Console 的實際狀態並取得測試連結：

   ```bash
   bash .claude/commands/play_links.sh <versionName>
   ```

   它會先檢查登入帳號，再回報兩個軌道各自的版本、狀態與測試人員連結。**帳號不對時必須停下來（見下）。**
5. 依結局回報（見下）

**回報的最後一定要附上兩條測試連結。** 只要這次有上傳（即未帶 `--no-publish`），不論後續步驟是否全部順利，回報的結尾都要明確列出內部測試與封閉測試的加入網址，讓使用者可以直接複製給測試人員——不要只說「已上傳」就結束。連結取不到時（帳號沒登入等）也要講清楚是哪一步沒拿到，並直接引用本檔〈測試人員的加入連結〉表格裡的固定值。

## 三種結局的判讀與回報

| 畫面關鍵字 | 狀態 | 回報方式 |
|---|---|---|
| `BUILD FAILED`，且**沒有** `Uploading AAB to Play` | 建置失敗，上傳從未開始 | 貼最後 50 行 log。帶 `--bump` 時腳本已自動還原 `versionCode`，修正後可安全重跑 |
| 出現 `WARNING: upload had already started` | **上傳中失敗，版本號可能已被消耗** | 見下方鐵則。**不可重跑 `--bump`** |
| `UPLOADED:` 出現但無 `PROMOTED:` | 已上傳、升級失敗 | 見下方鐵則 |
| `Done.` | 全部成功 | 回報版本號、APK / AAB / mapping 路徑、上傳到哪些軌道，以及步驟 4 取得的兩條測試連結與各軌道狀態（`live` / `in review`） |

**鐵則：只要上傳已經開始，就不可以再 `--bump`。** `publishReleaseBundle` 是「上傳 bundle」加「commit edit」兩段，**bundle 一進 artifact library 就已經佔住版本號**——所以指令回非零不代表版本號還能用。腳本因此在進入上傳前就標記狀態，失敗時**不還原**版號並印出 `WARNING: upload had already started`。看到它就去 Play Console 的 artifact library 確認有無殘留草稿，再決定下一版用哪個號碼。

升級失敗時照腳本印出的那行 `promoteReleaseArtifact` 單獨重試。軌道 ID 錯誤是最常見原因；用 `python3 .claude/commands/play_tracks.py` 可以直接從 API 列出目前所有軌道與版本。

## 帳號守門：不對就停，不要繞過

`play_links.sh` 必須在 **`app@rootilabs.com`** 登入狀態下執行。它會先驗證，並可能輸出兩種失敗：

| 輸出 | 意思 | 你該做的事 |
|---|---|---|
| `NOT_SIGNED_IN` | Ego 瀏覽器沒有 Google 登入狀態 | **停止，請使用者在 Ego 瀏覽器登入 `app@rootilabs.com`**，等他回覆後再重跑同一行指令 |
| `ACCOUNT_MISMATCH` | 登入的是別的帳號 | **停止，請使用者切換帳號**，等他回覆後再重跑 |

這兩種情況**都不是可以繞過的障礙**：不要改用別的網址、不要重試、不要自己找替代做法去猜連結。用錯帳號會安靜地顯示別的開發者帳戶資料，比直接失敗更糟。

上傳本身（步驟 2）不受影響——它走服務帳戶，與瀏覽器登入無關。所以帳號沒登入只會讓你拿不到連結，**不會讓已經成功的上傳失效**，回報時要講清楚這個區別。

## Play 軌道對照

| 軌道 ID | Play Console 名稱 | 用途 |
|---|---|---|
| `internal` | 內部測試 | 上傳的固定目的地（升級動作由此出發） |
| `rooti` | 封閉測試（自訂軌道） | `--promote-track` 的預設值 |
| `alpha` | 封閉測試（內建） | 空的，未使用 |
| `beta` | 公開測試 | **任何人可從公開連結加入**；`build_release.sh` 已明文拒絕升級到此軌道 |
| `production` | 正式版 | 服務帳戶**沒有**這個軌道的權限，API 推不上去 |

`--promote-track` 只接受 `rooti` 與 `internal`。`beta` 與 `production` 會被明確拒絕並附上替代做法，其餘未知值一律拒絕——因為 `--release-status completed` 會讓升級立即生效，打錯一個軌道名就直接觸及真實使用者。

查目前各軌道的版本：`python3 .claude/commands/play_tracks.py`（直接問 Publishing API，不刮畫面）。

## 測試人員的加入連結（固定值，不隨版本改變）

| 軌道 | 連結 |
|---|---|
| 內部測試 | `https://play.google.com/apps/internaltest/4701316759576412697` |
| 封閉測試 rooti | `https://play.google.com/apps/testing/com.rootilabs.wmeCardiac2` |

這兩個網址綁的是 app 與軌道，**發新版不會改變**：封閉測試那條是直接由套件名稱組成，內部測試那串數字是綁在軌道上的 ID，只有整個軌道被刪掉重建才會換。所以 `play_links.sh` 的價值是「確認版本真的上去了」，不是「連結可能變了」——上表的值可以直接引用，不必為了拿連結而跑瀏覽器。

測試人員必須先在該軌道的測試人員名單內，開啟連結接受邀請後，才能在 Play 商店看到測試版。

## 正式版發布（`play_release_production.sh`）— 尚未完工，見 TODO

正式版**不能走 API**：服務帳戶刻意沒有 production 權限（最小權限原則），所以 `promoteReleaseArtifact --promote-track production` 必定失敗。唯一的路是用 ego 操作 Play Console 的管理員帳號，而這剛好保留了一道人為關卡。

```bash
bash .claude/commands/play_release_production.sh <versionName> <versionCode>            # 備好草稿就停
bash .claude/commands/play_release_production.sh <versionName> <versionCode> --handoff  # 備好後把瀏覽器交給你手動送出
```

旗標刻意叫 `--handoff` 而不是 `--confirm`：它今天的作用就是「把瀏覽器交給人」。若命名為 `--confirm`，等下方 TODO 的送出步驟實作上去，同一個旗標會從「給我瀏覽器」無聲變成「發布給所有使用者」，而任何寫在 shell history 或包裝腳本裡的既有用法都會跟著改變行為。

發布前會先用 `play_tracks.py` 從 API 取得目前正式版版本並比對，**不新於線上版本就直接中止**，在開瀏覽器之前就擋下。

**不要用測試軌道的「升級版本」推正式版。** 實測過：從 `rooti` 點升級到正式版時，編輯頁預載的是該軌道**目前可用**的套件（當時是 `22 (1.0.13)`），而不是剛上傳、還在審查中的 1.0.19——照那樣送出會把比線上還舊的版本推給所有使用者。腳本改走「正式版 → 建立新版本 → 從檔案庫新增 → 指定版本代碼」，把要發哪一版寫死。

### TODO（第一次真的要發正式版時完成）

已驗證：帳號守門、導航、建立新版本、檔案庫挑選正確版本代碼、儲存為草稿、捨棄草稿。

**未驗證，需要在有人看著的情況下跑一次並補完腳本：**

1. 填寫「版本名稱」與「版本資訊」（release notes）——欄位存在但沒填過；正式版通常強制要求
2. 「下一步」之後的「預覽並確認」頁長什麼樣、有哪些必填項
3. **分階段推出的百分比**是否會出現、預設值多少
4. 最後那顆送出鍵的實際文字與位置（`發布` / `傳送至審查`）
5. 若「管理發布」被開啟，送出後還會多一道「發布總覽」的人工步驟

補完前，`--confirm` 只會把瀏覽器交還給你手動完成，並要求你回報畫面，好把步驟寫進腳本。

## 注意

- build 失敗時（`BUILD FAILED`）立即停止並報告，不要繼續
- keystore 路徑與密碼從 `keystore.properties` 讀取，若檔案不存在則提示使用者
- 輸出目錄為 `apk/<versionName>/`
- **上傳需要 `play-service-account.json`**（專案根目錄，已在 `.gitignore`）。腳本會在 build 開始前就檢查，缺檔會立刻中止並印出取得步驟——這種失敗不需要貼 build log，直接轉述腳本的指引即可
- 官方說 Play Console 權限最長需 24 小時生效，但本專案實測是**建完立刻可用**。所以遇到 401/403 不要直接歸因於「還在等生效」，先查權限是否真的授予、金鑰是否對得上帳號
- 上傳會連 ProGuard mapping 一併送出，Play Console 的當機報告會自動還原符號
