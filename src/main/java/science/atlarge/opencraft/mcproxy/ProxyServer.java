package science.atlarge.opencraft.mcproxy;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerLoginHandler;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoHandler;
import com.github.steveice10.packetlib.Server;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.server.ServerAdapter;
import com.github.steveice10.packetlib.event.server.SessionAddedEvent;
import com.github.steveice10.packetlib.event.server.SessionRemovedEvent;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpServer;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

    public final boolean VERIFY_USERS = true;

    private final String targetAddress;
    private final int targetPort;
    private final int proxyPort;

    private final Map<Session, ClientSession> clientSessions = new ConcurrentHashMap<>();

    public ProxyServer(String targetAddress, int targetPort, int proxyPort) {
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;
        this.proxyPort = proxyPort;
    }

    public void start() {
        SessionService sessionService = new SessionService();

        Server server = new TcpServer("0.0.0.0", proxyPort, MinecraftProtocol.class);
        server.setGlobalFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, VERIFY_USERS);
        server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, (ServerInfoBuilder) session -> pingServer());

        server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, (ServerLoginHandler) clientProxySession -> {
            try {
                GameProfile profile = clientProxySession.getFlag(MinecraftConstants.PROFILE_KEY);
                TcpClientSession proxyServerSession = new TcpClientSession(targetAddress, targetPort, new MinecraftProtocol(profile.getName()));
                proxyServerSession.connect();
                ClientSession clientSession = new ClientSession(clientProxySession, proxyServerSession);
                clientSession.init();
                clientSessions.put(clientProxySession, clientSession);
            } catch (Exception e) {
                clientProxySession.disconnect("Connection between Proxy and Minecraft Server failed", e);
                e.printStackTrace();
            }
        });

        server.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 100);
        server.addListener(new ServerAdapter() {
            @Override
            public void sessionAdded(SessionAddedEvent event) {
            }

            @Override
            public void sessionRemoved(SessionRemovedEvent event) {
                ClientSession session = clientSessions.remove(event.getSession());
            }
        });

        server.bind();
    }

    private ServerStatusInfo pingServer() {
        SessionService sessionService = new SessionService();
        sessionService.setProxy(Proxy.NO_PROXY);

        final ServerStatusInfo[] serverInfo = {null};
        MinecraftProtocol protocol = new MinecraftProtocol();
        Session client = new TcpClientSession(targetAddress, targetPort, protocol, null);
        client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        client.setFlag(MinecraftConstants.SERVER_INFO_HANDLER_KEY, (ServerInfoHandler) (session, info) -> serverInfo[0] = info);

        client.connect();
        while (serverInfo[0] == null) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        return serverInfo[0];
    }
}
