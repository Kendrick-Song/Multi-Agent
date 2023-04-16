/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tileworld.agent;

import sim.util.Int2D;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.AstarPathGenerator;
import tileworld.planners.TWPath;
import tileworld.planners.TWPathStep;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TWContextBuilder
 *
 * @author michaellees
 * Created: Feb 6, 2011
 *
 * Copyright michaellees Expression year is undefined on line 16, column 24 in Templates/Classes/Class.java.
 *
 *
 * Description:
 *
 */
public class SimpleTWAgent extends TWAgent{
	private String name;

    protected TWAgentWorkingMemory memory;

    private TWPath twPath;

    private TWPath fuelPath;

    private TWHole twHole;

    private TWTile twTile;

    private TWThought twThought;

    private boolean isToHole;

    public SimpleTWAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos,ypos,env,fuelLevel);
        this.name = name;
    }

    protected TWThought think() {
        return strategy2();
        //return strategy1();
    }

    //stragegy1() is with low efficiency, the algorithm used to find the fuel station should be improved
    //make finding the station a primary goal
    private TWThought strategy1() {
        AstarPathGenerator astarPathGenerator;
        this.sensor.sense();
        //try to find out the gas station
        if(getStationPos() == null) {
            //try to get the station position from messages
            if(!getEnvironment().getMessages().isEmpty()) {
                String position = this.getEnvironment().getMessages().get(0).getMessage();
                String[] tmp = position.split(",");
                //get the fuel station position from String to Int2D
                Int2D sp = new Int2D(getNumeric(tmp[0]), getNumeric(tmp[1]));
                setStationPos(sp);
            } else {
                return new TWThought(TWAction.MOVE,getRandomDirection());
            }
        } else {
            communicate();
        }

        //if the fuel is less than a threshold
        if(getFuelLevel() < 70) {
            if(getX() == stationPos.getX() && getY() == stationPos.getY()) {
                fuelPath = null;
                return new TWThought(TWAction.REFUEL,getRandomDirection());
            } else {
                if(fuelPath == null) {
                    astarPathGenerator = new AstarPathGenerator(getEnvironment(), this, 100000);
                    fuelPath = astarPathGenerator.findPath(getX(), getY(), stationPos.getX(), stationPos.getY());
                }
                if(fuelPath.hasNext()) {
                    TWPathStep twPathStep = fuelPath.popNext();
                    return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                }
                fuelPath = null;
                return new TWThought(TWAction.MOVE,getRandomDirection());
            }
        }

        //(TWTile) getMemory().getNearbyTile(getX(), getY(), Double.MAX_VALUE);
        TWTile tmpTile = (TWTile) getMemory().getClosestObjectInSensorRange(TWTile.class);
        //(TWHole) getMemory().getNearbyHole(getX(), getY(), Double.MAX_VALUE);
        TWHole tmpHole = (TWHole) getMemory().getClosestObjectInSensorRange(TWHole.class);
        //if there is no plan
        if(twPath == null) {
            astarPathGenerator = new AstarPathGenerator(getEnvironment(), this, 100000);
            if(this.carriedTiles.isEmpty()) {
                //twTile = (TWTile) getMemory().getNearbyTile(getX(), getY(), Double.MAX_VALUE);
                twTile = (TWTile) getMemory().getClosestObjectInSensorRange(TWTile.class);
                //if there is no tile in the sensor range, move randomly
                if(twTile == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                }
                else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), twTile.getX(), twTile.getY());
                    if(twPath != null && twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                }
            } else if(this.carriedTiles.size() <= 2) {
                //if there is a tile closer to us, we will choose to pick up the tile instead of filling the hole
                TWEntity tmpEntity = getNearestTWObject(tmpTile, tmpHole);
                if(tmpEntity == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                } else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), tmpEntity.getX(), tmpEntity.getY());
                    if(tmpEntity instanceof TWHole) {
                        twHole = (TWHole) tmpEntity;
                        isToHole = true;
                    } else {
                        twTile = (TWTile) tmpEntity;
                        isToHole = false;
                    }
                    if(twPath != null && twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                }
            } else {
                //twHole = (TWHole) getMemory().getNearbyHole(getX(), getY(), Double.MAX_VALUE);
                twHole = (TWHole) getMemory().getClosestObjectInSensorRange(TWHole.class);
                if(twHole == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                }
                else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), twHole.getX(), twHole.getY());
                }
            }
        }
        else {
            if(this.carriedTiles.isEmpty()) {
                if(getX() == twTile.getX() && getY() == twTile.getY()) {
                    twPath = null;
                    return new TWThought(TWAction.PICKUP,getRandomDirection());
                } else {
                    if(twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                    //if there is some mistake in current twPath,
                    //set the twPath to null, reprogram the path in the next think() phase
                    twPath = null;
                }
            } else if(carriedTiles.size() <= 2) {
                if(isToHole) {
                    if(getX() == twHole.getX() && getY() == twHole.getY()) {
                        twPath = null;
                        return new TWThought(TWAction.PUTDOWN,getRandomDirection());
                    } else {
                        if(twPath.hasNext()) {
                            TWPathStep twPathStep = twPath.popNext();
                            return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                        }
                        twPath = null;
                    }
                } else {
                    if(getX() == twTile.getX() && getY() == twTile.getY()) {
                        twPath = null;
                        return new TWThought(TWAction.PICKUP,getRandomDirection());
                    } else {
                        if(twPath.hasNext()) {
                            TWPathStep twPathStep = twPath.popNext();
                            return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                        }
                        //if there is some mistake in current twPath,
                        //set the twPath to null, reprogram the path in the next think() phase
                        twPath = null;
                    }
                }
            } else {
                if(getX() == twHole.getX() && getY() == twHole.getY()) {
                    twPath = null;
                    return new TWThought(TWAction.PUTDOWN,getRandomDirection());
                } else {
                    if(twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                    twPath = null;
                }
            }
        }

        System.out.println("Simple Score: " + this.score);
        return new TWThought(TWAction.MOVE,getRandomDirection());
    }

    //find tiles as many as possible, not specifically looking for the gas station
    private TWThought strategy2() {
        AstarPathGenerator astarPathGenerator;
        this.sensor.sense();
        //try to find out the gas station
        if(getStationPos() == null) {
            //try to get the station position from messages
            if(!getEnvironment().getMessages().isEmpty()) {
                String position = this.getEnvironment().getMessages().get(0).getMessage();
                String[] tmp = position.split(",");
                //get the fuel station position from String to Int2D
                Int2D sp = new Int2D(getNumeric(tmp[0]), getNumeric(tmp[1]));
                setStationPos(sp);
            }
        } else {
            communicate();
        }

        //(TWTile) getMemory().getNearbyTile(getX(), getY(), Double.MAX_VALUE);
        TWTile tmpTile = (TWTile) getMemory().getClosestObjectInSensorRange(TWTile.class);
        //(TWHole) getMemory().getNearbyHole(getX(), getY(), Double.MAX_VALUE);
        TWHole tmpHole = (TWHole) getMemory().getClosestObjectInSensorRange(TWHole.class);

        //if the fuel is less than a threshold, the station position is known
        //and there is no plan currently, try to get to the station and refuel
        if(getFuelLevel() < 80 && getStationPos() != null && twPath == null) {
            if(getX() == stationPos.getX() && getY() == stationPos.getY()) {
                fuelPath = null;
                return new TWThought(TWAction.REFUEL,getRandomDirection());
            } else {
                if(fuelPath == null) {
                    astarPathGenerator = new AstarPathGenerator(getEnvironment(), this, 100000);
                    fuelPath = astarPathGenerator.findPath(getX(), getY(), stationPos.getX(), stationPos.getY());
                }

                //if happen to encounter a tile or a hole, try to pick it or fill it
                if(tmpHole != null && getX() == tmpHole.getY() && getY() == tmpHole.getY() && carriedTiles.size() > 0) {
                    twHole = tmpHole;
                    return new TWThought(TWAction.PUTDOWN,getRandomDirection());
                } else if(tmpTile != null && getX() == tmpTile.getY() && getY() == tmpTile.getY() && carriedTiles.size() < 3) {
                    twTile = tmpTile;
                    return new TWThought(TWAction.PICKUP,getRandomDirection());
                }

                if(fuelPath.hasNext()) {
                    TWPathStep twPathStep = fuelPath.popNext();
                    return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                }
                fuelPath = null;
                return new TWThought(TWAction.MOVE,getRandomDirection());
            }
        }

        //if there is no plan
        if(twPath == null) {
            astarPathGenerator = new AstarPathGenerator(getEnvironment(), this, 100000);
            if(this.carriedTiles.isEmpty()) {
                twTile = tmpTile;
                //if there is no tile in the sensor range, move randomly
                if(twTile == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                }
                //generate path and move
                else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), twTile.getX(), twTile.getY());
                    isToHole = false;
                    if(twPath != null && twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                }
            } else if(this.carriedTiles.size() <= 2) {
                //if there is a tile closer to us, we will choose to pick up the tile instead of filling the hole
                TWEntity tmpEntity = getNearestTWObject(tmpTile, tmpHole);
                if(tmpEntity == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                } else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), tmpEntity.getX(), tmpEntity.getY());
                    if(tmpEntity instanceof TWHole) {
                        twHole = (TWHole) tmpEntity;
                        isToHole = true;
                    } else {
                        twTile = (TWTile) tmpEntity;
                        isToHole = false;
                    }
                    if(twPath != null && twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                }
            } else {
                twHole = tmpHole;
                if(twHole == null) {
                    return new TWThought(TWAction.MOVE,getRandomDirection());
                }
                else {
                    twPath = astarPathGenerator.findPath(getX(), getY(), twHole.getX(), twHole.getY());
                    isToHole = true;
                    if(twPath != null && twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                }
            }
        } else {
            if(carriedTiles.isEmpty()) {
                if(getX() == twTile.getX() && getY() == twTile.getY()) {
                    twPath = null;
                    return new TWThought(TWAction.PICKUP,getRandomDirection());
                } else {
                    if(twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                    //if there is some mistake in current twPath,
                    //set the twPath to null, reprogram the path in the next think() phase
                    twPath = null;
                }
            } else if(carriedTiles.size() <= 2) {
                if(isToHole) {
                    if(getX() == twHole.getX() && getY() == twHole.getY()) {
                        twPath = null;
                        return new TWThought(TWAction.PUTDOWN,getRandomDirection());
                    } else {
                        if(twPath.hasNext()) {
                            TWPathStep twPathStep = twPath.popNext();
                            return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                        }
                        twPath = null;
                    }
                } else {
                    if(getX() == twTile.getX() && getY() == twTile.getY()) {
                        twPath = null;
                        return new TWThought(TWAction.PICKUP,getRandomDirection());
                    } else {
                        if(twPath.hasNext()) {
                            TWPathStep twPathStep = twPath.popNext();
                            return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                        }
                        //if there is some mistake in current twPath,
                        //set the twPath to null, reprogram the path in the next think() phase
                        twPath = null;
                    }
                }
            } else {
                if(getX() == twHole.getX() && getY() == twHole.getY()) {
                    twPath = null;
                    return new TWThought(TWAction.PUTDOWN,getRandomDirection());
                } else {
                    if(twPath.hasNext()) {
                        TWPathStep twPathStep = twPath.popNext();
                        return new TWThought(TWAction.MOVE,twPathStep.getDirection());
                    }
                    twPath = null;
                }
            }
        }

        System.out.println("Simple Score: " + this.score);
        return new TWThought(TWAction.MOVE,getRandomDirection());
    }

    private TWEntity getNearestTWObject(TWEntity a, TWEntity b) {
        if(a == null || b == null) {
            return a == null? b : a;
        } else {
            return this.closerTo(a, b)? a : b;
        }
    }

    public int getNumeric(String str) {
        String regEx="[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        int num = Integer.parseInt(m.replaceAll("").trim());
        return num;
    }


    @Override
    public void communicate() {
            Message message = new Message("","",this.stationPos.toCoordinates());
            this.getEnvironment().receiveMessage(message); // this will send the message to the broadcast channel of the environment
    }

    @Override
    protected void act(TWThought thought) {
        this.twThought = thought;
        try {
            if(thought.getAction().equals(TWAction.PUTDOWN)) {
                putTileInHole(twHole);
                twHole = null;
            } else if (thought.getAction().equals(TWAction.PICKUP)){
                pickUpTile(twTile);
                twTile = null;
            } else if (thought.getAction().equals(TWAction.MOVE)) {
                move(thought.getDirection());
            } else {
                refuel();
            }
        } catch (CellBlockedException ex) {
            //if the agent can not move to the designated position, the path should be regenerated
            this.twPath = null;
            this.fuelPath = null;
            act(new TWThought(TWAction.MOVE,twThought.getDirection().next()));
        }
    }


    private TWDirection getRandomDirection(){

        TWDirection randomDir = TWDirection.values()[this.getEnvironment().random.nextInt(5)];

        if(this.getX()>=this.getEnvironment().getxDimension() ){
            randomDir = TWDirection.W;
        }else if(this.getX()<=1 ){
            randomDir = TWDirection.E;
        }else if(this.getY()<=1 ){
            randomDir = TWDirection.S;
        }else if(this.getY()>=this.getEnvironment().getxDimension() ){
            randomDir = TWDirection.N;
        }

       return randomDir;

    }

    @Override
    public String getName() {
        return name;
    }
}
