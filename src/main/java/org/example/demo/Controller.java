// Java
package org.example.demo;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Duration;
import org.example.demo.client.ClientNetworkService;

import java.util.Optional;

/**
 * Minimal JavaFX controller that mirrors last year's style while showing the new mechanics.
 */
public class Controller {

    @FXML
    private GridPane gameBoard;

    @FXML
    private Label coinsLabel;

    @FXML
    private Label playerLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button plantButton;

    @FXML
    private Button harvestButton;

    @FXML
    private Button stealButton;

    @FXML
    private Button visitButton;

    @FXML
    private Button returnButton;

    @FXML
    private Button reconnectButton;

    @FXML
    private Button disconnectButton;

    private ClientNetworkService clientNetworkService;
    private int coins = 40;
    private String currentPlayer;
    private String currentViewPlayer;
    // 保存本次尝试登录的用户名，防止二次弹窗
    private String pendingUsername;
    // 记录最近一次成功登录的用户名，用于断线重连后自动恢复会话
    private String lastSuccessfulUsername;
    private boolean loginDialogOpen = false;

    public enum PlotState { EMPTY, GROWING, RIPE }

    private PlotState[][] farmState = new PlotState[4][4];
    private ToggleButton[][] cells;
    private Timeline refreshTimeline;
    private String statusMessage = "Please login first.";

    private int selectedRow = -1;
    private int selectedCol = -1;
    private int[][] plotYields = new int[4][4]; // track remaining yields
    private int[][] plotGrowSeconds = new int[4][4]; // remaining seconds until ripe (approx)

