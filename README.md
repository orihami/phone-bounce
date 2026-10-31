# Phone Bounce

DVD のロゴが、スマホの画面に見えている **ウィンドウの枠** で跳ね返り続ける、常駐アプリ（Android 11 以上）。
Windows 用 Desktop Bounce のスマホ版。

- 画面の端・キーボード・分割画面・小窓で反射。反射のたびに色が変わる
- 窓が動くとロゴが押される。勢いよくぶつかる・挟まれる・ロゴの上に窓が開く、でロゴが窓の中に入る（半透明）。一定時間で枠をすり抜けて出る
- クリックは素通り。画面が消えている間と一時停止中は、コマ送りを止める

## 入れ方
最新版: https://github.com/orihami/phone-bounce/releases/latest/download/PhoneBounce.apk

1. APK を入れる
2. アプリを開き、「アクセシビリティの設定を開く」から Phone Bounce をオンにする（一度オンにすれば、再起動後も自動で起動する）
   - 灰色でオンにできないとき: 設定 → アプリ → Phone Bounce → ︙ → 「制限付き設定を許可」
3. （任意）電池の最適化を除外する。クイック設定に「跳ねるロゴ」のタイルを足すと、一時停止・再開できる

## プライバシー
使うのは、ウィンドウの **位置と大きさだけ**（`AccessibilityService.getWindows()`）。画面の文字・入力内容は読まない
（`AccessibilityNodeInfo` や `rootInActiveWindow` は、コードのどこでも呼んでいない）。通信もしない（INTERNET 権限なし）。

## 仕組み
- `core/` … Android に依存しない動きの計算（反射・押し出し・侵入・脱出）。`./gradlew testDebugUnitTest` で PC 上でテストできる
- `BounceService` … アクセシビリティサービス。透明な重ね表示（TYPE_ACCESSIBILITY_OVERLAY、タッチ素通り）にロゴを描き、窓の位置を読んで `core` に渡す
- `MainActivity` … 設定の画面。`PauseTileService` … クイック設定のタイル

「避ける窓」は、キーボード（入力メソッド）と、画面の 90% 未満の大きさのアプリ窓（分割画面・小窓・PiP）。全画面のアプリ本体は「背景」なので避けない。

## ビルド
```sh
./gradlew testDebugUnitTest assembleDebug
```
Android SDK（platform 35、build-tools 35.0.0）と JDK 17 以上が必要。
