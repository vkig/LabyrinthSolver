package hu.ppke.itk.java.labyrinthv09.server;


public interface LabyrinthGenerator {
    int[][] generate(String seed);
    Point getStart(int[][] labyrinth);
    Point getExit(int[][] labyrinth);
    String format(int[][] labyrinth);
}
