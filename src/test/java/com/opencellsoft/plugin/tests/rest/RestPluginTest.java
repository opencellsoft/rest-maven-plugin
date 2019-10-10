package com.opencellsoft.plugin.tests.rest;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import com.opencellsoft.plugin.RestPlugin;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public class RestPluginTest
        extends AbstractMojoTestCase {

    public static final String NULL_PLUGIN_MESSAGE = "Null Plugin";

    /**
     * {@inheritDoc}
     *
     * @throws java.lang.Exception
     */
    @Override
    protected void setUp()
            throws Exception {
        try {
            // required
            super.setUp();

        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.lang.Exception
     */
    @Override
    protected void tearDown()
            throws Exception {
        try {
            // required
            super.tearDown();
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }

    }

    /**
     * @return
     * @throws Exception if any
     */
    protected RestPlugin loadPlugin()
            throws Exception {
        File pom = getTestFile("src/test/resources/unit/rest-project/pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());

        return (RestPlugin) lookupMojo("rest-request", pom);
    }

    /**
     * @throws Exception if any
     */
    public void testEndpoint()
            throws Exception {
        try {
            RestPlugin myPlugin = loadPlugin();
            String url="http://localhost:8080";
            assertNotNull(NULL_PLUGIN_MESSAGE, myPlugin);
            assertNotNull("Null", myPlugin.getEndpoint());
            assertTrue("Expected [" + url + "] Not equal to:[" +
                       myPlugin.getEndpoint().toString() + "]",
                       myPlugin.getEndpoint().toString().equals(url));
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }
    }
    /**
     * @throws Exception if any
     */
    public void testFileset()
            throws Exception {
        try {
            RestPlugin myPlugin = loadPlugin();
            assertNotNull(NULL_PLUGIN_MESSAGE, myPlugin);
            assertNotNull("Null Fileset", myPlugin.getFileset());
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }

    }
    /**
     * @throws Exception if any
     */
    public void testSaveResponse()
            throws Exception {
        try {
            RestPlugin myPlugin = loadPlugin();
            assertNotNull(NULL_PLUGIN_MESSAGE, myPlugin);
            assertNotNull("Null save response", myPlugin.getSaveResponse());
            assertFalse("save response is true", myPlugin.getSaveResponse());
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }
    }
    /**
     * @throws Exception if any
     */
    public void testConvertJavaFiles()
            throws Exception {
        try {
            RestPlugin myPlugin = loadPlugin();
            assertNotNull(NULL_PLUGIN_MESSAGE, myPlugin);
            myPlugin.execute();
        } catch (InvocationTargetException ex) {
            System.out.println(ex.getCause().toString());
        }
    }
}
