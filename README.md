# Xperia_Clear_All
Restore the Clear all button in Recent

適用於 Sony Xperia 的 Android 工具，透過 Shizuku（ADB 模式）在最近使用畫面提供「全部清除」按鈕。
APK 發布於 GitHub Releases；Play Console 用來管理 Android 開發者驗證及套件註冊。

## GitHub 自動編譯、簽名及發布

此專案已提供 `.github/workflows/android.yml`，使用 JDK 21、Gradle Wrapper 8.13、Android SDK 36。

| 操作 | 結果 |
| --- | --- |
| 推送到 `main` 或提出 PR | 跑單元測試並產生 debug APK，不讀取正式簽名 Secrets |
| 在 `main` 手動執行 workflow | 通過測試後，產生正式金鑰簽名的 APK，存入 Actions Artifacts |
| 推送 `v` 開頭的版本標籤 | 通過測試後，簽名並建立 GitHub Release，附上 APK、檔案雜湊及簽名憑證資訊 |

### 1. 準備正式簽名金鑰

使用自己的 `.jks`／`.keystore`，並確認它對應 Play Console 已註冊的簽名憑證。
需要知道 keystore 密碼、key alias 及 key 密碼。只有公開的 SHA-256 指紋或 `.pem` 憑證，無法簽名。

若還沒有正式金鑰，可在 Android Studio 選 **Build → Generate Signed App Bundle or APK → APK → Create new**，
將 keystore 存到專案外，設定 alias、密碼及至少 25 年的效期。若已用某把金鑰完成套件註冊，請沿用原金鑰。

