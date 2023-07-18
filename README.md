# Eclipse Open VSX

[![Join the chat at https://gitter.im/eclipse/openvsx](https://badges.gitter.im/eclipse/openvsx.svg)](https://gitter.im/eclipse/openvsx)
[![CI](https://github.com/eclipse/openvsx/workflows/CI/badge.svg)](https://github.com/eclipse/openvsx/actions?query=workflow%3ACI)

Open VSX是[Visual Studio Marketplace](https://marketplace.visualstudio.com/vscode)的[中立供应商](https://projects.eclipse.org/projects/ecd.openvsx) 开源替代品。它提供了一个在数据库中管理[VS Code 扩展的服务器应用程序](https://code.visualstudio.com/api) , 一个类似于 VS Code Marketplace 的 Web 应用程序以及一个类似于[vsce](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#vsce)的用于发布扩展的命令行工具。

 Open VSX 的公共实例正在[open-vsx.org](https://open-vsx.org/)上运行。请在[EclipseFdn/open-vsx.org](https://github.com/EclipseFdn/open-vsx.org)报告与实例相关的问题。

## 入门

有关此项目的一般概念和用法的文档，请参阅[openvsx Wiki](https://github.com/eclipse/openvsx/wiki) 

## 发展
[![Run on Cloud Studio](https://cs-res.codehub.cn/common/assets/icon-badge.svg)](https://cloudstudio.net#https://github.com/cloudstudio-platform/openvsx)

### 命令行

 * `yarn build` &mdash; 构建库和 `ovsx` 命令
 * `yarn watch` &mdash; 观看(持续构建)
命令行工具位于`cli/lib/ovsx`.

### 网页界面

默认前端是捆绑在 Docker 镜像中的前端，也用于在开发环境中进行测试。它取决于编译的库，因此请确保在构建或观察默认前端之前构建或观察库。

 * `yarn build` &mdash; 建立 library
 * `yarn watch` &mdash; 观看（持续构建）
 * `yarn build:default` &mdash; 构建默认前端（运行 webpack）
 * `yarn watch:default` &mdash; 在监视模式下运行 webpack
 * `yarn start:default` &mdash; 启动 Express 以在 port 3000 上为前端提供服务

 Express 服务器在 Gitpod 中自动启动。通常不需要重新启动。
 
### 服务器

 * `./gradlew build` &mdash; 构建并测试服务器
 * `./gradlew assemble -t` &mdash;  持续构建（每次更改后服务器都会重新启动）
 * `./gradlew runServer` &mdash;在 port 8080 上启动 Spring 服务器
 * `./scripts/test-report.sh` &mdash; 显示 port 8081 上的测试结果

Spring服务器在Gitpod中自动启动。它包括 `spring-boot-devtools` 检测编译的类文件中的更改并重新启动服务器。

### OAuth 设置

如果您想通过 GitHub 测试授权，则需要 [创建一个OAuth app](https://developer.github.com/apps/building-oauth-apps/creating-an-oauth-app/) ，其回调 URL 指向 Gitpod 工作区公开的port 8080。您可以通过调用脚本来获取它：

```
server/scripts/callback-url.sh github
```

请注意,每当您创建新的Gitpod工作区时,都需要在[GitHub 上更新](https://github.com/settings/developers) 回调URL

创建 GitHub OAuth app后，下一步是将Client ID和 Client Secret复制到名为`GITHUB_CLIENT_ID` 和 `GITHUB_CLIENT_SECRET`并绑定到此存储库的 [Gitpod environment variables](https://www.gitpod.io/docs/environment-variables/) 如果更改正在运行的工作区中的变量，请在目录中运行以更新应用程序属性。

完成这些设置后，您应该能够通过授权 OAuth app来登录。

### Google Cloud 设置

如果您想通过 Google Cloud 测试文件存储，请按照以下步骤操作：

 * 创建一个[GCP](https://cloud.google.com/) 项目和一个 bucket
 * 通过向`allUsers`授予"Storage Object Viewer"角色公开 bucket
 *  `"*"`使用origin和method在 bucket上 [配置 CORS](https://cloud.google.com/storage/docs/configuring-cors#configure-cors-bucket)  `"GET"`.
 * 创建名为 `GCP_PROJECT_ID` 和 `GCS_BUCKET_ID` 含您的 GCP 项目和bucket identifiers。如果更改正在运行的工作区中的变量，请 `scripts/generate-properties.sh` 在 `server` 目录中运行以更新应用程序属性。
 * 创建一个具有“存储对象管理员”角色的 GCP 服务帐户，并将其凭据文件复制到您的工作区中。
 * `GOOGLE_APPLICATION_CREDENTIALS` 创建包含凭据文件路径的环境变量。


### Azure 设置

如果您想通过 Azure Blob 测试文件存储，请按照以下步骤操作：

 * 创建一个文件[存储账户](https://portal.azure.com/) 和一个名为`openvsx-resources`的容器(如果更改属性,则可以使用不同的名称 `ovsx.storage.azure.blob-container`)
 * 允许存储帐户中的 Blob 公共访问，并将容器的公共访问级别设置为“Blob”。
 * `"*"`使用origin,method`"GET"` 和允许的标头`"x-market-client-id, x-market-user-id, x-client-name, x-client-version, x-machine-id, x-client-commit"`在存储账户中配置CORS
 * `AZURE_SERVICE_ENDPOINT` 使用存储账户的"Blob service" URL创建环境变量,如果更改正在运行的工作区中的变量,请`scripts/generate-properties.sh`在 `server` 目录中运行以更新应用程序属性。
 *生成"Shared access signature" 并将其令牌放入环境变量中`AZURE_SAS_TOKEN`.

如果您还想通过 Azure Blob 测试下载计数，请按照以下步骤操作：

* 创建用于诊断日志记录的附加[storage account](https://portal.azure.com/) for diagnostics logging.
  * 重要提示：设置与文件存储帐户相同的位置（例如北欧）。
  * 禁用 Blob 公共访问。
* 在文件存储帐户中
  * 打开诊断设置 (`Monitoring` -> `Diagnostic settings (preview)`).
    * 单击 `blob`.
    * 单击 `Add diagnostic setting`.
    * 选择 `StorageRead`, `Transaction` 和 `Archive to a storage account`.
    * 选择 您在上一步中创建的诊断存储帐户`Storage account`.
* 您在上一步中创建的诊断存储帐户
  * 导航至 `Data Storage`-> `Containers`
    *  `insights-logs-storageread` 容器应该已添加（可能需要几分钟，您可能需要进行一些测试下载，否则不会创建容器）。
    * 为容器创建"Shared access token"`insights-logs-storageread` 
      * 单击 `insights-logs-storageread` 容器
        * 单击`Settings` -> `Shared access token`
          * 必须有 `Read` 和 `List` 权限
          * 将到期日期设置为合理的值
          * 将 "Allowed IP Addresses"设置为服务器的 IP 地址。
  * 转到`Data Management`-> `Lifecycle management`
    * 创建规则，以便日志不会堆积并且下载计数服务保持高性能。
    * 选择 `Limit blobs with filters`, `Block blobs` and `Base blobs`.
    * 选择天数（例如 7）。
    * 输入 `insights-logs-storageread/resourceId=` blob 前缀以将规则限制到`insights-logs-storageread` 容器
* 您需要在服务器环境中添加两个环境变量
  * `AZURE_LOGS_SERVICE_ENDPOINT` 与诊断存储帐户的“Blob service”URL。URL 必须以斜杠结尾！
  * `AZURE_LOGS_SAS_TOKEN` 以及容器的共享访问令牌`insights-logs-storageread` 
  * 如果更改正在运行的工作区中的变量，请 `scripts/generate-properties.sh` 在 `server` 目录中运行以更新应用程序属性。

## 执照

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
