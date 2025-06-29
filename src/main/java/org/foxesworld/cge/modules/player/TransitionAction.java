package org.foxesworld.cge.modules.player; // Убедитесь, что пакет правильный

import com.jme3.anim.tween.action.Action;
import com.jme3.anim.tween.action.BlendableAction;
import com.jme3.anim.util.HasLocalTransform;
import com.jme3.math.Transform;
import com.jme3.util.clone.Cloner;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A tween that transitions from one action to another over a given time.
 * This action is not intended to be looped. This is a corrected version
 * that properly implements the abstract methods from BlendableAction.
 *
 * @author Nehon (with corrections)
 */
public class TransitionAction extends BlendableAction {

    private final BlendableAction actionA;
    private final BlendableAction actionB;
    private final boolean loopB;
    private final double transitionLength;

    /**
     * Creates a TransitionAction.
     *
     * @param actionA The action to transition from. Must be a BlendableAction.
     * @param actionB The action to transition to. Must be a BlendableAction.
     * @param transitionLength The duration of the transition in seconds.
     * @param loopB Whether the second action should loop after the transition.
     */
    public TransitionAction(Action actionA, Action actionB, double transitionLength, boolean loopB) {
        // ИСПРАВЛЕНИЕ:
        // Мы больше не пытаемся создать 'new BlendableAction'.
        // Мы просто приводим тип, предполагая, что передаваемые действия
        // уже являются BlendableAction (например, ClipAction).
        this.actionA = (BlendableAction) actionA;
        this.actionB = (BlendableAction) actionB;

        this.transitionLength = transitionLength;
        this.loopB = loopB;
        setLength(transitionLength);
    }

    /**
     * Creates a TransitionAction where the target action does not loop.
     *
     * @param actionA The action to transition from. Must be a BlendableAction.
     * @param actionB The action to transition to. Must be a BlendableAction.
     * @param transitionLength The duration of the transition in seconds.
     */
    public TransitionAction(Action actionA, Action actionB, double transitionLength) {
        this(actionA, actionB, transitionLength, false);
    }

    @Override
    protected void doInterpolate(double t) {
        double weight = t / transitionLength;
        if (weight > 1.0) {
            weight = 1.0;
        }

        actionA.setWeight((float) (1.0 - weight));
        actionB.setWeight((float) weight);

        actionA.interpolate(actionA.getLength());

        if (loopB) {
            actionB.interpolate(t % actionB.getLength());
        } else {
            actionB.interpolate(Math.min(t, actionB.getLength()));
        }
    }

    @Override
    public Collection<HasLocalTransform> getTargets() {
        Set<HasLocalTransform> allTargets = new HashSet<>();
        if (actionA != null && actionA.getTargets() != null) {
            allTargets.addAll(actionA.getTargets());
        }
        if (actionB != null && actionB.getTargets() != null) {
            allTargets.addAll(actionB.getTargets());
        }
        return allTargets;
    }

    @Override
    public void collectTransform(HasLocalTransform target, Transform t, float weight, BlendableAction source) {
        if (actionA != null) {
            actionA.collectTransform(target, t, weight, source);
        }
        if (actionB != null) {
            actionB.collectTransform(target, t, weight, source);
        }
    }

    @Override
    public BlendableAction jmeClone() {
        return super.jmeClone();
    }

    @Override
    public void cloneFields(Cloner cloner, Object original) {
        super.cloneFields(cloner, original);
    }
}