金鑰存放在專案外並另外備份。不要把私鑰或密碼提交到 Git，也不要貼到 issue、建置日誌或聊天裡。
若需確認 alias 及 SHA-256，可在 PowerShell 執行以下命令（自行替換金鑰路徑；命令會互動式詢問密碼）：

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -list -v -keystore 'D:\Signing\xperia-release.jks'
```

### 2. 在 GitHub 加入四個 Repository Secrets

進入存放本專案的 GitHub repository（不是個人帳號設定）：

**Settings → Secrets and variables → Actions → Secrets → New repository secret**

逐一新增下列名稱，名稱須完全一致：

| Name | Secret |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 整份 keystore 轉成 Base64 後的內容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密碼 |
| `ANDROID_KEY_ALIAS` | 要使用的 key alias，不是檔名 |
| `ANDROID_KEY_PASSWORD` | 該 alias 的私鑰密碼；若與 keystore 密碼相同，填入同一個密碼 |

在本機 PowerShell 執行下列命令，把 Base64 直接複製到剪貼簿，再貼進第一個 Secret：

```powershell
$releaseKeystorePath = 'D:\Signing\xperia-release.jks'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($releaseKeystorePath)) | Set-Clipboard
```

不要把 Base64 寫進程式碼。Base64 是編碼，不是加密；GitHub Secret 才是儲存它的地方。
單一 Secret 上限為 48 KB；一般單一金鑰的 keystore 通常可容納，若超過上限，需改用加密檔案的流程。
Secrets 儲存後無法查看原文，但可以重新設定。填完後請清空剪貼簿。

不需要另外建立 Personal Access Token，也不需要上傳 Google Play service account JSON。
建立 GitHub Release 使用 workflow 自帶的 `GITHUB_TOKEN`，只有發布工作取得 `contents: write` 權限。

### 3. 將設定推送到 GitHub

先在 GitHub Desktop 檢查 Changes；應包含 workflow、release 輔助程式、README、Gradle 簽名設定、
`.gitignore` 以及 `gradle/wrapper/gradle-wrapper.jar`。不能包含自己的 keystore 或密碼檔。

在 `main` 提交這些變更，再按 **Push origin**。若使用命令列，可在確認 diff 後執行：

```powershell
git add .github .gitignore README.md app/build.gradle.kts gradle/wrapper/gradle-wrapper.jar
git commit -m "Add Android CI and signed GitHub releases"
git push origin main
```

若 Actions 被停用，先到 repository 的 **Actions** 頁啟用。
若 repository 或 organization 限制可使用的 actions，需允許本 workflow 引用的 `actions/*` 及 `gradle/actions/*`。

### 4. 先手動產出一個簽名 APK

1. 開啟 repository 的 **Actions**。
2. 選 **Android CI and release**。
3. 按 **Run workflow**，分支選 **main**，再按 **Run workflow**。
4. 開啟該次執行，等待 `Test and build debug APK` 及 `Sign APK and publish tagged release` 成功。
5. 在執行頁面下方的 **Artifacts** 下載 **signed-release-apk**，解壓縮後取得 APK。

首次建置要下載 SDK、Gradle 及測試依賴，可能需要較久。
`debug-apk` 使用的是測試簽章；要對外發布請使用 **signed-release-apk** 裡的 APK。
下載的 `signing-certificate.txt` 會包含 `Signer #1 certificate SHA-256 digest`，請和 Play Console 註冊的憑證比較。
`SHA256SUMS.txt` 則是 APK 檔案本身的 SHA-256，兩者用途不同。

手動執行只提供下載檔案，不會建立公開的 GitHub Release。
若還需要註冊用的所有權驗證 APK，可將 Play Console 提供的內容放進
`app/src/main/assets/adi-registration.properties` 後，使用這個手動流程建置，再上傳到 Play 的驗證頁面。
這不會將 App 上架 Play 商店。

### 5. 正式發布到 GitHub Releases

每次發布都先在 `app/build.gradle.kts` 更新：

```kotlin
versionCode = 31
versionName = "0.31-test"
```

`versionCode` 必須高於先前發布的數字。範例只供說明，請依實際版本調整。
先把更新提交並推送到 `main`，接著建立與 `versionName` 完全一致、前面加 `v` 的標籤：

```powershell
git tag v0.31-test
git push origin v0.31-test
```

等待 Actions 成功後，到 repository 的 **Releases** 下載 APK。
標籤含 `-`（如 `v0.31-test`）會標示為 pre-release；`v0.31` 這類標籤則發布為正式版。
workflow 會檢查標籤與 APK 的 `versionName` 一致，且發布的 commit 已包含在 `main`。
不要覆蓋既有 Release 的 APK；若要發布修改後的內容，請遞增版本並建立新標籤。

要讓所有人都能下載，repository 必須是 **Public**。Private repository 的 Release 只開放有存取權的人。
公開原始碼不等於公開金鑰：自己的私鑰仍只保留在本機備份及 GitHub Secrets。

### 6. 常見問題

| 問題 | 處理方式 |
| --- | --- |
| 沒看到 Run workflow | 先確認 workflow 已推送到預設分支 `main`，並有 repository 的寫入權限 |
| `Missing repository secret` | 依訊息補上對應 Repository Secret；不要只新增到 Variables |
| keystore 密碼或 alias 錯誤 | 用 `keytool -list` 核對 alias，再更新對應 Secret |
| 版本標籤檢查失敗 | 標籤必須是 `v` 加上 `versionName`，而且該 commit 要在 `main` 上 |
| 建立 Release 被拒絕 | 查看 job 日誌及 repository／organization 的 Actions 權限政策；workflow 已宣告 `contents: write` |
| 測試失敗 | 下載 `unit-test-reports`；測試未通過時不會進入簽名發布工作 |
| APK 無法覆蓋更新 | 檢查新舊版本的套件名稱、簽名及 versionCode；更換簽名通常需要解除安裝舊版，會清除設定 |

本機若未設定簽名環境變數，debug 建置照常運作，release 產物則不會套用正式簽章。
GitHub Actions 能驗證建置、單元測試及 APK 簽章；Sony 實機的 Shizuku、重開機恢復等行為仍需實機測試。

參考：[GitHub Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)、
[手動執行 workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)、
[在 workflow 使用 GitHub CLI](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-github-cli)、
[Play 套件註冊](https://support.google.com/googleplay/android-developer/answer/16761053?hl=zh-Hant)。
