<div align="center">

[简体中文](./README.md) | [English](./README.en-US.md) | **日本語**

# XnetAIops

**インフラ、サービス、Kubernetes を統合するオープンソース AIOps**

[![Version](https://img.shields.io/badge/version-1.0.0-1677ff.svg)](https://www.xnetaiops.synapxnet.cn)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[オンラインデモ](https://www.xnetaiops.synapxnet.cn) · [フロントエンド: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com) · [ライセンス](./LICENSE)

</div>

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

## GOAI Competition 1.0.0

`GOAI-Competition` ブランチは、アラート、Kubernetes ワークロード、サービス正常性を取得する読み取り専用 Agent ツールを追加します。すべての呼び出しは共通の `ToolResponse 1.0.0`、Workspace/Incident/Trace コンテキスト、短期かつ単一ツール限定の委任トークンを使用します。

[引き継ぎ、Fixture、API 例、検証結果](./docs/goai-handoff/HANDOFF-GOAI-COMPETITION-1.0.0.md) · [対応する証拠 UI](https://github.com/synapxnet/XnetAIops-web/tree/GOAI-Competition)

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
