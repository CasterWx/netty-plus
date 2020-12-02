package io.netty.example.mydemo;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BIOServer {

    public static void main(String[] args) {

        ExecutorService service = Executors.newFixedThreadPool(5);

        try {
            ServerSocket serverSocket = new ServerSocket(9999);

            while (true) {
                Socket socket = serverSocket.accept();

                service.submit(() -> {
                    handler(socket);
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void handler(Socket socket) {

        InputStream inputStream = null;
        try {
            inputStream = socket.getInputStream();
            byte []bytes = new byte[1024];
            while (true) {
                int read = inputStream.read(bytes);
                if (read != -1) {
                    System.out.print("[Client:"+Thread.currentThread().getId()+"]:"+new String(bytes, 0, read));
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
