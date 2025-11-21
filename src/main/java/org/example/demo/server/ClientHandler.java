package org.example.demo.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable{
    private Socket clientSocket;
    private FarmServer server;
    private boolean isConnected;
    private BufferedReader in;
    private PrintWriter out;
    private String currentPlayer;

    // 当前查看的农场（自己或好友的）
    private String currentViewPlayer;

    public ClientHandler(Socket clientSocket, FarmServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.isConnected = true;
    }



    @Override
    public void run() {
        try {
            // 建立输入输出流
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // 发送欢迎消息
            sendMessage("WELCOME to QQ Farm Server");

            // 主消息循环
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                System.out.println("Received from " + (currentPlayer != null ? currentPlayer : "unknown") +
                        ": " + message);
                processMessage(message);
            }

        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            disconnect();
        }



    }

    void pushFarmStateString(String farmState) {
        // Push a pre-built farm state to this client (used for visitor real-time updates)
        sendMessage(farmState);
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
            System.out.println("Sent to " + (currentPlayer != null ? currentPlayer : "unknown") +
                    ": " + message);
        }
    }

    private void processMessage(String message){
        String[] parts = message.split(" ");
        String command = parts[0].toUpperCase();
        try {
            switch (command) {
                case "LOGIN":
                    handleLogin(parts);
                    break;
                case "PLANT":
                    handlePlant(parts);
                    break;
                case "HARVEST":
                    handleHarvest(parts);
                    break;
                case "STEAL":
                    handleSteal(parts);
                    break;
                case "VISIT":
                    handleVisit(parts);
                    break;
                case "RETURN":
                    handleReturn();
                    break;
                case "PING":
                    sendMessage("PONG");
                    break;
                default:
                    sendMessage("ERROR Unknown command: " + command);
            }
        } catch (Exception e) {
            sendMessage("ERROR " + e.getMessage());
        }
    }

    private void handleLogin(String[] parts){
        if (parts.length < 2) {
            sendMessage("ERROR LOGIN requires username");
            return;
        }
        String username = parts[1];
        if (server.registerPlayer(username, this)) {
            this.currentPlayer = username;
            this.currentViewPlayer = username;
            sendMessage("LOGIN SUCCESS");
            sendFarmState();
        } else {
            sendMessage("ERROR Username already taken");
        }
    }

    private void handlePlant(String[] parts){
        if (!checkLogin()){
            return;
        }
        if (parts.length < 3) {
            sendMessage("ERROR PLANT requires row and col");
            return;
        }
        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);
        PlayerState state = server.getPlayerState(currentViewPlayer);

        if (state == null) {
            sendMessage("ERROR Player not found");
            return;
        }

        if (!currentViewPlayer.equals(currentPlayer)){
            sendMessage("ERROR Can only plant in your own farm");
            return;
        }
        if (state.plantCrop(row, col)) {
            sendMessage("SUCCESS Crop planted! Will mature in 10 seconds");
            sendFarmState();
            // Notify any visitors of my farm
            state.notifyViewers();
        } else {
            sendMessage("ERROR Cannot plant here");
        }
    }

    private void handleHarvest(String[] parts){
        if (!checkLogin()){
            return;
        }
        if (parts.length < 3) {
            sendMessage("ERROR HARVEST requires row and col");
            return;
        }
        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);
        PlayerState state = server.getPlayerState(currentViewPlayer);

        if (state == null) {
            sendMessage("ERROR Player not found");
            return;
        }

        if (!currentViewPlayer.equals(currentPlayer)){
            sendMessage("ERROR Can only harvest in your own farm");
            return;
        }
        if (state.harvestCrop(row, col)) {
            sendMessage("SUCCESS Crop harvested!");
            sendFarmState();
            // Notify any visitors of my farm
            state.notifyViewers();
        } else {
            sendMessage("ERROR Cannot harvest here");
        }
    }

    private void handleSteal(String[] parts){
        if (!checkLogin()) {
            return;
        }
        if (parts.length < 2) {
            sendMessage("ERROR Usage: STEAL [targetPlayer]");
            return;
        }

        String targetPlayer = parts[1];

        if (targetPlayer.equals(currentPlayer)) {
            sendMessage("ERROR Cannot steal from yourself");
            return;
        }
        PlayerState targetState = server.getPlayerState(targetPlayer);
        if (targetState == null) {
            sendMessage("ERROR Target player not found or offline");
            return;
        }

        // New rule: target owner must currently be visiting someone else's farm (i.e., not on their own farm)
        ClientHandler targetHandler = targetState.getClientHandler();
        if (targetHandler == null) {
            sendMessage("ERROR Cannot steal: target player is not controllable");
            return;
        }
        // If owner is currently viewing their own farm, forbid stealing
        if (targetPlayer.equals(targetHandler.currentViewPlayer)) {
            sendMessage("ERROR Cannot steal: target player is currently on their own farm");
            return;
        }

        synchronized (targetState) {
            int amount = targetState.stealCropWithAmount(0,0); // row/col ignored in new logic
            if (amount > 0){
                PlayerState selfState = server.getPlayerState(currentPlayer);
                if (selfState != null) {
                    selfState.addCoins(amount);
                }

                int plots = amount / 12; // each plot yields 12 coins to thief
                sendMessage("SUCCESS Stole " + plots + " plots (" + amount + " coins) from " + targetPlayer);
                sendFarmState();

                targetHandler.sendMessage("NOTIFY Your farm had " + plots + " ripe plots stolen (" + amount + " coins) by " + currentPlayer);
                targetHandler.sendFarmState();
                targetState.notifyViewers();
                if (selfState != null) {
                    selfState.notifyViewers();
                }
            } else if (amount == 0) {
                sendMessage("SUCCESS Steal attempted but nothing was available to steal.");
            } else {
                sendMessage("ERROR Steal failed - no enough ripe plots or target farm empty");
            }
        }

    }


    private void handleVisit(String[] parts){
        if (!checkLogin()){
            return;
        }
        if (parts.length < 2) {
            sendMessage("ERROR Usage: VISIT [friendName]");
            return;
        }

        String friendName = parts[1];
        PlayerState friendState = server.getPlayerState(friendName);

        if (friendState == null) {
            sendMessage("ERROR Friend not found or offline");
            return;
        }

        // Unsubscribe from previous viewed player's viewer list if it was someone else
        if (currentViewPlayer != null && !currentViewPlayer.equals(currentPlayer)) {
            PlayerState prev = server.getPlayerState(currentViewPlayer);
            if (prev != null) prev.removeViewer(this);
        }

        // Subscribe to friend's farm updates
        friendState.addViewer(this);

        this.currentViewPlayer = friendName;
        sendMessage("SUCCESS Now visiting " + friendName + "'s farm");
        sendFarmState();
    }

    private void handleReturn() {
        if (!checkLogin()) return;

        // Remove from previous friend's viewer list
        if (currentViewPlayer != null && !currentViewPlayer.equals(currentPlayer)) {
            PlayerState prev = server.getPlayerState(currentViewPlayer);
            if (prev != null) prev.removeViewer(this);
        }

        this.currentViewPlayer = currentPlayer;
        sendMessage("SUCCESS Returned to your farm");
        sendFarmState();
    }

    public void sendFarmState() {
        PlayerState state = server.getPlayerState(currentViewPlayer);
        if (state != null) {
            sendMessage(state.getFarmStateString());
        }
    }

    private boolean checkLogin(){
        if (currentPlayer == null){
            sendMessage("ERROR Not logged in");
            return false;
        }
        return true;
    }

    private void disconnect(){
        isConnected = false;

        // 如果当前在“参观别人的农场”，把自己从对方的观众列表中移除
        if (currentViewPlayer != null && currentPlayer != null && !currentViewPlayer.equals(currentPlayer)) {
            PlayerState prev = server.getPlayerState(currentViewPlayer);
            if (prev != null) prev.removeViewer(this);
        }

        // 不再从服务器的 onlinePlayers 中移除 PlayerState，以便断线重连后还能保留农场
        if (currentPlayer != null) {
            PlayerState selfState = server.getPlayerState(currentPlayer);
            if (selfState != null && selfState.getClientHandler() == this) {
                // 将 PlayerState 上绑定的客户端引用清空，表示当前离线，但保留农场数据
                selfState.setClientHandler(null);
            }
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }

        System.out.println("[DISCONNECT] Client disconnected, resources released for player (state preserved): " +
                (currentPlayer != null ? currentPlayer : "unknown"));
    }
}
