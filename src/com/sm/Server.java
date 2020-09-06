package com.sm;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private ServerSocket serverSocket;
    private ArrayList<Socket> clientSockets = new ArrayList<>();
    private Map<Socket, String> clientUsernames = new HashMap<>();
    private Map<Socket, Long> clientResponses = new HashMap<>();
    private boolean acceptingClients = true;
    private boolean listeningClients = true;
    private int Port;

    private final int PERSISTENCE_TIME = 10;
    private final int CLIENT_RESPONSE_TIME = 15;

    public Server(int p) {
        Port = p;
        gather();
        listen();
        persistence();
    }

    private void _gather() throws IOException {
        serverSocket = new ServerSocket(Port);
        while (acceptingClients) {
            Socket clientSocket = serverSocket.accept();
            clientSockets.add(clientSocket);
            clientUsernames.put(clientSocket, "Anonymous");
            clientResponses.put(clientSocket, System.currentTimeMillis());
            log("Client " + clientSocket.getRemoteSocketAddress() + " has connected to the server.");
        }
    }

    public void gather() {
        new Thread(() -> {
            try {
                _gather();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void _listen() throws IOException {
        while (listeningClients) {
            ArrayList<Socket> copiedClients = new ArrayList<>(clientSockets.size());
            copiedClients.addAll(clientSockets);
            for (Socket clientSocket: copiedClients) {
                BufferedReader inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String receivedData;
                checkHealth(clientSocket);
                try {
                    if (inputStream.ready()) {
                        if ((receivedData = inputStream.readLine()) != null) {
                            try {
                                JSONObject receivedInformation = new JSONObject(receivedData);
                                handle(receivedInformation, clientSocket);
                            } catch (Exception e) {
                                log("Received invalid data from client " + clientSocket.getRemoteSocketAddress() + ".", "ERROR");
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    clientSockets.remove(clientSocket);
                    log("Client " + clientSocket.getRemoteSocketAddress() + " has disconnected from the server.");
                }
            }
        }
    }

    public void listen() {
        new Thread(() -> {
            try {
                _listen();
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    private void _persistence() throws IOException, InterruptedException {
        while (true) {
            ArrayList<Socket> copiedClients = new ArrayList<>(clientSockets.size());
            copiedClients.addAll(clientSockets);
            for (Socket clientSocket: copiedClients) {
                JSONObject heartbeatRequest = new JSONObject();
                heartbeatRequest.put("data-type", "persistence");
                sendTo(heartbeatRequest.toString(), clientSocket);
                // clientSocket.setSoTimeout(CLIENT_RESPONSE_TIME * 1000); // ?????
                // Changed to checkHealth()
            }
            Thread.sleep(PERSISTENCE_TIME * 1000);
        }
    }

    public void persistence() {
        new Thread(() -> {
            try {
                _persistence();
            } catch (IOException | InterruptedException e) { e.printStackTrace(); }
        }).start();
    }

    public void handle(JSONObject information, Socket client) {
        switch (information.getString("data-type")) {
            case "ping":
                long pingTime = System.currentTimeMillis() - (long)information.get("time");
                JSONObject pingResponse = new JSONObject();
                pingResponse.put("data-type", "ping-response");
                pingResponse.put("ping", pingTime);
                sendTo(pingResponse.toString(), client);
                clientResponses.put(client, System.currentTimeMillis());
                log(String.format("Latency time %dms for client %s.", pingTime, client.getRemoteSocketAddress()));
                return;
            case "message":
                information.put("author", clientUsernames.get(client));
                send(information.toString());
                return;
            case "username":
                if (usernameExists(information.getString("username")) || information.getString("username").equalsIgnoreCase("server")) {
                    sendTo("{'data-type': 'message', 'content': 'Username already exists or is invalid, please choose a new one.', 'author': 'SERVER'}", client);
                } else {
                    clientUsernames.put(client, information.getString("username"));
                    log(String.format("Set username to %s for client %s.", information.getString("username"), client.getRemoteSocketAddress()));
                }
        }
    }

    public void checkHealth(Socket client) {
        try {
            if (((System.currentTimeMillis() - clientResponses.get(client)) / 1000) >= CLIENT_RESPONSE_TIME) {
                sendTo("{'data-type': 'message', 'content': 'Disconnected from server (failed to respond).', 'author': 'SERVER'}", client);
                client.close();
                clientSockets.remove(client);
                log("Client " + client.getRemoteSocketAddress() + " failed to response to a ping request in a timely manner.", "WARNING");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean usernameExists(String username) {
        for (String clientUsername: clientUsernames.values()) {
            if (clientUsername.equalsIgnoreCase(username)) {
                return true;
            }
        } return false;
    }

    public void sendTo(String message, Socket client) {
        try {
            PrintWriter outputStream = new PrintWriter(client.getOutputStream(), true);
            outputStream.println(message);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(String message) {
        ArrayList<Socket> copiedClients = new ArrayList<>(clientSockets.size());
        copiedClients.addAll(clientSockets);
        for (Socket clientSocket: copiedClients) {
            try {
                PrintWriter outputStream = new PrintWriter(clientSocket.getOutputStream(), true);
                outputStream.println(message);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void log(String message, String prefix) {
        System.out.println(new SimpleDateFormat("[dd/MM/yyyy HH:mm:ss]").format(new Date()) + " - " + prefix + ": " + message);
    }

    public void log(String message) {
        log(message, "INFO");
    }

}
