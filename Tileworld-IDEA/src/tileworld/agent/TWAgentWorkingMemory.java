package tileworld.agent;

import java.util.*;

import sim.engine.Schedule;
import sim.field.grid.ObjectGrid2D;
import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.environment.*;
import tileworld.Parameters;

/**
 * TWAgentMemory
 *
 * @author Song Jiayao
 * <p>
 * Created: Mar 04, 2022
 * <p>
 * Description:
 * <p>
 * This class represents the memory of the TileWorld agents. It stores
 * all objects which is has observed for a given period of time. You may
 * want to develop an entirely new memory system by extending this one.
 * <p>
 * The memory is supposed to have a probabilistic decay, whereby an element is
 * removed from memory with a probability proportional to the length of
 * time the element has been in memory. The maximum length of time which
 * the agent can remember is specified as MAX_TIME. Any memories beyond
 * this are automatically removed.
 */
public class TWAgentWorkingMemory {

    /**
     * Access to Schedule (TWEnvironment) so that we can retrieve the current time step of the simulation.
     */
    private Schedule schedule;
    private TWAgent me;
    private final static int MAX_TIME = 10;
    private final static float MEM_DECAY = 0.5f;

    private ObjectGrid2D memoryGrid;

    /**
     * This was originally a queue ordered by the time at which the fact was observed.
     * However, when updating the memory a queue is very slow.
     * Here we trade off memory (in that we maintain a complete image of the map)
     * for speed of update. Updating the memory is a lot more straightforward.
     */
    private TWAgentPercept[][] entities;

    /**
     * Stores (for each TWObject type) the closest object within sensor range,
     * null if no objects are in sensor range
     */
    private HashMap<Class<?>, TWObject> closestTWObjectsInMemory;

    public Int2D getFuelStationCoor() {
        return fuelStationCoor;
    }

    public void setFuelStationCoor(Int2D fuelStationCoor) {
        this.fuelStationCoor = fuelStationCoor;
    }

    private Int2D fuelStationCoor;

    public TWAgentWorkingMemory(TWAgent moi, Schedule schedule, int x, int y) {

        this.me = moi;

        this.entities = new TWAgentPercept[x][y];

        this.schedule = schedule;
        this.memoryGrid = new ObjectGrid2D(me.getEnvironment().getxDimension(), me.getEnvironment().getyDimension());
        this.closestTWObjectsInMemory = new HashMap<>(3);
        this.fuelStationCoor = null;
    }

    /**
     * Called at each time step, updates the memory map of the agent.
     * Note that some objects may disappear or be moved, in which case part of
     * sensed may contain null objects
     * <p>
     * Also note that currently the agent has no sense of moving objects, so
     * an agent may remember the same object at two locations simultaneously.
     * <p>
     * Other agents in the grid are sensed and passed to this function. But it
     * is currently not used for anything. Do remember that an agent sense itself
     * too.
     *
     * @param sensedObjects bag containing the sensed objects
     * @param objectXCoords bag containing x coordinates of objects
     * @param objectYCoords bag containing y coordinates of object
     * @param sensedAgents  bag containing the sensed agents
     * @param agentXCoords  bag containing x coordinates of agents
     * @param agentYCoords  bag containing y coordinates of agents
     */
    public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {

        this.clearMemoryInSensorRange(Parameters.defaultSensorRange);
        this.decayMemory();

        // must all be same size.
        assert (sensedObjects.size() == objectXCoords.size() && sensedObjects.size() == objectYCoords.size());

        for (int i = 0; i < sensedObjects.size(); i++) {
            TWEntity o = (TWEntity) sensedObjects.get(i);
            // we don't consider other agents
            if (o == null || o instanceof TWAgent) {
                continue;
            } else if (o instanceof TWObject) {
                // Add the object(tiles / holes / obstacles) to memory
                // If o is a TWObject, use its death time
                this.entities[o.getX()][o.getY()] = new TWAgentPercept(o, ((TWObject) o).getDeathTime());
                this.updateClosestTWObject((TWObject) o);
            } else {
                // TWFuelStation, which doesn't have death time, set it to 6000
                this.entities[o.getX()][o.getY()] = new TWAgentPercept(o, 6000);
                // Remember the fuelStationCoor
                if (this.fuelStationCoor == null && o instanceof TWFuelStation) {
                    this.fuelStationCoor = new Int2D(o.getX(), o.getY());
                    // broadcast at first time
                    if (this.me instanceof SimpleTWAgent) {
                        String m = String.format(MessagesCreator.FUEL_STATION_COOR, o.getX(), o.getY());
                        ((SimpleTWAgent) this.me).addBroadcastMessages(m);
                    }
                }
            }
            memoryGrid.set(o.getX(), o.getY(), o);
        }
    }

    /**
     * removes all facts earlier than now - max memory time.
     * <p>
     * remove probabilistically (exponential decay of memory)
     */
    public void decayMemory() {
        for (int x = 0; x < this.entities.length; x++) {
            for (int y = 0; y < this.entities[x].length; y++) {
                TWAgentPercept entity = this.entities[x][y];
                if (entity != null && entity.getT() <= this.schedule.getTime()) {
                    // death time <= current time
                    this.removeAgentPercept(x, y);
                }
            }
        }
        for (Map.Entry<Class<?>, TWObject> entry : this.closestTWObjectsInMemory.entrySet()) {
            TWObject o = entry.getValue();
            if (o != null && this.entities[o.getX()][o.getY()] == null)
                this.closestTWObjectsInMemory.put(entry.getKey(), null);
        }
    }

    public void removeAgentPercept(int x, int y) {
        this.entities[x][y] = null;
        this.memoryGrid.set(x, y, null);
    }

    public void removeObject(TWEntity o) {
        this.removeAgentPercept(o.getX(), o.getY());
    }

    /**
     * returns the object of a particular type
     * (Tile or Hole) which is closest to the agent and within it's sensor range
     */
    public TWObject getClosestObjectInMemory(Class<?> type) {
        return this.closestTWObjectsInMemory.get(type);
    }

    private void updateClosestTWObject(TWObject o) {
        assert (o != null);
        if (this.closestTWObjectsInMemory.get(o.getClass()) == null || this.me.closerTo(o, this.closestTWObjectsInMemory.get(o.getClass()))) {
            this.closestTWObjectsInMemory.put(o.getClass(), o);
        }
    }

    /**
     * Is the cell blocked according to our memory?
     *
     * @param tx x position of cell
     * @param ty y position of cell
     * @return true if the cell is blocked in our memory
     */
    public boolean isCellBlocked(int tx, int ty) {
        //no memory at all, so assume not blocked
        if (this.entities[tx][ty] == null) {
            return false;
        }
        TWEntity e = this.entities[tx][ty].getO();
        //is it an obstacle?
        return (e instanceof TWObstacle);
    }

    public ObjectGrid2D getMemoryGrid() {
        return this.memoryGrid;
    }

    private void clearMemoryInSensorRange(int sensorRange) {
        for (int i = this.me.getX() - sensorRange; i <= this.me.getX() + sensorRange; i++) {
            for (int j = this.me.getY() - sensorRange; j <= this.me.getY() + sensorRange; j++) {
                if (!this.me.getEnvironment().isInBounds(i, j))
                    continue;
                this.removeAgentPercept(i, j);
            }
        }
    }
}
