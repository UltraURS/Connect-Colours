package io.github.connectcolours;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityRenderTest {
    @Test
    public void rendersHomeStatesAndSettingsForVisualInspection() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View root = activity.findViewById(android.R.id.content);
            ReflectionHelpers.setField(activity, "preferences", null);
            root.findViewWithTag("nav_home").performClick();
            render(root, "home-inactive");
            ReflectionHelpers.setField(activity, "preferences", activity.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE));
            root.findViewWithTag("nav_home").performClick();
            render(root, "home-active");
            root.findViewWithTag("nav_settings").performClick();
            render(root, "settings");
        }
    }

    private void render(View root, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1560, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 720, 1560);
        Bitmap bitmap = Bitmap.createBitmap(720, 1560, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File directory = new File("../.tooling/ui-0.2.2");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
