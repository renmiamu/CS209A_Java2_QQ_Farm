package org.example.demo.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FarmServer {
    private static final int PORT = 8888;
    private static final int MAX_THREADS = 100;

    private ConcurrentHashMap<String, PlayerState> onlinePlayers;
    private ExecutorService threadPool;
    private boolean isRunning = true;

    public FarmServer(){
        this.onlinePlayers = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        System.out.println("QQ Farm Server initialized. Waiting for connections...");
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("Server started on port " + PORT);
            while (isRunning){
                try {
                    // 等待客户端连接
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " +
                            clientSocket.getInetAddress().getHostAddress());

                    // 为每个客户端创建处理器并提交到线程池
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    threadPool.execute(clientHandler);
                }catch (IOException e){
                    if (isRunning) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }

        }catch (IOException e){
            System.err.println("Server error: " + e.getMessage());
        }
    }

    public boolean registerPlayer(String username, ClientHandler clientHandler){
        if (onlinePlayers.containsKey(username)){
            return false;
        }
        PlayerState playerState = new PlayerState(username);
        playerState.setClientHandler(clientHandler);
        onlinePlayers.put(username, playerState);
        System.out.println("Player registered: " + username);
        return true;
    }

    public PlayerState getPlayerState(String username){
        return onlinePlayers.get(username);
    }

    public void removePlayer(String username){
        onlinePlayers.remove(username);
        System.out.println("Player offline: " + username);
    }

    public ConcurrentHashMap<String, PlayerState> getOnlinePlayers() {
        return onlinePlayers;
    }

    public void shutdown() {
        isRunning = false;
        threadPool.shutdown();
        System.out.println("Server is shutting down...");
    }

    public static void main(String[] args) {
        FarmServer server = new FarmServer();

        // 添加关闭钩子，确保资源正确释放
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
        }));

        server.start();
    }

}
