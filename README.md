# AI6125 Multi-Agent System Project: Build Intelligent Agents for Tileworld Environment 


This implementation is an extension to the work from main branch (base implementation). It focuses on the initialisation process of finding the fuel station.   
<br/>

## Idea
Sweep the environment to find the fuel station first before continuing to explore. (As base implementation had lower score due to fuel station not found, especially in larger env.)

1. Environment is split into 4 regions (upleft, upright, downleft, downright)
2. Each agent decides on a region based on proximity.
3. Assign agent(s) to change region if there is/are unallocated region(s).
4. Search path (current-start-end):
- from current position to start search position: move in L shaped manner. Start position is based on proximity to search region corners.
- from start to goal/end search position: move in square wave motion, e.g. repeat(move vertical, move 7 steps horizontally). End position is diagonally opposite the start position. 
- if there is an obstacle in path, it will go around the obstacle. (C shaped path)
5. One agent explores (i.e. does not participate in fuel station search), as there are 5 agents and 4 regions. 

Once fuel station is found, the agents move as per base implementation.  
<br/>

## Results
Ran experiment 10 x 5 = 50 times. 

Environment 1 (50x50 envsize): 

Score: AVERAGE(466.7, 470.8, 475.4, 473.2, 477.5) = **472.72**

Failure to find fuel station: 0 

Environment 2 (80x80 envsize): 

Score: AVERAGE(750.8, 683.2, 755.6, 767.6, 742.1) = **739.86** 

Failure to find fuel station: once in experiment 2  
<br/>


## Potential future work
- Understand there are cases where fuel station is not found.
- Assign closest agent to the empty search region. Currently, the duplicated agent is assigned to the empty region. Potential challenge is the cloest agent's original search region may be empty and hence need to reassign recursively.   
<br/>

## Other ideas
- Broadcast and check if other agents' goal clashes with own goal.
- Nearing the endTime, if agent's fuel is sufficient to last until the endTime, the agent need not focus on refuel. 
- Set adaptive threshold to refuel (in needToRefuel method) based on environment size etc.