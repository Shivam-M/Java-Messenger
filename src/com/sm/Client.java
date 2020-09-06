package com.sm;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    private String address = "localhost";
    private int port = 5000;
    private Socket socket;
    private PrintWriter outputStream;
    private BufferedReader inputStream;
    private boolean clientActive = true;

    public Client() { this("localhost", 5000); }

    public Client(String a) { this(a, 5000); }

    public Client(String a, int p) {
        try {
            address = a;
            port = p;
            socket = new Socket(address, port);
            outputStream = new PrintWriter(socket.getOutputStream(), true);
            inputStream = new BufferedReader((new InputStreamReader(socket.getInputStream())));
            listen();
            Scanner scanner = new Scanner(System.in);
            System.out.println("Type /username <username> to change your username, /quit to disconnect or type a message to chat.");
            while (clientActive) {
                String line = scanner.nextLine();
                if (line.startsWith("/")) {
                    handleCommand(line);
                    continue;
                }
                JSONObject messageRequest = new JSONObject();
                messageRequest.put("content", line);
                send(messageRequest, "message");
            }
            scanner.close();
            socket.close();
        } catch (ConnectException e) {
            System.out.printf("ERROR: Unable to connect to %s:%s\n", address, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(JSONObject message, String type) {
        message.put("data-type", type);
        outputStream.println(message.toString());
        outputStream.flush();
    }

    public void handleCommand(String command) {
        String[] arguments = command.replace("/", "").split(" ");
        switch (arguments[0]) {
            case "username":
                if (arguments.length <= 1) {
                    System.out.println("Invalid arguments, use /username <name>."); return;
                }
                JSONObject messageRequest = new JSONObject();
                messageRequest.put("username", arguments[1]);
                send(messageRequest, "username"); return;
            case "quit":
                clientActive = false;
        }
    }

    public void listen() {
        new Thread(() -> {
            try {
                _listen();
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    private void _listen() throws IOException {
        while (true) {
            String receivedData;
            if (inputStream.ready()) {
                if ((receivedData = inputStream.readLine()) != null) {
                    try {
                        JSONObject receivedInformation = new JSONObject(receivedData);
                        handle(receivedInformation);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void handle(JSONObject information) {
        switch (information.getString("data-type")) {
            case "ping-response":
                // System.out.printf("Latest ping time: %dms.\n", information.getInt("ping"));
                return;
            case "persistence":
                JSONObject pingRequest = new JSONObject();
                pingRequest.put("data-type", "ping");
                pingRequest.put("time", System.currentTimeMillis());
                outputStream.println(pingRequest.toString());
                outputStream.flush(); return;
            case "message":
                System.out.println(information.get("author") + " >> " + information.get("content"));
        }
    }

}
