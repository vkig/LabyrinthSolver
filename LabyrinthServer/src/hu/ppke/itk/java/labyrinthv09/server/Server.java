package hu.ppke.itk.java.labyrinthv09.server;

import java.net.*;
import java.io.*;

public class Server {
    ServerSocket server;

    void log(String message) {
        System.out.println("[ SERVER ] " + message);
    }

    void run(int port) {
        try {
            server = new ServerSocket(port);
            log("OK. Awaiting connections on localhost:" + port);
        }
        catch (IOException e) {
            log("Error: " + e.getMessage());
            log("Failed to initialize server.");
        }

        try {
            while (true) {
                Socket client = server.accept();
                log("Client connected");
                Session s = new Session(client);
                log("Created session");
                s.start();
            }
        }
        catch (IOException e) {
            log("Error: " + e.getMessage());
            log("Failed to accept client.");
        }
    }
}
