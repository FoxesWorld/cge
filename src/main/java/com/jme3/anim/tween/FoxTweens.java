package com.jme3.anim.tween;

/*
 * Copyright (c) 2015-2022 jMonkeyEngine
 * All rights reserved.
 *
 * This file is a fork for the CGE project and may contain modifications.
 * Original source code is governed by the original license.
 */

import com.jme3.anim.tween.*;
import com.jme3.anim.util.Primitives;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static utility methods for creating common generic Tween objects.
 * This is a forked version for the CGE project.
 *
 * @author Paul Speed
 */
public final class FoxTweens {

    private static final Logger log = Logger.getLogger(FoxTweens.class.getName());

    private static final CurveFunction SMOOTH = new SmoothStep();
    private static final CurveFunction SINE = new Sine();

    private FoxTweens() {
    }

    // =================================================================================
    // НОВЫЕ МЕТОДЫ (добавлены для совместимости с современным API и PlayerController)
    // =================================================================================

    public static <T, V> Tween setter(T target, V value, BiConsumer<T, V> setter) {
        return new LambdaTween(0, (t, l) -> setter.accept(target, value));
    }

    public static Tween setter(Object target, String propertyName) {
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        return callMethod(target, setterName);
    }

    public static Tween fromTo(double startValue, double endValue, double duration, Tween setter) {
        if (!(setter instanceof CallMethod)) {
            throw new IllegalArgumentException("Setter tween must be an instance of CallMethod tween, use Tweens.setter(...)");
        }
        CallMethod call = (CallMethod) setter;
        return new FromTo(startValue, endValue, duration, call.target, call.method);
    }

    public static <T> Tween fromTo(T target, float from, float to, float duration, BiConsumer<T, Float> setter) {
        return new LambdaTween(duration, (t, l) -> {
            // t здесь - это нормализованное время [0..1] от длины твина
            float value = from + (to - from) * t.floatValue();
            setter.accept(target, value);
        });
    }

    // =================================================================================
    // КОД, КОТОРЫЙ ВЫ ПРЕДОСТАВИЛИ + НОВЫЙ МЕТОД call(Runnable)
    // =================================================================================

    public static Tween sequence(Tween... delegates) {
        return new Sequence(delegates);
    }

    public static Tween parallel(Tween... delegates) {
        return new Parallel(delegates);
    }

    public static Tween delay(double length) {
        return new Delay(length);
    }

    public static Tween stretch(double desiredLength, Tween... delegates) {
        if (delegates.length == 1) {
            return new Stretch(delegates[0], desiredLength);
        }
        return new Stretch(sequence(delegates), desiredLength);
    }

    public static Tween sineStep(Tween... delegates) {
        if (delegates.length == 1) {
            return new Curve(delegates[0], SINE);
        }
        return new Curve(sequence(delegates), SINE);
    }

    public static Tween smoothStep(Tween... delegates) {
        if (delegates.length == 1) {
            return new Curve(delegates[0], SMOOTH);
        }
        return new Curve(sequence(delegates), SMOOTH);
    }

    /**
     * НОВЫЙ МЕТОД: Создает твин, который вызывает Runnable.
     * @param runnable Код для выполнения.
     * @return Новый твин длиной 0.
     */
    public static Tween call(Runnable runnable) {
        // Мы можем повторно использовать наш LambdaTween для этой задачи!
        return new LambdaTween(0, (t, length) -> runnable.run());
    }

    public static Tween callMethod(Object target, String method, Object... args) {
        return new CallMethod(target, method, args);
    }

    public static Tween callTweenMethod(double length, Object target, String method, Object... args) {
        return new CallTweenMethod(length, target, method, args);
    }

    public static Tween loopCount(int count, Tween... delegates) {
        if (delegates.length == 1) {
            return new Loop(delegates[0], count);
        }
        return new Loop(sequence(delegates), count);
    }

    public static Tween loopDuration(double duration, Tween... delegates) {
        if (delegates.length == 1) {
            return new Loop(delegates[0], duration);
        }
        return new Loop(sequence(delegates), duration);
    }

