<div align="center">

[简体中文](./README.md) | [English](./README.en-US.md) | **日本語**

# XnetAIops

**インフラ、サービス、Kubernetes を統合するオープンソース AIOps**

[![GOAI release](https://img.shields.io/badge/GOAI%20release-1.3.0-1677ff.svg)](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[オンラインデモ](https://www.xnetaiops.synapxnet.cn) · [フロントエンド: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com) · [ライセンス](./LICENSE)

</div>

## GOAI 決勝版 · v1.3.0

[リリース・チェックサム](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0) · [ソース ZIP](https://github.com/synapxnet/XnetAIops/releases/download/v1.3.0/XnetAIops-v1.3.0-0ccbeab1-source.zip) · [v1.3.0 ソース](https://github.com/synapxnet/XnetAIops/tree/v1.3.0) · [対応する XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/releases/tag/v1.3.0) · [OpenXnet デスクトップ](https://github.com/synapxnet/OpenXnet/releases/tag/v1.3.0)

> 既定の `display` ブランチには過去のコードが残っています。バッジは GOAI リリースを示します。v1.3.0 の利用にはタグまたはリリース添付ファイルを選択してください。

プラットフォーム常駐 Agent がインフラの証拠を提供し、OpenXnet と AgentTeams が共同作業を調整します。承認検証、リソース版、冪等性、チェックポイントで実行境界を管理し、読み取りサービスと実行サービスを分離します。

依存関係、検証結果、制限は[ソース配布・構築の説明](https://github.com/synapxnet/XnetAIops/blob/0ccbeab11ea0903c9e1eec55ad36543f726d56e6/docs/GOAI-FINALS-V1.3.0-SOURCE-DELIVERY.md)を参照してください。この公開はオンライン環境の再デプロイを意味しません。

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

本リポジトリはバックエンドです。[XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) と組み合わせることで、企業向けのマルチテナント、フロントエンド・バックエンド分離システムを構成します。

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

## クイックスタート

JDK 17+、Maven 3.9+、Docker Compose、MySQL 8.x、Redis 7.x が必要です。

```bash
mvn -DskipTests package
cp .env.example .env
docker compose up -d --build
docker compose ps
```

演示用データは `sql/xnet_aiops_demo.sql` にあります。到達不能なデモ用アドレスと無効なプレースホルダー認証情報のみを使用し、ユーザー作成データを上書きせず再実行できます。

## デモ

- URL: <https://www.xnetaiops.synapxnet.cn>
- 電話番号: `12345678900`
- 確認コード: `000000`

固定確認コードは公開デモ専用です。本番環境では安全な認証プロバイダーを利用してください。

## コミュニティとライセンス

XnetAIops は [OpenXnet](https://openxnet.synapxnet.com) オープンソースエコシステムの一部です。

[MIT License](./LICENSE) の下で公開されています。Copyright © 2026 SynapXnet.
