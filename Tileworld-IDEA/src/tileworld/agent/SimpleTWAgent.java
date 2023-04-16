/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tileworld.agent;

import sim.field.grid.ObjectGrid2D;
import java.util.Random;
import sim.util.Int2D;
import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.exceptions.InsufficientFuelException;
import tileworld.planners.ExploreNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.HashSet;

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
    private int indexOfPosition;
    protected boolean needBroadcast;
	private ArrayList<Integer> exploreRegionCoord;
	private ArrayList<TWDirection> searchPath;
	private ExploreRegion exploreRegionName;
	private ExploreRegion[] agentsExploreList;
	private boolean exploringAgent;
	private double distanceToNearestAgent;
	private ArrayList<ExploreNode> notExploredNode;
	private ArrayList<Integer> distance;

    public SimpleTWAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.state = AgentStates.EXPLORE;
        this.preDir = TWDirection.Z;
        this.overObstacle = false;
        this.needBroadcast = false;
        this.indexOfPath = -1;
        this.exploreNodes = new ExploreNode[2][4];
        this.exploreRegionName = ExploreRegion.CENTER;
        this.agentsExploreList = new ExploreRegion[5];
        this.exploringAgent = false; // if true, agent does not participate in initial fuel station search
        this.distanceToNearestAgent= 100;
        this.indexOfPosition =0;
        this.notExploredNode= new ArrayList<ExploreNode>();
        this.distance= new ArrayList<Integer>();
       

        
        // first dimension represents an explore path
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
    	
        
    	// initialization: get a region to explore 
    	if (this.getEnvironment().schedule.getSteps() == 0) {
    		System.out.println("step 0");
    	    
    	    // get nearest region to explore
    	    this.exploreRegionCoord = getExploreRegion(this.getX(), this.getY(), Parameters.xDimension, Parameters.yDimension);

    	    // get search path 
    	    this.searchPath = getSearchPath(this.getX(), this.getY(), this.exploreRegionCoord);
    	    
    	    needBroadcast = true; // broadcast 
    	    
    	}
    	
    	// change search region if there is unsearched region
    	else if (this.getEnvironment().schedule.getSteps() == 1) {
    		System.out.println("step 1");
    		
    		// get every agent's explore region (through send and parse message)
    		   	    
    	    // change region to explore if needed (TODO: improvement: ideally get the closest agent from the empty region to go there)
    		ArrayList<Integer> tempExploreRegionCoord = getChangeRegion(this.getX(), this.getY());
    		
    	    // update search path 
    	    if (tempExploreRegionCoord != this.exploreRegionCoord) {
    	    	System.out.println(name + " change region coord");
    	    	this.exploreRegionCoord = tempExploreRegionCoord;
    	    	this.searchPath = getSearchPath(this.getX(), this.getY(), this.exploreRegionCoord);
    	    }	
    	}
        

        // print basic info
        System.out.println(name
                + "\tScore: " + this.score
                + "\tState: " + this.state
                + "\tCoord: (" + this.getX() + ", " + this.getY() + ")"
                + "\tFuel Level: " + this.getFuelLevel()
                + "\tFuel station location: " + getFuelStationLoc()
                + "\tSteps: " + this.getEnvironment().schedule.getSteps()
                + "\tExploreRegion: " + this.exploreRegionCoord);

        TWDirection dir = null;
        ObjectGrid2D objectGrid = this.getMemory().getMemoryGrid();
        TWEntity e = (TWEntity) objectGrid.get(this.getX(), this.getY());
        
        TWAgent neighboueAgent = this.getNeighbour();
        if (neighboueAgent != null) {
        distanceToNearestAgent = neighboueAgent.getDistanceTo(this.getX(), this.getY());
        System.out.println("nearest agent to me \t "+ neighboueAgent.getName()+ "Manhattan distance is"+ distanceToNearestAgent);
        
        if (distanceToNearestAgent < 7) {
        	 if ( state == AgentStates.EXPLORE) {
        		 System.out.println("INFO: State change: AVOID_OVERLAP\t" + neighboueAgent.getName());
        		 state = AgentStates.AVOID_OVERLAP;
        	 }
        }	else if( state == AgentStates.AVOID_OVERLAP){
        	state = AgentStates.EXPLORE;
        	 System.out.println("INFO: State change: EXPLORE");
        }		        	
        } else {
        	if( state == AgentStates.AVOID_OVERLAP){
            	state = AgentStates.EXPLORE;
            	 System.out.println("INFO: State change: EXPLORE");
            }
        }


        /* follow searchPath under 3 conditions
        1. fuel station yet found
        2. searchPath is still valid (not out of bound)
        3. not the exploringAgent ·	·(one agent is selected as exploring agent since there's 5 agents and 4 regions)
        */
    	if ((getFuelStationLoc() == null) && (this.searchPath.size() > 0) && (this.exploringAgent == false)) {
    		dir = getSearchDirection();
    		preDir = dir;
    		return new TWThought(TWAction.MOVE, dir);
    	}
    	
        
        // check whether it can do something, if so, do it.
        if (hasTile() && isHole(e))
            return new TWThought(TWAction.PUTDOWN, null);
        else if (carriedTiles.size() < 3 && isTile(e))
            return new TWThought(TWAction.PICKUP, null);
        else if (isFuelStation(e) && state == AgentStates.REFUEL) {
        	if (neighboueAgent != null && distanceToNearestAgent < 7) {
        		 state = AgentStates.AVOID_OVERLAP;
                 System.out.println("INFO: State change: AVOID_OVERLAP\t" + neighboueAgent.getName());
                 return new TWThought(TWAction.REFUEL, null);
        	} else {
        		 state = AgentStates.EXPLORE;
                 System.out.println("INFO: State change: EXPLORE");
                 return new TWThought(TWAction.REFUEL, null);
        	}
           
        } 
        else if (state != AgentStates.REFUEL && needToRefuel()) {
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
        } else if (state == AgentStates.REFUEL) {
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
        } else if (state == AgentStates.AVOID_OVERLAP) {
        	   System.out.println("TRACE: AVOID_OVERLAP");
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
            	   if (neighboueAgent != null) {
            		   
            		   dir = getAvoidOverlapDirection(this.getX(),this.getY(),neighboueAgent.getX(),neighboueAgent.getY(),neighboueAgent.getName(),this.indexOfPath);
            	   }
            		   
                   
               //            dir = getRandomDirection();
        	
        	
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
            TWAgent neighboueAgent = this.getNeighbour();
            String s;
            if (neighboueAgent != null) {
	            s = String.format("Nearest agent to me: %s, %d", neighboueAgent.getName(), indexOfPath);
	            Message message = new Message(this.getName(),neighboueAgent.getName(), s);
	            this.getEnvironment().receiveMessage(message);
	            System.out.println("Nearest agent to me : %s, %d"+ neighboueAgent.getName()+ indexOfPath);
            }
            
          
            if (coord != null) {
                s = String.format("Fuel Station Coordinates: %d, %d", coord.x, coord.y);
                Message message = new Message("", "", s);
                this.getEnvironment().receiveMessage(message);
                System.out.println(this.name + "\tINFO: Broadcast the Fuel Station Coordinates");
            } 
            
            if (this.getEnvironment().schedule.getSteps() == 0 || this.getEnvironment().schedule.getSteps() == 1) { // send explore region
            	String idk = this.exploreRegionName.toString();
            	System.out.println(idk);
            	s = String.format("Agent Explore Region: %s, %s", name.substring(name.length() - 1), this.exploreRegionName.toString());
                Message message = new Message("", "", s);
                this.getEnvironment().receiveMessage(message);
                System.out.println(this.name + "\tINFO: Broadcast the Agent Explore Region");
            }
            else {
            	System.out.println(this.getEnvironment().schedule.getSteps());
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
            indexOfPath = this.getEnvironment().random.nextInt(2); //returns 0 or 1
            needBroadcast = true;
        }
        ExploreNode node = null;
        for (ExploreNode n : exploreNodes[indexOfPath]) {
            if (this.x == n.getX() && this.y == n.getY())
                n.setVisited(true);

            if (!n.isVisited()) {
            	// notExploredNode.add(n);
               node =n;
                // break;
            }
            
        }
        if (notExploredNode.size()>0) {
        	 Random rand = new Random();
             int randomIndex = rand.nextInt(notExploredNode.size());

             // Retrieve the element at the random index
            //  node = notExploredNode.get(randomIndex);
             notExploredNode.clear();
        }
       
        // all nodes in this path are visited
        if (node == null) {
            indexOfPath = this.getEnvironment().random.nextInt(2);
            indexOfPosition = this.getEnvironment().random.nextInt(4);
            
            node = exploreNodes[indexOfPath][0];
            // init nodes array
            for (ExploreNode n : exploreNodes[indexOfPath]) {
                n.setVisited(false);
            }
        }
        

        System.out.println("TRACE: Explore in Path: " + indexOfPath +","+ indexOfPosition+" to (" + node.getX() + ", " + node.getY() + ")");
        return getDirection(this.x, this.y, node.getX(), node.getY());
    }
    
