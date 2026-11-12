package io.github.connectcolours.recents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecentTaskFilterTest {
    private final Set<String> hidden = new HashSet<>(Arrays.asList("hide.a", "hide.b"));
    private RecentTaskFilter filter() throws Exception {
        return new RecentTaskFilter(Group.class, Task.class, Object.class, 0);
    }
    public static final class Key { public int userId; Key(int user) { userId = user; } }
    public static final class Task {
        public Key key;
        final String name;
        Task(String name) { this(name, 0); }
        Task(String name, int user) { this.name = name; key = new Key(user); }
        public String getPackageName() { return name; }
    }
    public static final class Group {
        public Task task1, task2;
        public Object bounds;
        public Group(Task a, Task b, Object bounds) { task1 = a; task2 = b; this.bounds = bounds; }
    }

    @Test public void filtersAllTasksOfSelectedAppsWithoutMutatingCache() throws Exception {
        Group a = new Group(new Task("hide.a"), null, null);
        Group b = new Group(new Task("visible"), null, null);
        Group c = new Group(new Task("hide.a"), null, null);
        ArrayList<Group> source = new ArrayList<>(Arrays.asList(a, b, c));
        assertEquals(Collections.singletonList(b), filter().groups(source, hidden));
        assertEquals(3, source.size());
        assertSame(a, source.get(0));
        assertEquals(source, filter().groups(source, Collections.emptySet()));
    }

    @Test public void keepsVisibleHalfOfEitherSplitWithoutChangingOriginalPair() throws Exception {
        Task kept = new Task("visible"), removed = new Task("hide.a");
        for (boolean firstHidden : new boolean[] {true, false}) {
            Group group = new Group(firstHidden ? removed : kept, firstHidden ? kept : removed, new Object());
            ArrayList<Object> result = filter().groups(new ArrayList<>(Collections.singletonList(group)), hidden);
            Group single = (Group) result.get(0);
            assertSame(kept, single.task1);
            assertNull(single.task2);
            assertNull(single.bounds);
            assertNotNull(group.task2);
            assertNotNull(group.bounds);
        }
    }

    @Test public void allHiddenProducesEmptyDisplayList() throws Exception {
        Group pair = new Group(new Task("hide.a"), new Task("hide.b"), new Object());
        assertTrue(filter().groups(new ArrayList<>(Collections.singletonList(pair)), hidden).isEmpty());
    }

    @Test public void doesNotHideSamePackageInAnotherUserOrUnknownPackage() throws Exception {
        Group otherUser = new Group(new Task("hide.a", 10), null, null);
        Group unknown = new Group(new Task(null), null, null);
        ArrayList<Group> source = new ArrayList<>(Arrays.asList(otherUser, unknown));
        assertEquals(source, filter().groups(source, hidden));
    }

    @Test public void runningTaskPlaceholderUsesSameFilterAndRetainsArrayType() throws Exception {
        Task removed = new Task("hide.a"), visible = new Task("visible");
        Task[] source = {removed, visible};
        Task[] result = (Task[]) filter().running(source, hidden);
        assertArrayEquals(new Task[] {visible}, result);
        assertSame(removed, source[0]);
        assertEquals(0, ((Task[]) filter().running(new Task[] {removed}, hidden)).length);
    }
}
