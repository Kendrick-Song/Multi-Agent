package tileworld.agent;

public class MessagesCreator {
    private MessagesCreator() {
    }

    // construct a message contain the Coordinates of the fuel station
    public static final String FUEL_STATION_COOR = "Fuel Station Coordinates:%d,%d";

    // construct a message contain the INDEX of exploring area
    // and the manhattan distance to the corresponding corner
    public static final String EXPLORING_AREA = "Exploring Area:%d,%d";
}
