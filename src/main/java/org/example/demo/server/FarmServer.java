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
        // 支持断线重连：
        // - 如果玩家第一次登录，不存在旧状态，则创建新的 PlayerState
        // - 如果玩家之前登录过且服务器还保留其状态，则复用原来的 PlayerState，只更新其中的 clientHandler
        PlayerState existing = onlinePlayers.get(username);
        if (existing != null) {
            // 已有农场状态，表示这是一次重连，切换到新的客户端连接
            existing.setClientHandler(clientHandler);
            System.out.println("Player reconnected: " + username);
            return true;
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

    /**
     * 不再在这里删除 PlayerState，这样断线重连时可以保留农场状态。
     * 仅作为可选 API，供将来真正需要“踢出并清空数据”时使用。
     */
    public void removePlayer(String username){
        onlinePlayers.remove(username);
        System.out.println("Player offline and state removed: " + username);
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
