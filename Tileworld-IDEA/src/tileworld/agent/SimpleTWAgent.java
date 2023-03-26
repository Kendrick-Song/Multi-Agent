/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tileworld.agent;

import sim.field.grid.ObjectGrid2D;
import sim.util.Int2D;
import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.exceptions.InsufficientFuelException;
import tileworld.planners.ExploreNode;

import java.util.*;


/**
 * Base TW Agent
 *
 * @author Song Jiayao
 * Created: March 25, 2023
 */
public class SimpleTWAgent extends TWAgent {
    private final String name;
    private AgentStates state;
    private TWDirection preDir;
    private boolean overObstacle;

    private int curPathIndex;
    private final ExploreNode[][] explorePath;

    private final int[] xBound;
    private final int[] yBound;

    private HashMap<String, Integer> broadcastMessages;

    public void addBroadcastMessages(String message) {
        this.broadcastMessages.put(message, 15);
    }

    public SimpleTWAgent(String name, int xpos, int ypos,
                         TWEnvironment env, double fuelLevel, Int2D exploreStartPoint) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.state = AgentStates.WARM_UP;
        this.preDir = TWDirection.Z;
        this.overObstacle = false;
        this.broadcastMessages = new HashMap<>();
        this.xBound = new int[]{exploreStartPoint.getX(), exploreStartPoint.getX() + Parameters.xDimension / 2};
        this.yBound = new int[]{exploreStartPoint.getY(), exploreStartPoint.getY() + Parameters.yDimension / 2};

        this.curPathIndex = 0;
        this.explorePath = new ExploreNode[2][4];
        // X shape path
        explorePath[0][0] = new ExploreNode(xBound[0] + 3, yBound[0] + 3); // top left
        explorePath[0][1] = new ExploreNode(xBound[1] - 4, yBound[1] - 4); // bottom right
        explorePath[0][2] = new ExploreNode(xBound[1] - 4, yBound[0] + 3); // top right
        explorePath[0][3] = new ExploreNode(xBound[0] + 3, yBound[1] - 4); // bottom left

