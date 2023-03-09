/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tileworld.agent;

import tileworld.environment.TWDirection;

/**
 * TWContextBuilder
 *
 * @author michaellees
 * Created: Jan 24, 2011
 * <p>
 * Copyright 2011
 * <p>
 * <p>
 * Description:
 * <p>
 * This small class is used to return the result of a think execution.
 * <p>
 * The result of a think procedure should include two things: the actions to take
 * and a direction in which to move if a move is selected.
 */
public class TWThought {

    private final TWAction action;
    private final TWDirection direction;

    public TWThought(TWAction action, TWDirection direction) {
        this.direction = direction;
        this.action = action;
    }

    public TWAction getAction() {
        return action;
    }

    public TWDirection getDirection() {
        return direction;
    }
}
