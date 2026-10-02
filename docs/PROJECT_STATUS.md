# 開発状況と引き継ぎ

最終更新日：2026-10-02（日本時間）

この文書は現在の進捗と次セッションへの引き継ぎを記録します。共通開発ルールは[AGENTS.md](../AGENTS.md)、概要・セットアップ・データモデルは[README.md](../README.md)を参照してください。記述は更新時点の状況です。開始時に実際のGit・ソース・テストと照合してください。

## 現在の状況とGit

- Phase 1・2の実装と下記環境での検証が完了しています。Phase 3〜5のUI／機能開発は未着手です。
- 作業ブランチ：`feature/phase2-trace-ui`。
- Phase 2実装コミット：`9e75e2965cba39f29f98ad94b3e689bd5cc0911c`（`feat: implement Phase 2 Trace UI`）。12ファイルの実装・テスト・README更新を含みます。
- Phase 1基準：`6ca5109e08a0d7e2779c6d028e1ed8c44e858a8b`（`Complete Phase 1 Room foundation and Japanese README`）。ローカル`main`と`origin/main`はこのコミットです。
- リモート：`git@github.com:mizukiflatvrc-arch/self_understanding.git`（Public）。2026-10-02に`git ls-remote --heads origin`で確認した実際のリモートも、上記Phase 1コミットの`main`のみでした。
- Phase 2はローカル作業ブランチにのみ反映済みです。Push・PR作成・mainへのマージは未実施かつ今回の許可対象外です。
- この文書を含む文書整備は、Phase 2コミットの後に別コミット`docs: add agent instructions and project status`としてまとめる作業です。文書自身のコミットIDはここへ埋め込まず、`git log -1`と`git status --short --branch`で実際のHEAD／作業ツリーを確認してください。
- 文書整備コミット後の想定状態は未コミット変更なしです。変更が存在する場合は、新しい変更か文書整備の未完了かを実差分で確認し、独断で破棄・コミットしないでください。

## フェーズ計画

| フェーズ | 計画 | 状況 |
|---|---|---|
| 1 | Room基盤：Entity、Converter、DAO、Repository、UUID、論理削除、順序・Transaction、Index、Schema、基本テスト | 実装・検証完了 |
| 2 | 本番DB接続、Trace一覧・作成・最小限の詳細への遷移 | 実装・ビルド・JVM／API 36実機検証完了。下記未検証項目あり |
| 3 | Trace詳細・Node管理：追加、編集、途中挿入、論理削除、7種類のNodeType選択、UNKNOWN初期選択 | 未着手。対応する保存APIはPhase 1で実装済み |
| 4 | Traceの終了操作・終了状態の表示 | UI未着手。終了日時設定APIはPhase 1に存在 |
| 5 | 解析用JSON生成・Androidクリップボードへのコピー | 未着手 |

計画を無断で拡張・変更しません。LLM分析は構想段階で、どの完了フェーズにも実装済みとして含めません。

## 実装済みの機能と責務

### Phase 1：データ操作層

- Trace／TraceNodeのEntity、Room Database v1、TypeConverter、DAO、Repository。
- UUIDのString ID、1対多、物理CASCADE DELETEなし、論理削除。
- Trace削除時に子Nodeの削除日時を変更しない一方、通常取得とNode変更では親Traceの有効性も確認。
- 1始まりのNode順序、有効件数に基づく挿入位置検証、Transaction内の途中挿入と削除後の再採番。
- 必須の7種類のNodeType（UNKNOWNを含む）、`occurredAt`のNULL保持とKeep／Set更新。
- Roomスキーマエクスポート、Instantを利用するためのAPI 24互換性設定。

### Phase 2：アプリ接続と画面

- `SelfUnderstandingApplication.kt`がapplication contextで本番`self_understanding.db`とRepositoryをlazy提供。破壊的MigrationやメインスレッドDBアクセスは有効にしていません。
- `MainActivity.kt`がActivityスコープの`TraceViewModel`を生成。手動の依存提供を使用し、Navigationライブラリ・大規模DIフレームワークは未導入。
- `TraceViewModel.kt`がRepository呼び出し、Room Flowの一覧更新、読み込み／空／エラー／保存中の状態、再試行を管理。選択ID・ダイアログ表示・下書きはSavedStateHandleに保持し、プロセス復元時に保存を自動再実行しません。
- `TraceApp.kt`がCompose / Material 3で一覧・作成ダイアログ・最小限の詳細を表示。DAOを直接操作しません。
- 一覧は`createdAt DESC`。タイトルをtrimして保存し、空文字を許可。表示時だけ「無題のTrace」を使用し、保存値を書き換えません。
- 開始日時は既存Repositoryが保存時に設定。作成成功後は対象IDの詳細へ直接遷移し、タイトルと開始日時を表示。
- Androidの戻る操作、保存中の二重実行防止、失敗時の下書き保持、不明・削除済みIDの安全な処理。
- 詳細は選択・状態復元・再試行時に単発取得します。表示中の詳細を継続的にDB監視する構成ではありません。