    public void init(ClientNetworkService clientNetworkService) {
        this.clientNetworkService = clientNetworkService;
        this.clientNetworkService.setMessageHandler(this::handleServerMessage);
        this.clientNetworkService.setConnectionLostHandler(this::onConnectionLost);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                farmState[i][j] = PlotState.EMPTY;
            }
        }

        createBoard();
        refreshBoard();
        startRefreshTicker();

        // 弹出登录对话框
        promptLogin();

        // 默认断线按钮隐藏，只有真正断线或用户需要模拟时再使用
        if (disconnectButton != null) {
            disconnectButton.setDisable(false);
            disconnectButton.setVisible(true);
        }
    }

    private void onConnectionLost() {
        // Disable all game interaction buttons when disconnected
        setActionButtonsEnabled(false);
        showToast("Disconnected from server. Please restart server and click Reconnect.", "error");
        if (reconnectButton != null) {
            reconnectButton.setDisable(false);
            reconnectButton.setVisible(true);
        }
    }

    private void setActionButtonsEnabled(boolean enabled) {
        if (plantButton != null) plantButton.setDisable(!enabled);
        if (harvestButton != null) harvestButton.setDisable(!enabled);
        if (stealButton != null) stealButton.setDisable(!enabled);
        if (visitButton != null) visitButton.setDisable(!enabled);
        if (returnButton != null) returnButton.setDisable(!enabled);
    }

    @FXML
    private void handleReconnect() {
        // 重新连接同一台服务器
        boolean ok = clientNetworkService.reconnect("localhost", 8888);
        if (!ok) {
            showToast("Reconnect failed. Make sure server is running.", "error");
            return;
        }

        clientNetworkService.setMessageHandler(this::handleServerMessage);
        clientNetworkService.setConnectionLostHandler(this::onConnectionLost);

        // 自动使用上一次成功登录的用户名恢复会话
        if (lastSuccessfulUsername != null && !lastSuccessfulUsername.isBlank()) {
            pendingUsername = lastSuccessfulUsername;
            clientNetworkService.sendMessage("LOGIN " + lastSuccessfulUsername);
            showToast("Reconnected. Restoring your farm state...", "info");
        } else {
            showToast("Reconnected. Please log in.", "info");
            promptLogin();
        }

        setActionButtonsEnabled(false);
        if (reconnectButton != null) {
            reconnectButton.setDisable(true);
            reconnectButton.setVisible(false);
        }
    }

    /**
     * UI handler to simulate a network disconnect.
     * This calls ClientNetworkService#simulateNetworkDrop so that
     * the server sees an abrupt socket close, and the existing
     * connectionLost handler will update the UI without crashing.
     */
    @FXML
    private void handleDisconnect() {
        if (clientNetworkService != null && clientNetworkService.isConnected()) {
            clientNetworkService.simulateNetworkDrop();
            showToast("Simulated network disconnect.", "info");
        } else {
            showToast("Already disconnected.", "info");
        }
    }

    private void promptLogin() {
        // 已登录或对话框已打开时不再弹出
        if (currentPlayer != null || loginDialogOpen) return;

        loginDialogOpen = true;

        TextInputDialog dialog = new TextInputDialog("player1");
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your username");
        dialog.setContentText("Username:");

        Optional<String> result = dialog.showAndWait();
        loginDialogOpen = false;

        result.ifPresent(username -> {
            pendingUsername = username.trim();
            if (!pendingUsername.isEmpty()) {
                clientNetworkService.sendMessage("LOGIN " + pendingUsername);
            } else {
                // 空用户名，重新提示
                Platform.runLater(this::promptLogin);
            }
        });
    }

    private void handleServerMessage(String message) {
        String[] parts = message.split(" ");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "WELCOME":
                showToast("Connected to server", "info");
                break;

            case "LOGIN":
                if (parts.length > 1 && "SUCCESS".equals(parts[1])) {
                    // 使用第一次输入的用户名，不再二次弹窗
                    currentPlayer = (pendingUsername != null && !pendingUsername.isBlank())
                            ? pendingUsername : currentPlayer;
                    lastSuccessfulUsername = currentPlayer;
                    pendingUsername = null;

                    currentViewPlayer = currentPlayer;
                    if (playerLabel != null) {
                        playerLabel.setText("Player: " + currentPlayer);
                    }
                    showToast("Login successful!", "success");
                    setActionButtonsEnabled(true);
                } else {
                    showToast("Login failed: " + message, "error");
                    // 失败时再次提示，但避免重入
                    Platform.runLater(this::promptLogin);
                }
                break;

            case "SUCCESS":
                // e.g. SUCCESS Crop planted! Will mature in 5 seconds
                showToast(message.substring(8), "success");
                break;

            case "ERROR":
                showToast(message.substring(6), "error");
                break;

            case "FARM":
                parseFarmState(parts);
                // 收到完整农场状态，说明可以继续游戏
                setActionButtonsEnabled(true);
                if (reconnectButton != null) {
                    reconnectButton.setDisable(true);
                    reconnectButton.setVisible(false);
                }
                break;

            case "NOTIFY":
                showToast(message.substring(7), "info");
                break;

            case "PONG":
                // 心跳响应，无需处理
                break;

            default:
                break;
        }
        refreshBoard();
    }

    private void parseFarmState(String[] parts) {
        if (parts.length >= 19) { // FARM username coins + 16 plot states (now STATE:yield)
            String playerName = parts[1];
            this.coins = Integer.parseInt(parts[2]);
            currentViewPlayer = playerName;
            if (playerLabel != null) {
                playerLabel.setText("Viewing: " + playerName + (playerName.equals(currentPlayer) ? " (You)" : ""));
            }

            int index = 3;
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    String token = parts[index++];
                    String[] seg = token.split(":");
                    PlotState newState = PlotState.valueOf(seg[0]);
                    int yield = 0;
                    if (seg.length > 1) {
                        try { yield = Integer.parseInt(seg[1]); } catch (NumberFormatException ignored) { }
                    }

                    // 如果从 EMPTY 变为 GROWING，本地倒计时设置为 10s，与服务器一致
                    if (farmState[i][j] != PlotState.GROWING && newState == PlotState.GROWING) {
                        plotGrowSeconds[i][j] = 10;
                    }
                    // if it became RIPE or EMPTY, clear timer
                    if (newState != PlotState.GROWING) {
                        plotGrowSeconds[i][j] = 0;
                    }

                    farmState[i][j] = newState;
                    plotYields[i][j] = yield;
                }
            }
        }
    }

    private void createBoard() {
        gameBoard.getChildren().clear();
        cells = new ToggleButton[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                ToggleButton cell = new ToggleButton();
                cell.setPrefSize(80, 80);
                cell.getStyleClass().add("plot-button");
                int r = row;
                int c = col;
                cell.setOnAction(event -> {
                    selectedRow = r;
                    selectedCol = c;
                    refreshBoard();
                });
                gameBoard.add(cell, col, row);
                cells[row][col] = cell;
            }
        }
    }

    private void refreshBoard() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                ToggleButton cell = cells[row][col];
                cell.setSelected(row == selectedRow && col == selectedCol);
                updateCellState(cell, row, col);
            }
        }
        // 根据当前视图玩家与自身是否一致，切换 Visit / Return 按钮
        updateButtonVisibility();
        updateStatus();
    }

    private void updateCellState(ToggleButton cell, int row, int col) {
        PlotState state = farmState[row][col];
        cell.getStyleClass().removeAll("state-empty", "state-growing", "state-ripe");

        String icon;
        String text;
        switch (state) {
            case EMPTY:
                icon = "\uD83D\uDFE4"; // green small square as placeholder soil
                text = "Empty";
                cell.getStyleClass().add("state-empty");
                break;
            case GROWING:
                icon = "\uD83C\uDF31"; // seedling
                int sec = plotGrowSeconds[row][col];
                if (sec < 0) sec = 0;
                text = "Growing " + sec + "s";
                cell.getStyleClass().add("state-growing");
                break;
            case RIPE:
                icon = "\uD83C\uDF3E"; // sheaf of rice
                text = "Ripe"; // do not show yield percentage
                cell.getStyleClass().add("state-ripe");
                break;
            default:
                icon = "";
                text = "";
        }
        cell.setText(icon + "\n" + text);
        cell.setTooltip(new Tooltip("Row " + (row + 1) + ", Col " + (col + 1) + ": " + text));
    }

    private void updateStatus() {
        if (coinsLabel != null) {
            coinsLabel.setText("Coins: " + coins);
        }
        if (currentPlayer != null && currentViewPlayer != null && !currentViewPlayer.equals(currentPlayer)) {
            if (playerLabel != null) {
                playerLabel.setText("Viewing: " + currentViewPlayer + " (Press Return to go back)");
            }
        }
        if (statusLabel != null && statusMessage != null) {
            statusLabel.setText(statusMessage);
        }
    }

    @FXML
    private void handlePlant() {
        if (!ensureSelection()) {
            showToast("Select a plot first.", "error");
            refreshBoard();
            return;
        }
        if (currentPlayer == null) {
            showToast("Please login first.", "error");
            refreshBoard();
            return;
        }
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }
        // 本地立即设置为 10s 倒计时，增强反馈效果
        plotGrowSeconds[selectedRow][selectedCol] = 10;
        clientNetworkService.sendMessage("PLANT " + selectedRow + " " + selectedCol);
    }

    @FXML
    private void handleHarvest() {
        if (!ensureSelection()) {
            showToast("Select a plot first.", "error");
            refreshBoard();
            return;
        }
        if (currentPlayer == null) {
            showToast("Please login first.", "error");
            refreshBoard();
            return;
        }
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }
        clientNetworkService.sendMessage("HARVEST " + selectedRow + " " + selectedCol);
    }

    @FXML
    private void handleSteal() {
        if (currentPlayer == null) {
            showToast("Please login first.", "error");
            refreshBoard();
            return;
        }
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("player2");
        dialog.setTitle("Steal");
        dialog.setHeaderText("Enter player name to steal from");
        dialog.setContentText("Target player:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(targetPlayer ->
                clientNetworkService.sendMessage("STEAL " + targetPlayer)
        );
    }

    @FXML
    private void handleVisit() {
        if (currentPlayer == null) {
            showToast("Please login first.", "error");
            refreshBoard();
            return;
        }
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("player2");
        dialog.setTitle("Visit Friend");
        dialog.setHeaderText("Enter friend's name to visit");
        dialog.setContentText("Friend name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(friendName -> clientNetworkService.sendMessage("VISIT " + friendName));
    }

    @FXML
    private void handleReturn() {
        if (currentPlayer == null) {
            showToast("Please login first.", "error");
            refreshBoard();
            return;
        }
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }
        clientNetworkService.sendMessage("RETURN");
    }

    @FXML
    private void handlePing() {
        if (clientNetworkService == null || !clientNetworkService.isConnected()) {
            showToast("Not connected to server.", "error");
            return;
        }
        clientNetworkService.sendMessage("PING");
    }

    public void shutdown() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        if (clientNetworkService != null) {
            clientNetworkService.disconnect();
        }
    }

    private boolean ensureSelection() {
        return selectedRow >= 0 && selectedCol >= 0;
    }

    private void startRefreshTicker() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            // 每秒本地更新成长计时器
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (farmState[i][j] == PlotState.GROWING && plotGrowSeconds[i][j] > 0) {
                        plotGrowSeconds[i][j]--;
                    }
                }
            }
            // 状态更新由服务器推送，这里主要处理UI刷新
            if (currentPlayer != null && clientNetworkService != null && clientNetworkService.isConnected()) {
                refreshBoard();
            }
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void updateButtonVisibility() {
        // 在自己页面显示 Visit；在别人页面显示 Return（默认显示 Visit）
        boolean viewingOther = currentPlayer != null && currentViewPlayer != null && !currentViewPlayer.equals(currentPlayer);
        boolean showVisit = !viewingOther; // 未登录或未知视图玩家时也显示 Visit

        if (visitButton != null) {
            visitButton.setVisible(showVisit);
            visitButton.setManaged(showVisit);
        }
        if (returnButton != null) {
            returnButton.setVisible(viewingOther);
            returnButton.setManaged(viewingOther);
        }
    }

    private void showToast(String text, String level) {
        statusMessage = text;
        if (statusLabel == null) return;

        statusLabel.setOpacity(0);
        statusLabel.setText(text);

        // Reset style classes for status label and apply level-specific class
        statusLabel.getStyleClass().removeAll("status-label", "status-success", "status-error", "status-info");
        // base class for general styling
        statusLabel.getStyleClass().add("status-label");
        if ("success".equalsIgnoreCase(level)) {
            statusLabel.getStyleClass().add("status-success");
        } else if ("error".equalsIgnoreCase(level) || "warning".equalsIgnoreCase(level)) {
            statusLabel.getStyleClass().add("status-error");
        } else {
            statusLabel.getStyleClass().add("status-info");
        }

        FadeTransition ft = new FadeTransition(Duration.millis(250), statusLabel);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }
}
