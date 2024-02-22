package hu.ppke.itk.java.labyrinthv09.server;

import hu.ppke.itk.java.labyrinthv09.LabyrinthProtos.ViewElement;
import hu.ppke.itk.java.labyrinthv09.Utils;

import java.io.*;
import java.util.*;

public class Labyrinth implements LabyrinthGenerator {
    private static final int UNVISITED = -1;
    private static final int PLAYER = -2;
    public static final int PATH = ViewElement.WAY_VALUE;
    public static final int WALL = ViewElement.WALL_VALUE;
    public static final int START = ViewElement.START_VALUE;
    public static final int EXIT = ViewElement.EXIT_VALUE;

    public final int nodeRows;
    public final int nodeCols;
    public final int rows;
    public final int cols;

    private final int[] maze;

    private Point start;
    private Point exit;

    /**
     * Létrehoz egy labirintust, egy adott sor- és oszlopszámú mátrixból.
     * A sorok és oszlopok a rácspontok számát jelölik ki, a falak automatikusan
     * kerülnek be ezek közé és köré. A {@code start} és {@code goal} cellákat egyedi
     * szám jelöli az elkészült labirintusban. Hiányzó {@code start} esetén
     * a {@code (0, 0)}, hiányzó {@code goal} esetén pedig a
     * {@code (columns-1, rows-1)} pontot használja.
     *
     * @param rows a rácspontok sorainak száma
     * @param columns a rácspontok oszlopainak száma
     * @param start a kezdőpontkéne megjelölt rácspont
     * @param exit a célként megjelölt rácspont
     */
    public Labyrinth(int rows, int columns, Point start, Point exit) {
        this.nodeRows = rows;
        this.nodeCols = columns;

        // account for walls between nodes
        this.rows = rows + (rows + 1);
        this.cols = columns + (columns + 1);

        this.maze = new int[this.rows * this.cols];

        if (start != null) setStart(start.x, start.y);
        else setStart(0, 0);

        if (exit != null) setExit(exit.x, exit.y);
        else setExit(columns-1, rows-1);
    }

    /**
     * Létrehoz egy labirintust, egy adott sor- és oszlopszámú mátrixból.
     * A sorok és oszlopok a rácspontok számát jelölik ki, a falak automatikusan
     * kerülnek be ezek közé. A {@code (0, 0)} kiindulási-, a
     * {@code (columns-1, rows-1)} pontot célként jelöli meg.
     *
     * @param rows a rácspontok sorainak száma
     * @param columns a rácspontok oszlopainak száma
     */
    public Labyrinth(int rows, int columns) {
        this(rows, columns, null, null);
    }

    public void toFile(String path) throws IOException {
        PrintWriter w = new PrintWriter(new FileWriter(path));

        w.println(nodeRows);
        w.println(nodeCols);

        for (int i = 0; i < rows * cols; i++) {
            w.write(maze[i]);
        }

        w.close();
    }

    /**
     * Kiszámolja egy rácspont {@code maze}-beli indexét annak
     * relatív (= egymáshoz viszonyított, falak nélküli) koordinátáiból.
     *
     * @param nodeX a rácspont relatív x koordinátája
     * @param nodeY a rácspont relatív y koordinátája
     * @return a rácspont indexe a {@code maze} tömbben
     */
    private int nodeIndex(int nodeX, int nodeY) {
        // coord * padding correction + top/left wall correction
        return (nodeY*2+1) * cols + (nodeX*2+1);
    }

    /**
     * Kiszámolja egy rácspont koordinátáit annak {@code maze}-ben
     * elfoglalt indexe alapján. Az eredmény relatív koordinátáként
     * (= csak rácspontok léteznek) értendő.
     *
     * @param gridIdx a rácspont indexe a {@code maze} tömbben
     * @return a rácspont koordinátái
     */
    private Point nodeCoords(int gridIdx) {
        int x = (gridIdx % cols) / 2;
        int y = (gridIdx / cols) / 2;
        return new Point(x, y);
    }

    /**
     * Kiszámolja egy cella koordinátáit annak {@code maze}-beli
     * indexe alapján.
     *
     * @param gridIdx a cella indexe a {@code maze} tömbben
     * @return a cella koordinátái
     */
    private Point gridCoords(int gridIdx) {
        int x = gridIdx % cols;
        int y = gridIdx / cols;
        return new Point(x, y);
    }

    /**
     * Átváltja egy rácspont relatív koordinátáit abszolút koordinátákra.
     *
     * @param nodeX a rácspont relatív x-koordinátája
     * @param nodeY a rácspont relatív y-koordinátája
     * @return a rácspont abszolút koordinátái
     */
    private Point nodeToGridCoords(int nodeX, int nodeY) {
        return gridCoords(nodeIndex(nodeX, nodeY));
    }

    /**
     * Megadja, hogy az adott koordináta a rácspontokon
     * kívül esik-e.
     *
     * @param x a vizsgált x-koordináta
     * @param y a vizsgált y-koordináta
     * @return kívül esik-e a rácspontokon a koordináta
     */
    public boolean outOfBounds(int x, int y) {
        return x < 0 || x > cols - 1
            || y < 0 || y > rows - 1;
    }

    /**
     * Kiszámolja egy cella {@code maze}-beli indexét.
     *
     * @param x a keresett cella x-koordinátája
     * @param y a keresett cella y-koordinátája
     * @return a cella indexe a {@code maze} tömbben
     */
    private int gridIndex(int x, int y) {
        return y * cols + x;
    }