    public static Tween invert(Tween delegate) {
        return new Invert(delegate);
    }

    public static Tween cycle(Tween delegate) {
        return sequence(delegate, invert(delegate));
    }

    private interface CurveFunction {
        double curve(double input);
    }

    private static class FromTo extends AbstractTween {
        private final double from, to;
        private final Object target;
        private final Method method;
        private final Object[] args = new Object[1];
        private final boolean isFloat;

        FromTo(double from, double to, double length, Object target, Method method) {
            super(length);
            this.from = from;
            this.to = to;
            this.target = target;
            this.method = method;
            this.isFloat = method.getParameterTypes()[0] == float.class;
        }

        @Override
        protected void doInterpolate(double t) {
            double value = from + (to - from) * t;
            try {
                if (isFloat) {
                    args[0] = (float) value;
                } else {
                    args[0] = value;
                }
                method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Error running method:" + method + " for object:" + target, e);
            }
        }
    }

    private static class LambdaTween extends AbstractTween {
        // BiConsumer принимает нормализованное время (t) и общую длину (length)
        private final BiConsumer<Double, Double> consumer;

        LambdaTween(double length, BiConsumer<Double, Double> consumer) {
            super(length);
            this.consumer = consumer;
        }

        @Override
        protected void doInterpolate(double t) {
            // В doInterpolate t уже нормализовано [0..1]
            consumer.accept(t, getLength());
        }
    }

    // Остальные внутренние классы остаются без изменений...
    private static class SmoothStep implements CurveFunction {
        @Override
        public double curve(double t) {
            if (t < 0) return 0;
            if (t > 1) return 1;
            return t * t * (3 - 2 * t);
        }
    }

    private static class Sine implements CurveFunction {
        @Override
        public double curve(double t) {
            if (t < 0) return 0;
            if (t > 1) return 1;
            double result = Math.sin(t * Math.PI - Math.PI * 0.5);
            return (result + 1) * 0.5;
        }
    }

    private static class Curve implements Tween {
        private final Tween delegate;
        private final CurveFunction func;
        private final double length;

        public Curve(Tween delegate, CurveFunction func) {
            this.delegate = delegate;
            this.func = func;
            this.length = delegate.getLength();
        }

        @Override
        public double getLength() {
            return length;
        }

        @Override
        public boolean interpolate(double t) {
            if (t < 0) return true;
            if (length == 0) return delegate.interpolate(t);
            t = func.curve(t / length);
            return delegate.interpolate(t * length);
        }

        @Override
        public String toString() { return getClass().getSimpleName() + "[delegate=" + delegate + ", func=" + func + "]"; }
    }

    private static class Sequence implements Tween, ContainsTweens {
        private final Tween[] delegates;
        private int current = 0;
        private double baseTime;
        private double length;

        public Sequence(Tween... delegates) {
            this.delegates = delegates;
            for (Tween t : delegates) {
                length += t.getLength();
            }
        }

        @Override
        public double getLength() {
            return length;
        }

        @Override
        public boolean interpolate(double t) {

            if (t < 0) {
                return true;
            }

            if (t < baseTime) {
                current = 0;
                baseTime = 0;
            }

            if (current >= delegates.length) {
                return false;
            }

            while (!delegates[current].interpolate(t - baseTime)) {
                baseTime += delegates[current].getLength();
                current++;
                if (current >= delegates.length) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[delegates=" + Arrays.asList(delegates) + "]";
        }

        @Override
        public Tween[] getTweens() {
            return delegates;
        }
    }

    private static class Parallel implements Tween, ContainsTweens {
        private final Tween[] delegates;
        private final boolean[] done;
        private double length;
        private double lastTime;

        public Parallel(Tween... delegates) {
            this.delegates = delegates;
            done = new boolean[delegates.length];

            for (Tween t : delegates) {
                if (t.getLength() > length) {
                    length = t.getLength();
                }
            }
        }

        @Override
        public double getLength() {
            return length;
        }

        protected void reset() {
            for (int i = 0; i < done.length; i++) {
                done[i] = false;
            }
        }

