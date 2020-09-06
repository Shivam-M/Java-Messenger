package com.sm;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            new Client();
        } else if (args[0].equalsIgnoreCase("-client")) {
            if (args.length == 1) {
                new Client();
            } else if (args.length == 2) {
                new Client(args[1]);
            } else {
                new Client(args[1], Integer.parseInt(args[2]));
            }
        } else if (args[0].equalsIgnoreCase("-server")) {
            if (args.length == 1) {
                new Server(5000);
            } else {
                new Server(Integer.parseInt(args[1]));
            }
        }
    }


}
