package hu.ppke.itk.java.labyrinthv09.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LabyrinthLite implements LabyrinthGenerator {
    private int rows;
    private int cols;
    private int[] maze;
    private Map<String, LabyrinthLite> cache;


    private static final int[][] DEFAULT = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 5, 1, 1, 1, 1, 1, 0, 0},
        {0, 0, 1, 0, 1, 0, 0, 0, 0},
        {0, 0, 1, 1, 1, 1, 1, 3, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
    };


    protected LabyrinthLite(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.maze = new int[rows * cols];
    }

    private static LabyrinthLite fromFile(String path) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(path));

        int rows = Integer.parseInt(r.readLine()) * 2 + 1;
        int cols = Integer.parseInt(r.readLine()) * 2 + 1;

        LabyrinthLite lab = new LabyrinthLite(rows, cols);

        for (int i = 0; i < rows * cols; i++) {
            lab.maze[i] = r.read();
        }

        r.close();
        return lab;
    }

    public static LabyrinthLite preload(Collection<String> files) throws IOException {
        LabyrinthLite store = new LabyrinthLite(0, 0);
        store.cache = new HashMap<>();

        for (var f : files) {
            String k = new File(f).getName();
            LabyrinthLite v = fromFile(f);

            store.cache.put(k, v);
        }
        return store;
    }

    public int[][] getLabyrinth() {
        int[][] mz = new int[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                mz[y][x] = maze[y * cols + x];
            }
        }

        return mz;
    }

    @Override
    public int[][] generate(String seed) {
        var lab = cache.getOrDefault(seed, null);

        return lab != null
            ? lab.getLabyrinth()
            : DEFAULT;
    }

    @Override
    public Point getStart(int[][] labyrinth) {
        int y = 0;
        for (int[] row : labyrinth) {
            int x = 0;
            for (int cell : row) {
                if (cell == 5){
                    Point end = new Point(x, y);
                    System.out.println("START: " + end.x + " " + end.y);
                    return end;
                }
                x++;
            }
            y++;
        }
        return new Point(1, 1);
    }

    @Override
    public Point getExit(int[][] labyrinth) {
        int y = 0;
        for (int[] row : labyrinth) {
            int x = 0;
            for (int cell : row) {
                if (cell == 3){
                    Point end = new Point(x, y);
                    System.out.println("END: " + end.x + " " + end.y);
                    return end;
                }
                x++;
            }
            y++;
        }
        return new Point(rows - 2, cols - 2);
    }

    @Override
    public String format(int[][] labyrinth) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : labyrinth) {
            for (int cell : row) {
                switch (cell) {
                    case 0:  sb.append("# "); break;
                    case 1:  sb.append("  "); break;
                    case 5:  sb.append("O "); break;
                    case 3:  sb.append("$ "); break;
                    case -1: sb.append("@ "); break;
                    default: sb.append(". "); break;
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
