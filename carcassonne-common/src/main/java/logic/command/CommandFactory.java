package logic.command;

/**
 * Class that creates commands from the given command type.
 */
public class CommandFactory {

    private CommandFactory() {
        // ignored
    }

    /**
     * Creates a command from the given command type.
     *
     * @param type the command type
     * @return the command
     */
    public static ICommand create(CommandType type) {
        return switch (type) {
            case PLACE_TILE_DRAWN -> new PlaceTileDrawnCommand();
            case PLACE_MEEPLE -> new PlaceMeepleCommand();
            case PLACE_FAIRY -> new PlaceFairyCommand();
            case REMOVE_MEEPLE -> new RemoveMeepleCommand();
            case SKIP_MEEPLE_PLACEMENT -> new SkipMeeplePlacementCommand();
            case ROTATE_TILE_DRAWN -> new RotateTileDrawnCommand();
            case MOVE_DRAGON -> new MoveDragonCommand();
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }
}
