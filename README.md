# self_understanding

自分の思考・感情・身体の反応・行動を、実行トレースのように記録して振り返るAndroidアプリです。

性格や能力を固定的に分類するのではなく、「その状況で何が起こったか」を残す自己理解ツールを目指しています。内容や発生時刻が不完全でも記録できるように設計しています。

現在は **Phase 1：Roomデータベース基盤** の実装と検証を完了しています。ユーザー向けの記録画面は未実装で、起動画面はEmpty Activityの「Hello Android!」のままです。

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
| テスト | JUnit 4 / AndroidJUnit4 |

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

一覧・入力・詳細画面、画面遷移、終了状態の表示、JSONエクスポートは未実装です。アプリ本番用のDB生成・Repository提供と画面への接続も、今後の作業です。

## アーキテクチャ

```text
app/src/main/java/com/example/self_understanding/
├── MainActivity.kt                  # 現在はテンプレート画面
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
└── ui/theme/
```

`TraceRepository`がアプリから利用するデータ操作の契約です。`TraceRepositoryImpl`がUUIDや更新日時を設定し、DAOがRoomを通してDBを操作します。複数のSQLが必要な順序操作はDAOのトランザクションに集約しています。

現段階ではRepositoryの返却型にRoom Entityを使用しています。ViewModelや画面との接続はまだありません。

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

2026-10-01に以下を確認しました。

| 検証 | 結果 |
|---|---|
| Debug APKビルド | 成功 |
| AndroidテストAPKビルド | 成功 |
| JVM単体テスト | 5件成功（追加4件＋既存1件）、失敗・スキップ0 |
| Android実機テスト | API 36で9件成功（追加8件＋既存1件）、失敗・スキップ0 |
| Roomコード生成 | KSPで成功 |
| スキーマ比較 | 再生成したv1 JSONが変更前とバイト単位で一致 |
| 旧v1互換性 | 旧DDL・identity hash・6種類の既存値を持つ専用DBの読み込み、UNKNOWN追加、再オープンに成功 |

追加テストの対象は、UUID、空内容、7種類の分類、NULL日時、KeepとSet、挿入位置、削除済みNodeを除いた件数、他Traceとの分離、論理削除、親削除後の操作拒否と子データ保持、挿入失敗時のロールバック、20件の並行末尾追加です。

API 24端末での実行は未検証です。互換性テストは固定した旧スキーマのテストDBで行っており、利用者の実データを検査・修復したものではありません。過去に保存された不明時刻の補完値や、既存のsequenceの重複・欠番は自動修復しません。

既存のGradle設定`android.disallowKotlinSourceSets=false`についてexperimental警告が出ますが、今回のビルド・テストは成功しています。

## 開発フェーズ

| フェーズ | 内容 | 進捗 |
|---|---|---|
| Phase 1 | Room、Entity、Converter、DAO、Repository、UUID、論理削除、順序、トランザクション、Index、Schema、基本テスト | 実装・上記環境での検証完了 |
| Phase 2 | Trace一覧・作成・詳細への画面遷移 | 未実装 |
| Phase 3 | Node追加・編集・途中挿入・論理削除、7種類の選択、UNKNOWN初期選択 | UI未実装。保存操作はPhase 1に実装済み |
| Phase 4 | Traceの終了・終了状態の表示 | UI未実装。終了日時設定APIは既存 |
| Phase 5 | 解析用JSON生成・クリップボードへのコピー | 未実装 |

Phase 2では、本番用DBとRepositoryの生成・提供、ViewModel、一覧・作成画面、詳細への遷移を接続します。

将来はLLMがUNKNOWNなどの記録に対して分類候補や振り返りを提示する構想があります。LLM解析は現在未実装です。推測された分類で元のNodeTypeを自動更新する設計にはしません。

投薬情報を管理する機能は実装対象に含めません。
