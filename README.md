# self_understanding

自分の思考・感情・身体の反応・行動を、実行トレースのように記録して振り返るAndroidアプリです。

性格や能力を固定的に分類するのではなく、「その状況で何が起こったか」を残す自己理解ツールを目指しています。内容や発生時刻が不完全でも記録できるように設計しています。

現在は **Phase 2：Trace一覧・作成・最小限の詳細** を実装しています。本番Room DBをアプリに接続し、JVMテストとビルドは成功しています。API 36実機で自動テストと本番画面の操作確認も完了しています。

## 技術スタック

| 項目 | 設定 |
|---|---|
| 言語・UI | Kotlin / Jetpack Compose / Material 3 |
| package・applicationId | `com.example.self_understanding` |
| Minimum SDK | API 24 |
| Compile / Target SDK | API 37 / API 36 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin Composeプラグイン | 2.2.10 |
| Room | 2.8.4 |
| KSP | 2.2.10-2.0.2 |
| Gradle Wrapper | 9.4.1 |
| Gradle JVM toolchain | JDK 21 |
| Java source / target | 11 |
| Core library desugaring | `desugar_jdk_libs:2.1.5` |
| ID | UUIDのString表現 |
| Lifecycle Compose / ViewModel / SavedStateHandle | 2.11.0 |
| テスト | JUnit 4 / AndroidJUnit4 / Compose UI test / Coroutines test 1.9.0 |

AGPの組み込みKotlin構成を使用しています。`Instant`をAPI 24以降で利用するため、core library desugaringを有効にしています。

## 開発環境とビルド

Android Studioでプロジェクトのルートを開き、Gradleを同期してください。JDK 21、Android SDK Platform 37、必要なSDK Build Toolsを用意します。初回の同期・ビルドでは依存ライブラリの取得にネットワーク接続が必要です。

SDKの場所はAndroid Studioが作成する`local.properties`の`sdk.dir`で設定します。このファイルにはローカル環境のパスが入るため、Git管理対象には含めません。

Windows PowerShellでのビルド：

```powershell
# 必要な場合のみ、インストール済みのJDK 21の場所を指定
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APKは`app/build/outputs/apk/debug/app-debug.apk`に生成されます。

JVM単体テスト：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

端末・エミュレータでのRoom統合テスト：

```powershell
# adb devicesで使用する端末がdevice状態になっていることを確認
.\gradlew.bat :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

端末テストではアプリとテストAPKをインストールします。通常のテストはインメモリDBを使い、旧スキーマの互換性テストだけがUUID付きの専用DBを作成し、終了後にそのDBを削除します。

テストレポート：

- JVM：`app/build/reports/tests/testDebugUnitTest/index.html`
- Android：`app/build/reports/androidTests/connected/debug/index.html`

## 現在の機能

データ操作層では次の機能を実装しています。

- Traceの作成・取得・終了日時の設定・論理削除
- Nodeの取得・末尾追加・途中挿入・編集・論理削除
- 7種類のNodeTypeの保存と復元
- 発生時刻のNULL保存、維持、明示的な変更
- Node削除後の有効Nodeのsequence再整理
- 削除済みTraceとNodeの通常取得からの除外
- 削除済みTraceへのNode追加・編集・途中挿入の拒否

## 画面と使い方

起動すると、有効なTraceの一覧が新しく作成した順（`createdAt DESC`）に表示されます。記録がない場合は空状態、取得中は読み込み状態、失敗した場合はエラーと再試行を表示します。

1. 「新しいTrace」を選び、作成ダイアログを開きます。
2. タイトルを入力します。空のままでも保存できます。
3. 「保存」を選ぶと端末内に保存し、そのTraceの詳細を開きます。
4. 詳細ではタイトルと開始日時を表示します。「一覧に戻る」またはAndroidの戻る操作で一覧へ戻れます。

タイトルの前後の空白は保存前に除去します。空文字は空文字として保存し、一覧・詳細の表示時だけ「無題のTrace」と表示します。開始日時は既存Repositoryが設定する保存時刻です。