        @Override
        public boolean interpolate(double t) {
            if (t < 0) {
                return true;
            }

            if (t < lastTime) {
                reset();
            }
            lastTime = t;

            int runningCount = delegates.length;
            for (int i = 0; i < delegates.length; i++) {
                if (!done[i]) {
                    done[i] = !delegates[i].interpolate(t);
                }
                if (done[i]) {
                    runningCount--;
                }
            }
            return runningCount > 0;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[delegates=" + Arrays.asList(delegates) + "]";
        }

        @Override
        public Tween[] getTweens() {
            return delegates;
        }
    }

    private static class Delay extends AbstractTween {

        public Delay(double length) {
            super(length);
        }

        @Override
        protected void doInterpolate(double t) {
        }
    }

    private static class Stretch implements Tween, ContainsTweens {

        private final Tween[] delegate = new Tween[1];
        private final double length;
        private final double scale;

        public Stretch(Tween delegate, double length) {
            this.delegate[0] = delegate;
            this.length = length;
            if (length != 0) {
                this.scale = delegate.getLength() / length;
            } else {
                this.scale = 0;
            }
        }

        @Override
        public double getLength() {
            return length;
        }

        @Override
        public Tween[] getTweens() {
            return delegate;
        }

        @Override
        public boolean interpolate(double t) {
            if (t < 0) {
                return true;
            }
            if (length > 0) {
                t *= scale;
            } else {
                t = length;
            }
            return delegate[0].interpolate(t);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[delegate=" + delegate[0] + ", length=" + length + "]";
        }
    }

    private static class CallMethod extends AbstractTween {

        private Object target;
        private Method method;
        private Object[] args;

        public CallMethod(Object target, String methodName, Object... args) {
            super(0);
            if (target == null) {
                throw new IllegalArgumentException("Target cannot be null.");
            }
            this.target = target;
            this.args = args;

            if (args == null) {
                this.method = findMethod(target.getClass(), methodName);
            } else {
                this.method = findMethod(target.getClass(), methodName, args);
            }
            if (this.method == null) {
                throw new IllegalArgumentException("Method not found for:" + methodName + " on type:" + target.getClass());
            }
            this.method.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        private static Method findMethod(Class type, String name, Object... args) {
            for (Method m : type.getDeclaredMethods()) {
                if (!Objects.equals(m.getName(), name)) {
                    continue;
                }
                Class[] paramTypes = m.getParameterTypes();
                if (paramTypes.length != args.length) {
                    continue;
                }
                int matches = 0;
                for (int i = 0; i < args.length; i++) {
                    if (paramTypes[i].isInstance(args[i])
                            || Primitives.wrap(paramTypes[i]).isInstance(args[i])) {
                        matches++;
                    }
                }
                if (matches == args.length) {
                    return m;
                }
            }
            if (type.getSuperclass() != null) {
                return findMethod(type.getSuperclass(), name, args);
            }
            return null;
        }

        @Override
        protected void doInterpolate(double t) {
            try {
                method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Error running method:" + method + " for object:" + target, e);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[method=" + method + ", parms=" + Arrays.asList(args) + "]";
        }
    }

    private static class CallTweenMethod extends AbstractTween {

        private Object target;
        private Method method;
        private Object[] args;
        private int tIndex = -1;
        private boolean isFloat = false;

        public CallTweenMethod(double length, Object target, String methodName, Object... args) {
            super(length);
            if (target == null) {
                throw new IllegalArgumentException("Target cannot be null.");
            }
            this.target = target;

            this.method = findMethod(target.getClass(), methodName, args);
            if (this.method == null) {
                throw new IllegalArgumentException("Method not found for:" + methodName + " on type:" + target.getClass());
            }
            this.method.setAccessible(true);

            this.args = new Object[args.length + 1];
            if (tIndex == 0) {
                for (int i = 0; i < args.length; i++) {
                    this.args[i + 1] = args[i];
                }
            } else {
                for (int i = 0; i < args.length; i++) {
                    this.args[i] = args[i];
                }
            }
        }

        private static boolean isFloatType(Class type) {
            return type == Float.TYPE || type == Float.class;
        }

        private static boolean isDoubleType(Class type) {
            return type == Double.TYPE || type == Double.class;
        }

        private Method findMethod(Class type, String name, Object... args) {
            for (Method m : type.getDeclaredMethods()) {
                if (!Objects.equals(m.getName(), name)) {
                    continue;
                }
                Class[] paramTypes = m.getParameterTypes();
                if (paramTypes.length != args.length + 1) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Param lengths of [" + m + "] differ.  method arg count:" + paramTypes.length + "  looking for:" + (args.length + 1));
                    }
                    continue;
                }

                if (isFloatType(paramTypes[0]) || isDoubleType(paramTypes[0])) {
                    int matches = 0;
                    for (int i = 1; i < paramTypes.length; i++) {
                        if (paramTypes[i].isInstance(args[i - 1])) {
                            matches++;
                        }
                    }
                    if (matches == args.length) {
                        tIndex = 0;
                        isFloat = isFloatType(paramTypes[0]);
                    }
                }
                if (tIndex >= 0) {
                    return m;
                }

                int last = paramTypes.length - 1;
                if (isFloatType(paramTypes[last]) || isDoubleType(paramTypes[last])) {
                    int matches = 0;
                    for (int i = 0; i < last; i++) {
                        if (paramTypes[i].isInstance(args[i])) {
                            matches++;
                        }
                    }
                    if (matches == args.length) {
                        tIndex = last;
                        isFloat = isFloatType(paramTypes[last]);
                        return m;
                    }
                }
            }
            if (type.getSuperclass() != null) {
                return findMethod(type.getSuperclass(), name, args);
            }
            return null;
        }

