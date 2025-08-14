package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.FoxTweens;
import com.jme3.anim.tween.action.BaseAction;
import com.jme3.anim.tween.action.LinearBlendSpace;
import com.jme3.app.Application;
import com.jme3.math.FastMath;
import org.foxesworld.cge.modules.player.config.AnimationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Упрощённый и очищенный контроллер анимаций игрока.
 * Использует встроенный в AnimComposer механизм блендинга для плавных переходов.
 */
public class PlayerAnimationController {
    private static final Logger logger = LoggerFactory.getLogger(PlayerAnimationController.class);
    private static final String DEFAULT_LAYER = AnimComposer.DEFAULT_LAYER;
    private static final int SPEED_SMOOTH_SAMPLES = 5;
    private static final float END_EPS = 1e-3f;

    private final Application app;
    private final AnimComposer composer;
    private final AnimationMapping animationMapping;
    private final Map<String, AnimationState> stateByLayer = new HashMap<>();
    private final Map<String, LinearBlendSpace> blendSpaces = new HashMap<>();
    private final Deque<Float> speedHistory = new ArrayDeque<>();
    private final Queue<AnimationTask> taskQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, PendingPlay> pendingPlays = new HashMap<>();
    private final List<String> availableAnimations;
    private final List<AnimationListener> listeners = new CopyOnWriteArrayList<>();

    private final Method setCurrentActionWithBlend;
    private final Map<String, String[]> locomotionPairs = new HashMap<>();

    public PlayerAnimationController(Application app, AnimComposer composer, AnimationMapping animationMapping) {
        this.app = app; // Оставляем, хотя в текущей логике не используется
        this.composer = Objects.requireNonNull(composer, "AnimComposer is required");
        this.animationMapping = animationMapping;
        this.setCurrentActionWithBlend = findBlendMethod(composer);
        ensureLayer(DEFAULT_LAYER);
        this.availableAnimations = Collections.unmodifiableList(enumerateAnimations());
        logger.info("Discovered animations: {}", availableAnimations);
    }

    private Method findBlendMethod(AnimComposer c) {
        try {
            // Ищем метод setCurrentAction(String, String, boolean, float)
            return c.getClass().getMethod("setCurrentAction", String.class, String.class, boolean.class, float.class);
        } catch (NoSuchMethodException e) {
            logger.warn("Extended 'setCurrentAction' with blend time not found. Blending will be instant.");
            return null;
        }
    }

    // --- Воспроизведение анимаций (УПРОЩЕННАЯ ВЕРСИЯ) ---
    public void play(String name, float blendTime, String layer, boolean loop) {
        String targetLayer = layer == null ? DEFAULT_LAYER : layer;
        String resolved = mapOrSelf(name);
        ensureLayer(targetLayer);

        AnimationState prev = stateByLayer.get(targetLayer);

        // Игнорируем вызов, если уже проигрываем эту анимацию.
        if (prev != null && prev.name.equals(resolved)) {
            return;
        }

        // Обработка очереди для одноразовых анимаций
        if ("idle".equals(resolved) && prev != null && !prev.loop && prev.clipLength > END_EPS && prev.elapsed + END_EPS < prev.clipLength) {
            pendingPlays.put(targetLayer, new PendingPlay(resolved, blendTime, loop));
            logger.debug("Queued '{}' on [{}] until '{}' finishes (remaining {}s)", resolved, targetLayer, prev.name, (prev.clipLength - prev.elapsed));
            return;
        }

        // --- ГЛАВНАЯ ЛОГИКА ---
        // Просто вызываем наш метод-обертку, который использует встроенный блендинг
        invokeSetAction(resolved, targetLayer, loop, blendTime);

        // Обновляем наше внутреннее состояние
        float length = getClipLength(resolved);
        stateByLayer.put(targetLayer, new AnimationState(resolved, blendTime, loop, length));
        logger.info("Play '{}' on [{}] (blend={}, loop={}, length={}s)", resolved, targetLayer, blendTime, loop, length);
        notifyStart(targetLayer, resolved);
    }

