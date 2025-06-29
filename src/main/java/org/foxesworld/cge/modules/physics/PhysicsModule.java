package org.foxesworld.cge.modules.physics;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.debug.BulletDebugAppState;
import com.jme3.bullet.debug.DebugConfiguration;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.modules.physics.body.rigid.RigidBodyModule;
import org.foxesworld.cge.modules.physics.body.soft.SoftBodyModule;
import org.foxesworld.cge.modules.physics.collision.CollisionModule;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Модуль физики, предоставляющий единый фасад для управления физическим миром в jMonkeyEngine.
 * <p>
 * Агрегирует подсистемы для работы с твердыми телами, мягкими телами и коллизиями,
 * делегируя управление свойствами соответствующим подмодулям.
 * <p>
 * Обеспечивает потокобезопасность при взаимодействии с физическим миром и графом сцены
 * путем выполнения всех модификаций в основном потоке приложения через {@link Application#enqueue(Callable)}.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);

    private final CalistaGameEngine app;
    private final ModuleManager subManager;

    private BulletAppState bulletAppState;
    private BulletDebugAppState debugAppState;

    // Подмодули для делегирования логики
    private RigidBodyModule rigidBodyModule;
    private SoftBodyModule softBodyModule;
    private CollisionModule collisionModule;

    public PhysicsModule(CalistaGameEngine app) {
        super(PhysicsModule.class, PhysicsConfig.class, app, false);
        this.app = Objects.requireNonNull(app, "Application cannot be null");
        this.subManager = app.getModuleManager();
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("Initializing PhysicsModule...");

        // Инициализируем BulletAppState, если он еще не существует
        bulletAppState = app.getStateManager().getState(BulletAppState.class);
        if (bulletAppState == null) {
            bulletAppState = new BulletAppState();
            // Можно настроить поток для физики, например:
            // bulletAppState.setThreadingType(BulletAppState.ThreadingType.PARALLEL);
            app.getStateManager().attach(bulletAppState);
            logger.debug("New BulletAppState attached.");
        } else {
            logger.debug("Reusing existing BulletAppState.");
        }

        // Регистрируем и инициализируем подмодули
        registerAndInitSubModules();
        applyConfig();

        // Настраиваем отладку, если включена в конфигурации
        debugAppState = app.getStateManager().getState(BulletDebugAppState.class);

        if (getConfig().debug) {
            if (debugAppState == null) {
                DebugConfiguration debugConfig = new DebugConfiguration();
                debugAppState = new BulletDebugAppState(debugConfig);
                app.getStateManager().attach(debugAppState);
                logger.info("BulletDebugAppState attached with new configuration.");
            }

            debugAppState.setEnabled(true);
            logger.info("Bullet physics debug view enabled.");

        } else {
            if (debugAppState != null) {
                debugAppState.setEnabled(false);
                logger.info("Bullet physics debug view disabled as per configuration.");
                 if (app.getStateManager().hasState(debugAppState)) {
                     app.getStateManager().detach(debugAppState);
                     this.debugAppState = null;
                 }
            }
        }

        logger.info("PhysicsModule initialized successfully.");
    }

    private void registerAndInitSubModules() {
        collisionModule = new CollisionModule(this);
        rigidBodyModule = new RigidBodyModule(this);
        softBodyModule = new SoftBodyModule(this);

        subManager.register(collisionModule, 10);
        subManager.register(rigidBodyModule, 20);
        subManager.register(softBodyModule, 30);

        // Предполагается, что ModuleManager сам вызовет init для зарегистрированных модулей.
        // Если нет, их нужно инициализировать здесь вручную.
    }

    private void applyConfig() {
        PhysicsConfig cfg = getConfig();
        getPhysicsSpace().setGravity(cfg.gravity);
        logger.info("Physics world gravity set to {}.", cfg.gravity);
    }

    @Override
    public void onConfigReloaded() {
        logger.info("Reloading physics configuration...");
        applyConfig();
        // Делегируем перезагрузку конфигурации подмодулям
        subManager.getModules().stream()
                .filter(m -> m instanceof EngineModule) // Убедимся, что это наши подмодули
                .forEach(m -> {
                    try {
                        ((EngineModule<?>) m).onConfigReloaded();
                    } catch (Exception e) {
                        logger.error("Failed to reload config for submodule: " + m.getClass().getSimpleName(), e);
                    }
                });
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up PhysicsModule...");
        subManager.shutdown(app); // Очищаем подмодули

        if (debugAppState != null && app.getStateManager().hasState(debugAppState)) {
            app.getStateManager().detach(debugAppState);
            logger.debug("BulletDebugAppState detached.");
        }
        if (bulletAppState != null && app.getStateManager().hasState(bulletAppState)) {
            app.getStateManager().detach(bulletAppState);
            logger.debug("BulletAppState detached.");
        }
    }

    /**
     * Обеспечивает выполнение действия в потоке рендеринга jME3 для потокобезопасности.
     * @param action Действие для выполнения.
     */
    private void execute(Runnable action) {
        app.enqueue(action);
    }

    /**
     * Проверяет, инициализирован ли модуль, и выбрасывает исключение, если нет.
     */
    private void checkInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("PhysicsModule is not initialized. Cannot perform this action.");
        }
    }

    // --- Public API ---

    public PhysicsSpace getPhysicsSpace() {
        return bulletAppState.getPhysicsSpace();
    }

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    public CalistaGameEngine getApp() {
        return app;
    }

    // --- Делегирование методов подмодулям с обеспечением потокобезопасности ---

    public RigidBodyModule getRigidBodyModule() {
        return rigidBodyModule;
    }

    /**
     * Добавляет твердое тело для указанного Spatial с заданной массой.
     * Форма столкновения будет сгенерирована автоматически.
     * @param spatial Объект сцены.
     * @param mass Масса объекта (0 для статичного тела).
     */
    public void addRigidBody(Spatial spatial, float mass) {
        checkInitialized();
        execute(() -> rigidBodyModule.addRigidBody(spatial, mass));
    }

    public void removeRigidBody(Spatial spatial) {
        checkInitialized();
        execute(() -> rigidBodyModule.removeRigidBody(spatial));
    }

    public void setRigidBodyFriction(Spatial spatial, float friction) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            // Проверяем, что control все еще существует и привязан к миру,
            // на случай если объект был удален между вызовом и выполнением.
            if (control != null && control.getPhysicsSpace() != null) {
                control.setFriction(friction);
            } else {
                logger.warn("Attempted to set friction on a non-existent or detached rigid body for spatial: {}", spatial.getName());
            }
        });
    }

    public void setRigidBodyRestitution(Spatial spatial, float restitution) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            if (control != null && control.getPhysicsSpace() != null) {
                control.setRestitution(restitution);
            }
        });
    }

    public void setRigidBodyDamping(Spatial spatial, float linear, float angular) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            if (control != null && control.getPhysicsSpace() != null) {
                control.setDamping(linear, angular);
            }
        });
    }

    // --- SoftBodyModule Delegation ---

    public SoftBodyModule getSoftBodyModule() {
        return softBodyModule;
    }

    public void addSoftBody(Spatial spatial) {
        checkInitialized();
        execute(() -> softBodyModule.addSoftBody(spatial));
    }

    public void removeSoftBody(Spatial spatial) {
        checkInitialized();
        execute(() -> softBodyModule.removeSoftBody(spatial));
    }

    // --- CollisionModule Delegation ---

    public CollisionModule getCollisionModule() {
        return collisionModule;
    }

    // Остальные методы жизненного цикла, если они не используются, можно оставить пустыми.
    @Override protected void updateModule(float tpf) { /* Физика обновляется через BulletAppState */ }
    @Override protected void onEnable() { /* NOP */ }
    @Override protected void onDisable() { /* NOP */ }
}