保存中は入力・保存・取消・ダイアログ閉鎖を無効化し、ViewModelでも重複保存を防ぎます。失敗時は入力を保持して再試行できます。存在しない・削除済みのTrace IDは「Traceが見つかりません」と表示し、クラッシュせず一覧へ戻れます。

選択ID、作成ダイアログの表示状態、タイトル下書きは`SavedStateHandle`に保持します。保存処理自体はプロセス復元時に自動再実行しません。Node管理、Traceの終了操作・終了状態表示、JSONエクスポートは未実装です。



## アーキテクチャ

```text
app/src/main/java/com/example/self_understanding/
├── SelfUnderstandingApplication.kt  # 本番DBとRepositoryをlazy提供
├── MainActivity.kt                  # ActivityスコープのViewModelを生成
├── domain/
│   ├── model/
│   │   ├── TraceNodeType.kt
│   │   └── OccurredAtUpdate.kt
│   └── repository/TraceRepository.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── converter/Converters.kt
│   │   ├── dao/TraceDao.kt
│   │   ├── dao/TraceNodeDao.kt
│   │   └── entity/
│   │       ├── TraceEntity.kt
│   │       └── TraceNodeEntity.kt
│   └── repository/TraceRepositoryImpl.kt
└── ui/
    ├── trace/
    │   ├── TraceViewModel.kt          # 状態、イベント処理、Factory
    │   └── TraceApp.kt                # 一覧、作成ダイアログ、最小限の詳細
    └── theme/
```

Applicationがapplication contextで`self_understanding.db`とRepositoryをプロセス単位でlazy生成します。本番DBはアプリ専用領域に保存します。画面からDAOを直接操作せず、ActivityスコープのViewModelが既存Repositoryを呼び出します。Repositoryの返却型には現在もRoom Entityを使用しています。

一覧はRoom Flowで更新され、画面は`collectAsStateWithLifecycle()`で状態を収集します。作成・詳細取得は`viewModelScope`からsuspend APIを利用します。DBのメインスレッドアクセスや破壊的Migrationは有効にしていません。

詳細は選択・状態復元・再試行時に`getTraceById`で取得します。保存直後に一覧Flowが更新されていなくても、新しいTraceを直接取得できます。選択IDを状態として保持して2画面を切り替えるため、Navigationライブラリや大規模DIフレームワークは追加していません。

初期化・一覧・詳細・保存の失敗には一般的なエラー表示と再試行を用意しています。入力内容や内部例外の詳細をログへ出力しません。

## データモデル

### TraceEntity

一つの記録のまとまりです。

| フィールド | 型 | 意味 |
|---|---|---|
| id | String | UUID |
| title | String | タイトル。空文字も保存可能 |
| startedAt | Instant | 記録の開始日時 |
| endedAt | Instant? | 終了日時。未終了はNULL |
| createdAt | Instant | 作成日時 |
| updatedAt | Instant | 更新日時 |
| deletedAt | Instant? | 論理削除日時。未削除はNULL |

### TraceNodeEntity

Traceに所属する個々の記録です。TraceとNodeは1対多です。

| フィールド | 型 | 意味 |
|---|---|---|
| id | String | UUID |
| traceId | String | 所属するTraceのID |
| sequence | Int | Trace内での表示・取得順。1始まり |
| type | TraceNodeType | 必須の分類 |
| content | String | 内容。空文字も保存可能 |
| occurredAt | Instant? | 発生日時。不明な場合はNULL |
| createdAt | Instant | 作成日時 |
| updatedAt | Instant | 更新日時 |
| deletedAt | Instant? | 論理削除日時。未削除はNULL |

`sequence`と`occurredAt`は独立しています。時刻が不明なNodeや、時刻順に並んでいないNodeも保存できます。

Instantはepoch millisecondsのINTEGER、NodeTypeはenumの名前のTEXTとして保存します。時刻の精度はミリ秒です。

