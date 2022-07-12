package science.atlarge.opencraft.mcproxy;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerLoginHandler;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoHandler;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.Server;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.server.ServerAdapter;
import com.github.steveice10.packetlib.event.server.SessionAddedEvent;
import com.github.steveice10.packetlib.event.server.SessionRemovedEvent;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;

import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

    public final boolean VERIFY_USERS = System.getenv().getOrDefault("VERIFY_USERS", "TRUE").equalsIgnoreCase("TRUE");

    private final String targetAddress;
    private final int targetPort;
    private final int proxyPort;
    private static final Proxy PROXY = Proxy.NO_PROXY;

    private final Map<Session, ClientSession> clientSessions = new ConcurrentHashMap<>();

    public ProxyServer(String targetAddress, int targetPort, int proxyPort) {
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;
        this.proxyPort = proxyPort;
    }

    public void start() {
        SessionService sessionService = new SessionService();

        Server server = new Server("0.0.0.0", proxyPort, MinecraftProtocol.class, new TcpSessionFactory(PROXY));

        if (VERIFY_USERS) {
            System.out.println("Proxy will authenticate users.");
        } else {
            System.out.println("WARNING! Proxy will NOT authenticate users.");
        }

        server.setGlobalFlag(MinecraftConstants.AUTH_PROXY_KEY, PROXY);
        server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, VERIFY_USERS);
        server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, (ServerInfoBuilder) session -> pingServer());

        server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, (ServerLoginHandler) clientProxySession -> {
            try {
                GameProfile profile = clientProxySession.getFlag(MinecraftConstants.PROFILE_KEY);
                TcpClientSession proxyServerSession = new TcpClientSession(targetAddress, targetPort, new MinecraftProtocol(profile.getName()), new Client("127.0.0.1", 25566, new MinecraftProtocol(SubProtocol.STATUS), new TcpSessionFactory(PROXY)), PROXY);
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
        final ServerStatusInfo[] serverInfo = {null};

        Session client = new TcpClientSession(targetAddress, targetPort, new MinecraftProtocol(SubProtocol.HANDSHAKE), new Client("127.0.0.1", 25566, new MinecraftProtocol(SubProtocol.STATUS), new TcpSessionFactory(PROXY)), PROXY);
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
