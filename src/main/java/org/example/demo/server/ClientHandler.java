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
            sendMessage("SUCCESS Crop planted! Will mature in 5 seconds");
            sendFarmState();
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
        } else {
            sendMessage("ERROR Cannot harvest here");
        }
    }

    private void handleSteal(String[] parts){
        if (!checkLogin()) {
            return;
        }
        if (parts.length < 4) {
            sendMessage("ERROR Usage: STEAL [targetPlayer] [row] [col]");
            return;
        }

        String targetPlayer = parts[1];
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);

        if (targetPlayer.equals(currentPlayer)) {
            sendMessage("ERROR Cannot steal from yourself");
            return;
        }
        PlayerState targetState = server.getPlayerState(targetPlayer);
        if (targetState == null) {
            sendMessage("ERROR Target player not found or offline");
            return;
        }
        synchronized (targetState.getPlotLock(row,col)){
            if (targetState.stealCrop(row,col)){
                PlayerState selfState = server.getPlayerState(currentPlayer);
                selfState.addCoins(4);

                sendMessage("SUCCESS Stole crop from " + targetPlayer + "! +4 coins");
                sendFarmState();

                // 通知被偷的玩家（如果在线）
                ClientHandler targetHandler = targetState.getClientHandler();
                if (targetHandler != null) {
                    targetHandler.sendMessage("NOTIFY Your crop was stolen by " + currentPlayer);
                    targetHandler.sendFarmState(); // 更新被偷玩家的界面
                }
            } else {
                sendMessage("ERROR Steal failed - crop not ripe or already stolen");
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
        this.currentViewPlayer = friendName;
        sendMessage("SUCCESS Now visiting " + friendName + "'s farm");
        sendFarmState();
    }

    private void handleReturn() {
        if (!checkLogin()) return;

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

        if (currentPlayer != null) {
            server.removePlayer(currentPlayer);
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }

        System.out.println("Client disconnected: " +
                (currentPlayer != null ? currentPlayer : "unknown"));

    }
}
