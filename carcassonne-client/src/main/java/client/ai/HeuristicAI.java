package client.ai;

import client.ai.evaluator.*;
import client.ai.target.TargetList;
import logic.Game;
import logic.board.GameBoard;
import logic.dragon.Dragon;
import logic.math.Vector2;
import logic.player.Player;
import logic.tile.Direction;
import logic.tile.Tile;
import logic.tile.TileRotation;
import logic.tile.area.Area;
import logic.tile.chunk.Chunk;
import logic.tile.chunk.ChunkId;

/**
 * AI that uses a heuristic to determine the best move to make.
 */
public class HeuristicAI extends AI {
    /**
     * Maximum number of entries in the target list picker.
     */
    private static final int TARGET_LIST_MAX_SIZE = 100;

    /**
     * Minimum of score to consider a meeple placement.
     */
    private static final int MEEPLE_PLACEMENT_MIN_SCORE = 10;

    /**
     * Minimum of score to consider a meeple removal.
     */
    private static final int MEEPLE_REMOVAL_MIN_SCORE = 10;

    /**
     * Minimum of score to consider a fairy placement.
     */
    private static final int FAIRY_PLACEMENT_MIN_SCORE = 10;

    /**
     * Evaluator used to evaluate the position of the tile to place.
     */
    private final TilePositionEvaluator tilePositionEvaluator;

    /**
     * Evaluator used to evaluate the placement of the meeple on the specified chunk.
     */
    private final MeeplePlacementEvaluator meeplePlacementEvaluator;

    /**
     * Evaluator used to evaluate the removal of the meeple on the specified chunk.
     */
    private final MeepleRemovalEvaluator meepleRemovalEvaluator;

    /**
     * Evaluator used to evaluate the placement of the fairy on the specified chunk.
     */
    private final FairyPlacementEvaluator fairyPlacementEvaluator;

    /**
     * Evaluator used to evaluate the moving of the dragon on the specified tile.
     */
    private final DragonMovementEvaluator dragonMovementEvaluator;

    public HeuristicAI(Player player) {
        super(player);

        Game game = player.getGame();

        if (player.getGame() == null) {
            throw new IllegalArgumentException("Player must be in a game.");
        }

        this.tilePositionEvaluator = new TilePositionEvaluator(game);
        this.meeplePlacementEvaluator = new MeeplePlacementEvaluator(player);
        this.meepleRemovalEvaluator = new MeepleRemovalEvaluator(player);
        this.fairyPlacementEvaluator = new FairyPlacementEvaluator(game, player);
        this.dragonMovementEvaluator = new DragonMovementEvaluator(game, player);
    }

    /**
     * Finds the best position for the given tile to be placed.
     *
     * @param tile The tile to find a position for.
     * @return The best position for the tile to be placed.
     */
    @Override
    protected TilePosition findPositionForTile(Tile tile) {
        TargetList<TilePosition> targetList = new TargetList<>(TARGET_LIST_MAX_SIZE);

        for (int i = 0; i < TileRotation.NUM_ROTATIONS; i++) {
            tile.rotate();
            TileRotation rotation = tile.getRotation();

            for (Vector2 freePlace : getGame().getBoard().findFreePlacesForTile(tile)) {
                targetList.add(new TilePosition(freePlace, rotation), tilePositionEvaluator.evaluate(tile, freePlace));
            }
        }

        return targetList.pick();
    }

    /**
     * Finds a tile's chunk where the meeple can be placed.
     * Returns null if no chunk should be placed.
     *
     * @return The chunk where the meeple can be placed.
     */
    @Override
    protected Chunk findChunkToPlaceMeeple(Tile tileDrawn) {
        TargetList<Chunk> targetList = new TargetList<>(TARGET_LIST_MAX_SIZE);

        if (tileDrawn.hasPortal()) {
            for (Tile tile : getGame().getBoard().getTiles()) {
                findChunkToPlaceMeeple(tile, targetList);
            }
        } else {
            findChunkToPlaceMeeple(tileDrawn, targetList);
        }

        return targetList.pick();
    }

    /**
     * Finds a tile's chunk where the meeple can be placed.
     *
     * @param tile       The tile to find a chunk for.
     * @param targetList The target list to add the chunks to.
     */
    private void findChunkToPlaceMeeple(Tile tile, TargetList<Chunk> targetList) {
        for (ChunkId chunkId : ChunkId.values()) {
            Chunk chunk = tile.getChunk(chunkId);

            if (!chunk.getArea().hasMeeple()) {
                int score = meeplePlacementEvaluator.evaluate(chunk);

                if (score >= MEEPLE_PLACEMENT_MIN_SCORE) {
                    targetList.add(chunk, score);
                }
            }
        }
    }

    /**
     * Finds a tile's chunk where the meeple can be removed.
     * Returns null if no chunk should be removed.
     *
     * @return The chunk where the meeple can be placed.
     */
    @Override
    protected Chunk findChunkToRemoveMeeple(Tile tileDrawn) {
        TargetList<Chunk> targetList = new TargetList<>(TARGET_LIST_MAX_SIZE);

        for (Area area : tileDrawn.getAreas()) {
            for (Chunk chunk : area.getChunks()) {
                if (chunk.hasMeeple()) {
                    int score = meepleRemovalEvaluator.evaluate(chunk);

                    if (score >= MEEPLE_REMOVAL_MIN_SCORE) {
                        targetList.add(chunk, score);
                    }
                }
            }
        }

        return targetList.pick();
    }

    /**
     * Finds a tile's chunk where the fairy can be placed.
     * Returns null if no chunk should be placed.
     *
     * @return The chunk where the fairy can be placed.
     */
    @Override
    protected Chunk findChunkToPlaceFairy() {
        TargetList<Chunk> targetList = new TargetList<>(TARGET_LIST_MAX_SIZE);
        GameBoard board = getGame().getBoard();

        for (Tile tile : board.getTiles()) {
            for (ChunkId chunkId : ChunkId.values()) {
                Chunk chunk = tile.getChunk(chunkId);

                if (chunk.hasMeeple() && chunk.getMeeple().getOwner() == player) {
                    int score = fairyPlacementEvaluator.evaluate(chunk);

                    if (score >= FAIRY_PLACEMENT_MIN_SCORE) {
                        targetList.add(chunk, score);
                    }
                }
            }
        }

        Chunk target = targetList.pick();

        if (target == null || board.hasFairy() && board.getFairy().getChunk() == target) {
            return null;
        }

        return target;
    }

    /**
     * Finds a position for the given dragon.
     *
     * @param dragon The dragon to find a position for.
     * @return The position where the dragon can be placed.
     */
    @Override
    protected Direction findDirectionForDragon(Dragon dragon) {
        TargetList<Direction> targetList = new TargetList<>(TARGET_LIST_MAX_SIZE);

        for (Direction direction : Direction.values()) {
            Vector2 position = dragon.getPosition().add(direction.value());

            if (dragon.canMoveTo(position)) {
                targetList.add(direction, dragonMovementEvaluator.evaluate(position));
            }
        }

        return targetList.pick();
    }
}
