# CS209A 作业 2 演示项目

## 运行环境

- **Java JDK**：openjdk-22（Oracle OpenJDK 22.0.2）
- **javafx-fxml**：22.0.1
- **javafx-controls**：22.0.1
- **Maven**：3.8.5

## 架构概览

本项目是一个简单的客户端–服务器版「QQ 农场」小游戏：

- 客户端使用 JavaFX 实现图形界面；
- 后端使用基于 TCP 的文本协议服务器，实现多玩家农场逻辑和断线重连。

### 模块与包结构

- `org.example.demo` —— JavaFX 前端
  - `Application` —— JavaFX 程序入口，创建窗口并加载 `board.fxml` 布局。
  - `Controller` —— 界面控制器，管理 4×4 农场网格、按钮逻辑以及与网络层的交互。
  - `Game` —— 单机模式的游戏逻辑封装（可作为参考实现）。
- `org.example.demo.client`
  - `ClientNetworkService` —— 负责与服务器的 TCP 连接、消息发送/接收和重连逻辑。
- `org.example.demo.server`
  - `FarmServer` —— 多线程服务器，负责监听端口、接收连接，并为每个连接创建 `ClientHandler`。
  - `ClientHandler` —— 单个客户端连接的处理器，解析协议命令并更新对应的 `PlayerState`。
  - `PlayerState` —— 保存单个玩家的农场状态、金币、当前连接等信息。
  - `PlotState` —— 表示服务器端单个地块的状态。

### 数据流（高层视角）

1. **UI → Controller**：用户点击按钮（Plant、Harvest、Steal、Visit、Return、Disconnect、Reconnect）。
2. **Controller → ClientNetworkService**：Controller 校验动作是否合法，然后通过 TCP 发送一行文本命令（例如 `PLANT 1 2`）。
3. **ClientNetworkService → FarmServer**：消息通过 Socket 发送到服务器，由对应的 `ClientHandler` 接收。
4. **FarmServer / ClientHandler**：解析命令，更新 `PlayerState` / `PlotState`，并生成响应消息。
5. **FarmServer → ClientNetworkService**：服务器将 `SUCCESS ...` 或 `FARM ...` 等响应行写回客户端。
6. **ClientNetworkService → Controller**：后台监听线程逐行读取消息，通过 `messageHandler` 在 JavaFX UI 线程中回调给 `Controller`。
7. **Controller → UI**：Controller 更新本地模型（`farmState`、金币、当前查看的玩家等），并刷新 JavaFX 网格界面。

### 连接管理

- 启动时，客户端通过 `ClientNetworkService.connect` 连接到 `localhost:8888`，并弹出登录对话框。
- 如果 Socket 意外关闭，`ClientNetworkService` 会调用注册的 `connectionLostHandler`：
  - `Controller` 会禁用所有操作按钮，并在状态栏显示「已断开连接」提示；
  - **Reconnect**（重连）按钮变为可见。
- 用户点击 **Reconnect** 按钮时：
  - 客户端调用 `ClientNetworkService.reconnect` 建立新连接；
  - Controller 会自动使用上一次成功登录的用户名重新发送 `LOGIN`，尝试恢复原有农场状态。

## 文件说明

- `Application.java`：JavaFX 客户端入口类。
- `Game.java`：单机版游戏逻辑，控制农场行为（可作为逻辑参考）。
- `Controller.java`：JavaFX 控制器，处理界面事件并与网络层交互。
- `ClientNetworkService.java`：负责 TCP 连接、消息发送接收与断线重连。
- `FarmServer.java`、`ClientHandler.java`、`PlayerState.java`、`PlotState.java`：服务器端逻辑与玩家农场状态维护。
- `board.fxml`：JavaFX 界面布局，包括 4×4 网格和操作按钮。
- `styles.css`：JavaFX 样式文件，定义按钮、地块和状态栏的样式。
- `resources`：存放游戏中使用的图片资源（来自 https://www.iconfont.cn/）。

## 文本协议说明