### TraceNodeType

| 値 | 記録する内容 |
|---|---|
| SITUATION | 状況・出来事 |
| THOUGHT | 思考 |
| EMOTION | 感情 |
| BODY | 身体の反応 |
| ACTION | 行動 |
| RESULT | 結果 |
| UNKNOWN | 分類が不明・未確定の内容 |

`type`はNULLにせず、分類できない場合はUNKNOWNを指定します。UNKNOWNを画面の初期選択にする機能はPhase 3で実装予定です。

### 発生時刻の編集

作成時に渡した`occurredAt = null`は、そのままNULLとして保存します。現在時刻による補完は行いません。

編集時は`OccurredAtUpdate`で操作を区別します。

```kotlin
// repositoryの型はTraceRepository。suspend関数内で使用する。
repository.updateNode(nodeId, TraceNodeType.UNKNOWN, "内容を修正")
// 省略時はKeep：既存の発生時刻を維持

repository.updateNode(
    nodeId, TraceNodeType.UNKNOWN, "発生時刻は不明",
    OccurredAtUpdate.Set(null)
)
// 明示的にNULLへ変更

repository.updateNode(
    nodeId, TraceNodeType.ACTION, "発生時刻を指定",
    OccurredAtUpdate.Set(instant)
)
```

### 順序・トランザクション・エラー

- `addNode`の`sequence`省略時は末尾へ追加します。
- 明示的な挿入位置は、1〜有効Node件数＋1です。削除済みNodeは件数に含めません。
- 範囲外は`IllegalArgumentException`を返し、位置の補正やDB変更は行いません。
- 欠落・削除済みのTraceへの追加や、その配下のNode編集は`IllegalStateException`を返します。
- Node編集・削除の対象が欠落または削除済みの場合も`IllegalStateException`を返します。
- Traceの終了・論理削除の対象が欠落または削除済みの場合も`IllegalStateException`を返します。
- 末尾追加のsequence決定とINSERT、途中挿入のsequenceシフトとINSERT、Node論理削除と再採番は、それぞれ同一トランザクションです。
- 順序変更は対象Traceの有効Nodeに限定します。
- Node編集は編集対象の列だけを更新し、sequenceやdeletedAtを古いEntityの値で上書きしません。

### 論理削除・スキーマ管理

物理削除APIと物理CASCADE DELETEは使用しません。外部キーの削除動作は`NO_ACTION`です。

Traceを論理削除しても、所属Nodeの`deletedAt`は変更しません。通常のNode取得では親Traceが未削除であることも確認します。Trace復元機能は未実装です。

Nodeの順序取得に対応する複合インデックスは`(traceId, deletedAt, sequence)`です。

DBのversionは **1** です。スキーマは以下にエクスポートします。

```text
app/schemas/com.example.self_understanding.data.local.AppDatabase/1.json
```

UNKNOWN追加では保存形式やDB構造を変えず、versionを維持しました。将来スキーマを変更する場合は、履歴を保持し、既存データを維持するMigrationを定義・検証してください。

## 検証結果

### Phase 2：2026-10-02

| 検証 | 結果 |
|---|---|
| Debug APKビルド | 成功 |
| AndroidテストAPKビルド | 成功 |
| JVM単体テスト | 11件成功（既存5件＋追加6件）、失敗・スキップ0 |
| Roomコード生成 | KSPで成功 |
| スキーマ比較 | 再生成したv1 JSONがPhase 2着手前とバイト単位で一致 |
| Android端末テスト | API 36実機で15件成功（既存9件＋追加6件） |
| 実機での手動操作 | API 36で本番アプリの起動・作成・詳細・戻る・回転・再起動後の保存を確認 |

追加したJVMテストで、自動的に確認した項目：

