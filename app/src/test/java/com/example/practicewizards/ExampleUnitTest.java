package com.example.practicewizards;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void multiply_isCorrect() {
        assertEquals(10, 5*2 );
    }

    @Test
    public void test_camera_opening() {
        Camera camera = new Camera(true);

    }
}