客户端与服务器之间使用基于行的简单文本协议，每条消息为一行，以 `\n` 结尾，字段之间用空格分隔。

### 客户端 → 服务器 命令

- `LOGIN <username>`
  - 用户输入用户名后发送的登录命令；
  - 如果服务器已有该用户的历史状态，将从历史状态继续（支持断线重连）。

- `PLANT <row> <col>`
  - 在给定的 0 基下标 `(row, col)` 处种植作物；
  - 要求该地块为空，且玩家金币足够。

- `HARVEST <row> <col>`
  - 在 `(row, col)` 处收获成熟作物，服务器会更新金币和地块状态。

- `STEAL <targetPlayer>`
  - 访问并尝试从目标玩家的农场中偷取成熟作物。

- `VISIT <friendName>`
  - 仅访问好友农场，不进行偷取行为。

- `RETURN`
  - 从他人农场视图返回自己的农场。

- `PING`
  - 心跳检测，服务器可选地返回 `PONG`。

### 服务器 → 客户端 消息

- `WELCOME ...`
  - 连接建立后发送的一次性欢迎信息。

- `LOGIN SUCCESS` / `LOGIN ERROR <reason>`
  - 登录结果通知；成功时 Controller 会启用操作按钮。

- `SUCCESS <message>`
  - 通用的成功提示消息，用于种植、收获、偷取、访问、返回等操作。

- `ERROR <message>`
  - 通用错误提示，常见原因包括：非法地块、金币不足、地块状态不匹配等。

- `FARM <username> <coins> <cell1> <cell2> ... <cell16>`
  - 返回某玩家完整的农场快照，总是 16 个地块（4×4）。
  - 每个地块使用 `<STATE>[:yield]` 表示，例如：
    - `EMPTY`
    - `GROWING:0`
    - `RIPE:3`
  - 客户端解析后更新本地 `farmState`，并刷新界面。

- `NOTIFY <message>`
  - 通知类消息，例如系统广播、提示信息等。

- `PONG`
  - 对 `PING` 的响应（若实现）。

该协议设计为简单可读，方便通过 `telnet` 或 `nc` 等工具调试。

## 运行方式

### 1. 启动服务器

在项目根目录执行：

```bash
mvn -q -DskipTests package
java -cp target/classes org.example.demo.server.FarmServer
```

以上命令会在本地 `8888` 端口启动服务器，并在控制台输出连接日志。

### 2. 启动客户端（JavaFX 界面）

在另一个终端窗口中，从项目根目录执行：

```bash
mvn -q javafx:run
```

或者，如果你希望直接运行 `Application` 类，请根据本地环境正确配置 JavaFX 模块路径。

### 3. 基本使用流程

1. 启动服务器；
2. 启动客户端；
3. 弹出对话框时输入用户名（例如 `player1`）；
4. 在 4×4 网格中选择一个地块，点击 **Plant** 进行种植；
5. 作物成熟后点击 **Harvest** 收获并获得金币；
6. 使用 **Visit** / **Return** 浏览其他玩家农场并返回；
7. 使用 **Disconnect** 模拟网络中断，随后点击 **Reconnect** 测试断线重连与状态恢复。

## 逻辑说明

- **游戏开始**：连接服务器、登录并初始化 4×4 农场棋盘；
- **操作校验**：Controller 在发送命令前会检查是否选中地块、是否已登录、是否仍然连接到服务器；服务器再次从规则层面验证操作是否合法；
- **结束/反馈**：游戏没有固定「结束」条件，但状态栏会通过 toast 的形式持续反馈成功 / 失败 / 提示等信息。

## 备注

- 建议先完成或理解单机模式（`Game.java`），再在此基础上扩展多人模式或更复杂的交互；
- 如果在渲染多个游戏界面或窗口时遇到 GUI 问题，可以优先检查 `Application` 中的 `start` 方法以及 `board.fxml` 布局；
- 如有问题或发现 bug，可联系：`12442018@mail.sustech.edu.cn` 或 QQ：`503652093`。

