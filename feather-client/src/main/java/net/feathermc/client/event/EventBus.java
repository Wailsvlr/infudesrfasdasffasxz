package net.feathermc.client.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void register(Object subscriber) {
        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                method.setAccessible(true);
                Class<?> eventType = method.getParameterTypes()[0];
                int priority = method.getAnnotation(Subscribe.class).priority();
                listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                        .add(new Listener(subscriber, method, priority));
                listeners.get(eventType).sort(Comparator.comparingInt(l -> -l.priority));
            }
        }
    }

    public void unregister(Object subscriber) {
        listeners.values().forEach(list -> list.removeIf(l -> l.instance == subscriber));
    }

    public <T> T post(T event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list != null) {
            for (Listener l : list) {
                try {
                    l.method.invoke(l.instance, event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return event;
    }

    private record Listener(Object instance, Method method, int priority) {}
}
