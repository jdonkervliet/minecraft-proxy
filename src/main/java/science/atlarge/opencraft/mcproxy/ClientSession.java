package science.atlarge.opencraft.mcproxy;

import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.login.client.EncryptionResponsePacket;
import com.github.steveice10.mc.protocol.packet.login.client.LoginPluginResponsePacket;
import com.github.steveice10.mc.protocol.packet.login.client.LoginStartPacket;
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginPluginRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSetCompressionPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectingEvent;
import com.github.steveice10.packetlib.event.session.PacketErrorEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.event.session.PacketSentEvent;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ClientSession {

    /**
     * All Messages which are not forwarded from the client to the server, and vice versa.
     */
    public static Set<Class<? extends Packet>> messageFilter = new HashSet<>(Arrays.asList(
            // Keep alive packets
            ServerKeepAlivePacket.class,
            ClientKeepAlivePacket.class,

            // Login packets
            LoginDisconnectPacket.class,
            EncryptionRequestPacket.class,
            LoginSuccessPacket.class,
            LoginSetCompressionPacket.class,
            LoginPluginRequestPacket.class,
            LoginStartPacket.class,
            EncryptionResponsePacket.class,
            LoginPluginResponsePacket.class
            //ServerDisconnectPacket.class
    ));

    private final Session clientProxySession;
    private final Session proxyServerSession;

    public ClientSession(Session session, Session proxyServerSession) {
        this.clientProxySession = session;
        this.proxyServerSession = proxyServerSession;
    }

    public void init() throws IOException {
        setupMessageForwarding(clientProxySession, proxyServerSession);
        setupMessageForwarding(proxyServerSession, clientProxySession);
    }

    private void setupMessageForwarding(Session source, Session destination) {
        source.addListener(new SessionListener() {
            @Override
            public void packetReceived(PacketReceivedEvent packetReceivedEvent) {
                Packet packet = packetReceivedEvent.getPacket();
                if (shouldForwardPacket(packet)) {
                    destination.send(packetReceivedEvent.getPacket());
                }
            }

            @Override
            public void packetSending(PacketSendingEvent packetSendingEvent) {

            }

            @Override
            public void packetSent(PacketSentEvent packetSentEvent) {

            }

            @Override
            public void packetError(PacketErrorEvent packetErrorEvent) {

            }

            @Override
            public void connected(ConnectedEvent connectedEvent) {

            }

            @Override
            public void disconnecting(DisconnectingEvent disconnectingEvent) {

            }

            @Override
            public void disconnected(DisconnectedEvent disconnectedEvent) {
                destination.disconnect(disconnectedEvent.getReason());
            }
        });
    }

    private boolean shouldForwardPacket(Packet packet) {
        MinecraftProtocol pro = (MinecraftProtocol) clientProxySession.getPacketProtocol();
        MinecraftProtocol pro2 = (MinecraftProtocol) proxyServerSession.getPacketProtocol();
        return (pro.getSubProtocol() == SubProtocol.GAME && pro2.getSubProtocol() == SubProtocol.GAME
                && !messageFilter.contains(packet.getClass()));
    }
}
