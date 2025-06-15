package org.foxesworld.cge.importers.fbx;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.ModelKey;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import com.jme3.scene.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FBX importer leveraging JME's built-in FBX loader, enhanced with physics setup and
 * robust error handling. Inspired by RAGE-style patterns.
 */
@Deprecated
public class FBXImporter implements AssetLoader {
    private static final Logger logger = LoggerFactory.getLogger(FBXImporter.class);

    @Override
    public Spatial load(AssetInfo assetInfo) {
        if (!(assetInfo.getKey() instanceof ModelKey mk)) {
            throw new IllegalArgumentException("Expected ModelKey for FBX import, got "
                    + assetInfo.getKey().getClass().getSimpleName());
        }
        String fbxPath = mk.getName();
        logger.debug("Loading FBX model: {}", fbxPath);

        // Delegate to built-in FBX loader
        Spatial model = assetInfo.getManager().loadModel(fbxPath);
        if (model == null) {
            throw new RendererException("Failed to load FBX model: " + fbxPath);
        }

        // Wrap in root node
        Node root = new Node("FBXRoot");
        root.attachChild(model);

        // Setup physics for each Geometry child
        root.depthFirstTraversal(spat -> {
            if (spat instanceof Geometry geom) {
                try {
                    CollisionShape shape = CollisionShapeFactory.createMeshShape(geom);
                    RigidBodyControl control = new RigidBodyControl(shape, 0f);
                    geom.addControl(control);
                } catch (Exception e) {
                    logger.warn("Physics setup failed for {}: {}", geom.getName(), e.getMessage());
                }
            }
        });

        // Ensure shadow mode and alpha blending if needed
        root.depthFirstTraversal(spat -> {
            if (spat instanceof Geometry geom) {
                geom.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                Material mat = geom.getMaterial();
                if (mat != null && mat.getAdditionalRenderState().getBlendMode() == RenderState.BlendMode.Alpha) {
                    mat.setTransparent(true);
                }
            }
        });

        logger.debug("FBX model loaded and processed: {}", fbxPath);
        return root;
    }
}
