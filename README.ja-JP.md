<div align="center">

[简体中文](./README.md) | [English](./README.en-US.md) | **日本語**

# XnetAIops

**インフラ、サービス、Kubernetes を統合するオープンソース AIOps**

[![GOAI release](https://img.shields.io/badge/GOAI%20release-1.3.0-1677ff.svg)](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[オンラインデモ](https://goai.xnetaiops.synapxnet.online) · [フロントエンド: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/tree/v1.3.0) · [OpenXnet](https://openxnet.synapxnet.com) · [ライセンス](./LICENSE)

</div>

## GOAI 決勝版 · v1.3.0

[リリース・チェックサム](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0) · [ソース ZIP](https://github.com/synapxnet/XnetAIops/releases/download/v1.3.0/XnetAIops-v1.3.0-source.zip) · [v1.3.0 ソース](https://github.com/synapxnet/XnetAIops/tree/v1.3.0) · [対応する XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/releases/tag/v1.3.0) · [OpenXnet デスクトップ](https://github.com/synapxnet/OpenXnet/releases/tag/v1.3.0)

> 既定の `display` ブランチには過去のコードが残っています。バッジは GOAI リリースを示します。v1.3.0 の利用にはタグまたはリリース添付ファイルを選択してください。

プラットフォーム常駐 Agent がインフラの証拠を提供し、OpenXnet と AgentTeams が共同作業を調整します。承認検証、リソース版、冪等性、チェックポイントで実行境界を管理し、読み取りサービスと実行サービスを分離します。

依存関係、検証結果、制限は[プログラム検証基準・構築・配布の説明](https://github.com/synapxnet/XnetAIops/blob/0ccbeab11ea0903c9e1eec55ad36543f726d56e6/docs/GOAI-FINALS-V1.3.0-SOURCE-DELIVERY.md)を参照してください。この公開はオンライン環境の再デプロイを意味しません。

> 以下は旧版の画面例です。v1.3.0 の UI 検証画像ではありません。

![XnetAIops 3D クラスター概要](./docs/images/xnetaiops-overview-2026.png)

## 画面プレビュー

| デモログイン | プロジェクト情報 |
| --- | --- |
| ![デモログイン](./docs/images/xnetaiops-login.png) | ![XnetAIops について](./docs/images/xnetaiops-about.png) |
| クラスター管理 | ホスト登録 |
| ![クラスター管理](./docs/images/xnetaiops-clusters.png) | ![ホスト登録](./docs/images/xnetaiops-host-add.png) |
| Kubernetes | サービス編成 |
| ![Kubernetes](./docs/images/xnetaiops-kubernetes.png) | ![サービス編成](./docs/images/xnetaiops-service.png) |
| 監視とアラート | イメージレジストリ |
| ![監視](./docs/images/xnetaiops-monitor-2026.png) | ![レジストリ](./docs/images/xnetaiops-registry.png) |
| マルチテナントユーザー | Kubernetes ノード |
| ![ユーザー](./docs/images/xnetaiops-users.png) | ![Kubernetes ノード](./docs/images/xnetaiops-k8s-nodes.png) |
| Kubernetes 名前空間 | Kubernetes ワークロード |
| ![Kubernetes 名前空間](./docs/images/xnetaiops-k8s-namespaces.png) | ![Kubernetes ワークロード](./docs/images/xnetaiops-workloads-2026.png) |

## 概要

XnetAIops は **SynapXnet チーム**が開発・公開する運用管理プラットフォームです。サーバー、ミドルウェア、業務サービス、Kubernetes、監視、イメージレジストリを一つのワークスペースで管理できます。

本リポジトリはバックエンドです。[XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/tree/v1.3.0) と組み合わせることで、企業向けのマルチテナント、フロントエンド・バックエンド分離システムを構成します。

## 特長

- **企業向けマルチテナント:** ユーザー、ロール、チーム、リソース境界を統合管理。
- **フロントエンド・バックエンド分離:** 既存ゲートウェイやインフラへ柔軟に統合。
- **モジュール構成:** インフラ、サービス、Kubernetes、監視、レジストリを独立して拡張。
- **コンテナ配備:** Maven と Docker Compose による再現可能な導入。
- **継続的な更新:** 自動化、可観測性、セキュリティ、ドキュメントを継続改善。

## モジュール

| モジュール | サービス | 主な機能 |
| --- | --- | --- |
| CLM | `aiops-clm-service` | クラスターライフサイクル、MySQL、Redis、Hadoop、Jenkins |
| HOM | `aiops-hom-service` | ホスト、ラック、SSH 接続、リソース台帳 |
| SVM | `aiops-svm-service` | サービス定義、ロール割当、コマンド、ライフサイクル |
| MON | `aiops-mon-service` | 監視ダッシュボード、アラートルール、履歴、通知 |
| K8S | `aiops-k8s-service` | クラスター、Helm、監視、アラート、アプリ、パイプライン |
| REG | `aiops-reg-service` | レジストリ、プロジェクト、タグ、同期、配備履歴 |
| USR | `aiops-usr-service` | 認証、ユーザー、ロール、アクセス制御 |

## v1.3.0 の取得とビルド

JDK 17、Maven 3.9、MySQL 8.x、Redis 7.x を使用します。バックエンドは Spring Boot 3.4.6 と MyBatis です。過去の `display` ではなく固定 GOAI タグを取得してください。

```bash
git clone --branch v1.3.0 --depth 1 https://github.com/synapxnet/XnetAIops.git
cd XnetAIops
mvn -B -DskipTests package
```

成果物は各モジュールの `target/*-1.3.0.jar` です。このコマンドはビルドのみです。テストと制限は配布説明を参照してください。

### 配備の前提

- 外部 MySQL と Redis を用意し、`XnetAIops.sql`、K8s/DevOps テーブル、`database/migrations` を既存状態と照合して、バックアップ後に必要な初期化・移行を行います。初期化済み DB イメージは同梱しません。
- `.env.example` から DB、Redis、`K8S_ENCRYPTION_KEY` の設定を作成します。USR の `JWT_SECRET` は汎用 Compose では渡されないため、専用 override などで `aiops-usr-service` へ注入してください。`.env` のコピーだけでは不十分です。
- 対応フロントエンドも `v1.3.0` でビルドし、`WEB_DIST_PATH` を `apps/web-antd/dist` へ設定します。Web は `81`、内部 USR は `9185`、CLM/HOM/SVM/MON/K8S/REG は `9181/9182/9183/9184/9186/9187` です。公開アクセスには HTTPS ゲートウェイを使用します。
- 汎用 Compose には常駐 Agent、AgentTeams、承認サービス、reader/checkpoint の分離配備は含まれません。固定版 OpenXnet の常駐サービスを別途配備し、`/api/resident/v1/` を転送します。完全な決勝環境は Compose 一行だけでは再現できません。

設定後、隔離環境で `docker compose up -d --build` と `docker compose ps` を実行します。既存の大会環境を汎用設定で上書きしないでください。

## デモへのアクセス

- 現在の GOAI 入口: <https://goai.xnetaiops.synapxnet.online/#/auth/login>。
- デモ電話番号: `17870171303`。確認コード: `000000`（6 桁、このデモ環境専用）。
- 電話番号と確認コードでログインします。OpenXnet デスクトップのパスワードではありません。この画面は SMS を送信せず、コードはプロジェクトから提供されます。
- 2026-09-18 の確認結果: `goai_operator` / `OPERATOR` でログイン成功。ページ名は `XnetAIops`、常駐 Agent は `platform=aiops`、`agentVersion=1.3.0`、`ONLINE`。
- このコードはログイン専用です。AgentTeams デモアクセスコード、Live 実行承認、モデル API キーとは別であり、後者は公開しません。

API ゲートウェイは同一オリジンの `https://goai.xnetaiops.synapxnet.online` です。ログインは `POST /api/usr/login`、身元確認は `GET /api/usr/user/info`、常駐状態は `GET /api/resident/v1/status`。後二者にはプラットフォームの Bearer トークンが必要です。業務の接頭辞は `/api/clm`、`/api/hom`、`/api/svm`、`/api/mon`、`/api/k8s`、`/api/reg` です。

今回確認したのはログインと読み取り専用の身元・常駐状態です。業務変更やモデル呼び出しは行っていません。`modelConfigured=true` は設定の存在を示し、推論の検証結果ではありません。公開デモの認証を本番環境で使用しないでください。

## コミュニティとライセンス

XnetAIops は [OpenXnet](https://openxnet.synapxnet.com) オープンソースエコシステムの一部です。

[MIT License](./LICENSE) の下で公開されています。Copyright © 2026 SynapXnet.