        @Override
        protected void doInterpolate(double t) {
            try {
                if (isFloat) {
                    args[tIndex] = (float) t;
                } else {
                    args[tIndex] = t;
                }
                method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Error running method:" + method + " for object:" + target, e);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[method=" + method + ", parms=" + Arrays.asList(args) + "]";
        }
    }

    private static class Loop implements Tween, ContainsTweens {

        private final Tween[] delegate = new Tween[1];
        private final double length;
        private final int loopCount;
        private double baseTime;
        private int current = 0;

        public Loop (Tween delegate, double duration) {
            if (delegate.getLength() <= 0) {
                throw new IllegalArgumentException("Delegate length must be greater than 0");
            }
            if (duration <= 0) {
                throw new IllegalArgumentException("Duration must be greater than 0");
            }

            this.delegate[0] = delegate;
            this.length = duration;
            this.loopCount = (int) Math.ceil(duration / delegate.getLength());
        }

        public Loop (Tween delegate, int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("Loop count must be greater than 0");
            }

            this.delegate[0] = delegate;
            this.length = count * delegate.getLength();
            this.loopCount = count;
        }

        @Override
        public double getLength() {
            return length;
        }

        @Override
        public Tween[] getTweens() {
            return delegate;
        }

        @Override
        public boolean interpolate(double t) {
            if (t < 0) {
                return true;
            }

            if (t < baseTime) {
                current = 0;
                baseTime = 0;
            }

            if (current >= loopCount) {
                return false;
            }

            while (!delegate[0].interpolate(t - baseTime)) {
                baseTime += delegate[0].getLength();
                current++;
                if (current >= loopCount) {
                    return false;
                }
            }

            return t < length;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[delegate=" + delegate[0] + ", length=" + length + "]";
        }
    }

    private static class Invert extends AbstractTween implements ContainsTweens {

        private final Tween[] delegate = new Tween[1];

        public Invert( Tween delegate ) {
            super(delegate.getLength());
            this.delegate[0] = delegate;
        }

        @Override
        protected void doInterpolate(double t) {
            delegate[0].interpolate((1.0 - t) * getLength());
        }

        @Override
        public Tween[] getTweens() {
            return delegate;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[delegate=" + delegate[0] + ", length=" + getLength() + "]";
        }
    }
}