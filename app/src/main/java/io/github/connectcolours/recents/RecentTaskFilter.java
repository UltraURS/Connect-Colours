package io.github.connectcolours.recents;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

/** Filters presentation copies only; never modifies a task or the launcher's task cache. */
public final class RecentTaskFilter {
    private final Field first, second, key, userId;
    private final Method packageName;
    private final Constructor<?> singleGroup;
    private final int currentUser;

    public RecentTaskFilter(Class<?> group, Class<?> task, Class<?> bounds, int currentUser)
            throws ReflectiveOperationException {
        first = group.getField("task1");
        second = group.getField("task2");
        key = task.getField("key");
        userId = key.getType().getField("userId");
        packageName = task.getMethod("getPackageName");
        singleGroup = group.getConstructor(task, task, bounds);
        this.currentUser = currentUser;
    }

    private boolean hidden(Object task, Set<String> packages) throws ReflectiveOperationException {
        return task != null && userId.getInt(key.get(task)) == currentUser
                && packages.contains((String) packageName.invoke(task));
    }

    public ArrayList<Object> groups(ArrayList<?> source, Set<String> packages) throws ReflectiveOperationException {
        ArrayList<Object> visible = new ArrayList<>();
        for (Object group : source) {
            Object a = first.get(group), b = second.get(group);
            boolean hideA = hidden(a, packages), hideB = hidden(b, packages);
            if (!hideA && !hideB) visible.add(group);
            else if (!hideA) visible.add(singleGroup.newInstance(a, null, null));
            else if (b != null && !hideB) visible.add(singleGroup.newInstance(b, null, null));
        }
        return visible;
    }

    public Object running(Object source, Set<String> packages) throws ReflectiveOperationException {
        ArrayList<Object> visible = new ArrayList<>();
        for (int i = 0; i < Array.getLength(source); i++) {
            Object task = Array.get(source, i);
            if (task != null && !hidden(task, packages)) visible.add(task);
        }
        Object result = Array.newInstance(source.getClass().getComponentType(), visible.size());
        for (int i = 0; i < visible.size(); i++) Array.set(result, i, visible.get(i));
        return result;
    }
}
