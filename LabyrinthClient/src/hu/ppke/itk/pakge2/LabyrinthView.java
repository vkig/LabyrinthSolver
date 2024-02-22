package hu.ppke.itk.pakge2;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * A labirintus grafikus kezelői felületét leíró osztály
 */
public class LabyrinthView {
    protected VBox root;
    protected BorderPane panel;
    protected TextField username_tf, ip_tf, port_tf, labyrinthId_tf;
    protected String username = "pakge", ip = "localhost", port = "6900", labyrinthId = null;
    protected Canvas cv;
    protected GraphicsContext gc;
    protected TextArea serverResponse;
    protected LabyrinthModell labyrinthModell;
    protected Button connect_btn, start_btn, stop_btn, disconnect_btn;
    protected Label connectionStatus, gameStatus;

    /**
     * Konstruktor a root paraméterrel, amely elkészíti a megfelelő gombokat, szöveges mezőket, stb
     * @param r A grafikus program root Node-ja
     */
    public LabyrinthView(VBox r){
        root = r;

        //BorderPane declaration
        panel = new BorderPane();
        panel.setPadding(new Insets(10));

        //Fonts for different labels
        Font f = new Font("Times New Roman Bold",40);
        Font f1 = new Font("Times New Roman Bold", 14);
        Font f2 = new Font("Times New Roman Bold", 20);
        Font f3 = new Font("Courier New", 12);

        //Menubar settings
        Menu m =  new Menu("File");
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(actionEvent -> {System.exit(0);});
        m.getItems().addAll(exit);

        //Title
        Label l = new Label("LabyrinthGame");
        l.setFont(f);

        //Labels for input TextFields
        Label usl = new Label("Username:");
        usl.setFont(f1);
        Label ipl = new Label("IP Adress:");
        ipl.setFont(f1);
        Label portl = new Label("Port:");
        portl.setFont(f1);
        Label connection_statusl = new Label("Connection status: ");
        connection_statusl.setFont(f1);
        Label game_statusl = new Label("Game status: ");
        game_statusl.setFont(f1);
        Label labyrinthIdl = new Label("Labyrinth ID: ");
        labyrinthIdl.setFont(f1);
        connectionStatus = new Label("Inactive");
        gameStatus = new Label("Not started");
        connectionStatus.setPadding(new Insets(10));
        gameStatus.setPadding(new Insets(10));


        //Input TextFields
        username_tf = new TextField();
        ip_tf = new TextField();
        port_tf = new TextField();
        labyrinthId_tf = new TextField();
        username_tf.setPromptText("username");
        ip_tf.setPromptText("localhost");
        port_tf.setPromptText("6900");
        labyrinthId_tf.setPromptText("Null");

        //Connect, Disconnect Buttons
        connect_btn = new Button("Connect");
        disconnect_btn = new Button("Disconnect");
        connect_btn.setFont(f2);
        disconnect_btn.setFont(f2);

        //Start, Stop Buttons
        start_btn = new Button("S\nT\nA\nR\nT");
        start_btn.setFont(f);
        start_btn.setMaxHeight(Double.MAX_VALUE);
        start_btn.setPadding(new Insets(20));
        stop_btn = new Button("S\nT\nO\nP");
        stop_btn.setFont(f);
        stop_btn.setMaxHeight(Double.MAX_VALUE);
        stop_btn.setPadding(new Insets(20));

        //Canvas for labyrinth graphic representation
        cv = new Canvas(500,500);
        gc = cv.getGraphicsContext2D();
        gc.setFill(Color.GRAY);
        gc.fillRect(0, 0, 500, 500);

        //Layouts
        ////Top
        VBox top = new VBox();
        top.setPadding(new Insets(20));
        top.setSpacing(20);
        top.setAlignment(Pos.CENTER);
        HBox hb = new HBox();
        GridPane gp = new GridPane();
        gp.add(usl,0, 0);
        gp.add(ipl, 1,0);
        gp.add(portl,2,0);
        gp.add(username_tf, 0, 1);
        gp.add(ip_tf, 1, 1);
        gp.add(port_tf, 2, 1);
        hb.getChildren().addAll(gp, connect_btn);
        hb.setAlignment(Pos.CENTER);
        HBox hb1 = new HBox();
        hb1.setAlignment(Pos.CENTER);
        hb1.getChildren().addAll(labyrinthIdl, labyrinthId_tf);
        connect_btn.setMaxHeight(Double.MAX_VALUE);
        top.getChildren().add(l);
        top.getChildren().add(hb);
        top.getChildren().add(hb1);

        ////Bottom
        VBox bottom = new VBox();
        bottom.setAlignment(Pos.CENTER);
        HBox bottomhb = new HBox();
        bottomhb.setAlignment(Pos.CENTER);
        bottomhb.getChildren().add(disconnect_btn);
        bottomhb.setPadding(new Insets(20));
        serverResponse = new TextArea("[INFO] Labyrinth Client Started...");
        serverResponse.setFont(f3);
        serverResponse.setMinHeight(100);
        serverResponse.setEditable(false);
        HBox hb2 = new HBox();
        hb2.setAlignment(Pos.CENTER_RIGHT);
        hb2.getChildren().addAll(connection_statusl,connectionStatus, game_statusl, gameStatus);
        bottom.getChildren().addAll(bottomhb, serverResponse, hb2);

        //BorderPane settings
        panel.setTop(top);
        panel.setLeft(start_btn);
        panel.setCenter(cv);
        panel.setRight(stop_btn);
        panel.setBottom(bottom);
        root.getChildren().addAll(new MenuBar(m), panel);

        //Set Button enable/disable
        disconnect_btn.setDisable(true);
        start_btn.setDisable(true);
        stop_btn.setDisable(true);
        labyrinthId_tf.setDisable(true);

        //Set actions
        connect_btn.setOnAction(actionEvent -> {connect();});
        disconnect_btn.setOnAction(actionEvent -> {disconnect();});
        start_btn.setOnAction(actionEvent -> {start();});
        stop_btn.setOnAction(actionEvent -> {stop();});
    }

