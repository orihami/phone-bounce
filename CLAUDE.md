# CLAUDE.md — Phone Bounce

**ユーザーへの返答・報告は日本語で。** 待ち時間や予定を書くときは日本時間を添える。技術的な安全の判断はユーザーにさせず、結論と「ユーザーにしかできない操作」だけ頼む。

## 何のアプリか
DVD ロゴが、スマホで見えているウィンドウの枠（キーボード・分割画面・小窓）で跳ね返る常駐アプリ（Android 11+）。
元は、ユーザーが作った Windows 用の Desktop Bounce（Rust 製、常駐。ソースの場所は不明。配布物と説明書は NAS の `make/somethingプログラム/desktop-bounce/`）。
動きの仕様は、その README（反射／押し出し／侵入／脱出）に合わせた。

## 構成
- `app/src/main/java/jp/haruto/phonebounce/core/` … **Android に依存しない**動きの計算（`Sim`、`Box`、`Obstacle`、`Params`）。ここだけで動きが決まる
- `BounceService` … アクセシビリティサービス（常駐の本体）。重ね表示にロゴを描き、`getWindows()` で窓の位置を読み、`Sim` に渡す
- `LogoView`、`MainActivity`（設定。XML を使わずコードで組む）、`PauseTileService`、`AppSettings`
- テスト: `app/src/test/.../core/SimTest.kt`（動きの核。実機が無くても確かめられる。**動きを変えたら必ずテストを足す・直す**）

## 守ること
- **画面の中身を読まない。** `AccessibilityNodeInfo`・`rootInActiveWindow`・イベントの中身（文字）は使わない。使うのは窓の位置と大きさだけ。INTERNET 権限は付けない
- ユーザーの好み: 隠れた操作より見えるボタン。絵を描き足す装飾（泡・ステッカーなど）は入れない
- **公開リポジトリ**: 秘密（鍵・トークン）、NAS や機械の内部情報（IP・ホスト名・共有名）は、コードにも docs にも HANDOFF.md にも書かない
- 実機で確かめていないことは「未確認」と書く（ここでは実機が無い）

## 引き継ぎ
作業の始めに `git pull` → `HANDOFF.md` を読む。区切りごと・終わるときに `HANDOFF.md` を更新して push（未 push を残さない）。

## ビルドとリリース
- テスト: `./gradlew testDebugUnitTest`、APK: `./gradlew assembleDebug`（JDK 17+、Android SDK platform 35 / build-tools 35.0.0）
- 署名は Secret `DEBUG_KEYSTORE_B64`（DreamCamera と同じ鍵。ワークフローが証明書を確認して止める）。Secret はユーザーが自分で登録する
- リリース手順:
  1. `app/build.gradle.kts` の versionCode を +1、versionName を新しい版に
  2. `docs/releases/<版>.md` に変更点（日本語・ユーザー向け）
  3. main に push → Actions が通るのを確認
  4. タグ `<版>`（v なし。例 `0.1.1`）を push → Actions が Release を作る。タグを push できないときは、Actions の build を手動実行（`release` に印、ブランチ main）
  5. ユーザーには固定リンク https://github.com/orihami/phone-bounce/releases/latest/download/PhoneBounce.apk を伝える
