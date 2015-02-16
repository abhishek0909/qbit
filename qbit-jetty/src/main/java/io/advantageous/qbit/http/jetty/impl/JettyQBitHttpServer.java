package io.advantageous.qbit.http.jetty.impl;

import io.advantageous.qbit.GlobalConstants;
import io.advantageous.qbit.http.request.HttpRequest;
import io.advantageous.qbit.http.config.HttpServerOptions;
import io.advantageous.qbit.http.server.impl.SimpleHttpServer;
import io.advantageous.qbit.http.websocket.WebSocket;
import io.advantageous.qbit.system.QBitSystemManager;
import org.boon.core.reflection.BeanUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Consumer;

import static io.advantageous.qbit.servlet.QBitServletUtil.convertRequest;
import static org.boon.Boon.puts;

/**
 * Created by rhightower on 2/13/15.
 */
public class JettyQBitHttpServer extends SimpleHttpServer {

    private final Logger logger = LoggerFactory.getLogger(SimpleHttpServer.class);
    private final boolean debug = false || GlobalConstants.DEBUG || logger.isDebugEnabled();
    private final Server server;
    private final WebSocketPolicy policy = new WebSocketPolicy(WebSocketBehavior.SERVER);


    private final WebSocketServletFactory webSocketServletFactory;
    private final HttpServerOptions options;


    public JettyQBitHttpServer(HttpServerOptions options, QBitSystemManager systemManager) {

        super(systemManager, options.getFlushInterval());

        this.options = BeanUtils.copy(options);
        if (debug) {
            puts(options);
        }
        this.server = new Server();
        configureServer(server);
        webSocketServletFactory = webSocketServletFactory();


    }

    private void configureServer(Server server) {
        configureThreadPool(options);
        configureConnector(options);
        configureHandler();


    }

    private void configureHandler() {
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(final String target,
                               final Request baseRequest,
                               final HttpServletRequest request,
                               final HttpServletResponse response)
                    throws IOException, ServletException {

                if (webSocketServletFactory.isUpgradeRequest(request, response)) {
                    /* We have an upgrade request. */
                    if (webSocketServletFactory.acceptWebSocket(request, response)) {

                        baseRequest.setHandled(true);
                        /* websocket created */
                        return;
                    }
                    if (response.isCommitted()) {
                        return;
                    }
                } else {
                    baseRequest.setAsyncSupported(true);
                    handleRequestInternal(request);
                }
            }
        });
    }

    private void configureConnector(HttpServerOptions options) {
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(options.getPort());
        if (options.getHost()!=null) {
            connector.setHost(options.getHost());
        }
        server.addConnector(connector);
    }

    private void configureThreadPool(HttpServerOptions options) {
        final ThreadPool threadPool = this.server.getThreadPool();

        if (threadPool instanceof QueuedThreadPool) {
            if (options.getWorkers() > 4) {
                ((QueuedThreadPool) threadPool).setMaxThreads(options.getWorkers());
                ((QueuedThreadPool) threadPool).setMinThreads(4);
            }
        }
    }


    private WebSocketServletFactory webSocketServletFactory() {

        try {
            WebSocketServletFactory webSocketServletFactory = WebSocketServletFactory.Loader.create(policy);
            webSocketServletFactory.init();
            webSocketServletFactory.setCreator((request, response) -> new JettyNativeWebSocketHandler(request, JettyQBitHttpServer.this));
            return webSocketServletFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void handleRequestInternal(final HttpServletRequest request) {
        final HttpRequest httpRequest = convertRequest(request.startAsync());
        super.handleRequest(httpRequest);
    }

    @Override
    public void start() {
        super.start();
        try {
            server.start();
        } catch (Exception ex) {
            logger.error("Unable to start up Jetty", ex);
        }
    }


    public void stop() {
        super.stop();
        try {
            server.stop();
        } catch (Exception ex) {
            logger.error("Unable to shut down Jetty", ex);
        }
    }



}