package hu.ppke.itk.java.labyrinthv09.server;

public class Main {
    static final String USAGE = "server <row count> <column count>";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println(USAGE);
            System.exit(1);
        }

        try {
            int rows = Integer.parseInt(args[0]);
            int cols = Integer.parseInt(args[1]);

            if (rows <= 0 || cols <= 0) {
                System.out.println("Row & column count must be positive.");
                System.exit(1);
            }

            Session.LABYRINTH_ROWS = rows;
            Session.LABYRINTH_COLS = cols;
        }
        catch (NumberFormatException e) {
            System.out.println(USAGE);
            System.exit(1);
        }

        Server s = new Server();
        s.run(6900);
    }
}
