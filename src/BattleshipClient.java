import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.ArrayList;


public class BattleshipClient {

    private static int version = 1;
    private static int serverPortNumber = 2511;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    private ArrayList<Byte> triedShoot;
    private static int gameLength = 70;


    public BattleshipClient() throws Exception {
        // Create a new instance of the client (used for one game)
        try {
            this.socket = new Socket("localhost", serverPortNumber);
            // Can throw exception if it can't reach the server when trying to connect
            this.socket.setTcpNoDelay(true);     //send as soon as possible
            this.socket.setSoTimeout(5 * 60000); //5min timeout on read call

            this.inputStream = this.socket.getInputStream();
            this.outputStream = this.socket.getOutputStream();
            this.triedShoot = new ArrayList<>();
        } catch (Exception e) {
            throw new Exception("Can't Reach The Server On port " + serverPortNumber);
        }
    }

    private void sendNewGame() throws Exception {
        //Send the new game message to the server
        try {
            byte[] msg = new byte[2];
            msg[0] = (byte) version;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            throw new Exception("The Connection With The Server Have Been Lost");
        }
    }

    private void sendShoot(byte position) throws Exception {
        // send the message to get the result of a shoot to the server
        try {
            byte[] msg = new byte[3];
            msg[0] = (byte) version;
            msg[1] = 1;
            msg[2] = position;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (Exception e) {
            throw new Exception("The Connection With The Server Have Been Lost");
        }
    }

    private void sendRequestHistory() throws Exception {
        //send the request for history to the server
        try {
            byte[] msg = new byte[2];
            msg[0] = (byte) version;
            msg[1] = 2;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (Exception e) {
            throw new Exception("The Connection With The Server Have Been Lost");
        }
    }

    private boolean isValidPosition(String cell) {
        //Check if the position imput from the user is valid ie between 'A0' and 'J9'
        if (cell == null || cell.length() != 2) {
            return false;
        }

        char columnId = cell.charAt(0);
        int column = columnId - 'A';
        char lineId = cell.charAt(1);
        int line = Character.getNumericValue(lineId);

        return (0 <= column && column <= 9 && 0 <= line && line <= 9);
    }

    private byte cellToId(String cell) {
        //convert a valid sting position to its byte value (between 0 and 99)
        char columnId = cell.charAt(0);
        int column = columnId - 'A';
        char lineId = cell.charAt(1);
        int line = Character.getNumericValue(lineId);

        return (byte) (column * 10 + line);
    }

    private boolean hasWin() throws Exception {
        //Check if the game was win after 70 shoots
        byte[] header = new byte[2], historyLength = new byte[1], history;
        int requestNumber = 0;
        do {
            try {
                sendRequestHistory();
                this.inputStream.read(header);
                requestNumber++;
            } catch (IOException e) {
                throw new Exception("The Connection With The Server Have Been Lost");
            }
        } while (header[1] != 3 && requestNumber < 3);
        if (header[1] != 3) {
            throw new Exception("No Longer synchronize with the Server");
        }
        this.inputStream.read(historyLength);
        history = new byte[2 * (int) historyLength[0]];
        this.inputStream.read(history);

        int hit = 0;
        for (int i=0; i < historyLength[0]; i++) {
            if (history[i+1] != 0) {
                hit++;
            }
        }
        return hit == 17; // 2+3+3+4+5 =17 => true if all the ships have been sunk
    }

    private void shootHandler() throws Exception {
        // Handle the communication with the server for when the user want to try a shoot
        Scanner consoleInput = new Scanner(System.in);

        //Get a position that haven't been tried before
        System.out.print("Enter Your Guess : ");
        String guess = consoleInput.nextLine();
        while (!isValidPosition(guess)) {
            System.out.println(guess + " Is Not A Valid Guess");
            System.out.print("Enter Your Guess : ");
            guess = consoleInput.nextLine();
        }
        byte position = cellToId(guess);
        while(this.triedShoot.contains(position)) {
            System.out.println("You Have Already Tried This Position");
            System.out.print("Enter Your Guess : ");
            guess = consoleInput.nextLine();
            while (!isValidPosition(guess)) {
                System.out.println(guess + " Is Not A Valid Guess");
                System.out.print("Enter Your Guess : ");
                guess = consoleInput.nextLine();
            }
            position = cellToId(guess);  // We have a valid position now
        }

        //Get the answer from the server
        byte[] header = new byte[2], answer = new byte[1];
        int requestNumber = 0;
        do {
            try {
                sendShoot(position);
                this.inputStream.read(header);
                requestNumber++;
            } catch (Exception e) {
                throw new Exception("The Connection With The Server Have Been Lost");
            }
        } while (header[1] != 2 && requestNumber < 3);
        // The version have been tested at the connection so we just have to wait for a correct response
        // We can continue only when the shoot has been resolve by the server
        if (header[1] != 2) {
            throw new Exception("No Longer synchronize with the Server");
        }
        this.inputStream.read(answer);

        //Print the answer for the user
        if (answer[0] == 0) {
            System.out.println("You Miss");
        } else if (answer [0] == 1) {
            System.out.println("You Hit A Destroyer");
        } else if (answer [0] == 2) {
            System.out.println("You Hit A Submarine");
        } else if (answer [0] == 3) {
            System.out.println("You Hit A Cruiser");
        } else if (answer [0] == 4) {
            System.out.println("You Hit A Battleship");
        } else if (answer [0] == 5) {
            System.out.println("You Hit A Carrier");
        } else {
            System.out.println("Shoot : Unexpected answer");
            System.exit(-1);
        }

        // Update the tried position
        this.triedShoot.add(position);
    }

    private void displayBoard() throws Exception {
        //Display the discovered board to the user

        //retrieve the data from the server
        byte[] header = new byte[2], historyLength = new byte[1], history;
        int requestNumber = 0;
        do {
            try {
                sendRequestHistory();
                this.inputStream.read(header);
                requestNumber++;
            } catch (IOException e) {
                throw new Exception("The Connection With The Server Have Been Lost");
            }
        } while (header[1] != 3 && requestNumber < 3);
        if (header[1] != 3) {
            throw new Exception("No Longer Synchronize this the server");
        }
        this.inputStream.read(historyLength);
        history = new byte[2 * (int) historyLength[0]];
        this.inputStream.read(history);

        //Create the reresentation of the board and fill in the current info
        String[] board = new String[100];
        for (int i=0; i < 100; i++) {
            board[i] = "?";
        }
        for (int i = 0; i < history.length; i += 2) {
            if (history[i + 1] == 0) {
                board[history[i]] = "~";
            } else if (history[i + 1] == 1) {
                board[history[i]] = "D";
            } else if (history[i + 1] == 2) {
                board[history[i]] = "S";
            } else if (history[i + 1] == 3) {
                board[history[i]] = "U";
            } else if (history[i + 1] == 4) {
                board[history[i]] = "B";
            } else if (history[i + 1] == 5) {
                board[history[i]] = "A";
            } else {
                throw new Exception("Unexpected Ship Type");
            }
        }

        //The display
        System.out.println("\nCurrent Board State :  ");
        System.out.println("    A B C D E F G H I J ");
        System.out.println("  -----------------------");
        StringBuilder line;
        for (int i=0; i < 10; i++) {
            line = new StringBuilder(i + " |");
            for(int j=0; j < 10; j++) {
                line.append(" ");
                line.append(board[10 * j + i]);
            }
            line.append(" |");
            if (i == 1) { line.append("   ?: Unknow"); }
            if (i == 2) { line.append("   ~: Empty"); }
            if (i == 3) { line.append("   D: Destoyer   (2)"); }
            if (i == 4) { line.append("   S: Submarine  (3)"); }
            if (i == 5) { line.append("   U: Cruiser    (3)"); }
            if (i == 6) { line.append("   B: Battleship (4)"); }
            if (i == 7) { line.append("   A: Carrier    (5)"); }
            System.out.println(line);
        }
        System.out.println("  -----------------------");
    }

    private void Game() throws Exception {
        Scanner consoleInput = new Scanner(System.in);

        // Handle the game
        // "HandShake" with the server : initialize the game and check that the version match
        sendNewGame();
        byte[] header = new byte[2];
        this.inputStream.read(header);
        if (header[0] != (byte) version) {
            throw new Exception("Client Version (" + version +") Does Not Match Server Version (" + header[0] + ")");
        }
        if (header[1] != 1) {
            throw new Exception("Unexpected Server Answer");
        }

        //loop handle the 70-shoot long game
        while (this.triedShoot.size() < gameLength && !hasWin()) {
            System.out.println("\nWhat Do You Want To Do ?");
            System.out.println("  1. Try A Tile");
            System.out.println("  2. Display Game");
            System.out.println("  3. Quit Current Game");
            System.out.print("Your Choice : ");
            String userInput = consoleInput.nextLine();

            if (userInput == null || userInput.length() != 1) {
                System.out.println("Invalid Choice");
                continue;
            } else if (Character.getNumericValue(userInput.charAt(0)) == 1) {
                this.shootHandler();
            } else if (Character.getNumericValue(userInput.charAt(0)) == 2) {
                this.displayBoard();
            } else if (Character.getNumericValue(userInput.charAt(0)) == 3) {
                break;
            } else{
                System.out.println("Invalid Choice");
                continue;
            }
        }
        if (hasWin()) {
            System.out.println("Congratulation, You Have Defeat The Ennemy Fleet");
        } else if (this.triedShoot.size() == gameLength){
            System.out.println("You Have Been Defeated, Try Again");
        }
        this.outputStream.close();
        this.inputStream.close();
        this.socket.close();
    }

    public static void main(String[] args) {
        Scanner consoleInput = new Scanner(System.in);

        System.out.println("    Welcome To The Game Of Battleship!");
        BattleshipClient client;

        while (true) {
            System.out.println("\nWhat Do You Want To Do ?");
            System.out.println("  1. Start A New Game");
            System.out.println("  2. Quit");
            System.out.print("Your Choice : ");
            String userInput = consoleInput.nextLine();

            if (userInput == null || userInput.length() != 1) {
                System.out.println("Invalid Choice");
            } else if (Character.getNumericValue(userInput.charAt(0)) == 1) {
                try {
                    client = new BattleshipClient();
                    client.Game();
                } catch (Exception e) { System.out.println(e.getMessage());}
            } else if (Character.getNumericValue(userInput.charAt(0)) == 2) {
                System.out.println("\nThanks For Playing !");
                System.exit(0);
            } else {
                System.out.println("Invalid Choice");
            }
        }
    }
}