        // 十 shape path
        explorePath[1][0] = new ExploreNode(xBound[1] - 4, yBound[1] / 2); // right middle
        explorePath[1][1] = new ExploreNode(xBound[0] + 3, yBound[1] / 2); // left middle
        explorePath[1][2] = new ExploreNode(xBound[1] / 2, yBound[1] - 4); // bottom middle
        explorePath[1][3] = new ExploreNode(xBound[1] / 2, yBound[0] + 3); // top middle
    }

    protected TWThought think() {
        // print basic info
        System.out.println("---------------------------------");
        System.out.println(this.name
                + "    Score: " + this.score
                + "    State: " + this.state
                + "    Coord: (" + this.getX() + ", " + this.getY() + ")"
                + "    Fuel Level: " + this.getFuelLevel()
                + "    Fuel station location: " + this.getFuelStationLoc()
                + "    Steps: " + this.getEnvironment().schedule.getSteps());

        TWDirection dir = null;
        ObjectGrid2D objectGrid = this.getMemory().getMemoryGrid();
        TWEntity e = (TWEntity) objectGrid.get(this.getX(), this.getY());

        // check whether it can do something, if so, do it.
        if (this.hasTile() && this.isHole(e)) {
            return new TWThought(TWAction.PUTDOWN, null);
        } else if (this.carriedTiles.size() < 3 && this.isTile(e)) {
            return new TWThought(TWAction.PICKUP, null);
        } else if (this.isFuelStation(e) && this.state == AgentStates.REFUEL) {
            this.state = AgentStates.WARM_UP;
            System.out.println("INFO: State change: WARM UP");
            return new TWThought(TWAction.REFUEL, null);
        } else if (this.state != AgentStates.REFUEL && this.needToRefuel()) {
            this.state = AgentStates.REFUEL;
            System.out.println("INFO: State change: REFUEL");
        } else if (this.state == AgentStates.WARM_UP
                && this.getX() < xBound[1] && this.getX() > this.xBound[0]
                && this.getY() < yBound[1] && this.getY() > this.yBound[0]) {
            this.state = AgentStates.EXPLORE;
            System.out.println("INFO: State change: EXPLORE");
        }

        // decide whether to explore or to refuel
        if (this.state == AgentStates.WARM_UP) {
            dir = this.getDirection(this.getX(), this.getY(), explorePath[0][0].getX(), explorePath[0][0].getY());
        } else if (this.state == AgentStates.EXPLORE) {
            System.out.println("TRACE: Exploring");

            // find the nearest object
            TWHole hole = (TWHole) this.memory.getClosestObjectInMemory(TWHole.class);
            TWTile tile = (TWTile) this.memory.getClosestObjectInMemory(TWTile.class);
            // The priorities are: 1. score  2. tile  3. explore
            if (hole != null && this.isValidCoor(hole.getX(), hole.getY()) && this.hasTile()) {
                // move to the closest hole
                dir = this.getDirection(this.getX(), this.getY(), hole.getX(), hole.getY());
                System.out.println("TRACE: Getting to the HOLE at (" + hole.getX() + ", " + hole.getY() + ")");
            } else if (tile != null && this.isValidCoor(tile.getX(), tile.getY()) && this.carriedTiles.size() < 3) {
                // move to the closest tile
                dir = this.getDirection(this.getX(), this.getY(), tile.getX(), tile.getY());
                System.out.println("TRACE: Getting to the TILE at (" + tile.getX() + ", " + tile.getY() + ")");
            } else {
                // decide how to explore
                dir = this.getExploreDirection();
            }
        } else if (this.state == AgentStates.REFUEL) {
            // need to refuel
            System.out.println("TRACE: Getting to the fuel station");
            Int2D loc = this.getFuelStationLoc();
            if (loc != null) {
                // already known where is the Fuel station
                dir = this.getDirection(this.getX(), this.getY(), loc.getX(), loc.getY());
            } else
                // don't know the location, try to explore and find
                dir = this.getExploreDirection();
        }
        System.out.println("TRACE: Move " + dir);
        this.preDir = dir;
        return new TWThought(TWAction.MOVE, dir);
    }

    @Override
    protected void act(TWThought thought) {
        TWAgentWorkingMemory memory = this.getMemory();
        TWEntity e = (TWEntity) memory.getMemoryGrid().get(this.getX(), this.getY());
        TWAction act = thought.getAction();
        TWDirection dir = thought.getDirection();
        switch (act) {
            case PUTDOWN:
                this.putTileInHole((TWHole) e);
                memory.removeObject(e);
                break;
            case PICKUP:
                this.pickUpTile((TWTile) e);
                memory.removeObject(e);
                break;
            case REFUEL:
                this.refuel();
                break;
            case MOVE:
                try {
                    this.move(dir);
                } catch (CellBlockedException ex) {
                    System.out.println("WARN: Cell Blocked");
                } catch (InsufficientFuelException insufficientFuelException) {
                    System.out.println("ERROR: Agent ran out of fuel, Score: " + this.score);
                }
                break;
        }
    }

    @Override
    public void communicate() {
        // get the messages
        ArrayList<Message> messages = this.getEnvironment().getMessages();
        this.parseMessages(messages);

        // broadcast messages
        for (Map.Entry<String, Integer> entry : this.broadcastMessages.entrySet()) {
            Message m = new Message("", "", entry.getKey());
            System.out.println("TRACE: Broadcast message:" + entry.getKey() + " from " + this.name);
            this.getEnvironment().receiveMessage(m);
            this.broadcastMessages.put(entry.getKey(), entry.getValue() - 1);
        }

        // clear broadcastMessages
        for (Map.Entry<String, Integer> entry : this.broadcastMessages.entrySet()) {
            if (entry.getValue() == 0) {
                this.broadcastMessages.remove(entry.getKey());
            }
        }
    }

    private TWDirection getExploreDirection() {
        ExploreNode node = null;
        for (ExploreNode n : explorePath[this.curPathIndex]) {
            if (this.getX() == n.getX() && this.getY() == n.getY()) {
                n.setVisited(true);
            }
            if (!n.isVisited()) {
                node = n;
                break;
            }
        }

        // all nodes in this path are visited
        if (node == null) {
            this.curPathIndex = (this.curPathIndex + 1) % 2;
            node = explorePath[this.curPathIndex][0];
            // init nodes array
            for (ExploreNode n : explorePath[this.curPathIndex]) {
                n.setVisited(false);
            }
        }

        System.out.println("TRACE: Explore in Path: " + this.curPathIndex + " to (" + node.getX() + ", " + node.getY() + ")");
        return getDirection(this.getX(), this.getY(), node.getX(), node.getY());
    }

    @Override
    public String getName() {
        return this.name;
    }

    private boolean isHole(TWEntity e) {
        return e instanceof TWHole;
    }

    private boolean isTile(TWEntity e) {
        return e instanceof TWTile;
    }

    private boolean isFuelStation(TWEntity e) {
        return e instanceof TWFuelStation;
    }

    private Int2D getFuelStationLoc() {
        return this.getMemory().getFuelStationCoor();
    }

    private boolean needToRefuel() {
        // Loose upper boundary to ensure it can get the fuel
        Int2D loc = getFuelStationLoc();
        if (loc != null) {
            return this.getFuelLevel() <= this.getDistanceTo(loc.x, loc.y) + 20;
        }
        return this.getFuelLevel() <= 100;
    }

    private TWDirection getDirection(int sx, int sy, int gx, int gy) {
        TWDirection xDir = this.getXDirection(sx, sy, gx, gy);
        TWDirection yDir = this.getYDirection(sx, sy, gx, gy);

        if (!overObstacle) {
            if (xDir != null && yDir != null) {
                // diagonal dir
                if (preDir.isSameAxis(xDir) && yDir != TWDirection.Z)
                    return yDir;
                else if (preDir.isSameAxis(xDir) && xDir != TWDirection.Z)
                    return xDir;
            }
            if (xDir != null && xDir != TWDirection.Z)
                return xDir;
            if (yDir != null && yDir != TWDirection.Z)
                return yDir;
            if (xDir == null && yDir == TWDirection.Z) {
                // target is directly above/below the source AND there's an obstacle
                overObstacle = true;
                System.out.println("INFO: Overing OBSTACLE");
                return isValidCoor(sx + 1, sy) ? TWDirection.E : TWDirection.W;
            }
            if (yDir == null && xDir == TWDirection.Z) {
                // target is on the directly left/right source AND there's an obstacle
                overObstacle = true;
                System.out.println("INFO: Overing OBSTACLE");
                return isValidCoor(sx, sy + 1) ? TWDirection.S : TWDirection.N;
            }
        } else {
            overObstacle = false;
            System.out.println("INFO: Finish overing OBSTACLE");
            // 躲避障碍时是横向移动，则现在纵向移动，反之亦然
            if (preDir == TWDirection.E || preDir == TWDirection.W)
                return yDir == null ? TWDirection.S : yDir;
            else
                return xDir == null ? TWDirection.E : xDir;
        }
        // should not return Z
        System.out.println("ERROR: Return Z direction");
        return TWDirection.Z;
    }

    /**
     * @return Null if gy == sy, Z if there's invalid direction
     */
    private TWDirection getXDirection(int sx, int sy, int tx, int ty) {
        if (tx == sx) {
            return null;
        } else if (tx > sx && this.isValidCoor(sx + 1, sy)) {
            return TWDirection.E;
        } else if (tx < sx && this.isValidCoor(sx - 1, sy)) {
            return TWDirection.W;
        } else {
            return TWDirection.Z;
        }
    }

    /**
     * @return Null if gy == sy, Z if there's invalid direction
     */
    private TWDirection getYDirection(int sx, int sy, int tx, int ty) {
        if (ty == sy)
            return null;
        else if (ty > sy && isValidCoor(sx, sy + 1))
            return TWDirection.S;
        else if (ty < sy && isValidCoor(sx, sy - 1))
            return TWDirection.N;
        else
            return TWDirection.Z;
    }

    private boolean isValidCoor(int x, int y) {
        return this.isInBounds(x, y) && !this.memory.isCellBlocked(x, y);
    }

    private boolean isInBounds(int x, int y) {
        if (this.state == AgentStates.REFUEL || this.state == AgentStates.WARM_UP) {
            // refuel have to cross over the local bounds
            return this.getEnvironment().isInBounds(x, y);
        } else {
            return !((x < this.xBound[0]) || (this.yBound[1] < 0) || (x >= this.xBound[1] || y >= this.yBound[1]));
        }
    }

    private void parseMessages(ArrayList<Message> messages) {
        for (Message m : messages) {
            String[] s = m.getMessage().split(":", 2);
            String key = s[0];
            String val = s[1];
            switch (key) {
                case "Fuel Station Coordinates":
                    int x = Integer.parseInt(val.split(",")[0]);
                    int y = Integer.parseInt(val.split(",")[1]);
                    this.getMemory().setFuelStationCoor(new Int2D(x, y));
                    break;
                case "Exploring Area":
                    // TODO
                    break;
                default:
                    break;
            }
        }
    }
}
