package hu.ppke.itk.java.labyrinthv09.server;

import java.io.*;
import java.net.*;
import java.util.List;

import hu.ppke.itk.java.labyrinthv09.LabyrinthProtos.*;
import hu.ppke.itk.java.labyrinthv09.Utils;

public class Session extends Thread {
    //region Error messages
    static final String ERR_USERNAME_MISSING = "Username cannot be empty.";
    static final String ERR_SESSION_MISMATCH = "Session ID mismatch.";
    static final String ERR_SESSION_EXISTS = "Session already established.";
    static final String ERR_GAME_EXISTS = "Game already in progress.";
    static final String ERR_GAME_MISMATCH = "Labyrinth ID mismatch.";
    static final String ERR_BAD_LABYRINTH = "Labyrinth ID cannot be empty";
    static final String ERR_GAME_MISSING = "Game wasn't started.";
    static final String ERR_BAD_MOVE = "Can't move into wall";
    //endregion

    public static int LABYRINTH_ROWS = 5;
    public static int LABYRINTH_COLS = 5;

    private final Socket socket;
    private boolean running;
    private boolean closing;
    private boolean hasSession;

    private final String sessionId;
    private String labyrinthId;

    static LabyrinthGenerator labyrinthGen;
    static LabyrinthGenerator labyrinthCache;
    static String[] labyrinthFiles = {
        "lab_r12c5.txt",
        "lab_r5c5.txt",
        "lab_r5c17.txt",
        "lab_r8c8.txt",
        "lab_r8c15.txt",
        "lab_r20c21.txt",
        "lab_r1c23.txt",
        "lab_r2c23.txt",
        "lab_r4c23.txt",
        "lab_r100c100.txt",
        "reverse.txt",
        "reverse100.txt"
    };

    int[][] labyrinth;
    Point playerPos;
    Point exitPos;

    public Session(Socket socket) {
        this.socket = socket;
        this.running = true;
        this.sessionId = Utils.randomString(8);
        log("Create session");
    }

    void log(String message) {
        System.out.printf("[%s] %s\n", sessionId, message);
    }

    void loop(InputStream in, OutputStream out) throws IOException {
        Request req = Request.parseDelimitedFrom(in);
        if (req == null) {
            log("Unexpected end of input. Close connection");
            running = false;
            return;
        }

        Response resp;
        switch (req.getSelectorCase()) {
            case STARTSESSIONREQUEST:
                resp = startSession(req.getStartSessionRequest());
                break;
            case STARTGAMEREQUEST:
                resp = startGame(req.getStartGameRequest());
                break;
            case LOOKAROUNDREQUEST:
                resp = lookAround(req.getLookAroundRequest());
                break;
            case MOVETOREQUEST:
                resp = moveTo(req.getMoveToRequest());
                break;
            case QUITGAMEREQUEST:
                resp = quitGame(req.getQuitGameRequest());
                break;
            case CLOSESESSIONREQUEST:
                resp = closeSession(req.getCloseSessionRequest());
                break;
            default:
                resp = Response.newBuilder().build();
                break;
        }
        resp.writeDelimitedTo(out);

        if (closing) running = false;
    }

    Response startSession(startSessionRequest req) {
        var ssr = startSessionResponse.newBuilder();

        if (hasSession) {
            ssr.setSessionId(sessionId);
            ssr.setStatus(Status.SESSION_ALLOCATION_ERROR);
            ssr.setCause(ERR_SESSION_EXISTS);
            log(ERR_SESSION_EXISTS);
        }
        else if (req.getUsername().equals("")) {
            ssr.setSessionId("");
            ssr.setStatus(Status.SESSION_ALLOCATION_ERROR);
            ssr.setCause(ERR_USERNAME_MISSING);
            log(ERR_USERNAME_MISSING);
        }
        else {
            hasSession = true;

            ssr.setSessionId(sessionId);
            ssr.setStatus(Status.SUCCESS);
            log("Session created for user [" + req.getUsername() + ']');
        }

        return Response.newBuilder()
            .setStartSessionResponse(ssr)
            .build();
    }

    boolean initializeGenerators() {
        if (labyrinthCache != null && labyrinthGen != null) {
            return true;
        }

        if (labyrinthFiles.length == 0) {
            log("WARNING: no labyrinth files specified");
            return false;
        }

        try {
            var files = List.of(labyrinthFiles);
            labyrinthCache = LabyrinthLite.preload(files);
            labyrinthGen = new Labyrinth(LABYRINTH_ROWS, LABYRINTH_COLS);
            return true;
        }
        catch (IOException e) {
            log("Error while reading labyrinth files");
            return false;
        }
    }

