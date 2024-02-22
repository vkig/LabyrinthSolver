package hu.ppke.itk.pakge2;

import hu.ppke.itk.java.labyrinthv09.LabyrinthProtos.*;
import javafx.application.Platform;
import javafx.scene.paint.Color;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * A labyrintust bejáró, a szerverrel kommunikáló a Thread osztályból örökölt osztály
 */
public class LabyrinthModell extends Thread{
    protected LabyrinthView labyrinthView;
    protected Socket clientSocket;
    protected InputStream input;
    protected OutputStream output;
    protected Status status;
    protected GameStatus gameStatus = GameStatus.LOST;
    protected String labyrinthId = "reverse100.txt", sessionId, username, ip, port;
    protected boolean stop = false, connected = false, start = true;

    /**
     * Belső osztály a labirintus mezőinek reprezentálására
     */
    protected class Field implements Comparable<Field>, Comparator<Field>, Serializable{
        Integer x, y;
        ViewElement type;

        /**
         * Konstructor a belső osztályhoz
         * @param x A szélességi koordinátája a kezdőponthoz (0,0) viszonyítva relatívan
         * @param y A hosszúsági koordinátája a kezdőponthoz (0,0) viszonyítva relatívan
         * @param type A mező típusa: WALL, WAY, START és EXIT lehet
         */
        public Field(Integer x, Integer y, ViewElement type){
            this.x = x;
            this.y = y;
            this.type = type;
        }

        /**
         * Konstruktor érkezési iránnyal együtt
         * @param x Ahonnan az adott mezőre érkeztünk szélességi irányban
         * @param y Ahonnan az adott mezőre érkeztünk hosszúsági irányban
         * @param type A mező típusa
         * @param dir A mezőre érkezés iránya
         */
        public Field(Integer x, Integer y, ViewElement type, Direction dir){
            switch (dir){
                case WEST: {this.x = x - 1; this.y = y; break;}
                case EAST: {this.x = x + 1; this.y = y; break;}
                case NORTH: {this.x = x; this.y = y - 1; break;}
                case SOUTH: {this.x = x; this.y = y + 1; break;}
            }
            this.type = type;
        }

        /**
         * Másoló konstriktor, mely a paraméterként kapott mezőt lemásolja
         * @param o A másoldandó mező
         */
        public Field(Field o){
            this.x = o.x;
            this.y = o.y;
            this.type = o.type;
        }

        /**
         * A mező elmozdítása egy dott irányba
         * @param dir Az irány
         */
        public void move(Direction dir){
            switch (dir){
                case EAST: x = x + 1; break;
                case WEST: x = x - 1; break;
                case NORTH: y = y - 1; break;
                case SOUTH: y = y + 1; break;
            }
            if(x == 0 && y == 0){
                type = ViewElement.START;
            }
        }

        /**
         * Két mező példányt hasonlít össze, megállapítható segítságável hogy két mező ugyanaz-e
         * @param o1 Az egyik mező
         * @param o2 A másik mező
         * @return 0 ha a két mező értékeiben megegyezik, 1 ha az x paramétere nagyobb az elsőnek, ha egyenlő, akkor az
         * y-t vizsgálja
         */
        @Override
        public int compare(Field o1, Field o2) {
            if(o1.x.equals(o2.x) && o1.y.equals(o2.y)){
                return 0;
            }
            if(!(o1.x.equals(o2.x))){
                return o1.x.compareTo(o2.x);
            }
            return o1.y.compareTo(o2.y);
        }

        /**
         * Két mező egyenlőségének vizsgálatát teszi lehetővé
         * @param o A másik mező
         * @return true, ha a két mező pozíciója megegyezik, false egyébként
         */
        @Override
        public boolean equals(Object o){
            if(o == this){
                return true;
            }
            if(!(o instanceof Field)){
                return false;
            }
            Field f = (Field) o;
            return compare(this, f) == 0;
        }

        /**
         * Az aktuális mezőt hasonlítja egy másikhoz
         * @param o A másik mező
         * @return 0 ha megegyeznek, 1 ha nagyobb az aktuális, -1, ha kisebb, mint a paraméterként kapott
         */
        @Override
        public int compareTo(Field o) {
            return compare(this, o);
        }
    }
    protected Field topLeft, topRight, bottomLeft, bottomRight, startField;
    protected TreeMap<Field, LinkedList<Direction>> fields;
    protected TreeSet<Field> allFields;
    protected Stack<Direction> path;