    /**
     * Beállítja a gombokat, szöveges mezőket a csatlakás előtt szükséges állapotokba
     */
    public void setDefault(){
        labyrinthId = null;
        connect_btn.setDisable(false);
        start_btn.setDisable(true);
        stop_btn.setDisable(true);
        disconnect_btn.setDisable(true);
        labyrinthId_tf.setDisable(true);
        ip_tf.setDisable(false);
        port_tf.setDisable(false);
        username_tf.setDisable(false);
    }

    /**
     * Beállítja a gombokat, szöveges mezőket a játék indítás előtt szükséges állapotokba
     */
    public void setBeforeStart(){
        connect_btn.setDisable(true);
        start_btn.setDisable(false);
        stop_btn.setDisable(true);
        disconnect_btn.setDisable(false);
        labyrinthId_tf.setDisable(false);
        ip_tf.setDisable(true);
        port_tf.setDisable(true);
        username_tf.setDisable(true);
    }

    /**
     * Csatlakozik a szerverhez, mely egy labyrinthModell példányosításán keresztül történik
     */
    public void connect(){
        if(!username_tf.getText().equals("")){
            username = username_tf.getText();
        }
        if(!ip_tf.getText().equals("")){
            ip = ip_tf.getText();
        }
        if(!port_tf.getText().equals("")){
            port = port_tf.getText();
        }
        setMessage("[INFO] Connecting to server on: " + ip + ":" + port + ", as " + username);
        labyrinthModell = new LabyrinthModell(this, username, ip, port);
        setBeforeStart();
        ip_tf.setText(ip);
        port_tf.setText(port);
        username_tf.setText(username);
    }

    /**
     * Lecsatlakozik a szerverről és meghívja a setDefault() metódust
     */
    public void disconnect(){
        labyrinthModell.disconnect();
        setDefault();
    }

    /**
     * Elindítja a játékot egy új Thread formájában, melyet a labyrinthModell példány valósít meg
     */
    public void start(){
        gc.clearRect(0,0,500,500);
        gc.setFill(Color.GRAY);
        gc.fillRect(0, 0, 500, 500);
        labyrinthId = labyrinthId_tf.getText();
        if(labyrinthId.equals("")){
            labyrinthId = null;
        }
        labyrinthModell.setLabyrinthId(labyrinthId);
        if(labyrinthModell.isAlive()){
            labyrinthModell.enableStart();
        }
        else{
            labyrinthModell.start();
        }
        start_btn.setDisable(true);
        connect_btn.setDisable(true);
        stop_btn.setDisable(false);
        disconnect_btn.setDisable(false);
        labyrinthId_tf.setDisable(true);
    }

    /**
     * Leállítja a játékot a labyrinthModell példányán keresztül
     */
    public void stop(){
        labyrinthModell.disableStop();
    }

    /**
     * A paraméterként kapott log üzenetet a korábbiak után fűzi, ha 7000-nél több karakter szerepel benne, akkor
     * elmenti az utolsó 3000 karaktert, majd ezt beállítja a szöveges mező szövegének és utána fűzi a paraméterként
     * kapottat
     * @param message Log üzenet, melyet a felhasználó számára megjelenít a program
     */
    public void setMessage(String message){
        if(serverResponse.getLength() > 7000){
            String text = serverResponse.getText(4000,7000);
            serverResponse.clear();
            serverResponse.setText(text);
        }
        serverResponse.appendText("\n" + message);
        serverResponse.setScrollTop(0);
    }

    /**
     * A csatlakozás állapotát visszaigazoló label beállítására szolgál
     * @param conStat Az új kijelzett állapot szövegesen
     */
    public void setConnectionStatus(String conStat){
        connectionStatus.setText(conStat);
    }

    /**
     * A játék állapotát visszaigazoló label beállítására szolgál
     * @param gstat Az új kijelzett állapot szövegesen
     */
    public void setGameStatus(String gstat){
        gameStatus.setText(gstat);
    }

    /**
     * A játék megállítása, lecsatlakozás
     */
    public void terminate(){
        if(labyrinthModell != null && labyrinthModell.isConnected()){
            labyrinthModell.disconnect();
        }
    }

    /**
     * Beállítja a paraméterként kapott értéket labyrinthId-nak
     * @param labyrinthId Az új labyrinthId
     */
    public void setLabyrinthId(String labyrinthId){
        this.labyrinthId = labyrinthId;
        labyrinthId_tf.setText(labyrinthId);
    }
}
