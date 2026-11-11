package dev.donut.client.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Subscribe {
        int priority() default 0;
    }

    private static class Listener implements Comparable<Listener> {
        final Object instance;
        final Method method;
        final int priority;

        Listener(Object instance, Method method, int priority) {
            this.instance = instance;
            this.method = method;
            this.priority = priority;
        }

        @Override
        public int compareTo(Listener o) {
            return Integer.compare(this.priority, o.priority);
        }
    }

    private final Map<Class<? extends Event>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public void register(Object obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            Subscribe sub = method.getAnnotation(Subscribe.class);
            if (sub == null) continue;
            if (method.getParameterCount() != 1) continue;
            Class<?> paramType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(paramType)) continue;

            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) paramType;
            listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                    .add(new Listener(obj, method, sub.priority()));
            listeners.get(eventType).sort(null);
        }
    }

    public void unregister(Object obj) {
        listeners.values().forEach(list -> list.removeIf(l -> l.instance == obj));
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list == null) return event;
        for (Listener listener : list) {
            try {
                listener.method.invoke(listener.instance, event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return event;
    }
}