- Loading・空・一覧・更新・エラーと再試行
- タイトルのtrimと空文字保存、作成後の対象ID・詳細への切り替え
- 保存処理の二重実行防止、保存中のダイアログ閉鎖防止
- 保存失敗時の入力保持と再試行
- 不明・削除済みTrace、詳細取得失敗と再試行
- 選択ID・ダイアログ・下書きの復元、保存の自動再実行がないこと

新しいAndroidテストは、Fake RepositoryによるUIテスト5件と、専用ファイルDBの再オープンによる永続化テスト1件です。既存9件と合わせて15件すべてがAPI 36実機で成功しました。UIテストは本番DBに接続せず、永続化テストはUUID付き専用DBだけを作成・削除します。

最初のconnectedDebugAndroidTestは端末がoffline／unauthorizedのため失敗しました。再接続後、アプリとテストAPKをadb install -rでデータを保持してインストールし、AndroidJUnitRunnerをam instrument -wで直接実行しました。結果は「OK (15 tests)」、所要時間12.504秒です。この再実行はGradleのconnected HTMLレポートを生成する経路ではありません。

本番アプリをADBで手動操作し、空一覧からの空タイトル作成、無題表示、開始日時表示、作成後の詳細遷移、Androidの戻る操作、一覧への反映、再起動後の保存維持、一覧からの再選択を確認しました。画面回転では作成ダイアログと入力途中のタイトル、および選択中の詳細画面が維持されました。回転設定は検証前の値へ戻しました。検証用の空タイトルTrace 1件は端末内に残しています。アプリデータの消去は行っていません。

保存失敗・再試行・二重保存防止・不明／削除済みIDは自動テストで検証しています。本番DBに意図的な故障や削除は加えていません。OSによるプロセス破棄後のSavedState復元とAPI 24端末での動作は未検証です。force-stop後の再起動による保存維持と、SavedStateHandleのJVM復元試験は、それぞれ区別しています。

### Phase 1の過去の結果：2026-10-01

JVM5件、API 36実機9件、APKビルド、KSP生成、旧v1 DDL・identity hash・既存6種の保存値を使った互換性テストが成功しています。Phase 2でも既存JVM5件と既存実機9件を再実行し、すべて成功しました。

互換性テストはテストDBで行っており、利用者の実データを検査・修復したものではありません。過去に保存された不明時刻の補完値や、sequenceの重複・欠番は自動修復しません。

既存の`android.disallowKotlinSourceSets=false`のexperimental警告、および一部ネイティブライブラリのdebug symbolをstripできない警告が出ましたが、今回のAPK・テストAPKビルドは成功しています。

## 開発フェーズ

| フェーズ | 内容 | 進捗 |
|---|---|---|
| Phase 1 | Room、Entity、Converter、DAO、Repository、UUID、論理削除、順序、トランザクション、Index、Schema、基本テスト | 実装・上記環境での検証完了 |
| Phase 2 | 本番DB接続、Trace一覧・作成・詳細への画面遷移 | 実装・ビルド・JVM／API 36実機での検証完了（上記未検証項目を除く） |
| Phase 3 | Node追加・編集・途中挿入・論理削除、7種類の選択、UNKNOWN初期選択 | UI未実装。保存操作はPhase 1に実装済み |
| Phase 4 | Traceの終了・終了状態の表示 | UI未実装。終了日時設定APIは既存 |
| Phase 5 | 解析用JSON生成・クリップボードへのコピー | 未実装 |

Phase 2の追加依存はLifecycleのruntime-compose／viewmodel-compose／viewmodel-savedstate（2.11.0）と、JVMテスト用kotlinx-coroutines-test（現在のcoreと同じ1.9.0）です。Phase 1のRepository・Entity・DAO・スキーマv1は変更していません。

次のPhase 3ではNode管理UIを追加する計画です。Phase 3以降にはまだ着手していません。

将来はLLMがUNKNOWNなどの記録に対して分類候補や振り返りを提示する構想があります。LLM解析は現在未実装です。推測された分類で元のNodeTypeを自動更新する設計にはしません。

投薬情報を管理する機能は実装対象に含めません。
