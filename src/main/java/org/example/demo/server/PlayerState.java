package org.example.demo.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerState {

    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int HARVEST_REWARD = 12;
    private static final int STEAL_REWARD = 4;


    private String username;
    private int coins;
    private PlotState[][] farm;
    private ClientHandler clientHandler;
    private ScheduledExecutorService scheduler;

    public PlayerState(String username){
        this.username = username;
        this.coins = 40;
        this.farm = new PlotState[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                farm[r][c] = PlotState.EMPTY;
            }
        }
        //相当于创建了一个专属闹钟，单线程的定时任务执行器
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public synchronized boolean plantCrop(int row, int col) {
        if (coins < PLANT_COST) return false;
        if (farm[row][col] != PlotState.EMPTY) return false;

        coins -= PLANT_COST;
        farm[row][col] = PlotState.GROWING;

        scheduler.schedule(() -> {
            synchronized (this) {
                if (farm[row][col] == PlotState.GROWING) {
                    farm[row][col] = PlotState.RIPE;
                    if (clientHandler != null) {
                        clientHandler.sendFarmState();
                    }
                }
            }
        }, 5, TimeUnit.SECONDS);

        return true;
    }

    public synchronized boolean harvestCrop(int row, int col){
        if (farm[row][col] != PlotState.RIPE) {
            return false;
        }
        coins += HARVEST_REWARD;
        farm[row][col] = PlotState.EMPTY;
        return true;
    }

    public synchronized boolean stealCrop(int row, int col){
        if (farm[row][col] != PlotState.RIPE) return false;

        farm[row][col] = PlotState.EMPTY;
        return true;
    }

    public synchronized String getFarmStateString(){
        StringBuilder sb = new StringBuilder();
        sb.append("FARM ").append(username).append(" ").append(coins);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                sb.append(" ").append(farm[i][j].name());
            }
        }

        return sb.toString();
    }

    public synchronized void addCoins(int amount){
        this.coins += amount;
    }

    public Object getPlotLock(int row, int col) {
        // 返回地块对象作为锁，确保同一地块的偷菜操作串行化
        return farm[row][col];
    }

    public String getUsername() { return username; }
    public int getCoins() { return coins; }
    public PlotState[][] getFarm() { return farm; }
    public ClientHandler getClientHandler() { return clientHandler; }
    public void setClientHandler(ClientHandler clientHandler) { this.clientHandler = clientHandler; }

}
