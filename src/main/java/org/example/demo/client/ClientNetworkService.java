package org.example.demo.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientNetworkService {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Consumer<String> messageHandler;
    private boolean connected = false;
    // Callback when connection to server is lost
    private Runnable connectionLostHandler;

    public void setConnectionLostHandler(Runnable handler) {
        this.connectionLostHandler = handler;
    }

    public void connect(String serverHost, int serverPort) throws IOException {
        this.socket = new Socket(serverHost, serverPort);
        this.in = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.connected = true;

        new Thread(this::messageListener).start();
    }

    // Simple reconnect helper used by UI when user clicks Reconnect
    public synchronized boolean reconnect(String serverHost, int serverPort) {
        disconnect();
        try {
            connect(serverHost, serverPort);
            return true;
        } catch (IOException e) {
            System.err.println("Reconnect failed: " + e.getMessage());
            return false;
        }
    }

    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void sendMessage(String message) {
        if (out != null && connected) {
            out.println(message);
            System.out.println("Sent: " + message);
        }
    }

    private void messageListener(){
        try{
            String message;
            while(connected&&(message = in.readLine()) != null){
                System.out.println("Received: " + message);
                if (messageHandler != null) {
                    final String finalMessage = message;  // 创建final副本
                    javafx.application.Platform.runLater(() -> {
                        messageHandler.accept(finalMessage);
                    });
                }
            }
        }catch (IOException e) {
            System.err.println("Connection lost: " + e.getMessage());
        } finally {
            // Mark as disconnected first so UI will stop sending further messages
            connected = false;
            // Notify UI on FX thread that connection was lost
            if (connectionLostHandler != null) {
                javafx.application.Platform.runLater(connectionLostHandler);
            }
            disconnect();
        }
    }

    /**
     * Simulate a sudden network drop from the client side.
     * This will close the underlying socket without sending any
     * application-level logout, so that the server experiences it
     * as a real disconnect.
     */
    public synchronized void simulateNetworkDrop() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error simulating network drop: " + e.getMessage());
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    public boolean isConnected() {
        return connected;
    }

}
