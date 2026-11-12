package io.github.connectcolours.hook;

import android.app.Activity;
import android.app.Dialog;
import android.view.ContextThemeWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class SimDialogTest {
    public static class TextMessage {
        public String text = "尊敬的客户： 感谢您选择中国移动通信！";
    }

    // Models the verified APK's members; it is not the actual vendor Activity.
    public static class StkActivity extends Activity {
        private TextMessage mTextMsg = new TextMessage();
        private Dialog mAlertDialog;
        int responseCount;
        int responseId;
        boolean confirmed;
        boolean acceptsResponse = true;

        private void sendResponse(int id, boolean confirmation) {
            responseCount++;
            responseId = id;
            confirmed = confirmation;
            if (acceptsResponse) finish();
        }
    }

    private boolean intercept(Dialog dialog) throws Exception {
        Field message = StkActivity.class.getDeclaredField("mTextMsg");
        Field alert = StkActivity.class.getDeclaredField("mAlertDialog");
        Method response = StkActivity.class.getDeclaredMethod("sendResponse", int.class, boolean.class);
        message.setAccessible(true);
        alert.setAccessible(true);
        response.setAccessible(true);
        return SimWelcomeHook.dismissWelcome(dialog, StkActivity.class, message,
                TextMessage.class.getDeclaredField("text"), alert, response);
    }

    @Test
    public void welcomeUsesNativeCancelBeforeShowingThroughThemeWrappers() throws Exception {
        try (ActivityController<StkActivity> controller = Robolectric.buildActivity(StkActivity.class).setup()) {
            StkActivity activity = controller.get();
            activity.mAlertDialog = new Dialog(new ContextThemeWrapper(
                    new ContextThemeWrapper(activity, android.R.style.Theme_Material),
                    android.R.style.Theme_Material));
            assertTrue(intercept(activity.mAlertDialog));
            assertEquals(13, activity.responseId);
            assertFalse(activity.confirmed);
            assertTrue(activity.isFinishing());
            assertFalse(activity.mAlertDialog.isShowing());
            assertFalse(intercept(activity.mAlertDialog));
            assertEquals(1, activity.responseCount);
        }
    }

    @Test
    public void otherPhoneActivityAndOtherDialogsAreUntouched() throws Exception {
        try (ActivityController<Activity> phone = Robolectric.buildActivity(Activity.class).setup();
             ActivityController<StkActivity> stk = Robolectric.buildActivity(StkActivity.class).setup()) {
            assertFalse(intercept(new Dialog(phone.get())));
            stk.get().mAlertDialog = new Dialog(stk.get());
            assertFalse(intercept(new Dialog(stk.get())));
            assertEquals(0, stk.get().responseCount);
        }
    }

    @Test
    public void nonWelcomeAndMissingMessagesAreUntouched() throws Exception {
        try (ActivityController<StkActivity> controller = Robolectric.buildActivity(StkActivity.class).setup()) {
            StkActivity activity = controller.get();
            activity.mAlertDialog = new Dialog(activity);
            activity.mTextMsg.text = "是否订购套餐？";
            assertFalse(intercept(activity.mAlertDialog));
            activity.mTextMsg = null;
            assertFalse(intercept(activity.mAlertDialog));
            assertEquals(0, activity.responseCount);
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void failedNativeDismissalDoesNotSuppressTheDialog() throws Exception {
        try (ActivityController<StkActivity> controller = Robolectric.buildActivity(StkActivity.class).setup()) {
            StkActivity activity = controller.get();
            activity.mAlertDialog = new Dialog(activity);
            activity.acceptsResponse = false;
            assertFalse(intercept(activity.mAlertDialog));
            assertFalse(activity.isFinishing());
        }
    }
}
