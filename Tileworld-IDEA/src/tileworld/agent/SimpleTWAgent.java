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

import java.util.ArrayList;
import java.util.Random;

/**
 * TWContextBuilder
 *
 * @author michaellees
 * Created: Feb 6, 2011
 * <p>
 * Copyright michaellees Expression year is undefined on line 16, column 24 in Templates/Classes/Class.java.
 * <p>
 * <p>
 * Description:
 */
public class SimpleTWAgent extends TWAgent {
    private final String name;
    private AgentStates state;
    private TWDirection preDir;
    private boolean overObstacle;
    private final ExploreNode[][] exploreNodes;
    private int indexOfPath;
    protected boolean needBroadcast;

    public SimpleTWAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.state = AgentStates.EXPLORE;
        this.preDir = TWDirection.Z;
        this.overObstacle = false;
        this.needBroadcast = false;
        this.indexOfPath = -1;
        this.exploreNodes = new ExploreNode[2][4];
        exploreNodes[0][0] = new ExploreNode(3, 3); // top left
        exploreNodes[0][1] = new ExploreNode(Parameters.xDimension - 4, Parameters.yDimension - 4); // bottom right
        exploreNodes[0][2] = new ExploreNode(Parameters.xDimension - 4, 3); // top right
        exploreNodes[0][3] = new ExploreNode(3, Parameters.yDimension - 4); // bottom left

