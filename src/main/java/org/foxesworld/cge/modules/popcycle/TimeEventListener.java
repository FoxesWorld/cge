package org.foxesworld.cge.modules.popcycle;

public class TimeEventListener implements EventListener {
    private int priority;
    public TimeEventListener(int priority) { this.priority = priority; }
    @Override
    public void onEvent(GameEvent event) {
        TimeEvent te = (TimeEvent) event;
        //System.out.println("Новое время суток: " + te.getTimeOfDay());
        // Здесь можно запускать спавн новых NPC/транспорта в зависимости от времени
    }
    @Override
    public int getPriority() { return priority; }
}