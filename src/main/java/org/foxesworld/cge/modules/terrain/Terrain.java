package org.foxesworld.cge.modules.terrain;

import com.jme3.app.Application;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Terrain module: generates a gently rolling, infinite procedural terrain using noise,
 * dynamically expanding with chunks around the player.
 */
public class Terrain extends EngineModule<TerrainConfig> {

    private static final int CHUNK_SIZE = 128;
    private static final float CHUNK_WORLD_SIZE = 128f;
    private static final int LOAD_RADIUS = 2; // chunks in each direction

    private final Map<ChunkCoord, Geometry> loadedChunks = new HashMap<>();
    private Node terrainRoot;
    private Material terrainMaterial;
    private boolean ready = false;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private SimplexNoise noise;

    public Terrain(CalistaGameEngine calistaGameEngine) {
        super(Terrain.class, TerrainConfig.class, calistaGameEngine, false);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        terrainRoot = new Node("TerrainRoot");
        gameEngine.getRootNode().attachChild(terrainRoot);
        noise = new SimplexNoise(1337);
        getGameEngine().getAssetLoader().onAssetsLoaded(() -> {
            terrainMaterial = gameEngine.getMaterialManager().getMaterial("assets/MatDefs/grass.j3m");
            ready = true;
            Vector3f playerPos = getPlayerPosition();
            createChunksAroundPlayer(playerPos);
        });
    }

    @Override
    protected void updateModule(float tpf) {

    }

