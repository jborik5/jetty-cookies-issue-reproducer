package org.jetty.example.test;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.*;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CookiesTestCase {

    private static final int LISTEN_PORT = 8080;
    private static final String COOKIE1_KEY = "COOKIE1";
    private static final String COOKIE1_VALUE = "ABC";
    private static final String COOKIE2_KEY = "COOKIE2";
    private static final String COOKIE2_VALUE = "123";

    private static Server server;

    private static Cookie[] cookies;
    private static CountDownLatch waitForCookies;

    @BeforeClass
    public static void startServer() throws Exception {
        server = new Server();
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setCookieCompliance(CookieCompliance.RFC6265);
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setPort(LISTEN_PORT);
        server.setConnectors(new Connector[] {connector});
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(TestServlet.class, "/*");
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stop();
    }

    @Before
    public void beforeTest() {
        cookies = null;
        waitForCookies = new CountDownLatch(1);
    }

    @Test
    public void cookiesSingleHeaderSeparatedByCommaTest() throws Exception {
        Socket clientSocket = new Socket("localhost", LISTEN_PORT);
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
        writer.println("GET / HTTP/1.1");
        writer.println("Host: localhost");
        writer.println(String.format("Cookie: $Version=\"1\"; %s=\"%s\", $Version=\"1\"; %s=\"%s\"", COOKIE1_KEY, COOKIE1_VALUE, COOKIE2_KEY, COOKIE2_VALUE));
        writer.println();
        writer.println("Content");
        writer.flush();

        clientSocket.close();

        Assert.assertTrue(waitForCookies.await(5, TimeUnit.SECONDS));
        Assert.assertNotNull(cookies);
        Assert.assertEquals(2, cookies.length);
        Assert.assertEquals(COOKIE1_KEY, cookies[0].getName());
        Assert.assertEquals(COOKIE1_VALUE, cookies[0].getValue());
        Assert.assertEquals(COOKIE2_KEY, cookies[1].getName());
        Assert.assertEquals(COOKIE2_VALUE, cookies[1].getValue());
    }

    @Test
    public void cookiesSingleHeaderSeparatedBySemicolonTest() throws Exception {
        Socket clientSocket = new Socket("localhost", LISTEN_PORT);
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
        writer.println("GET / HTTP/1.1");
        writer.println("Host: localhost");
        writer.println(String.format("Cookie: $Version=\"1\"; %s=\"%s\"; $Version=\"1\"; %s=\"%s\"", COOKIE1_KEY, COOKIE1_VALUE, COOKIE2_KEY, COOKIE2_VALUE));
        writer.println();
        writer.println("Content");
        writer.flush();

        clientSocket.close();

        Assert.assertTrue(waitForCookies.await(5, TimeUnit.SECONDS));
        Assert.assertNotNull(cookies);
        Assert.assertEquals(2, cookies.length);
        Assert.assertEquals(COOKIE1_KEY, cookies[0].getName());
        Assert.assertEquals(COOKIE1_VALUE, cookies[0].getValue());
        Assert.assertEquals(COOKIE2_KEY, cookies[1].getName());
        Assert.assertEquals(COOKIE2_VALUE, cookies[1].getValue());
    }

    /**
     * The HttpClient sends each Cookie as separated header.
     */
    @Test
    public void cookiesMulitpleHeadersTest() throws Exception {
        HttpClient httpClient = new HttpClient();
        GetMethod request = new GetMethod(String.format("http://localhost:%d", LISTEN_PORT));
        HttpState state = new HttpState();
        state.addCookie(new org.apache.commons.httpclient.Cookie("localhost", COOKIE1_KEY, COOKIE1_VALUE, "/", 30, false));
        state.addCookie(new org.apache.commons.httpclient.Cookie("localhost", COOKIE2_KEY, COOKIE2_VALUE, "/", 30, false));
        httpClient.setState(state);
        httpClient.executeMethod(request);
    }

    public static class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            cookies = req.getCookies();
            waitForCookies.countDown();
        }
    }

}