    /**
     * Megkeresi egy rácspont szomszédait.
     *
     * @param idx a rácspont indexe, a rácspontok koordináta rendszerében
     * @return a rácspont szomszédai
     */
    private List<Integer> neighborNodes(int idx) {
        Point c = nodeCoords(idx);
        int x = c.x;
        int y = c.y;

        List<Integer> nbs = new ArrayList<>();
        if (x > 0)          nbs.add(nodeIndex(x-1, y));
        if (x < nodeCols-1) nbs.add(nodeIndex(x+1, y));
        if (y > 0)          nbs.add(nodeIndex(x, y-1));
        if (y < nodeRows-1) nbs.add(nodeIndex(x, y+1));
        return nbs;
    }

    /**
     * Elkészíti vagy frissíti a létező labirintust a megadott {@code seed}
     * alapján, DFS algoritmussal. Azonos {@code seed}ek ugyanazt a labirintust
     * eredményezik.
     */
    @Override
    public int[][] generate(String seed) {
        // Reset maze
        Arrays.fill(maze, 0);

        // Set all nodes to unvisited
        for (int y = 0; y < nodeRows; y++) {
            for (int x = 0; x < nodeCols; x++) {
                maze[nodeIndex(x, y)] = UNVISITED;
            }
        }

        // Generate maze
        long _seed = Utils.stringToSeed(seed);
        Random rnd = new Random(_seed);
        Stack<Integer> s = new Stack<>();

        s.push(nodeIndex(0, 0));

        // While the stack is not empty
        while (!s.empty()) {

            // Pop a cell from the stack and make it a current cell
            int c = s.pop();

            var unvisited = neighborNodes(c).stream()
                .filter(p -> maze[p] == UNVISITED)
                .toArray(Integer[]::new);

            // If the current cell has any neighbours which have not been visited
            if (unvisited.length > 0) {
                // Choose one of the unvisited neighbours
                int randidx = (rnd.nextInt() & Integer.MAX_VALUE) % unvisited.length;
                int neighbor = unvisited[randidx];

                // Mark the chosen cell as visited and push it to the stack
                maze[neighbor] = 1;

                // Remove the wall between the current cell and the chosen cell
                Point a = gridCoords(c);
                Point b = gridCoords(neighbor);

                // Wall position
                int wx = (a.x + b.x) / 2;
                int wy = (a.y + b.y) / 2;

                maze[gridIndex(wx, wy)] = PATH;

                // Push the current and chosen cells to the stack
                s.push(c);
                s.push(neighbor);
            }
        }

        // Set start and exit
        maze[gridIndex(start.x, start.y)] = START;
        maze[gridIndex(exit.x, exit.y)] = EXIT;

        return getLabyrinth();
    }

    /**
     * Megjelöli a koordináták alapján a rácspontot a {@code START = 5} konstans
     * értékkel, és visszaadja annak abszolút ({@code getMaze} kimenethez
     * igazított) koordinátáit.
     *
     * @param x a kezdő pont relatív x-koordinátája
     * @param y a kezdő pont relatív y-koordinátája
     * @return a kezdő pont abszolút koordinátái
     */
    public void setStart(int x, int y) {
        var gc = nodeToGridCoords(x, y);
        if (outOfBounds(gc.x, gc.y))
            throw new RuntimeException("Start point can't be out of bounds.");

        maze[gridIndex(gc.x, gc.y)] = START;
        this.start = gc;
    }

    /**
     * Megjelöli a koordináták alapján a rácspontot a {@code EXIT = 3} konstans
     * értékkel, és visszaadja annak abszolút ({@code getMaze} kimenethez
     * igazított) koordinátáit.
     *
     * @param x a cél pont relatív x-koordinátája
     * @param y a cél pont relatív y-koordinátája
     * @return a cél pont abszolút koordinátái
     */
    public void setExit(int x, int y) {
        var gc = nodeToGridCoords(x, y);
        if (outOfBounds(gc.x, gc.y))
            throw new RuntimeException("Exit point can't be out of bounds.");

        maze[gridIndex(gc.x, gc.y)] = EXIT;
        this.exit = gc;
    }

    /**
     * Megadja a cél pont koordinátáit, a {@code getMaze} kimenetéhez
     * igazítva.
     *
     * @return
     *      a cél pont koordinátái
     */
    @Override
    public Point getStart(int[][] labyrinth) {
        return new Point(start.x, start.y);
    }

    /**
     * Megadja a cél pont koordinátáit, a {@code getMaze} kimenetéhez
     * igazítva.
     *
     * @return
     *      a cél pont koordinátái
     */
    @Override
    public Point getExit(int[][] labyrinth) {
        return new Point(exit.x, exit.y);
    }


    /**
     * Visszaadja az elkészített labirintus másolatát, 2D tömbként.
     * @return az elkészített labirintus
     */
    public int[][] getLabyrinth() {
        int[][] mz = new int[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                mz[y][x] = maze[gridIndex(x, y)];
            }
        }

        return mz;
    }

    @Override
    public String format(int[][] labyrinth) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : labyrinth) {
            for (int cell : row) {
                switch (cell) {
                    case WALL:  sb.append("# "); break;
                    case PATH:  sb.append("  "); break;
                    case START: sb.append("O "); break;
                    case EXIT:  sb.append("$ "); break;
                    case PLAYER:sb.append("@ "); break;
                    default:    sb.append(". "); break;
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String toString(Point player) {
        var lb = getLabyrinth();
        lb[player.y][player.x] = PLAYER;

        return format(lb);
    }

    /**
     * Megrajzolja a labirintust, az egyes mezőket különböző ASCII
     * karakterekkel jelölve.
     * @return a labirintus rajza ASCII karakterekkel
     */
    @Override
    public String toString() {
        return format(getLabyrinth());
    }
}
