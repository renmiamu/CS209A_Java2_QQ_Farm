package org.example.demo.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class PlayerState {

    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int BASE_YIELD = 12; // Mature crop initial yield
    private static final double MAX_STEAL_PERCENT = 0.25; // 0-25%

    private String username;
    private int coins;
    private PlotState[][] farm;
    // Track remaining yield on each plot (0..BASE_YIELD)
    private int[][] yields;
    private ClientHandler clientHandler;
    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    // Track clients that are currently visiting this player's farm
    private final Set<ClientHandler> viewers = ConcurrentHashMap.newKeySet();

    public PlayerState(String username){
        this.username = username;
        this.coins = 40;
        this.farm = new PlotState[ROWS][COLS];
        this.yields = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                farm[r][c] = PlotState.EMPTY;
                yields[r][c] = 0;
            }
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public synchronized boolean plantCrop(int row, int col) {
        if (coins < PLANT_COST) return false;
        if (farm[row][col] != PlotState.EMPTY) return false;

        coins -= PLANT_COST;
        farm[row][col] = PlotState.GROWING;
        yields[row][col] = 0;

        scheduler.schedule(() -> {
            synchronized (this) {
                if (farm[row][col] == PlotState.GROWING) {
                    farm[row][col] = PlotState.RIPE;
                    yields[row][col] = BASE_YIELD; // Set initial yield when mature
                    if (clientHandler != null) {
                        clientHandler.sendFarmState();
                    }
                    notifyViewers();
                }
            }
        }, 5, TimeUnit.SECONDS);

        return true;
    }

    public synchronized boolean harvestCrop(int row, int col){
        if (farm[row][col] != PlotState.RIPE) {
            return false;
        }
        // Collect remaining yield (may be reduced by steals)
        int gain = yields[row][col];
        coins += gain;
        farm[row][col] = PlotState.EMPTY;
        yields[row][col] = 0;
        return true;
    }

    public synchronized boolean stealCrop(int row, int col){
        if (farm[row][col] != PlotState.RIPE) return false;
        if (yields[row][col] <= 0) return false; // nothing left to steal

        // Random percentage between 0 and MAX_STEAL_PERCENT (inclusive)
        double percent = random.nextDouble() * MAX_STEAL_PERCENT; // 0 <= percent < 0.25
        // Calculate amount based on BASE_YIELD (requirement: coin = 12 * steal percentage)
        int amount = (int)Math.round(BASE_YIELD * percent);
        if (amount <= 0) {
            // Allow zero steal (requirement includes 0%), but if 0, just return false? We'll treat as no-op success.
            amount = 0;
        }
        // Clamp to remaining yield
        if (amount > yields[row][col]) {
            amount = yields[row][col];
        }
        // Reduce yield, ensure not below 0
        yields[row][col] -= amount;
        if (yields[row][col] < 0) yields[row][col] = 0;
        // If yield exhausted, mark plot empty
        if (yields[row][col] == 0) {
            farm[row][col] = PlotState.EMPTY;
        }
        // Add coins to thief via caller (ClientHandler will call addCoins)
        return amount >= 0; // success even if 0 stolen
    }

    // Expose last calculated potential? For message we need amount and percent -> we will recompute inside ClientHandler with synchronized block for accuracy. Alternative: modify stealCrop to return amount.
    // Simpler: overload returning amount.
    public synchronized int stealCropWithAmount(int row, int col){
        if (farm[row][col] != PlotState.RIPE) return -1;
        if (yields[row][col] <= 0) return -1;
        double percent = random.nextDouble() * MAX_STEAL_PERCENT;
        int amount = (int)Math.round(BASE_YIELD * percent);
        if (amount < 0) amount = 0;
        if (amount > yields[row][col]) amount = yields[row][col];
        yields[row][col] -= amount;
        if (yields[row][col] < 0) yields[row][col] = 0;
        if (yields[row][col] == 0) farm[row][col] = PlotState.EMPTY;
        return amount; // 0 means nothing stolen but considered attempt
    }

    public synchronized String getFarmStateString(){
        StringBuilder sb = new StringBuilder();
        sb.append("FARM ").append(username).append(" ").append(coins);
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                // Format: STATE:yield
                sb.append(" ").append(farm[i][j].name()).append(":").append(yields[i][j]);
            }
        }
        return sb.toString();
    }

    public void addViewer(ClientHandler handler) {
        if (handler != null) {
            viewers.add(handler);
        }
    }

    public void removeViewer(ClientHandler handler) {
        if (handler != null) {
            viewers.remove(handler);
        }
    }

    public void notifyViewers() {
        String farmState = getFarmStateString();
        for (ClientHandler viewer : viewers) {
            viewer.pushFarmStateString(farmState);
        }
    }

    public synchronized void addCoins(int amount){
        this.coins += amount;
    }

    public Object getPlotLock(int row, int col) {
        return farm[row][col];
    }

    public String getUsername() { return username; }
    public int getCoins() { return coins; }
    public PlotState[][] getFarm() { return farm; }
    public ClientHandler getClientHandler() { return clientHandler; }
    public void setClientHandler(ClientHandler clientHandler) { this.clientHandler = clientHandler; }
}