        exploreNodes[1][0] = new ExploreNode(Parameters.xDimension - 4, Parameters.yDimension / 2); // right middle
        exploreNodes[1][1] = new ExploreNode(3, Parameters.yDimension / 2); // left middle
        exploreNodes[1][2] = new ExploreNode(Parameters.xDimension / 2, Parameters.yDimension - 4); // bottom middle
        exploreNodes[1][3] = new ExploreNode(Parameters.xDimension / 2, 3); // top middle
    }

    protected TWThought think() {
        // get the messages
        ArrayList<Message> messages = this.getEnvironment().getMessages();
        this.parseMessages(messages);

        // print basic info
        System.out.println(name
                + "\tScore: " + this.score
                + "\tState: " + this.state
                + "\tCoord: (" + this.getX() + ", " + this.getY() + ")"
                + "\tFuel Level: " + this.getFuelLevel()
                + "\tFuel station location: " + getFuelStationLoc()
                + "\tSteps: " + this.getEnvironment().schedule.getSteps());

        TWDirection dir = null;
        ObjectGrid2D objectGrid = this.getMemory().getMemoryGrid();
        TWEntity e = (TWEntity) objectGrid.get(this.getX(), this.getY());

        // check whether it can do something, if so, do it.
        if (hasTile() && isHole(e))
            return new TWThought(TWAction.PUTDOWN, null);
        else if (carriedTiles.size() < 3 && isTile(e))
            return new TWThought(TWAction.PICKUP, null);
        else if (isFuelStation(e) && state == AgentStates.REFUEL) {
            state = AgentStates.EXPLORE;
            System.out.println("INFO: State change: EXPLORE");
            return new TWThought(TWAction.REFUEL, null);
        } else if (state != AgentStates.REFUEL && needToRefuel()) {
            state = AgentStates.REFUEL;
            System.out.println("INFO: State change: REFUEL");
        }

        // decide whether to explore or to refuel
        if (state == AgentStates.EXPLORE) {
            System.out.println("TRACE: Exploring");

            // find the nearest object o
            TWHole hole = (TWHole) this.memory.getClosestObjectInMemory(TWHole.class);
            TWTile tile = (TWTile) this.memory.getClosestObjectInMemory(TWTile.class);
            // The priorities are: 1. score  2. tile  3. explore
            if (hole != null && hasTile()) {
                // move to the closest hole
                dir = getDirection(this.getX(), this.getY(), hole.getX(), hole.getY());
                System.out.println("TRACE: Getting to the HOLE at (" + hole.getX() + ", " + hole.getY() + ")");
            } else if (tile != null && carriedTiles.size() < 3) {
                // move to the closest tile
                dir = getDirection(this.getX(), this.getY(), tile.getX(), tile.getY());
                System.out.println("TRACE: Getting to the TILE at (" + tile.getX() + ", " + tile.getY() + ")");
            } else
                // decide how to explore
                dir = getExploreDirection();
            //            dir = getRandomDirection();
        } else {
            // need to refuel
            System.out.println("TRACE: Getting to the fuel station");
            Int2D loc = getFuelStationLoc();
            if (loc != null) {
                // already known where is the Fuel station
                dir = getDirection(this.getX(), this.getY(), loc.getX(), loc.getY());
            } else
                // don't know the location, try to explore and find
                dir = getExploreDirection();
//                dir = getRandomDirection();
        }
        System.out.println("TRACE: Move " + dir);
        preDir = dir;
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
                putTileInHole((TWHole) e);
                memory.removeObject(e);
                break;
            case PICKUP:
                pickUpTile((TWTile) e);
                memory.removeObject(e);
                break;
            case REFUEL:
                refuel();
                break;
            case MOVE:
                try {
                    move(dir);
                    memory.removeAgentPercept(this.getX(), this.getY());
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
        if (needBroadcast) {
            Int2D coord = this.getFuelStationLoc();
            String s;
            if (coord != null) {
                s = String.format("Fuel Station Coordinates: %d, %d", coord.x, coord.y);
                Message message = new Message("", "", s);
                this.getEnvironment().receiveMessage(message);
                System.out.println(this.name + "\tINFO: Broadcast the Fuel Station Coordinates");
            } else {
                s = String.format("Exploring Path: %d", indexOfPath);
                Message message = new Message("", "", s);
                this.getEnvironment().receiveMessage(message);
                System.out.println(this.name + "\tINFO: Broadcast the Exploring Path");
            }
            needBroadcast = false;
        }
    }

    private TWDirection getRandomDirection() {

        TWDirection randomDir = TWDirection.values()[this.getEnvironment().random.nextInt(4)];

        while (randomDir.isOpposite(preDir) || !isValidCor(this.x + randomDir.dx, this.y + randomDir.dy))
            randomDir = TWDirection.values()[this.getEnvironment().random.nextInt(4)];

        return randomDir;

    }

    private TWDirection getExploreDirection() {
        // haven't chosen any path to explore
        if (indexOfPath == -1) {
            // need broadcast when it first chooses the path
            indexOfPath = this.getEnvironment().random.nextInt(2);
            needBroadcast = true;
        }
        ExploreNode node = null;
        for (ExploreNode n : exploreNodes[indexOfPath]) {
            if (this.x == n.getX() && this.y == n.getY())
                n.setVisited(true);

            if (!n.isVisited()) {
                node = n;
                break;
            }
        }

        // all nodes in this path are visited
        if (node == null) {
            indexOfPath = this.getEnvironment().random.nextInt(2);
            node = exploreNodes[indexOfPath][0];
            // init nodes array
            for (ExploreNode n : exploreNodes[indexOfPath]) {
                n.setVisited(false);
            }
        }

        System.out.println("TRACE: Explore in Path: " + indexOfPath + " to (" + node.getX() + ", " + node.getY() + ")");
        return getDirection(this.x, this.y, node.getX(), node.getY());
    }

    @Override
    public String getName() {
        return name;
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
        return this.getMemory().getFuelStationLoc();
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
        TWDirection xDir = getXDirection(sx, sy, gx, gy);
        TWDirection yDir = getYDirection(sx, sy, gx, gy);

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
                return isValidCor(sx + 1, sy) ? TWDirection.E : TWDirection.W;
            }
            if (yDir == null && xDir == TWDirection.Z) {
                // target is on the directly left/right source AND there's an obstacle
                overObstacle = true;
                System.out.println("INFO: Overing OBSTACLE");
                return isValidCor(sx, sy + 1) ? TWDirection.S : TWDirection.N;
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
     * @param sx
     * @param sy
     * @param gx
     * @param gy
     * @return Null if gy == sy, Z if there's invalid direction
     */
    private TWDirection getXDirection(int sx, int sy, int gx, int gy) {
        if (gx == sx)
            return null;
        else if (gx > sx && isValidCor(sx + 1, sy))
            return TWDirection.E;
        else if (gx < sx && isValidCor(sx - 1, sy))
            return TWDirection.W;
        else
            return TWDirection.Z;
    }

    /**
     * @param sx
     * @param sy
     * @param gx
     * @param gy
     * @return Null if gy == sy, Z if there's invalid direction
     */
    private TWDirection getYDirection(int sx, int sy, int gx, int gy) {
        if (gy == sy)
            return null;
        else if (gy > sy && isValidCor(sx, sy + 1))
            return TWDirection.S;
        else if (gy < sy && isValidCor(sx, sy - 1))
            return TWDirection.N;
        else
            return TWDirection.Z;
    }

    private boolean isValidCor(int x, int y) {
        return this.getEnvironment().isInBounds(x, y) && !this.memory.isCellBlocked(x, y);
    }

    private void parseMessages(ArrayList<Message> messages) {
        for (Message m : messages) {
            String[] s = m.getMessage().split(": ", 2);
            String key = s[0];
            String val = s[1];
            switch (key) {
                case "Fuel Station Coordinates":
                    int x = Integer.parseInt(val.split(", ")[0]);
                    int y = Integer.parseInt(val.split(", ")[1]);
                    this.getMemory().setFuelStationLoc(new Int2D(x, y));
                    break;
                case "Exploring Path":
                    int index = Integer.parseInt(val);
                    if (index == this.indexOfPath) {
                        // If the paths are the same, there is a 50% probability of changing
                        boolean change = this.getEnvironment().random.nextBoolean(0.5);
                        if (change) {
                            this.indexOfPath = (this.indexOfPath + 1) % exploreNodes.length;
                            System.out.println("INFO: Path change: " + this.indexOfPath);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
