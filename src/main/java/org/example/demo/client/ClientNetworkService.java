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

    public void connect(String serverHost, int serverPort) throws IOException {
        this.socket = new Socket(serverHost, serverPort);
        this.in = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.connected = true;

        new Thread(this::messageListener).start();
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
            disconnect();
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