    /**
     * Beállítja az osztály mezőit, megpróbál csatlakozni a GUI felületen megadott szerverhez
     * @param labyrinthView Paraméterként megkapja az osztály a GUI-t megvalósíó osztály példányát
     * @param username A felhasználónév, amellyel csatlakozunk
     * @param ip Az ip cím, amin csatlakozik a szerverhez
     * @param port A port, amelyen csatlakozik
     */
    public LabyrinthModell(LabyrinthView labyrinthView, String username, String ip, String port){
        this.labyrinthView = labyrinthView;
        this.username = username;
        this.ip = ip;
        this.port = port;
        try {
            clientSocket = new Socket(ip, Integer.parseInt(port));
            input = clientSocket.getInputStream();
            output = clientSocket.getOutputStream();
            startSession();
            connected = true;
        } catch (Exception e) {
            Platform.runLater(()->
            {
                labyrinthView.setMessage("[ERROR] " + "Error occured while connecting to the server");
                labyrinthView.setDefault();
            });
        }
    }

    /**
     * Elindít egy ciklust, mely 50 ms-os szünetekkel vizsgálja, hogy még csatlakozva vagyunk-e, illetve hogy
     * indítunk-e új játékot
     */
    @Override
    public void run() {
        while(connected){
            if(start){
                start = false;
                startGame();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        closeSession();
        labyrinthView.setDefault();
    }

    /**
     * A labirintusból kitaláló algoritmus egy gráfként fogja fel a labirintus mezőit és ezeken egy mélységi bejárást hajt végre
     */
    public void solveLabyrinth(){
        fields = new TreeMap<>();
        allFields = new TreeSet<Field>();
        path = new Stack<>();
        Field actual = new Field(0, 0, ViewElement.START);
        startField = new Field(actual);
        topLeft = new Field(actual);
        topRight = new Field(actual);
        bottomLeft = new Field(actual);
        bottomRight = new Field(actual);
        allFields.add(new Field(actual));
        while(!stop){
            Field next;
            Field finalActual = actual;
            Platform.runLater(()->{
                labyrinthView.setMessage("[INFO] Look around at " + finalActual.x.toString() + " " + finalActual.y.toString());
            });
            lookAroundResponse.View view = lookAround();

            if(!stop){
                boolean exit;
                if(path.empty()){
                    exit = addDirectionsToField(actual,view, null);
                }
                else{
                    exit = addDirectionsToField(actual,view,path.peek());
                }
                Integer d = getDirection(actual);
                if(d != -1){
                    path.push(Direction.forNumber(d));
                    if(exit){
                        next = new Field(actual.x,actual.y,ViewElement.EXIT, path.peek());
                    }
                    else {
                        next = new Field(actual.x, actual.y, ViewElement.WAY, path.peek());
                    }
                    allFields.add(new Field(next));
                    moveTo(Direction.forNumber(d));
                    if(stop){
                        break;
                    }
                    actual = next;
                    if(actual.x <= topLeft.x && actual.y <= topLeft.y){
                        topLeft = new Field(actual);
                    }
                    if(actual.x <= bottomLeft.x && actual.y >= bottomLeft.y){
                        bottomLeft = new Field(actual);
                    }
                    if(actual.x >= topRight.x && actual.y <= topRight.y){
                        topRight = new Field(actual);
                    }
                    if(actual.x >= bottomRight.x && actual.y >= bottomRight.y){
                        bottomRight = new Field(actual);
                    }
                }
                else{
                    while(!(fields.containsKey(actual) && fields.get(actual).size() > 0) && !interrupted()){
                        Direction dir = path.pop();
                        dir = Direction.forNumber((dir.getNumber() + 2)%4);
                        actual.move(dir);
                        moveTo(dir);
                        if(stop){
                            break;
                        }
                    }
                    if(stop){
                        break;
                    }
                }
            }
            if(actual.type == ViewElement.EXIT){
                stop = true;
            }
        }
        labyrinthView.setBeforeStart();
        quitGame();
    }

    /**
     * Egy adott mezőhöz hozzáadja az onnan lehetséges további irányokat, tehát amerre nincsen fal
     * @param field A kérdéses mező
     * @param view A mező környezete önmagát is beleértve
     * @param prevdir Az irány, amelyből érkeztünk
     * @return Azt jelzi, hogy valamely mező a környzetből a kiutat jelenti-e true - kiút, false - nem kiút
     */
    public boolean addDirectionsToField(Field field, lookAroundResponse.View view, Direction prevdir){
        if(fields.containsKey(field)) {
            return false;
        }
        if(prevdir != null){
            prevdir = Direction.valueOf((prevdir.getNumber() + 2 ) % 4);
        }
        if(view.getEast() != ViewElement.WALL && prevdir != Direction.EAST){
            if (!fields.containsKey(field)) {
                fields.put(field, new LinkedList<>());
            }
            fields.get(field).add(Direction.EAST);
            if(view.getEast() == ViewElement.EXIT){
                return true;
            }
        }
        if(view.getWest() != ViewElement.WALL && prevdir != Direction.WEST){
            if (!fields.containsKey(field)) {
                fields.put(field, new LinkedList<>());
            }
            fields.get(field).add(Direction.WEST);
            if(view.getWest() == ViewElement.EXIT){
                return true;
            }
        }
        if(view.getNorth() != ViewElement.WALL && prevdir != Direction.NORTH){
            if (!fields.containsKey(field)) {
                fields.put(field, new LinkedList<>());
            }
            fields.get(field).add(Direction.NORTH);
            if(view.getNorth() == ViewElement.EXIT){
                return true;
            }
        }
        if(view.getSouth() != ViewElement.WALL && prevdir != Direction.SOUTH){
            if (!fields.containsKey(field)) {
                fields.put(field, new LinkedList<>());
            }
            fields.get(field).add(Direction.SOUTH);
            if(view.getSouth() == ViewElement.EXIT){
                return true;
            }
        }
        return false;
    }

    /**
     * Az adott mező nem fal és még korábban meg nem látogatott szomszédai közül d vissza egyet véletlenszerű
     * en
     * @param field A mező melyből az irány kell
     * @return Egy irány: EAST, WEST, NORTH, SOUTH
     */
    public Integer getDirection(Field field){
        Random rd = new Random();
        if(!fields.containsKey(field) || fields.get(field).size() == 0){
            return -1;
        }
        int random = rd.nextInt(fields.get(field).size());
        Field next = new Field(field.x, field.y, ViewElement.WAY, fields.get(field).get(random));
        while(allFields.contains(next)){
            fields.get(field).remove(random);
            if(fields.get(field).isEmpty()){
                fields.remove(field);
                return -1;
            }
            random = rd.nextInt(fields.get(field).size());
            next = new Field(field.x, field.y, ViewElement.WAY, fields.get(field).get(random));
        }
        Integer res = fields.get(field).get(random).getNumber();
        fields.get(field).remove((int) random);
        if(fields.get(field).size() == 0){
            fields.remove(field);
        }
        return res;
    }

    /**
     * Elindítja a szerverrel való kommunikáció segítségével a kapcsolatot, adott protokoll lépésekkel
     */
    public void startSession(){
        try {
            startSessionRequest.Builder builder = startSessionRequest.newBuilder();
            builder.setUsername(username);
            Request req = Request.newBuilder().setStartSessionRequest(builder).build();
                req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if(resp.hasStartSessionResponse()){
                startSessionResponse sresp = resp.getStartSessionResponse();
                sessionId = sresp.getSessionId();
                status = sresp.getStatus();
                if(status == Status.SUCCESS){
                    Platform.runLater(()->{
                        labyrinthView.setMessage("[INFO] " + "Connection " + status);
                        labyrinthView.setConnectionStatus("active");
                        labyrinthView.setMessage("[INFO] " + "Session Started, id = " + sessionId);
                    });
                }
                else{
                    Platform.runLater(()->{
                        labyrinthView.setMessage("[ERROR] " + "Error occured while startin Session");
                        labyrinthView.setMessage("[ERROR] " + "Error message: " + status);
                        labyrinthView.setDefault();
                    });
                }
            }
            else{
                Platform.runLater(()->{
                    labyrinthView.setMessage("[ERROR] The startSession response wasn't received");
                });
            }
        } catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during starting session");
            });
        }
    }

    /**
     * Elindítja a játék megoldását
     */
    public void startGame(){
        try {
            startGameRequest.Builder builder = startGameRequest.newBuilder();
            if (labyrinthId != null) {
                builder.setLabyrinthId(labyrinthId);
            }
            builder.setSessionId(sessionId);
            Request req = Request.newBuilder().setStartGameRequest(builder).build();
            req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if (resp.hasStartGameResponse()) {
                startGameResponse sresp = resp.getStartGameResponse();
                labyrinthId = sresp.getLabyrinthId();
                status = sresp.getStatus();
            }
            if (status == Status.SUCCESS) {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[INFO] Game is started, labyrinthID: " + labyrinthId);
                    labyrinthView.setGameStatus("Running");
                    labyrinthView.setLabyrinthId(labyrinthId);
                });
                stop = false;
                solveLabyrinth();
            } else {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[ERROR] Error occured");
                    labyrinthView.setMessage("[ERROR] Error message: " + status);
                });
            }
        }
        catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during starting game");
            });
        }
    }

    /**
     * Az aktuális mező (melyet alapvetően a szerver tárol) környezetét kéri le
     * @return A paraméterként kapott mező környezete
     */
    public lookAroundResponse.View lookAround(){
        try {
            lookAroundRequest.Builder builder = lookAroundRequest.newBuilder();
            builder.setLabyrinthId(labyrinthId);
            builder.setSessionId(sessionId);
            Request req = Request.newBuilder().setLookAroundRequest(builder).build();
            req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if (resp.hasLookAroundResponse()) {
                lookAroundResponse laresp = resp.getLookAroundResponse();
                status = laresp.getStatus();
                if (status != Status.SUCCESS) {
                    Platform.runLater(() -> {
                        labyrinthView.setMessage("[ERROR] Error occured while looking around");
                        labyrinthView.setMessage("[ERROR] Error message: " + status);
                        labyrinthView.setBeforeStart();
                    });
                    stop = true;
                    return null;
                }
                return laresp.getView();
            }
        } catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during looking around");
            });
        }
        stop = true;
        return null;
    }

    /**
     * Az aktuális mezőről elmozdul a megadott irányba
     * @param dir Az elmozdulás iránya
     */
    public void moveTo(Direction dir){
        try {
            moveToRequest.Builder builder = moveToRequest.newBuilder();
            builder.setLabyrinthId(labyrinthId);
            builder.setSessionId(sessionId);
            builder.setDirection(dir);
            Request req = Request.newBuilder().setMoveToRequest(builder).build();
            req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if (resp.hasMoveToResponse()) {
                moveToResponse mresp = resp.getMoveToResponse();
                status = mresp.getStatus();
            }
            if (status != Status.SUCCESS) {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[ERROR] Error while moving in the labyrinth");
                    labyrinthView.setMessage("[ERROR] Error message: " + status);
                    labyrinthView.setBeforeStart();
                });
                stop = true;
            }
            else{
                Platform.runLater(()->{
                    labyrinthView.setMessage("[INFO] Moving to " + dir);
                });
            }
        } catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during moving in the labyrinth");
            });
        }
    }

    /**
     * A játék befejezése, mely indikálja, hogy a GUI-n megjelenjen, mi lett az eredmény
     */
    public void quitGame(){
        try {
            stop = true;
            quitGameRequest.Builder builder = quitGameRequest.newBuilder();
            builder.setLabyrinthId(labyrinthId);
            builder.setSessionId(sessionId);
            Request req = Request.newBuilder().setQuitGameRequest(builder).build();
                req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if(resp.hasQuitGameResponse()){
                quitGameResponse qresp = resp.getQuitGameResponse();
                status = qresp.getStatus();
                gameStatus = qresp.getGameStatus();
            }
            if(status != Status.SUCCESS){
                Platform.runLater(()->{
                    labyrinthView.setMessage("[ERROR] Error while quitting game");
                    labyrinthView.setMessage("[ERROR] Error message: " + status);
                    labyrinthView.setBeforeStart();
                });
            }
            else {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[INFO] Quit game is " + status);
                    labyrinthView.setMessage("[INFO] The game status is: " + gameStatus);
                    labyrinthView.setGameStatus("Stopped, " + gameStatus);
                });
                drawResults();
            }
        } catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during quitting game");
            });
        }
    }

    /**
     * A szerverrel való kommunikáció bezárása
     */
    public void closeSession(){
        try {
            closeSessionRequest.Builder builder = closeSessionRequest.newBuilder();
            builder.setSessionId(sessionId);
            Request req = Request.newBuilder().setCloseSessionRequest(builder).build();
            req.writeDelimitedTo(output);
            output.flush();
            Response resp = Response.parseDelimitedFrom(input);
            if (resp.hasCloseSessionResponse()) {
                closeSessionResponse sresp = resp.getCloseSessionResponse();
                status = sresp.getStatus();
            }
            if (status == Status.SUCCESS) {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[INFO] Session is closed");
                });
            } else {
                Platform.runLater(() -> {
                    labyrinthView.setMessage("[ERROR] Error occured while closing session");
                    labyrinthView.setMessage("[ERROR] Error message: " + status);
                });
            }
        } catch (Exception e) {
            Platform.runLater(()->{
                labyrinthView.setMessage("[ERROR] Unknown error during closing session");
            });
        }
    }

    /**
     * Újabb labirintus keresés indításához szükséges, hogy igazzá tegyük a start mezőt egy rövid ideig
     */
    public void enableStart(){
        start = true;
    }

    /**
     * Kivülről hívható a stop mezőt igazra beállító metódus
     */
    public void disableStop(){
        stop = true;
    }

    /**
     * Kapcsolat bontása a szerverrel
     */
    public void disconnect(){
        stop = true;
        connected = false;
        if(!isAlive()){
            closeSession();
        }
        Platform.runLater(()-> {
            labyrinthView.setConnectionStatus("incative");
        });
    }

    /**
     * Labirintus id beállítása a paraméterként kapott String alapján
     * @param labyrinthId Az új labyrinth ID
     */
    public void setLabyrinthId(String labyrinthId){
        this.labyrinthId = labyrinthId;
    }

    /**
     * Lekérdezi, hogy a kliens csatlakozva van-e a szerverre
     * @return True - csatlakozva, false - nincs csatlakozva
     */
    public boolean isConnected(){
        return connected;
    }

    /**
     * A labyrintus megfejtésre során bejárt mezőket rajzolja ki, kékkel kiemelve azt az útvonalat, melyen eljutott a
     * kijáratig
     */
    public void drawResults(){
        Integer width;
        Integer height;
        Integer minx;
        Integer miny;
        Integer maxx;
        Integer maxy;
        double a;
        double b;
        minx = bottomLeft.x;
        if(topLeft.x < bottomLeft.x){
            minx = topLeft.x;
        }
        miny = topRight.y;
        if(topLeft.y < topRight.y){
            miny = topLeft.y;
        }
        maxx = bottomRight.x;
        if(topRight.x > bottomLeft.x){
            maxx = bottomRight.x;
        }
        maxy = bottomRight.y;
        if(bottomLeft.y > bottomRight.y){
            maxy = bottomLeft.y;
        }
        width = maxx - minx + 1;
        height = maxy - miny + 1;
        a = labyrinthView.cv.getWidth() / (double) width;
        b = labyrinthView.cv.getHeight() / (double) height;
        Integer finalMinx = minx;
        Integer finalMiny = miny;
        Platform.runLater(()->{
            for(Field field : allFields){
                int x = (int) ((field.x - finalMinx) * a);
                int y = (int) ((field.y - finalMiny) * b);
                labyrinthView.gc.setFill(Color.BLACK);
                labyrinthView.gc.fillRect(x, y, a, b);
                labyrinthView.gc.setFill(Color.WHITE);
                if(field.type == ViewElement.START){
                    labyrinthView.gc.setFill(Color.RED);
                }
                if(field.type == ViewElement.EXIT){
                    labyrinthView.gc.setFill(Color.GREEN);
                }
                labyrinthView.gc.fillRect(x + 0.1*(double) a, y + 0.1 * (double) b, a * 0.8, b * 0.8);
            }
            Field field = new Field(startField);
            for(Direction dir : path){
                int x = (int) ((field.x - finalMinx) * a);
                int y = (int) ((field.y - finalMiny) * b);
                labyrinthView.gc.setFill(Color.BLACK);
                labyrinthView.gc.fillRect(x, y, a, b);
                labyrinthView.gc.setFill(Color.BLUE);
                if(field.type == ViewElement.START){
                    labyrinthView.gc.setFill(Color.RED);
                }
                if(field.type == ViewElement.EXIT){
                    labyrinthView.gc.setFill(Color.GREEN);
                }
                labyrinthView.gc.fillRect(x + 0.1*(double) a, y + 0.1 * (double) b, a * 0.8, b * 0.8);
                field = new Field(field.x, field.y, ViewElement.WAY, dir);
            }
            field = new Field(field.x, field.y, ViewElement.EXIT);
            int x = (int) ((field.x - finalMinx) * a);
            int y = (int) ((field.y - finalMiny) * b);
            labyrinthView.gc.setFill(Color.BLACK);
            labyrinthView.gc.fillRect(x, y, a, b);
            labyrinthView.gc.setFill(Color.GREEN);
            labyrinthView.gc.fillRect(x + 0.1*(double) a, y + 0.1 * (double) b, a * 0.8, b * 0.8);
        });
    }
}
