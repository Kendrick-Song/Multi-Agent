/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tileworld.planners;

import sim.util.Int2D;
import tileworld.environment.TWDirection;

/**
 * DefaultTWPlanner
 *
 * @author michaellees
 * Created: Apr 22, 2010
 * <p>
 * Copyright michaellees 2010
 * <p>
 * Here is the skeleton for your planner. Below are some points you may want to
 * consider.
 * <p>
 * Description: This is a simple implementation of a Tileworld planner. A plan
 * consists of a series of directions for the agent to follow. Plans are made,
 * but then the environment changes, so new plans may be needed
 * <p>
 * As an example, your planner could have 4 distinct behaviors:
 * <p>
 * 1. Generate a random walk to locate a Tile (this is triggered when there is
 * no Tile observed in the agents' memory)
 * <p>
 * 2. Generate a plan to a specified Tile (one which is nearby preferably,
 * nearby is defined by threshold - @see TWEntity)
 * <p>
 * 3. Generate a random walk to locate a Hole (this is triggered when the agent
 * has (is carrying) a tile but doesn't have a hole in memory)
 * <p>
 * 4. Generate a plan to a specified hole (triggered when agent has a tile,
 * looks for a hole in memory which is nearby)
 * <p>
 * The default path generator might use an implementation of A* for each of the behaviors
 */
public class DefaultTWPlanner implements TWPlanner {

    public TWPath generatePlan() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean hasPlan() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void voidPlan() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Int2D getCurrentGoal() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public TWDirection execute() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}

