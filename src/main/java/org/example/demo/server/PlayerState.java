package org.example.demo.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        }, 10, TimeUnit.SECONDS);

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

    /**
     * New steal logic: randomly steal from at most 25% of currently ripe plots.
     * Each stolen plot removes its full remaining yield (up to BASE_YIELD) and
     * the thief gains BASE_YIELD coins per stolen plot.
     *
     * @return number of plots successfully stolen, or -1 if no eligible plots.
     */
    public synchronized int stealRandomRipePlots() {
        // Collect indices of all ripe plots with remaining yield
        List<int[]> ripePlots = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (farm[r][c] == PlotState.RIPE && yields[r][c] > 0) {
                    ripePlots.add(new int[]{r, c});
                }
            }
        }
        int totalRipe = ripePlots.size();
        if (totalRipe == 0) {
            return -1; // no plots to steal from
        }

        // At most 25% of ripe plots, round down but at least 1 if there is any ripe plot
        int maxToSteal = (int) Math.floor(totalRipe * MAX_STEAL_PERCENT);

        // Shuffle to select random subset
        Collections.shuffle(ripePlots, random);
        int plotsToSteal = Math.min(maxToSteal, totalRipe);

        int stolenPlots = 0;
        for (int i = 0; i < plotsToSteal; i++) {
            int[] idx = ripePlots.get(i);
            int r = idx[0];
            int c = idx[1];
            if (farm[r][c] == PlotState.RIPE && yields[r][c] > 0) {
                // Remove entire remaining yield from this plot
                yields[r][c] = 0;
                farm[r][c] = PlotState.EMPTY;
                stolenPlots++;
            }
        }
        if (stolenPlots == 0) {
            return -1;
        }
        return stolenPlots;
    }

    // Old per-plot stealing APIs are no longer used by the new logic but kept for compatibility if needed.
    public synchronized boolean stealCrop(int row, int col){
        // Deprecated single-plot steal: delegate to stealRandomRipePlots when target is ripe.
        if (farm[row][col] != PlotState.RIPE || yields[row][col] <= 0) return false;
        int result = stealRandomRipePlots();
        return result > 0;
    }

    public synchronized int stealCropWithAmount(int row, int col){
        // Deprecated API: returns total coins that thief should gain from this steal action.
        if (farm[row][col] != PlotState.RIPE || yields[row][col] <= 0) return -1;
        int plots = stealRandomRipePlots();
        if (plots <= 0) return -1;
        // Each stolen plot is worth BASE_YIELD coins for the thief
        return plots * BASE_YIELD;
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
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