    // Этот метод теперь является основной рабочей лошадкой для блендинга
    private void invokeSetAction(String name, String layer, boolean loop, float blend) {
        // Если нашли продвинутый метод и просят блендинг - используем его
        if (blend > 0f && setCurrentActionWithBlend != null) {
            try {
                setCurrentActionWithBlend.invoke(composer, name, layer, loop, blend);
                return; // Успешно, выходим
            } catch (Exception ex) {
                logger.warn("Blended 'setCurrentAction' failed, falling back to instant switch: {}", ex.getMessage());
            }
        }
        // Запасной вариант: если блендинг не нужен, не найден или не удался - резкое переключение
        composer.setCurrentAction(name, layer, loop);
    }

    // Метод performCrossFade БОЛЬШЕ НЕ НУЖЕН И УДАЛЕН.

    // --- Остальной код остается практически без изменений ---

    public void forcePlay(String name, float blendTime, String layer, boolean loop) {
        String targetLayer = ensureLayer(layer);
        String resolved = mapOrSelf(name);
        invokeSetAction(resolved, targetLayer, loop, blendTime);
        float length = getClipLength(resolved);
        stateByLayer.put(targetLayer, new AnimationState(resolved, blendTime, loop, length));
        logger.info("Force play '{}' on [{}] (blend={}, length={}s)", resolved, targetLayer, blendTime, length);
        notifyStart(targetLayer, resolved);
    }

    public interface AnimationListener {
        void onAnimationStart(String layer, String animName);

        void onAnimationEnd(String layer, String animName);
    }

