# B1 Aurora — Xperia Clear All

正式向量版本，以三條由短到長、向左延伸的錯位橫線構成。

## 色彩

| 用途 | 色碼 |
| --- | --- |
| 上層青色 | #00AFC8 |
| 中層藍色 | #3767E8 |
| 下層紫色 | #7544D8 |
| 桌面背景 | #EDF5FF |

## 素材

- `aurora/logo-color.svg` / `.png`：透明背景彩色標誌，PNG 1024px。
- `aurora/logo-monochrome.svg` / `.png`：透明背景黑色標誌，PNG 1024px。
- `aurora/icon-square.svg` / `.png`：完整方形背景版本，PNG 1024px。
- `aurora/icon-circle.svg` / `.png`：圓形展示版，圓形外透明，PNG 1024px。
- `aurora/store-icon-512.png`：512px 完整方形商店圖示。
- `aurora/preview.svg` / `.png`：彩色、不同遮罩與單色展示。

Android 桌面圖示以 108dp 圖層組合，標誌位於中央直徑 66dp 安全區內。前景不含背景或遮罩，交由啟動器裁切。API 26 資源提供彩色前景與背景；API 33 資源另提供相同 alpha 輪廓的 monochrome 圖層。主題圖示的配色由啟動器決定。通知圖示為獨立白色透明底向量。

Android 資源位於 `app/src/main/res/` 的 `drawable/ic_launcher_foreground.xml`、`drawable/ic_launcher_monochrome.xml`、`drawable/ic_rootless.xml`、`values/icon_colors.xml` 以及 `mipmap-anydpi-v26` / `mipmap-anydpi-v33`。

## 重建

在專案根目錄執行 `python3 branding/build_logo.py`。Python 使用標準函式庫；PNG 匯出需要 Inkscape。正常建置 Android App 直接讀取已提供的 XML，無須 Inkscape。
