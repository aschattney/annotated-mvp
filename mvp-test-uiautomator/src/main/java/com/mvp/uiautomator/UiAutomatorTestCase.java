package com.mvp.uiautomator;

import android.app.Application;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;

import com.mvp.TestCase;

/**
 * Created by Andy on 14.02.2017.
 */

public class UiAutomatorTestCase<T extends Application> extends TestCase<T>
{

    private UiDevice mDevice;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public UiDevice device()
    {
        return mDevice;
    }

    protected void allowPermissionsIfNeeded()  {
        if (Build.VERSION.SDK_INT >= 23) {
            UiObject allowPermissions = mDevice.findObject(
                    new UiSelector().className("android.widget.Button")
                                    .resourceId("com.android.packageinstaller:id/permission_allow_button"));
            if (allowPermissions.exists()) {
                try {
                    allowPermissions.click();
                } catch (UiObjectNotFoundException e) {
                    Log.e(this.getClass().getName(), "There is no permissions dialog to interact with ", e);
                }
            }
        }
    }
}
