package org.example.demo;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal game logic to demonstrate multithreading and synchronization.
 */
public class Game {

    //EMPTY（空）、GROWING（生长中）、RIPE（成熟）
    public enum PlotState {EMPTY, GROWING, RIPE}

    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int HARVEST_REWARD = 12;
    private static final int STEAL_REWARD = 4;

    private final PlotState[][] board = new PlotState[ROWS][COLS];
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "crop-growth");
        thread.setDaemon(true);
        return thread;
    });
    private final Random random = new Random();

    private int coins = 40;

    public Game() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = PlotState.EMPTY;
            }
        }
    }

    public synchronized int getCoins() {
        return coins;
    }

    public synchronized PlotState getState(int row, int col) {
        return board[row][col];
    }

    public synchronized void plant(int row, int col) {
        if (board[row][col] != PlotState.EMPTY) {
            throw new IllegalStateException("Plot occupied");
        }
        if (coins < PLANT_COST) {
            throw new IllegalStateException("Not enough coins");
        }
        coins -= PLANT_COST;
        board[row][col] = PlotState.GROWING;

        // Simulate growth finishing after 5 seconds
        scheduler.schedule(() -> {
            synchronized (Game.this) {
                if (board[row][col] == PlotState.GROWING) {
                    board[row][col] = PlotState.RIPE;
                }
            }
        }, 5, TimeUnit.SECONDS);
    }

    public synchronized void harvest(int row, int col) {
        if (board[row][col] != PlotState.RIPE) {
            throw new IllegalStateException("Crop not ripe");
        }
        board[row][col] = PlotState.EMPTY;
        coins += HARVEST_REWARD;
    }

    public synchronized void stealRandom() {
        // Simple simulation of being stolen by another player
        // TODO: replace this demo logic with server-driven stealing requests from other clients.
        int row = random.nextInt(ROWS);
        int col = random.nextInt(COLS);
        if (board[row][col] == PlotState.RIPE) {
            board[row][col] = PlotState.EMPTY;
            coins = Math.max(0, coins - STEAL_REWARD);
        }
    }

    public int getRows() {
        return ROWS;
    }

    public int getCols() {
        return COLS;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
