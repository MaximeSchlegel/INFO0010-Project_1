import java.io.IOException;
import java.net.ServerSocket;


public class BattleshipServer {

    private static int portNumber = 2511;


    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);
            System.out.println("Server Is Running On Port " + portNumber + "\n");
            while (true) {
                System.out.println("Waiting for connection");
                try {
                    new BattleshipGameHandler(serverSocket.accept()).start();
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + portNumber);
            System.exit(-1);
        }
    }
}