Phase 2でEntity・DAO・Repository・DB構造・スキーマv1・`.gitignore`は変更していません。Node入力・編集・削除UI、Trace終了UI、JSON出力はありません。詳しいデータ仕様・依存バージョンはREADMEを参照してください。

## 最新の検証結果

以下は2026-10-02のPhase 2検証の記録です。今回の文書整備だけのためにアプリのビルド・テストを再実行したものではありません。

| 分類 | 実行・結果 |
|---|---|
| ビルド | `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`成功。Debug APK／テストAPKを生成 |
| JVM自動テスト | 11件成功、失敗・スキップ0。既存5件＋ViewModel追加6件。ローカルXML結果も文書整備時に照合済み |
| API 36実機自動テスト | AndroidJUnitRunnerで15件成功、12.504秒。Phase 1既存9件＋Compose UI 5件＋専用ファイルDB永続化1件 |
| 本番アプリの手動操作 | ADB操作で起動・空一覧・空タイトル作成・無題表示・開始日時・作成直後の詳細・戻る・一覧反映・一覧からの再選択を確認 |
| 回転・再起動 | 作成ダイアログ／入力下書き／選択中の詳細の回転時保持を確認。force-stop後の起動でも保存済みTraceが一覧に残ることを確認 |
| Room | KSP生成成功。v1スキーマJSONはPhase 2前とバイト単位で一致。DB version 1を維持 |

JVM結果：`app/build/test-results/testDebugUnitTest/`、HTML：`app/build/reports/tests/testDebugUnitTest/index.html`。生成物はGit管理対象外です。

端末が当初offline／unauthorizedだったため、Gradleの`connectedDebugAndroidTest`は`No online devices found`で失敗しました。その後、`adb install -r`で両APKをデータ保持してインストールし、`adb shell am instrument -w com.example.self_understanding.test/androidx.test.runner.AndroidJUnitRunner`で15件の成功を確認しました。この成功は直接実行のコンソール結果であり、Gradle connected HTMLレポートの成功ではありません。

追加UIテストは実Compose／ViewModelとFake Repositoryを使用し、本番DBの故障や削除を再現していません。保存失敗・再試行・二重保存防止・不明／削除済みIDは自動試験です。永続化試験はUUID付き専用ファイルDBのclose→reopenです。本番Application・MainActivity・DB接続は上記手動操作で確認しました。

スキーマJSONの比較基準SHA-256：`3609a469fbbe6a489c0d882050a068b909ca2a81989bbb8b852133de9088125d`。Gitの改行変換を考慮し、将来の比較は同じ環境・基準で行ってください。

## 未検証事項・既知の制約

- API 24端末での動作は未検証です。Minimum SDKとdesugaring設定はコードで確認済みです。
- OSによるプロセス破棄後のSavedState復元は未検証です。JVMでのSavedStateHandle復元・保存再実行なし、実機回転、force-stop再起動後の永続化は別の検証です。
- ADB接続が断続的にofflineになりました。端末試験前に接続・認証を再確認してください。
- 既存の`android.disallowKotlinSourceSets=false`のexperimental警告、一部ネイティブライブラリのdebug symbol strip警告があります。上記ビルドの成功を妨げませんでした。
- 検証用の空タイトルTrace 1件が端末内に残っています。既存アプリデータを消去していません。個人的な記録をGitに取り込まないでください。
- 旧データの時刻補完値・sequence重複や欠番を自動修復する機能はありません。互換性試験の成功は、利用者の実データを検査・修復したという意味ではありません。

## 次の作業と承認状況

次の開発作業はPhase 3の設計・認識合わせです。ただし、今回の文書整備ではPhase 3の調査・実装を開始していません。

次セッションでは、まずAGENTS、本文書、READMEと実際のGit・コード・テストを確認し、今回の2コミットがローカルに存在すること、main／リモートへの反映状況、未コミット変更の有無を照合してください。そのうえでユーザーがPhase 3の調査を依頼した場合に、次の範囲の計画を提示します。

- 既存Trace詳細にNodeの追加・編集・途中挿入・論理削除UIを追加する計画。
- 7種類のNodeType選択、UNKNOWNを初期選択とし、分類できなくても記録できる設計。
- 既存Repositoryと保存仕様の再利用、ライフサイクル・エラー・データ保全、必要なテストと文書更新。

Phase 3の実装開始は未承認です。調査・計画の提示後に明示的な承認を得てから実装してください。Phase 4・5、LLM、クラウド等を先取りしません。Phase 2／文書コミットのPush・PR・マージも別途承認が必要です。
