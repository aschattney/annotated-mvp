package com.mvp.weather_example;

import android.app.Application;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;

public abstract class TestCase<T extends Application>
{

    private T app;

    public T app()
    {
        return app;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final AbstractRunner abstractRunner = (AbstractRunner) InstrumentationRegistry.getInstrumentation();
        T app = (T) abstractRunner.getApplication();
        this.app = app;
    }

    @After
    public void tearDown() throws Exception {
        resetFields(app.getClass());
    }

    private void resetFields(Class<?> clazz) throws Exception{
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field f : declaredFields){
            f.setAccessible(true);
            f.set(app, null);
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Application.class)){
            resetFields(superclass);
        }
    }

}