private TWDirection getAvoidOverlapDirection(int tAgentX, int tAgentY,int nAgentX, int nAgentY, String nAgentName, int indexOfPath_passin ) {
    	
    	if (indexOfPath_passin ==-1) {
    		indexOfPath_passin=0;
    	}
            // need broadcast when it first chooses the path
            // indexOfPath = this.getEnvironment().random.nextInt(2); //returns 0 or 1
        needBroadcast = true;
       
        ExploreNode node = null;
        for (ExploreNode n : exploreNodes[indexOfPath_passin]) {
            if (this.x == n.getX() && this.y == n.getY())
                n.setVisited(true);

            if (!n.isVisited()) {
            	notExploredNode.add(n);
            	distance.add((Math.abs(nAgentY-n.getY())+Math.abs(nAgentX-n.getX())));

            }
            
        }
        if (notExploredNode.size()>0 && distance.size()>0) {
        	 
             int max = Integer.MIN_VALUE;
             int maxindex = -1;
             for (int i = 0; i < distance.size(); i++) {
                 if (distance.get(i) > max) {
                     max = distance.get(i);
                     maxindex = i;
              }
             }

             // Retrieve the element at the random index
             node = notExploredNode.get(maxindex);
             notExploredNode.clear();
             distance.clear();
        }
       
        // all nodes in this path are visited
        if (node == null) {
            indexOfPath = this.getEnvironment().random.nextInt(2);
            indexOfPosition = this.getEnvironment().random.nextInt(4);
            
           //  node = exploreNodes[indexOfPath_passin][0];
            // init nodes array
            for (ExploreNode n : exploreNodes[indexOfPath_passin]) {
            	notExploredNode.add(n);
            	distance.add((Math.abs(nAgentY-n.getY())+Math.abs(nAgentX-n.getX())));
                n.setVisited(false);
            }
            
            if (notExploredNode.size()>0 && distance.size()>0) {
           	 
                int max = Integer.MIN_VALUE;
                int maxindex = -1;
                for (int i = 0; i < distance.size(); i++) {
                    if (distance.get(i) > max) {
                        max = distance.get(i);
                        maxindex = i;
                 }
                }

                // Retrieve the element at the random index
                node = notExploredNode.get(maxindex);
                notExploredNode.clear();
                distance.clear();
           } else {
        	   node = exploreNodes[indexOfPath][0];
           }
        }
        

        System.out.println("TRACE: Explore in Path: " + indexOfPath +","+ indexOfPosition+" to (" + node.getX() + ", " + node.getY() + ")");
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
    
    private TWAgent getNeighbour() {
        return this.getMemory().getNeighbour();
    }

    private boolean needToRefuel() {
        // Loose upper boundary to ensure it can get the fuel
        Int2D loc = getFuelStationLoc();
       
        if (loc != null) {
            return this.getFuelLevel() <= this.getDistanceTo(loc.x, loc.y) + 20 ;
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
                        boolean change = this.getEnvironment().random.nextBoolean(0);
                        if (change) {
                            this.indexOfPath = (this.indexOfPath + 1) % exploreNodes.length;
                            System.out.println("INFO: Path change: " + this.indexOfPath);
                        }
                    }
                    break;
                case "Agent Explore Region":
                	int agentID = Integer.parseInt(val.split(", ")[0]);
                	String exploreRegion = val.split(", ")[1];
                	
                	System.out.println(name + " receives agent " + agentID + " explore " + exploreRegion);
                	
                	switch(exploreRegion) {
                	case("UPLEFT"):
                		this.agentsExploreList[agentID-1] = ExploreRegion.UPLEFT;
                		break;
                	case("UPRIGHT"):
                		this.agentsExploreList[agentID-1] = ExploreRegion.UPRIGHT;
                		break;
                	case("DOWNLEFT"):
                		this.agentsExploreList[agentID-1] = ExploreRegion.DOWNLEFT;
                		break;
                	case("DOWNRIGHT"):
                		this.agentsExploreList[agentID-1] = ExploreRegion.DOWNRIGHT;
                		break;
                	default:
                		System.out.println("ERROR: parse message agent explore region");
                		System.out.println(exploreRegion);
                		this.agentsExploreList[agentID-1] = ExploreRegion.CENTER;
            		break;
                	}

                	break; 
                case "Nearest agent to me":
                	String fromAgent;
                	String toAgent;
                	int indexofPathNAgent;
                	fromAgent = m.getFrom();
                	toAgent = m.getTo();
                	
                	System.out.println( " receives agent avoid route message from " + fromAgent + "to" + toAgent);
                	indexofPathNAgent = Integer.parseInt(val.split(", ")[1]);
                	System.out.println( " Suggest agent to avoid index of route " + indexofPathNAgent );
                	if (indexofPathNAgent == this.indexOfPath && toAgent == this.name) {
                        // If the paths are the same, there is a 50% probability of changing
                        this.indexOfPath = (this.indexOfPath + 1) % exploreNodes.length;
                        System.out.println("INFO: Path change: " + this.indexOfPath); 
                    }

                	break;
                default:
                    break;
            }
        }
    }
    
    private ArrayList<Integer> getExploreRegion(int x, int y, int xEnv, int yEnv) {
    	
    	Map<Double, ExploreRegion> explore_dict = new Hashtable<Double, ExploreRegion>();

        // get region based on closest distance from 4 corners
    	explore_dict.put(this.getDistanceTo(0, 0), ExploreRegion.UPLEFT);
    	explore_dict.put(this.getDistanceTo(Parameters.xDimension, 0), ExploreRegion.UPRIGHT);
    	explore_dict.put(this.getDistanceTo(0, Parameters.yDimension), ExploreRegion.DOWNLEFT);
    	explore_dict.put(this.getDistanceTo(Parameters.xDimension, Parameters.yDimension), ExploreRegion.DOWNRIGHT);
    	
    	
    	this.exploreRegionName = explore_dict
						        .entrySet()
						        .stream()
						        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
						        .findFirst()
						        .get()
						        .getValue();
    	
    	ArrayList<Integer> exploreRegionCoord = new ArrayList<Integer>(); // xmin, xmax, ymin, ymax
    	
    	exploreRegionCoord = getExploreRegionCoord(this.exploreRegionName, xEnv, yEnv);
    	
    	return exploreRegionCoord;
    
    }
    
    private ArrayList<Integer> getExploreRegionCoord(ExploreRegion exploreRegionName, int xEnv, int yEnv) {
    	
    	ArrayList<Integer> tempexploreRegionCoord = new ArrayList<Integer>(); // xmin, xmax, ymin, ymax
    	
    	switch (exploreRegionName) { // get coordinates based on region
		case UPLEFT:
			System.out.println("Getexploreregioncoord upleft");
    	    tempexploreRegionCoord.add(Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(xEnv / 2 - Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv / 2 - Parameters.defaultSensorRange);
    	    break;
		case UPRIGHT:
			System.out.println("Getexploreregioncoord upright");
			tempexploreRegionCoord.add(xEnv / 2 + Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(xEnv - Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv / 2 - Parameters.defaultSensorRange);
      	    break;
    	case DOWNLEFT:
    		System.out.println("Getexploreregioncoord downleft");
    		tempexploreRegionCoord.add(Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(xEnv / 2 - Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv / 2 + Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv - Parameters.defaultSensorRange);
      	    break;
    	case DOWNRIGHT:
    		System.out.println("Getexploreregioncoord downright");
    		tempexploreRegionCoord.add(xEnv / 2 + Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(xEnv - Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv / 2 + Parameters.defaultSensorRange);
    	    tempexploreRegionCoord.add(yEnv - Parameters.defaultSensorRange);
      	    break;
    	case CENTER:
    		break;

    	}
    	System.out.println("Getexploreregioncoord" + tempexploreRegionCoord);
    	return tempexploreRegionCoord; 
    }
    
    /* 
    get start and end/goal point in search region based on distance from 4 corners
    */
    private int[] getStartGoalPoint(int x, int y, ArrayList<Integer> exploreRegionCoord) {
    	
    	Map<Double, ExploreRegion> explore_dict = new Hashtable<Double, ExploreRegion>();
    	
    	int xmin = exploreRegionCoord.get(0);
    	int xmax = exploreRegionCoord.get(1);
    	int ymin = exploreRegionCoord.get(2);
    	int ymax = exploreRegionCoord.get(3);

    	explore_dict.put(this.getDistanceTo(xmin, ymin), ExploreRegion.UPLEFT);
    	explore_dict.put(this.getDistanceTo(xmax, ymin), ExploreRegion.UPRIGHT);
    	explore_dict.put(this.getDistanceTo(xmin, ymax), ExploreRegion.DOWNLEFT);
    	explore_dict.put(this.getDistanceTo(xmax, ymax), ExploreRegion.DOWNRIGHT);
    	
    	
    	ExploreRegion exploreRegionName = explore_dict
						        .entrySet()
						        .stream()
						        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
						        .findFirst()
						        .get()
						        .getValue();
  
    	
    	switch (exploreRegionName) {
    		case UPLEFT:
    			return new int[]{exploreRegionCoord.get(0), exploreRegionCoord.get(2),
    					 exploreRegionCoord.get(1), exploreRegionCoord.get(3)}; 
    		case UPRIGHT:
    			return new int[]{exploreRegionCoord.get(1), exploreRegionCoord.get(2),
   					 exploreRegionCoord.get(0), exploreRegionCoord.get(3)};
	    	case DOWNLEFT:
	    		return new int[]{exploreRegionCoord.get(0), exploreRegionCoord.get(3),
   					 exploreRegionCoord.get(1), exploreRegionCoord.get(2)};
	    	case DOWNRIGHT:
	    		return new int[]{exploreRegionCoord.get(1), exploreRegionCoord.get(3),
   					 exploreRegionCoord.get(0), exploreRegionCoord.get(2)};
	    	case CENTER:
	    		break;

    	}
    	
    	System.out.println("ERROR: error in getStartGoalPoint");
		return new int[]{0, 0, 0, 0};
    	
    	
    }
    
    private ArrayList<TWDirection> getSearchPath(int cX, int cY, ArrayList<Integer> exploreRegionCoord) {
    	int[] out = getStartGoalPoint(cX, cY, exploreRegionCoord);
    	
    	int sX = out[0]; // start X
    	int sY = out[1]; // start Y
    	int gX = out[2]; // goal X
    	int gY = out[3]; // goal Y
    	
    	ArrayList<TWDirection> searchPath = new ArrayList<TWDirection>();
    	
        	
    	// get from current pos to start pos (take L shape path)
    	int csVertSteps = Math.abs(cY-sY);
    	int csHoriSteps = Math.abs(cX-sX);
    	TWDirection csHorizontalDir = (cX < sX) ? TWDirection.E : TWDirection.W;
    	TWDirection csVerticalDir = (cY < sY) ? TWDirection.S : TWDirection.N;
    	
    	for (int i = 0; i < (csVertSteps); i++) { //add vertical steps
    		searchPath.add(csVerticalDir);
    	}
    	for (int i = 0; i < (csHoriSteps); i++) { //add vertical steps
    		searchPath.add(csHorizontalDir);
    	}
    	
    	// search from start pos to goal pos
    	/*
    	 move in square wave motion, start vertically, repeat (move vertically, go horizontal 7 steps - based on agent perceive view)
    	 
    	 */
    	
    	boolean notReachGoal = true;
    	boolean moveVertical = true;
    	TWDirection horizontalDir = (sX < gX) ? TWDirection.E : TWDirection.W;
    	TWDirection verticalDir = (sY < gY) ? TWDirection.S : TWDirection.N;
    	int vertSteps = Math.abs(gY-sY);
    	int iX = sX; // track goal reached
    	
    	
    	while (notReachGoal) {
    	
	    	if (moveVertical) { // move vertically across the explore region
	    		for (int i = 0; i < (vertSteps); i++) {
	    			searchPath.add(verticalDir);
	    		}
	    		moveVertical = false; // switch to moving horizontally
	    		// switch opp vertical direction
	    		verticalDir = (verticalDir == TWDirection.S) ? TWDirection.N : TWDirection.S; 
	    		}
	    	
	    	else if (!moveVertical) { // move horizontally towards goal by (sensor range) steps
	    		for (int i = 0; i < (2*Parameters.defaultSensorRange+1); i++) {
	    			if (iX != gX) {
	    				searchPath.add(horizontalDir);
	    				if (horizontalDir==TWDirection.E) {
	    					iX++;
	    				}
	    				else iX--;
	    			}
	    			else {
	    				for (int j = 0; j < (vertSteps); j++) { // move vert for the last time
	    					searchPath.add(verticalDir);
	    				}
	    				notReachGoal = false; // end
	    				break;
	    			}	
	    		}
	    		moveVertical = true;
	    	}
	    			
	    	}
	    	
    	return searchPath;
    	
    }
    
    private TWDirection getSearchDirection() {
    	
    	TWDirection dir = this.searchPath.get(0);
    	
    	int nX = this.getX(); // store next X
    	int nY = this.getY(); // store next Y
    	
    	switch (dir) {
    	case N: 
    		nY--;
    		break;
    	case S:
    		nY++;
    		break;
    	case E:
    		nX++;
    		break;
    	case W:
    		nX--;
    		break;
		default:
			break;
    	}
    	
    	if (isValidCor(nX, nY)) {
    		this.searchPath.remove(0);
    	}
    	else { // TODO: add path to go around the obstacle ( |_ _ | shape)
    		this.searchPath.remove(0); //end pos is two steps ahead from current pos
    		this.searchPath.remove(0);
    		
    		System.out.println(name + " overcoming obstacle during fuel station search while going " + dir);
    		
    		switch (dir) {
        	case N: 
        		this.searchPath.add(0, TWDirection.W);
        		this.searchPath.add(0, TWDirection.N);
        		this.searchPath.add(0, TWDirection.N);
        		this.searchPath.add(0, TWDirection.E);
        		break;
        	case S:
        		this.searchPath.add(0, TWDirection.W);
        		this.searchPath.add(0, TWDirection.S);
        		this.searchPath.add(0, TWDirection.S);
        		this.searchPath.add(0, TWDirection.E);
        		break;
        	case E:
        		this.searchPath.add(0, TWDirection.N);
        		this.searchPath.add(0, TWDirection.E);
        		this.searchPath.add(0, TWDirection.E);
        		this.searchPath.add(0, TWDirection.S);
        		break;
        	case W:
        		this.searchPath.add(0, TWDirection.N);
        		this.searchPath.add(0, TWDirection.W);
        		this.searchPath.add(0, TWDirection.W);
        		this.searchPath.add(0, TWDirection.S);
        		break;
    		default:
    			break;
        	}

    	}

    	return dir;
    }
    
    private ArrayList<Integer> getChangeRegion(int x, int y) {
    	
    	// check if each region is allocated
    	ArrayList<ExploreRegion> unallocatedRegion = new ArrayList<ExploreRegion>();
    	unallocatedRegion.add(ExploreRegion.UPLEFT);
    	unallocatedRegion.add(ExploreRegion.UPRIGHT);
    	unallocatedRegion.add(ExploreRegion.DOWNLEFT);
    	unallocatedRegion.add(ExploreRegion.DOWNRIGHT);
    	
    	// remove region from unallocatedRegion if exist
    	for (int i = 0; i < this.agentsExploreList.length; i++) { // iterate through agentsExploreList
    		for (int j = 0; j < unallocatedRegion.size(); j++) { // iterate through unallocatedRegion
    			if (this.agentsExploreList[i] == unallocatedRegion.get(j)) {
    				try { 
    					System.out.println("Remove " + unallocatedRegion.get(j) + " from unallocated region based on agent" + (i+1));
	    				unallocatedRegion.remove(j);
	    				    				}
    				catch (Exception e) {
    				      System.out.println("Something went wrong.");
    				      
    				    }
    			}
    		}
    	}
    	
    	
    	// check if agent need to change region
    	ArrayList<Integer> agentReallocate = new  ArrayList<Integer>();
    	
    	Set<ExploreRegion> set = new HashSet<ExploreRegion>();
    	if (this.agentsExploreList.length > 0) {
	    	for (int i = 0; i < this.agentsExploreList.length; i++) {
				if (set.add(this.agentsExploreList[i]) == false) { 
	    			System.out.println(this.agentsExploreList[i] + " has duplicate agents.");
	    			System.out.println("agent" + (i+1) + " added to potential reallocated list.");
	    			agentReallocate.add(i+1); // agent1 indexed as 0
	    			} 	
	    	}
    	}
    	
    	
    	String agentName = name.substring(name.length() - 1);
    	int agentID = Integer.parseInt(agentName); 
    	
    	// remove one of the agent and set as the exploring agent (since there's 5 agents for 4 regions)
    	if (agentID == agentReallocate.get(0)) {
    		this.exploringAgent = true;
    	}
    	
    	agentReallocate.remove(0);
    	
    	for (int k = 0; k < agentReallocate.size(); k++) { 
    		System.out.println("agent" + agentReallocate.get(k) + " to be reallocated.");
    	}
    	
    	
    	// if agent does not need to change region
    	if (agentReallocate.contains(agentID) == false) {
    		System.out.println(name + " need not change region");
    		return this.exploreRegionCoord;
    	}
    		
    		
    	// assign an agent to go to each unallocatedRegion 
    	System.out.println("unallocated region: " + unallocatedRegion);
    	
    	while (unallocatedRegion.size() > 0) {
    		for (int k = 0; k < unallocatedRegion.size(); k++) {
    			System.out.println(unallocatedRegion.get(k) + " assigned to agent " + agentReallocate.get(0));
    			if (agentID == agentReallocate.get(0)) {
    				System.out.println("Reassigning " + name + " to region " + unallocatedRegion.get(k) + " now ...");
    				ArrayList<Integer> newExploreRegionCoord = new ArrayList<Integer>(); // xmin, xmax, ymin, ymax
    				newExploreRegionCoord = getExploreRegionCoord(unallocatedRegion.get(k), Parameters.xDimension, Parameters.yDimension);
    				// System.out.println("Old Coord: " + this.exploreRegionCoord);
    				// System.out.println("New Coord: " + newExploreRegionCoord);
    				return newExploreRegionCoord;
    			}
    			agentReallocate.remove(0);
    		}
    	 
    	}
    	return new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0));
	}

}