    LabyrinthGenerator assignGenerator(String labyrinthId) {
        for (String fname : labyrinthFiles) {
            if (fname.equals(labyrinthId)) {
                log("Labyrinth file '"+labyrinthId+"' found");
                return labyrinthCache;
            }
        }

        log("Labyrinth file '"+labyrinthId+"' not found, using ID as seed");
        return labyrinthGen;
    }

    Response startGame(startGameRequest req) {
        var sgr = startGameResponse.newBuilder();

        if (!sessionId.equals(req.getSessionId())) {
            sgr.setSessionId("");
            sgr.setLabyrinthId("");
            sgr.setStatus(Status.SESSION_ALLOCATION_ERROR);
            sgr.setCause(ERR_SESSION_MISMATCH);
            log(ERR_SESSION_MISMATCH);
        }
        else if (labyrinthId != null) {
            sgr.setSessionId(sessionId);
            sgr.setLabyrinthId("");
            sgr.setStatus(Status.PROTOCOL_ERROR);
            sgr.setCause(ERR_GAME_EXISTS);
            log(ERR_GAME_EXISTS);
        }
        else if (req.hasLabyrinthId() &&
                 req.getLabyrinthId().equals(""))
        {
            sgr.setSessionId(sessionId);
            sgr.setLabyrinthId("");
            sgr.setStatus(Status.GAME_ALLOCATION_ERROR);
            sgr.setCause(ERR_BAD_LABYRINTH);
            log(ERR_BAD_LABYRINTH);
        }
        else if (!initializeGenerators()) {
            sgr.setSessionId(sessionId);
            sgr.setLabyrinthId("");
            sgr.setStatus(Status.GAME_ALLOCATION_ERROR);
            sgr.setCause("");
            log("Could not load labyrinths from files");
        }
        else {
            LabyrinthGenerator generator;

            if (req.hasLabyrinthId()) {
                labyrinthId = req.getLabyrinthId();
                generator = assignGenerator(labyrinthId);
            }
            else {
                labyrinthId = Utils.randomString(8);
                generator = labyrinthGen;
            }

            labyrinth = generator.generate(labyrinthId);
            playerPos = generator.getStart(labyrinth);
            exitPos = generator.getExit(labyrinth);

            System.out.println(generator.format(labyrinth));

            sgr.setSessionId(sessionId);
            sgr.setLabyrinthId(labyrinthId);
            sgr.setStatus(Status.SUCCESS);
            log("Game started");
        }

        return Response.newBuilder()
            .setStartGameResponse(sgr)
            .build();
    }


    lookAroundResponse.View neighbors(Point p) {
        return lookAroundResponse.View.newBuilder()
            .setCenter(ViewElement.forNumber(labyrinth[p.y][p.x]))
            .setNorth(ViewElement.forNumber(labyrinth[p.y-1][p.x]))
            .setSouth(ViewElement.forNumber(labyrinth[p.y+1][p.x]))
            .setEast(ViewElement.forNumber(labyrinth[p.y][p.x+1]))
            .setWest(ViewElement.forNumber(labyrinth[p.y][p.x-1]))
            .build();
    }

    Response lookAround(lookAroundRequest req) {
        var lar = lookAroundResponse.newBuilder();

        if (!sessionId.equals(req.getSessionId())) {
            lar.setSessionId("");
            lar.setLabyrinthId("");
            lar.setStatus(Status.PROTOCOL_ERROR);
            lar.setCause(ERR_SESSION_MISMATCH);
            log(ERR_SESSION_MISMATCH);
        }
        else if (labyrinthId == null) {
            lar.setSessionId(sessionId);
            lar.setLabyrinthId("");
            lar.setStatus(Status.PROTOCOL_ERROR);
            lar.setCause(ERR_GAME_MISSING);
            log(ERR_GAME_MISSING);
        }
        else if (!labyrinthId.equals(req.getLabyrinthId())) {
            lar.setSessionId(sessionId);
            lar.setLabyrinthId("");
            lar.setStatus(Status.PROTOCOL_ERROR);
            lar.setCause(ERR_GAME_MISMATCH);
            log(ERR_GAME_MISMATCH);
        }
        else {
            lar.setSessionId(sessionId);
            lar.setLabyrinthId(labyrinthId);
            lar.setStatus(Status.SUCCESS);
            lar.setView(neighbors(playerPos));
            log("Look around");
        }

        return Response.newBuilder()
            .setLookAroundResponse(lar)
            .build();
    }

    boolean tryMovePlayer(Direction d) {
        int dx = 0, dy = 0;

        switch (d) {
            case NORTH: dy = -1; break;
            case SOUTH: dy = +1; break;
            case EAST:  dx = +1; break;
            case WEST:  dx = -1; break;
        }

        Point newpos = new Point(playerPos.x + dx, playerPos.y + dy);
        if (labyrinth[newpos.y][newpos.x] != ViewElement.WALL_VALUE) {
            playerPos = newpos;
            return true;
        }
        else {
            return false;
        }
    }

