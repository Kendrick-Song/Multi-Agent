package tileworld.planners;

public class ExploreNode {
    private final int x;
    private final int y;
    private boolean visited;

    public ExploreNode() {
        this.x = -1;
        this.y = -1;
        this.visited = false;
    }

    public ExploreNode(int x, int y) {
        this.x = x;
        this.y = y;
        this.visited = false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public boolean isVisited() {
        return visited;
    }
}
