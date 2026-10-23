<!-- Copyright (C) 2026 Synapxnet. All rights reserved.
Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0 | Security Level: INTERNAL -->
# XnetAIOps

[简体中文](README.md) · [English](README.en-US.md)

XnetAIOps は、インフラ運用と権限制御された Agent 実行を提供します。クラスター、ホスト、サービス、監視、Kubernetes、レジストリ、組織権限に加え、常駐 Agent、横断的な証拠収集、承認、独立検証、復旧の機能を維持します。

開発の主ブランチは `main` です。過去のリリースブランチとタグも保持します。

## モジュール

| モジュール | 役割 |
| --- | --- |
| CLM / HOM | クラスター、ホスト、ラック、基盤のライフサイクル |
| SVM / MON | サービス操作、コマンド、監視、アラート |
| K8S / REG | Kubernetes、Helm、パイプライン、イメージ、成果物 |
| USR | ユーザー、ロール、組織、ログイン認証 |
| Agent contract | 委任認証、証拠、計画に紐づく承認、バージョン、冪等性、補償 |

OpenXnet がプラットフォーム間を連携し、各プラットフォームは独自の操作と権限境界を維持します。常駐 Agent と AgentTeams は別サービスとして構成します。

## ビルドとテスト

JDK 17 と Maven 3.9 を使用します。サービスの実行には MySQL 8 と Redis 7 が必要です。

```bash
mvn -B test
mvn -B -DskipTests package
```

成果物は各サービスの `target/` に出力されます。標準 Compose は基本サービスを対象とし、常駐 Agent、AgentTeams、承認サービス、実行ランタイムは別途設定します。

## 設定とログイン

`.env.example` を基に、データベース、Redis、`JWT_SECRET`、`K8S_ENCRYPTION_KEY` を設定します。データベースをバックアップし、`XnetAIops.sql`、K8s/DevOps スキーマ、`database/migrations` を確認して必要な初期化・移行のみを適用してください。`WEB_DIST_PATH` は対応するフロントエンドのビルド先です。

管理者が実際のアカウントと組織所属を登録します。共通アカウントや固定認証コードはありません。`XNET_SMS_GATEWAY_URL` と `XNET_SMS_GATEWAY_TOKEN` を設定します。ゲートウェイは Bearer 認証付き POST の `{phone, code, purpose: "login", expiresInSeconds: 300}` を受け取り、HTTP 2xx と `{"accepted": true}` で送信成功を返します。

空のデータベースには `XnetAIops.sql`、続いて同じデータベース内で `devops-tables.sql` を適用します。既存環境はバックアップ後、必要に応じて `database/migrations/20261023-mainline-organization-access.sql` を適用し、初期化ファイル全体を再実行しないでください。組織データへのアクセスは既定で無効です。初回のみデータベース権限を持つ運用者が、一意の UID、実際の電話番号、新規 BCrypt ハッシュ、`user_type=admin`、`status=active` を指定して管理者を登録します。SMS ログイン後に他のアカウントを管理できます。匿名の初期化 API はありません。

認証コードは有効期間 5 分、送信間隔 60 秒、誤入力上限 5 回で、成功後は再利用できません。未設定時は送信を拒否します。無効化されたアカウントは古い JWT でもユーザー情報や権限を取得できません。

## 実行と証拠

実行には、本人確認、ワークスペース、承認、リソースバージョン、冪等性の検査を適用します。本番証拠は `PRODUCTION` と `synthetic=false` の組み合わせで確認し、隔離演習の証拠とは区別します。`OPENXNET_REHEARSAL_ENABLED` は既定で無効です。実行先が未設定の場合、模擬成功を返しません。

[旧アカウントのアーカイブスクリプト](database/maintenance/archive-competition-identities.sql) は既定でプレビューのみです。対象の確認と個別バックアップ後に実行し、監査主体と業務履歴は保持します。

[移行情報](docs/MAINLINE-TRANSITION.md) · [フロントエンド](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com)

## ライセンス

[LICENSE](LICENSE) と各ファイルの表記を参照してください。