    public void addListener(AnimationListener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(AnimationListener l) {
        if (l != null) listeners.remove(l);
    }

    private void notifyStart(String layer, String anim) {
        for (AnimationListener l : listeners) {
            try {
                l.onAnimationStart(layer, anim);
            } catch (Exception ex) {
                logger.warn("listener start failed", ex);
            }
        }
    }

    private void notifyEnd(String layer, String anim) {
        for (AnimationListener l : listeners) {
            try {
                l.onAnimationEnd(layer, anim);
            } catch (Exception ex) {
                logger.warn("listener end failed", ex);
            }
        }
    }

    public void update(float tpf) {
        AnimationTask task;
        while ((task = taskQueue.poll()) != null) task.execute(this);
        if (!stateByLayer.isEmpty()) {
            List<String> finished = null;
            for (Map.Entry<String, AnimationState> e : stateByLayer.entrySet()) {
                AnimationState st = e.getValue();
                if (!st.loop && st.clipLength > END_EPS) {
                    st.elapsed += tpf;
                    if (st.elapsed + END_EPS >= st.clipLength) {
                        if (finished == null) finished = new ArrayList<>();
                        finished.add(e.getKey());
                    }
                }
            }
            if (finished != null) {
                for (String layer : finished) onAnimationFinished(layer);
            }
        }
    }

    private void onAnimationFinished(String layer) {
        AnimationState ended = stateByLayer.get(layer);
        if (ended == null) return;
        notifyEnd(layer, ended.name);
        logger.debug("Animation '{}' finished on layer [{}] (len={}s).", ended.name, layer, ended.clipLength);
        PendingPlay pending = pendingPlays.remove(layer);
        if (pending != null) {
            logger.debug("Starting pending '{}' on [{}] after '{}' finished", pending.name, layer, ended.name);
            play(pending.name, pending.blend, layer, pending.loop);
            return;
        }
        String fallback = mapOrSelf("idle");
        float blendBack = Math.max(0f, ended.blend);
        play(fallback, blendBack, layer, true);
    }

    private float smoothSpeed(float raw) {
        if (speedHistory.size() >= SPEED_SMOOTH_SAMPLES) {
            speedHistory.poll();
        }
        speedHistory.offer(raw);
        double sum = 0.0;
        for (Float f : speedHistory) {
            sum += f;
        }
        return speedHistory.isEmpty() ? 0f : (float) (sum / speedHistory.size());
    }

    public void setLocomotion(float rawSpeedNorm) {
        setLocomotion(rawSpeedNorm, null, null);
    }

    public void setLocomotion(float rawSpeedNorm, String prew, String current) {
        float norm = FastMath.clamp(rawSpeedNorm, 0f, 1f);
        norm = smoothSpeed(norm);
        String layer = DEFAULT_LAYER;
        String[] pair = locomotionPairs.get(layer);
        if (prew != null && current != null) {
            pair = new String[]{mapOrSelf(prew), mapOrSelf(current)};
            locomotionPairs.put(layer, pair);
        } else if (pair == null) {
            pair = new String[]{mapOrSelf("walk"), mapOrSelf("run")};
            locomotionPairs.put(layer, pair);
        }
        final String preAnim = pair[0];
        final String curAnim = pair[1];
        LinearBlendSpace bs = blendSpaces.computeIfAbsent(layer, l -> {
            ensureLayer(l);
            LinearBlendSpace space = new LinearBlendSpace(0f, 1f);
            composer.actionBlended(l + ":locomotion", space, preAnim, curAnim);
            composer.setCurrentAction(l + ":locomotion", l);
            return space;
        });
        try {
            composer.actionBlended(layer + ":locomotion", bs, preAnim, curAnim);
            composer.setCurrentAction(layer + ":locomotion", layer);
        } catch (Exception ignored) {
        }
        if (Math.abs(bs.getWeight() - norm) > 1e-3f) {
            bs.setValue(norm);
            stateByLayer.put(layer, new AnimationState("locomotion", 0f, true, Float.POSITIVE_INFINITY));
            logger.debug("Locomotion blend set to {} (pair: {}/{})", norm, preAnim, curAnim);
        }
    }

    public void queuePlay(String name, float blendTime, String layer, boolean loop) {
        String targetLayer = layer == null ? DEFAULT_LAYER : layer;
        String resolved = mapOrSelf(name);
        pendingPlays.put(targetLayer, new PendingPlay(resolved, blendTime, loop));
    }

    public void cancelPending(String layer) {
        pendingPlays.remove(layer == null ? DEFAULT_LAYER : layer);
    }

    private String mapOrSelf(String name) {
        if (name == null) return null;
        try {
            return animationMapping != null ? animationMapping.get(name) : name;
        } catch (Exception ex) {
            return name;
        }
    }

    private String ensureLayer(String layer) {
        if (layer == null) {
            layer = DEFAULT_LAYER;
        }
        if (!composer.getLayerNames().contains(layer)) {
            composer.makeLayer(layer, null);
        }
        return layer;
    }

    private float getClipLength(String animName) {
        if (animName == null) return 0f;
        try {
            AnimClip clip = composer.getAnimClip(animName);
            if (clip != null) return (float) clip.getLength();
        } catch (RuntimeException ignored) {
        }
        return 0f;
    }

    public List<String> getAvailableAnimations() {
        return availableAnimations;
    }

    public boolean hasAnimation(String name) {
        if (name == null) return false;
        return availableAnimations.contains(mapOrSelf(name));
    }

    public void playWithEvent(String name, float blendTime, String layer, boolean loop, Object target, String method, float delay) {
        taskQueue.add(new AnimationTask(name, blendTime, layer, loop, target, method, delay));
    }

    private static class AnimationState {
        final String name;
        final float blend;
        final boolean loop;
        float elapsed;
        final float clipLength;

        AnimationState(String name, float blend, boolean loop, float clipLength) {
            this.name = name;
            this.blend = blend;
            this.loop = loop;
            this.clipLength = clipLength;
            this.elapsed = 0f;
        }
    }

    private static class PendingPlay {
        final String name;
        final float blend;
        final boolean loop;

        PendingPlay(String name, float blend, boolean loop) {
            this.name = name;
            this.blend = blend;
            this.loop = loop;
        }
    }

    private static class AnimationTask {
        final String name;
        final float blend;
        final String layer;
        final boolean loop;
        final Object target;
        final String method;
        final float delay;

        AnimationTask(String name, float blend, String layer, boolean loop, Object target, String method, float delay) {
            this.name = name;
            this.blend = blend;
            this.layer = layer;
            this.loop = loop;
            this.target = target;
            this.method = method;
            this.delay = delay;
        }

        void execute(PlayerAnimationController ctrl) {
            ctrl.play(name, blend, layer, loop);
            try {
                BaseAction action = new BaseAction(FoxTweens.smoothStep(FoxTweens.sequence(FoxTweens.delay(delay), FoxTweens.callMethod(target, method))));
                String key = (layer == null ? DEFAULT_LAYER : layer) + ":evt:" + name;
                ctrl.composer.addAction(key, action);
                ctrl.composer.setCurrentAction(key, layer, loop);
                logger.info("Event '{}' scheduled at {}s on [{}]", name, delay, layer);
            } catch (Exception ex) {
                logger.warn("Failed to schedule event callback for '{}': {}", name, ex.getMessage());
            }
        }
    }

    // Код для enumerateAnimations и его хелперов остается без изменений.
    private List<String> enumerateAnimations() {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        if (composer == null) return new ArrayList<>(found);
        tryInvokeAndCollect(composer, found, "getAnimClipNames", "getAnimationNames", "getAnimNames", "getAnimClipNamesArray", "getNames");
        if (found.isEmpty()) {
            tryInvokeAndCollectMapKeys(composer, found, "getAnimClips", "getClips", "getAnimations", "getAnimMap");
        }
        if (found.isEmpty()) {
            reflectivelyGatherFromMethods(composer, found);
        }
        if (found.isEmpty()) {
            scanRecursivelyForNames(composer, found, new IdentityHashSet<>(), 0);
        }
        return new ArrayList<>(found);
    }

    private void tryInvokeAndCollect(Object target, Set<String> out, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                if (m.getParameterCount() != 0) continue;
                Object res = m.invoke(target);
                if (res == null) continue;
                if (res instanceof String[]) {
                    Collections.addAll(out, (String[]) res);
                    if (!out.isEmpty()) return;
                } else if (res instanceof Collection) {
                    for (Object o : (Collection<?>) res) if (o instanceof String) out.add((String) o);
                    if (!out.isEmpty()) return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void tryInvokeAndCollectMapKeys(Object target, Set<String> out, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                if (m.getParameterCount() != 0) continue;
                Object res = m.invoke(target);
                if (res instanceof Map) {
                    for (Object k : ((Map<?, ?>) res).keySet()) if (k instanceof String) out.add((String) k);
                    if (!out.isEmpty()) return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void reflectivelyGatherFromMethods(Object target, Set<String> out) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String name = m.getName().toLowerCase();
            if (name.contains("class") || name.contains("clone") || name.contains("notify") || name.contains("wait"))
                continue;
            try {
                Object res = m.invoke(target);
                if (res != null) scanObjectSimple(res, out);
                if (!out.isEmpty()) return;
            } catch (Exception ignored) {
            }
        }
    }

    private void scanRecursivelyForNames(Object obj, Set<String> out, IdentityHashSet visited, int depth) {
        if (obj == null || depth > 4 || visited.contains(obj)) return;
        visited.add(obj);
        if (scanObjectSimple(obj, out) && !out.isEmpty()) return;
        for (Field f : obj.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                scanRecursivelyForNames(f.get(obj), out, visited, depth + 1);
                if (!out.isEmpty()) return;
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean scanObjectSimple(Object obj, Set<String> out) {
        if (obj instanceof String) {
            out.add((String) obj);
            return true;
        }
        if (obj instanceof String[]) {
            Collections.addAll(out, (String[]) obj);
            return true;
        }
        if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                Object el = Array.get(obj, i);
                if (el instanceof String) out.add((String) el);
            }
            return true;
        }
        if (obj instanceof Collection) {
            for (Object el : (Collection<?>) obj) {
                String n = extractNameFromObject(el);
                if (n != null) out.add(n);
            }
            return true;
        }
        if (obj instanceof Map) {
            for (Object k : ((Map<?, ?>) obj).keySet()) {
                String n = extractNameFromObject(k);
                if (n != null) out.add(n);
            }
            if (out.isEmpty()) {
                for (Object v : ((Map<?, ?>) obj).values()) {
                    String n = extractNameFromObject(v);
                    if (n != null) out.add(n);
                }
            }
            return true;
        }
        String maybe = extractNameFromObject(obj);
        if (maybe != null) {
            out.add(maybe);
            return true;
        }
        return false;
    }

    private String extractNameFromObject(Object o) {
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        String[] tryMethods = {"getName", "name", "getId", "getKey"};
        for (String mn : tryMethods) {
            try {
                Method m = o.getClass().getMethod(mn);
                if (m.getReturnType() == String.class && m.getParameterCount() == 0) {
                    return (String) m.invoke(o);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static final class IdentityHashSet<E> {
        private final Map<E, Boolean> map = new IdentityHashMap<>();

        boolean contains(E e) {
            return map.containsKey(e);
        }

        void add(E e) {
            map.put(e, Boolean.TRUE);
        }
    }
}