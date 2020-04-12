import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

public class BattleshipGameHandler extends Thread{

    private static int version = 1;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private ArrayList<ArrayList<Byte>> ship;
    private ArrayList<byte[]> history;
    private static int gameLength = 70;

    public BattleshipGameHandler(Socket socket) throws Exception {
        super("BattleshipGameHandler");
        try {
            this.socket = socket;
            this.socket.setTcpNoDelay(true);
            this.socket.setSoTimeout(5 * 60000); //a read call unanswer for 5 min throw an error (so deconnect if the clent is idle
            this.outputStream = this.socket.getOutputStream();
            this.inputStream = this.socket.getInputStream();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died During Initialisation");
        }

        this.history = new ArrayList<>();
        this.ship = new ArrayList<>();

        System.out.println("New Game Handler Created");
    }

    private void initializeShip(int len) {
        Random r = new Random();
        boolean done = false, cellUsed;
        int begin, orientation;
        ArrayList<Byte> position;
        while (!done) {
            begin = r.nextInt(99);
            orientation = r.nextInt(3);
            cellUsed = false;
            if (orientation == 0
                    && begin - 10 * len >= 0) {
                position = new ArrayList<>();
                for (int i=0; i < len; i++) {
                    for(ArrayList<Byte> toTest : this.ship) {
                        if (toTest.contains((byte) (begin - 10 * i))) {
                            cellUsed = true;
                        }
                    }
                    position.add((byte) (begin - 10 * i));
                }
                if (!cellUsed) {
                    this.ship.add(position);
                    done = true;
                }
            } else if (orientation == 1
                    && (begin - begin % 10) - ((begin + len) - (begin + len) % 10) == 0) {
                position = new ArrayList<>();
                for (int i=0; i < len; i++) {
                    for(ArrayList<Byte> toTest : this.ship) {
                        if (toTest.contains((byte) (begin + i))) {
                            cellUsed = true;
                        }
                    }
                    position.add((byte) (begin + i));
                }
                if (!cellUsed) {
                    this.ship.add(position);
                    done = true;
                }
            } else if (orientation == 2
                    && begin + 10 * len <= 99) {
                position = new ArrayList<>();
                for (int i=0; i < len; i++) {
                    for(ArrayList<Byte> toTest : this.ship) {
                        if (toTest.contains((byte) (begin + 10 * i))) {
                            cellUsed = true;
                        }
                    }
                    position.add((byte) (begin + 10 * i));
                }
                if (!cellUsed) {
                    this.ship.add(position);
                    done = true;
                }
            } else if ((begin - begin % 10) - ((begin - len) - ((begin - len) % 10)) == 0) {
                position = new ArrayList<>();
                for (int i=0; i < len; i++) {
                    for(ArrayList<Byte> toTest : this.ship) {
                        if (toTest.contains((byte) (begin - i))) {
                            cellUsed = true;
                        }
                    }
                    position.add((byte) (begin - i));
                }
                if (!cellUsed) {
                    this.ship.add(position);
                    done = true;
                }
            }
        }
    }

    private void sendNewGame() throws Exception {
        try {
            byte[] msg = new byte[2];
            msg[0] = (byte) version;
            msg[1] = (byte) 1;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died : Can Not Reach Client");
        }
    }

    private void sendHit(int boat) throws Exception {
        try {
            byte[] msg = new byte[3];
            msg[0] = (byte) version;
            msg[1] = (byte) 2;
            msg[2] = (byte) boat;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died : Can Not Reach Client");

        }
    }

    private void sendMiss() throws Exception {
        try {
            byte[] msg = new byte[3];
            msg[0] = (byte) version;
            msg[1] = (byte) 2;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died : Can Not Reach Client");
        }
    }

    private void sendHistory() throws Exception {
        try {
            byte[] msg = new byte[3 + 2 * this.history.size()];
            msg[0] = (byte) version;
            msg[1] = (byte) 3;
            msg[2] = (byte) this.history.size();
            for (int i=0; i < this.history.size(); i++) {
                msg[3 + 2 * i] = this.history.get(i)[0];
                msg[3 + 2 * i + 1] = (byte) (this.history.get(i)[1]);
            }
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died : Can Not Reach Client");
        }
    }

    private void sendError() throws Exception {
        try {
            byte[] msg = new byte[2];
            msg[0] = (byte) version;
            msg[1] = (byte) 4;
            this.outputStream.write(msg);
            this.outputStream.flush();
        } catch (IOException e) {
            System.out.println("    " + e);
            throw new Exception("Game Handler Died : Can Not Reach Client");
        }
    }

    private void shootHandler(byte position) throws Exception {
        //handle the shhot dialogue this the client
        //Expected exception: can't reach server (in send method or read)
        if (0 > position || 99 < position) {
            sendError();
            return;
        }

        for (int shipNumber = 0; shipNumber < this.ship.size(); shipNumber++) {
            //test each ship
            for (byte i: this.ship.get(shipNumber)) {
                if (i == position) {
                    byte[] shoot = new byte[2];
                    shoot[0] = position;
                    shoot[1] = (byte) (shipNumber + 1);
                    this.history.add(shoot);
                    sendHit(shipNumber + 1);  // 0 is for a miss
                    return;
                }
            }
        }

        // player have miss
        byte[] shoot = new byte[2];
        shoot[0] = position;
        this.history.add(shoot);
        sendMiss();
    }

    private void displayBoard () {
        //Display the discovered board to the user
        String[] board = new String[100];
        for (int i=0; i < 100; i++) {
            board[i] = "~";
        }
        for (int i=0; i < this.ship.size(); i++) {
            for (byte position : this.ship.get(i)) {
                if (i == 0) {
                    board[position] = "D";
                } else if (i == 1) {
                    board[position] = "S";
                } else if (i == 2) {
                    board[position] = "U";
                } else if (i == 3) {
                    board[position] = "B";
                } else {
                    board[position] = "A";
                }
            }
        }

        //The display
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
            if (i == 2) { line.append("   ~: Empty"); }
            if (i == 3) { line.append("   D: Destoyer"); }
            if (i == 4) { line.append("   S: Submarine"); }
            if (i == 5) { line.append("   U: Cruiser"); }
            if (i == 6) { line.append("   B: Battleship"); }
            if (i == 7) { line.append("   A: Carrier"); }
            System.out.println(line);
        }
        System.out.println("  -----------------------");
    }

    public void run() {
        byte[] header = new byte[2];
        try {
            this.inputStream.read(header);
            if (header[0] == (byte) version && header[1] == 0) {
                this.initializeShip(2);
                this.initializeShip(3);
                this.initializeShip(3);
                this.initializeShip(4);
                this.initializeShip(5);
                System.out.println("New Game Handler Initialize");
                System.out.println("Board :");
                this.displayBoard();
                sendNewGame();
            } else {
                sendError();
            }
            while (this.socket.isConnected() && this.history.size() < gameLength) {
                this.inputStream.read(header);

                if (header[0] != (byte) version) {
                    sendError();
                } else if (header[1] == 0) {
                    sendError();
                } else if (header[1] == (byte) 1) {
                    byte position = (byte) this.inputStream.read();
                    shootHandler(position);
                } else if (header[1] == (byte) 2) {
                    sendHistory();
                } else {
                    sendError();
                }
            }
            while (this.socket.isConnected()) {
                this.inputStream.read(header);

                if (header[0] != (byte) version && header[1] == (byte) 2) {
                    sendHistory();
                } else {
                    sendError();
                }
            }
            this.outputStream.close();
            this.inputStream.close();
            this.socket.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