    @Override
    public void update(float tpf){
        if (!ready) return;
        Vector3f playerPos = getPlayerPosition();
        int playerChunkX = (int) Math.floor(playerPos.x / CHUNK_WORLD_SIZE);
        int playerChunkZ = (int) Math.floor(playerPos.z / CHUNK_WORLD_SIZE);
        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ) {
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            createChunksAroundPlayer(playerPos);
        }
    }

    private Vector3f getPlayerPosition() {
        // Change to actual player position if needed
        return gameEngine.getCamera().getLocation();
    }

    private void createChunksAroundPlayer(Vector3f playerPos) {
        int centerChunkX = (int) Math.floor(playerPos.x / CHUNK_WORLD_SIZE);
        int centerChunkZ = (int) Math.floor(playerPos.z / CHUNK_WORLD_SIZE);

        // 1. Generate all required chunks around player
        Map<ChunkCoord, Boolean> keepChunks = new HashMap<>();
        for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
            for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);
                keepChunks.put(coord, true);
                if (!loadedChunks.containsKey(coord)) {
                    // Enqueue creation and addition to scene/physics to avoid native crashes
                    final int fx = chunkX, fz = chunkZ;
                    gameEngine.enqueue(() -> {
                        Geometry chunkGeom = createProceduralChunk(fx, fz);
                        loadedChunks.put(new ChunkCoord(fx, fz), chunkGeom);
                        terrainRoot.attachChild(chunkGeom);
                        return null;
                    });
                }
            }
        }

        // 2. Remove old chunks outside radius (also via enqueue for thread safety)
        loadedChunks.entrySet().removeIf(entry -> {
            if (!keepChunks.containsKey(entry.getKey())) {
                Geometry geom = entry.getValue();
                gameEngine.enqueue(() -> {
                    removeChunkPhysics(geom);
                    geom.removeFromParent();
                    return null;
                });
                return true;
            }
            return false;
        });
    }

    private void removeChunkPhysics(Geometry geom) {
        RigidBodyControl rbc = geom.getControl(RigidBodyControl.class);
        if (rbc != null) {
            PhysicsModule phys = getGameEngine().getModuleManager().getModule(PhysicsModule.class);
            if (phys != null) {
                phys.getBulletAppState().getPhysicsSpace().remove(rbc);
            }
            geom.removeControl(rbc);
        }
    }

    private Geometry createProceduralChunk(int chunkX, int chunkZ) {
        float width = CHUNK_WORLD_SIZE;
        float height = CHUNK_WORLD_SIZE;
        int gridSize = CHUNK_SIZE;
        // Сделать горы и холмы более пологими (меньше вертикальный масштаб!)
        float verticalScale = Math.min(width, height) * 0.025f; // БЫЛО 0.07f — стало 0.025f

        float[] positions = new float[gridSize * gridSize * 3];
        float[] normals   = new float[gridSize * gridSize * 3];
        float[] texcoords = new float[gridSize * gridSize * 2];

        float noiseScale = 0.025f; // чуть плавнее (было 0.035f)
        float fractalPersistence = 0.4f; // чуть сглаженнее (было 0.5f)
        int fractalOctaves = 4; // меньше октав — меньше "шума", более мягкие холмы

        float xOffset = chunkX * width;
        float zOffset = chunkZ * height;

        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int i = z * gridSize + x;
                float fx = x / (float) (gridSize - 1);
                float fz = z / (float) (gridSize - 1);

                float wx = fx * width + xOffset;
                float wz = fz * height + zOffset;

                float elevation = 0;
                float amplitude = 1f;
                float frequency = 1f;
                float maxValue = 0;
                for (int o = 0; o < fractalOctaves; o++) {
                    float n = noise.noise2D(wx * noiseScale * frequency, wz * noiseScale * frequency);
                    elevation += n * amplitude;
                    maxValue += amplitude;
                    amplitude *= fractalPersistence;
                    frequency *= 2.0f;
                }
                elevation /= maxValue;
                elevation *= verticalScale;

                positions[i * 3]     = wx - xOffset - width / 2f;
                positions[i * 3 + 1] = elevation;
                positions[i * 3 + 2] = wz - zOffset - height / 2f;

                texcoords[i * 2]     = fx * 16f + xOffset / width * 16f;
                texcoords[i * 2 + 1] = fz * 16f + zOffset / height * 16f;
            }
        }

        int quads = (gridSize - 1) * (gridSize - 1);
        int[] indices = new int[quads * 6];
        int idx = 0;
        for (int z = 0; z < gridSize - 1; z++) {
            for (int x = 0; x < gridSize - 1; x++) {
                int i0 = z * gridSize + x;
                int i1 = i0 + 1;
                int i2 = i0 + gridSize;
                int i3 = i2 + 1;
                indices[idx++] = i0;
                indices[idx++] = i2;
                indices[idx++] = i1;
                indices[idx++] = i1;
                indices[idx++] = i2;
                indices[idx++] = i3;
            }
        }

        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int i = z * gridSize + x;
                Vector3f center = new Vector3f(
                        positions[i * 3],
                        positions[i * 3 + 1],
                        positions[i * 3 + 2]
                );
                Vector3f right = x < gridSize - 1 ?
                        new Vector3f(positions[(i + 1) * 3], positions[(i + 1) * 3 + 1], positions[(i + 1) * 3 + 2]) : center;
                Vector3f down = z < gridSize - 1 ?
                        new Vector3f(positions[(i + gridSize) * 3], positions[(i + gridSize) * 3 + 1], positions[(i + gridSize) * 3 + 2]) : center;
                Vector3f dx = right.subtract(center);
                Vector3f dz = down.subtract(center);
                Vector3f normal = dz.cross(dx).normalizeLocal();
                normals[i * 3]     = normal.x;
                normals[i * 3 + 1] = normal.y;
                normals[i * 3 + 2] = normal.z;
            }
        }

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texcoords));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();

        Geometry terrain = new Geometry("Chunk_" + chunkX + "_" + chunkZ, mesh);
        terrain.setMaterial(terrainMaterial);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        MeshCollisionShape shape = new MeshCollisionShape(mesh);
        RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
        terrain.addControl(rbc);
        PhysicsModule phys = getGameEngine().getModuleManager().getModule(PhysicsModule.class);
        if (phys != null) {
            phys.getBulletAppState().getPhysicsSpace().add(rbc);
        }

        terrain.setLocalTranslation(chunkX * CHUNK_WORLD_SIZE, 0, chunkZ * CHUNK_WORLD_SIZE);

        return terrain;
    }

    private static class ChunkCoord {
        final int x, z;
        ChunkCoord(int x, int z) { this.x = x; this.z = z; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord)) return false;
            ChunkCoord cc = (ChunkCoord) o;
            return x == cc.x && z == cc.z;
        }
        @Override public int hashCode() { return (x * 73856093) ^ (z * 19349663); }
    }

    @Override
    public void onConfigReloaded() {}
    @Override
    protected void cleanupModule(Application app) {}
    @Override
    protected void onEnable() {}
    @Override
    protected void onDisable() {}

    // SimplexNoise class stays unchanged!
}