    Response moveTo(moveToRequest req) {
        var mtr = moveToResponse.newBuilder();

        if (!sessionId.equals(req.getSessionId())) {
            mtr.setSessionId("");
            mtr.setLabyrinthId("");
            mtr.setStatus(Status.PROTOCOL_ERROR);
            mtr.setCause(ERR_SESSION_MISMATCH);
            log(ERR_SESSION_MISMATCH);
        }
        else if (labyrinthId == null) {
            mtr.setSessionId(sessionId);
            mtr.setLabyrinthId("");
            mtr.setStatus(Status.PROTOCOL_ERROR);
            mtr.setCause(ERR_GAME_MISSING);
            log(ERR_GAME_MISSING);
        }
        else if (!labyrinthId.equals(req.getLabyrinthId())) {
            mtr.setSessionId(sessionId);
            mtr.setLabyrinthId("");
            mtr.setStatus(Status.PROTOCOL_ERROR);
            mtr.setCause(ERR_GAME_MISMATCH);
            log(ERR_GAME_MISMATCH);
        }
        else if (!tryMovePlayer(req.getDirection())){
            mtr.setSessionId(sessionId);
            mtr.setLabyrinthId(labyrinthId);
            mtr.setStatus(Status.FAILURE);
            mtr.setCause(ERR_BAD_MOVE);
            log(ERR_BAD_MOVE);
        }
        else {
            mtr.setSessionId(sessionId);
            mtr.setLabyrinthId(labyrinthId);
            mtr.setStatus(Status.SUCCESS);

            String dir = req.getDirection().name();
            dir = dir.charAt(0) + dir.substring(1).toLowerCase();

            log("Move " + dir);
        }

        return Response.newBuilder()
            .setMoveToResponse(mtr)
            .build();
    }

    Response quitGame(quitGameRequest req) {
        var qgr = quitGameResponse.newBuilder();

        if (!sessionId.equals(req.getSessionId())) {
            qgr.setSessionId("");
            qgr.setLabyrinthId("");
            qgr.setStatus(Status.PROTOCOL_ERROR);
            qgr.setCause(ERR_SESSION_MISMATCH);
            log(ERR_SESSION_MISMATCH);
        }
        else if (labyrinthId == null) {
            qgr.setSessionId(sessionId);
            qgr.setLabyrinthId("");
            qgr.setStatus(Status.PROTOCOL_ERROR);
            qgr.setCause(ERR_GAME_MISSING);
            log(ERR_GAME_MISSING);
        }
        else if (!labyrinthId.equals(req.getLabyrinthId())) {
            qgr.setSessionId(sessionId);
            qgr.setLabyrinthId("");
            qgr.setStatus(Status.PROTOCOL_ERROR);
            qgr.setCause(ERR_GAME_MISMATCH);
            log(ERR_GAME_MISMATCH);
        }
        else {
            int playerCell = labyrinth[playerPos.y][playerPos.x];
            var gamestate = playerCell == ViewElement.EXIT_VALUE
                ? GameStatus.WON
                : GameStatus.LOST;

            qgr.setSessionId(sessionId);
            qgr.setLabyrinthId(labyrinthId);
            qgr.setStatus(Status.SUCCESS);
            qgr.setGameStatus(gamestate);

            labyrinthId = null;
            log("Game ended");
        }

        return Response.newBuilder()
            .setQuitGameResponse(qgr)
            .build();
    }

    Response closeSession(closeSessionRequest req) {
        var csr = closeSessionResponse.newBuilder();

        if (!sessionId.equals(req.getSessionId())) {
            csr.setSessionId("");
            csr.setStatus(Status.FAILURE);
            csr.setCause(ERR_SESSION_MISMATCH);
            log(ERR_SESSION_MISMATCH);
        }
        else {
            hasSession = false;
            labyrinthId = null;

            closing = true;

            csr.setSessionId(sessionId);
            csr.setStatus(Status.SUCCESS);
            log("Session ended");
        }

        return Response.newBuilder()
            .setCloseSessionResponse(csr)
            .build();
    }

    void closeSocket() {
        try {
            socket.close();
            log("Closed socket");
        }
        catch (IOException e) {
            log("Failed to close socket");
        }
    }

    @Override
    public void run() {
        log("Start session");

        InputStream in;
        OutputStream out;

        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
            log("Open communication streams");
        }
        catch (IOException e) {
            log("Error:" + e.getMessage());
            log("Failed to open communication streams");
            closeSocket();
            return;
        }

        try {
            while (running) {
                loop(in, out);
            }
            log("Closing session");
        }
        catch (IOException e) {
            log("Error: " + e.getMessage());
            log("Error during communication");
        }
        finally {
            closeSocket();
        }

        log("Good-bye");
    }
}
