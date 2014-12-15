/** 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.lfv.yada.net.client;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.input.SAXBuilder;

import com.lfv.lanzius.application.Controller;
import com.lfv.lanzius.application.TerminalProperties;
import com.lfv.yada.data.client.ClientBundle;
import com.lfv.yada.data.client.ClientChannel;
import com.lfv.yada.data.client.ClientTerminal;
import com.lfv.yada.net.NetworkConstants;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import com.lfv.yada.net.RequestHandler;
import com.lfv.yada.net.ResponseHandler;
import com.lfv.yada.net.SocketAddress;
import com.lfv.yada.net.TransactionManager;

public class ClientNetworkManager implements NetworkConstants {

    private Log                  log;
    private int                  terminalId;
    private int                  sessionId;
    private ClientBundle         bundle;
    private ClientPacketSender   sender;
    private ClientPacketReceiver receiver;
    private Timer                timer;
    private TimerTask            pingJob;
    private SocketAddress        localSocketAddr;
    private SocketAddress        serverSocketAddr;
    private TransactionManager   transactionManager;
    private ClientNetworkHandler networkHandler;
    private boolean              multicastAvailable;
    private boolean              initialized;
    private boolean              connected;
    private boolean              started;

    private Timer isaTimer;

    private TerminalProperties properties;

    public ClientNetworkManager(int terminalId,
                           ClientBundle bundle,
                           SocketAddress serverSocketAddr,
                           SocketAddress localhostBindSocketAddr,
                           SocketAddress multicastSocketAddr,
                           int multicastTTL,
                           TerminalProperties properties) throws IOException {

        // Create a logger for this class
        log = LogFactory.getLog(getClass());

        // Load terminal properties
        this.properties = properties;

        // Setup stuff
        this.terminalId = terminalId;
        this.bundle = bundle;
        this.serverSocketAddr = new SocketAddress(serverSocketAddr);

        // Resolve local host address
        InetAddress localHost = getLocalHostFix();
        if(localHost==null) {
            localHost = InetAddress.getLocalHost();
            if(localHost==null)
                throw new UnknownHostException("Could not resolve ip address of localhost");
        }

        // The socket to be used for sending and receiving unicast packets
        DatagramSocket usocket;
        if(localhostBindSocketAddr.getAddress()==0)
            usocket = new DatagramSocket();
        else
            usocket = new DatagramSocket(localhostBindSocketAddr.getPort(), localhostBindSocketAddr.getInetAddress());

        // The multicast socket
        InetAddress     maddr   = multicastSocketAddr.getInetAddress();
        int             mport   = multicastSocketAddr.getPort();
        MulticastSocket msocket = null;

        multicastAvailable = (maddr!=null&&mport>0);
        if(multicastAvailable) {
            msocket = new MulticastSocket(mport);
            try {
                msocket.joinGroup(maddr);
                msocket.setTimeToLive(multicastTTL);
            } catch(SocketException ex) {
                log.warn("!!!");
                log.warn("!!! Unable to create multicast socket! Multicasting has been disabled!");
                log.warn("!!! On linux systems a default gateway must be defined, try:");
                log.warn("!!! > route add default gw <some_ip_address>");
                log.warn("!!!");
                msocket = null;
                multicastAvailable = false;
            }
        }
        else
            log.warn("No multicast address or port defined, multicasting has been disabled!");

        // Store the local unicast ip address and port
        localSocketAddr = new SocketAddress(localHost, usocket.getLocalPort());

        // Create a receiver and a sender (send/recv must use the same port number)
        receiver = new ClientPacketReceiver(terminalId, usocket, msocket);
        sender   = new ClientPacketSender(usocket, msocket, multicastSocketAddr);

        // Create a transaction mananger
        transactionManager = new TransactionManager(sender);
        receiver.setControlPacketDispatcher(transactionManager);
        receiver.setSendPacketDispatcher(sender);

        // Create a timer for handling pings
        timer = new Timer("Snetworkmanager", true);

        // Initialize packet pool
        PacketPool.getPool();

        // Setup request handlers
        transactionManager.setRequestHandler(Packet.SESSION,  new SessionRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.UPDATE,   new UpdateRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.INFO,     new InfoRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.ISA,      new IsaRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.CONNECT,  new ConnectRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.INITIATE, new InitiateRequestPacketHandler());
    }

    //
    // COMMON
    //
    public void setNetworkHandler(ClientNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }
    public ClientPacketReceiver getReceiver() {
        return receiver;
    }
    public ClientPacketSender getSender() {
        return sender;
    }

    //
    // PING
    //
    public void serverConnect() {
        if(!initialized) {
            // Start up modules
            transactionManager.startModule();
            sender.startModule();
            receiver.startModule();
            log.debug("Network manager started");
            initialized = true;
        }
        if(connected) {
            log.error("Trying to connect when already connected to server!");
        }

        sendPingRequest(true,false);
    }
    public boolean serverDisconnect() {
        if(connected) {
            log.debug("Connected to server, sending ping stop request");
            sendPingRequest(false,true);
            return true;
        }
        else {
            log.debug("Not connected to server, stopping");
            shutdown();
            return false;
        }
    }
    private void sendPingRequest(boolean startFlag, boolean stopFlag) {
        log.debug("Ping: Sending request");

        // Set up a new resolve request packet
        Packet packet = PacketPool.getPool().borrowPacket(Packet.PING | Packet.REQUEST);

        // Set source and address
        packet.setSourceId(terminalId);
        packet.getSocketAddress().setAddress(serverSocketAddr);

        // Set flags and socket address in case of start
        // Both flags are not allowed to be set, but if they are
        // stop flag will have higher priority
        if(stopFlag)  {
            packet.setFlag(Packet.FLAG_STOP);
        }
        else if(startFlag) {
            packet.setFlag(Packet.FLAG_START);
            packet.addAttributeInt(Packet.ATTR_IPADDRESS, localSocketAddr.getAddress());
            packet.addAttributeInt(Packet.ATTR_IPPORT, localSocketAddr.getPort());
        }

        // Send the packet and capture the response with the internal handler
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new PingResponsePacketHandler());
    }
    private class PingResponsePacketHandler implements ResponseHandler {
        public void handleResponse(Packet requestPacket, Packet responsePacket) {
            if(requestPacket.getFlag(Packet.FLAG_START)) {

                // Call handler
                if(networkHandler!=null) {
                    networkHandler.serverConnected(true);
                    connected = true;
                }

                // Close already running ping job
                if(pingJob!=null) {
                    pingJob.cancel();
                }

                // Create a task that sends keep-alive pings to server
                pingJob = new TimerTask() {
                    public void run() {
                        sendPingRequest(false, false);
                    }
                };

                // Schedule the job
                timer.schedule(pingJob, PING_PERIOD, PING_PERIOD);
            }
            else if(requestPacket.getFlag(Packet.FLAG_STOP)) {
                shutdown();
            }
        }
        public void handleTimeout(Packet requestPacket) {
            log.debug("Ping: Server timeout occured!");
            timeout();
        }
    }

    //
    // SESSION
    //
    private class SessionRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();

            // Setup response packet and send response back to server
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.SESSION | Packet.RESPONSE);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            if(networkHandler!=null) {
                if(requestPacket.getFlag(Packet.FLAG_START))
                    networkHandler.sessionStart();
                else if(requestPacket.getFlag(Packet.FLAG_STOP))
                    networkHandler.sessionStop();
                else if(requestPacket.getFlag(Packet.FLAG_CLOSE)) {
                    connected = false;
                    networkHandler.sessionClose();
                }
                else
                    log.warn("Session: Unknown request!");
            }
        }
    }

    //
    // UPDATE
    //
    public void sessionConnect(Collection<Integer> channels) {
        if(log.isDebugEnabled()) {
            log.debug("Update: Connect session, joining channels "+Arrays.toString(channels.toArray()));
        }

        // New session
        sessionId++;
        started = true;

        // Setup packet
        Packet packet = PacketPool.getPool().borrowPacket(Packet.UPDATE | Packet.REQUEST);
        packet.setSourceId(terminalId);
        packet.setSessionId(sessionId);
        packet.getSocketAddress().setAddress(serverSocketAddr);

        // Add flag and channels to join
        packet.setFlag(Packet.FLAG_START);
        packet.addAttributeList(Packet.ATTR_CHANNEL_LIST, channels);

        // Send packet
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new UpdateResponsePacketHandler());
    }
    public void sessionDisconnect() {
        log.debug("Update: Disconnecting session, leaving all channels");

        // Setup packet
        Packet packet = PacketPool.getPool().borrowPacket(Packet.UPDATE | Packet.REQUEST);
        packet.setSourceId(terminalId);
        packet.setSessionId(sessionId);
        packet.getSocketAddress().setAddress(serverSocketAddr);
        packet.setFlag(Packet.FLAG_STOP);

        // Send packet
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new UpdateResponsePacketHandler());
    }
    private class UpdateResponsePacketHandler implements ResponseHandler {
        public void handleResponse(Packet requestPacket, Packet responsePacket) {
            if(!session(requestPacket)) return;

            if(networkHandler!=null) {
                if(requestPacket.getFlag(Packet.FLAG_START)) {
                    networkHandler.sessionConnected();
                }
                else if(requestPacket.getFlag(Packet.FLAG_STOP)) {
                    networkHandler.sessionDisconnected();
                    started = false;
                }
            }
        }
        public void handleTimeout(Packet requestPacket) {
            if(!session(requestPacket)) return;

            log.error("Update: Server timeout occured!");
            timeout();
        }
    }
    private class UpdateRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();

            // Setup and send response back to server directly
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.UPDATE | Packet.RESPONSE);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            // Check session
            if(!session(requestPacket)) return;

            // Look for source terminal in bundle
            int terminalId = requestPacket.getSourceId();
            ClientTerminal terminal = bundle.getTerminal(terminalId);

            // Joining
            if(requestPacket.getFlag(Packet.FLAG_START)) {

                // Create a new terminal
                if(terminal!=null)
                    log.warn("Terminal "+terminalId+" already exists in a channel!");
                else
                    terminal = new ClientTerminal(terminalId);

                // Store the address for the terminal
                int ip   = requestPacket.getAttributeInt(Packet.ATTR_IPADDRESS);
                int port = requestPacket.getAttributeInt(Packet.ATTR_IPPORT);
                terminal.setSocketAddress(ip, port);

                // Get the channel list
                int[] channels = requestPacket.getAttributeList(Packet.ATTR_CHANNEL_LIST);

                if(log.isDebugEnabled()) {
                    log.debug("Update: Terminal "+terminalId+" @ "+ terminal.getSocketAddress());
                    log.debug("        Channels: "+Arrays.toString(channels));
                }

                // Add terminal to each channel
                for(int channelId : channels) {
                    ClientChannel c = bundle.getChannel(channelId);
                    if(c!=null) {
                        c.addTerminal(terminal);
                        if(log.isDebugEnabled())
                            log.debug("Update: Adding "+terminal.getId()+" to channel "+c.getId());
                    }
                }

                // Now we have information about the terminal, can we connect
                // to it? Send connect request to find connection paths!
                sendConnectRequests(terminal);
            }
            else if(requestPacket.getFlag(Packet.FLAG_STOP)) {
                if(terminal!=null) {
                    // Remove terminal from each channel
                    Collection<ClientChannel> channels = bundle.getChannelCollection();
                    Iterator<ClientChannel> iter = channels.iterator();
                    while(iter.hasNext()) {
                        ClientChannel c = iter.next();
                        if(log.isDebugEnabled())
                            log.debug("Update: Removing "+terminal.getId()+" from channel "+c.getId());
                        c.removeTerminal(terminal);
                    }
                }
            }
        }
    }

    //
    // INFO
    //
    private class InfoRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();

            // Setup and send response back to server directly
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.INFO | Packet.RESPONSE);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            if(networkHandler!=null) {
                networkHandler.infoPacket(requestPacket);
            }
        }
    }

    //
    // ISA
    //
    private class IsaRequestPacketHandler implements RequestHandler {
        int ISA_TIMEOUT = 30000;
        boolean executing;
        public void handleRequest(Packet requestPacket) {
                if (isaTimer != null) {
                        isaTimer.cancel();
                        isaTimer = null;
                        log.debug("ISA thread stopped");
                } else {
                        int isaPeriod = requestPacket.getAttributeInt(Packet.ATTR_ISA_PERIOD)*1000;
                        final int isaNumChoices = requestPacket.getAttributeInt(Packet.ATTR_ISA_NUM_CHOICES);
                        final boolean isaExtendedMode = requestPacket.getAttributeBool(Packet.ATTR_ISA_EXTMODE);
                        final String[] isakeytext = { "1", "2", "3", "4", "5", "6", "7", "8", "9" };

                        isakeytext[0] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT0);


                        isakeytext[1] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT1);
                        isakeytext[2] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT2);
                        isakeytext[3] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT3);
                        isakeytext[4] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT4);
                        isakeytext[5] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT5);
                        isakeytext[6] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT6);
                        isakeytext[7] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT7);
                        isakeytext[8] = requestPacket.getAttributeString(Packet.ATTR_ISA_KEYTEXT8);

                        log.debug("Start ISA thread, period = " + isaPeriod + " isaNumChoices=" + isaNumChoices );
                        for (int i = 0; i < isaNumChoices; i++){
                           log.debug("ISA keytext " + i + ": " + isakeytext[i]);
                        }
                        isaTimer = new Timer();
                        isaTimer.schedule(new TimerTask() {
                        public void run() {
                                if (!executing) {
                                        try {
                                                executing = true;
                                        while (!Controller.getInstance().isaDialogOpen(isaNumChoices, isaExtendedMode, isakeytext)) {
                                                Thread.sleep(200);
                                        }
                                        Thread.sleep(ISA_TIMEOUT);
                                        Controller.getInstance().isaValueChosen(0); // Value is sent only if ISA dialog is still open
                                                } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                } finally {
                                                        executing = false;
                                                }
                                } else {
                                        log.debug("Last ISA request not finnished, skip this one");
                                }
                        }
                    }, isaPeriod, isaPeriod);
                }
        }
    }

    public void sendIsaResponse(int value, long responseTime) {
        String url = "http://"+properties.getServerAddress()+":"+properties.getServerHttpPort()+"/xml/isa?terminal="+terminalId+"&val="+value+"&t="+responseTime;
        log.debug("Send ISA value to: "+ url);
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.build(new URL(url));
        } catch (Exception ex) {
            log.error("Failed to get or parse the configuration document from "+url.toString(), ex);
            ex.printStackTrace();
            return;
        }
    }

    //
    // CONNECT
    //
    private void sendConnectRequests(ClientTerminal destTerminal) {

        int destTerminalId = destTerminal.getId();
        log.debug("Connect: Sending requests to terminal "+destTerminalId+" @ "+destTerminal.getSocketAddress());

        // Send a connection packet for unicast connection
        Packet unicastPacket = PacketPool.getPool().borrowPacket(Packet.CONNECT | Packet.REQUEST);
        unicastPacket.setSourceId(terminalId);
        unicastPacket.setDestId(destTerminalId);
        unicastPacket.getSocketAddress().setAddress(destTerminal.getSocketAddress());
        transactionManager.sendRequest(unicastPacket,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new ConnectResponsePacketHandler());

        // Send a connection packet for multicasting
        if(multicastAvailable) {
            Packet multicastPacket = PacketPool.getPool().borrowPacket(Packet.CONNECT | Packet.REQUEST);
            multicastPacket.setSourceId(terminalId);
            multicastPacket.setDestId(destTerminalId);
            multicastPacket.setFlag(Packet.FLAG_MULTICAST);
            transactionManager.sendRequest(multicastPacket,
                    TRANSACTION_TIMEOUT,
                    TRANSACTION_RETRIES,
                    new ConnectResponsePacketHandler());
        }
    }
    private class ConnectResponsePacketHandler implements ResponseHandler {
        public void handleResponse(Packet requestPacket, Packet responsePacket) {
            ClientTerminal terminal = bundle.getTerminal(responsePacket.getSourceId());
            if(terminal!=null) {
                if(requestPacket.getFlag(Packet.FLAG_MULTICAST)) {
                    log.debug("Connect: Multicast connection established with terminal "+terminal.getId());
                    terminal.setMulticastConnection(true);
                }
                else {
                    log.debug("Connect: Unicast connection established with terminal "+terminal.getId());
                    terminal.setUnicastConnection(true);
                }
            }
        }
        public void handleTimeout(Packet requestPacket) {
        }
    }
    private class ConnectRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            int sourceTerminalId = requestPacket.getSourceId();
            if(log.isDebugEnabled()) {
                log.debug("Connect: Request from "+sourceTerminalId+", multicast="+requestPacket.getFlag(Packet.FLAG_MULTICAST));
            }

            // Send a mirrored request as response, always relayed through the server
            Packet packet = PacketPool.getPool().borrowPacket(Packet.CONNECT | Packet.RESPONSE);
            packet.setSourceId(terminalId);
            packet.setDestId(sourceTerminalId);
            packet.getSocketAddress().setAddress(serverSocketAddr);
            transactionManager.sendResponse(packet, requestPacket.getTransactionId());
        }
    }

    //
    // INITIATE
    //
    public void radioAcquire(int channelId) {
       sendInitiateRequestRadio(channelId, Packet.ATTR_RADIO_ACQUIRE);
    }
    public void radioRelease(int channelId) {
       sendInitiateRequestRadio(channelId, Packet.ATTR_RADIO_RELEASE);
    }
    public void phoneRing(int sourceRoleId, int destRoleId) {
        sendInitiateRequestPhone(destRoleId, sourceRoleId, Packet.ATTR_PHONE_RING);
    }
    public void phoneAnswer(int sourceRoleId, int destRoleId) {
        sendInitiateRequestPhone(destRoleId, sourceRoleId, Packet.ATTR_PHONE_ANSWER);
    }
    public void phoneHangup(int sourceRoleId, int destRoleId) {
        sendInitiateRequestPhone(destRoleId, sourceRoleId, Packet.ATTR_PHONE_HANGUP);
    }
    private void sendInitiateRequestRadio(int channelId, int initiateAttribute) {
        Packet packet = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.REQUEST);

        // Set up packet
        packet.setSourceId(terminalId);
        packet.setSessionId(sessionId);
        packet.getSocketAddress().setAddress(serverSocketAddr);
        packet.addAttributeInt(Packet.ATTR_CHANNEL, channelId);
        packet.addAttributeBool(initiateAttribute);

        // Send packet
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new InitiateResponsePacketHandler());
    }
    private void sendInitiateRequestPhone(int destRoleId, int sourceRoleId, int initiateAttribute) {
        Packet packet = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.REQUEST);

        // Set up packet
        packet.setSourceId(terminalId);
        packet.setDestId(0);
        packet.setSessionId(sessionId);
        packet.getSocketAddress().setAddress(serverSocketAddr);
        packet.addAttributeBool(initiateAttribute);
        packet.addAttributeInt(Packet.ATTR_SOURCE_ROLE, sourceRoleId);
        packet.addAttributeInt(Packet.ATTR_DEST_ROLE, destRoleId);

        // Send packet
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new InitiateResponsePacketHandler());
    }
    private class InitiateResponsePacketHandler implements ResponseHandler {
        public void handleResponse(Packet requestPacket, Packet responsePacket) {
            if(!session(requestPacket)) return;

            boolean radioAcquireAccept  = responsePacket.getAttributeBool(Packet.ATTR_RADIO_ACQUIRE_ACCEPT);
            boolean radioAcquireBusy    = responsePacket.getAttributeBool(Packet.ATTR_RADIO_ACQUIRE_BUSY);
            boolean radioReleaseAccept = responsePacket.getAttributeBool(Packet.ATTR_RADIO_RELEASE_ACCEPT);
            boolean radio = radioAcquireAccept||radioAcquireBusy||radioReleaseAccept;

            if(radio) {
                // Get the channel id
                int channelId = responsePacket.getAttributeInt(Packet.ATTR_CHANNEL);

                // Find the channel
                ClientChannel channel = bundle.getChannel(channelId);

                if(radioAcquireAccept) {
                    networkHandler.radioAcquired(channel, true);
                }
                else if(radioAcquireBusy) {
                    networkHandler.radioAcquired(channel, false);
                }
                else if(radioReleaseAccept) {
                    networkHandler.radioReleased(channel);
                }
            }
            else {
                // Get the dest terminal id
                int destTerminalId = responsePacket.getSourceId();
                int destRoleId     = requestPacket.getAttributeInt(Packet.ATTR_DEST_ROLE);
                int sourceRoleId   = requestPacket.getAttributeInt(Packet.ATTR_SOURCE_ROLE);

                // Find other terminal
                ClientTerminal destTerminal = bundle.getTerminal(destTerminalId);

                // If the ring was accepted, it is now ringing in the destination phone
                if(responsePacket.getAttributeBool(Packet.ATTR_PHONE_RING_ACCEPT)) {
                    networkHandler.phoneRingOutgoing(destTerminal, destRoleId, sourceRoleId, true);
                }

                // The other terminal might also be busy when ringing
                else if(responsePacket.getAttributeBool(Packet.ATTR_PHONE_RING_BUSY)) {
                    networkHandler.phoneRingOutgoing(destTerminal, destRoleId, sourceRoleId, false);
                }

                // The phone is answered and the call was accepted
                else if(responsePacket.getAttributeBool(Packet.ATTR_PHONE_ANSWER_ACCEPT)) {
                    networkHandler.phoneAnswerOutgoing(destTerminal, destRoleId, sourceRoleId, true);
                }

                // The phone is answered but no one was there
                else if(responsePacket.getAttributeBool(Packet.ATTR_PHONE_ANSWER_REJECT)) {
                    networkHandler.phoneAnswerOutgoing(destTerminal, destRoleId, sourceRoleId, false);
                }

                // The phone is hung up
                else if(responsePacket.getAttributeBool(Packet.ATTR_PHONE_HANGUP_ACCEPT)) {
                    networkHandler.phoneHangupOutgoing(destTerminal, destRoleId, sourceRoleId);
                }
            }
        }
        public void handleTimeout(Packet requestPacket) {
            if(!session(requestPacket)) return;

            log.error("Initiate: Server timeout occured!");
            timeout();
        }
    }
    private class InitiateRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();

            // Setup response packet and send response back to server
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.RESPONSE);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            // Check session
            if(!session(requestPacket)) return;

            int sourceTerminalId = requestPacket.getSourceId();
            int sourceRoleId     = requestPacket.getAttributeInt(Packet.ATTR_SOURCE_ROLE);
            int destRoleId       = requestPacket.getAttributeInt(Packet.ATTR_DEST_ROLE);

            // Call handler to application, sourceTerminal may be null
            ClientTerminal sourceTerminal = bundle.getTerminal(sourceTerminalId);
            if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_RING))
                networkHandler.phoneRingIncoming(sourceTerminal, sourceRoleId, destRoleId);
            else if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_ANSWER))
                networkHandler.phoneAnswerIncoming(sourceTerminal, sourceRoleId, destRoleId);
            else if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_HANGUP))
                networkHandler.phoneHangupIncoming(sourceTerminal, sourceRoleId, destRoleId);
        }
    }

    //
    // HELPERS
    //
    private void timeout() {

        if(pingJob!=null) {
            pingJob.cancel();
            pingJob = null;
        }

        started = false;
        if(connected) {
            connected = false;
            initialized = false;
            if(networkHandler!=null) {
                networkHandler.timeout();
            }
        }
        else {
            if(networkHandler!=null) {
                networkHandler.serverConnected(false);
            }
        }
    }
    private void shutdown() {
        log.debug("Shutting down network manager");
        if(pingJob!=null) {
            pingJob.cancel();
            pingJob = null;
        }
        if (isaTimer != null) {
                isaTimer.cancel();
        }
        timer.cancel();
        sender.stopModule();
        receiver.stopModule();
        transactionManager.stopModule();
        PacketPool.getPool().printInfoMessage();
        networkHandler.serverDisconnected();
        started = false;
        connected = false;
        initialized = false;
    }
    private boolean session(Packet packet) {
        boolean b = started && packet.getSessionId()==sessionId;
        if(!b) log.warn("Packet from another session encountered, type: 0x" + Integer.toHexString(packet.getType()));
        return b;
    }

    private InetAddress getLocalHostFix() throws SocketException {
        // Hack for getting the localhost inetaddress on linux systems
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements()) {
            NetworkInterface networkInterface = (NetworkInterface)e.nextElement();
            Enumeration e2 = networkInterface.getInetAddresses();
            while(e2.hasMoreElements()) {
                InetAddress ip = (InetAddress)e2.nextElement();
                if(!ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":")==-1) {
                    return ip;
                }
            }
        }
        return null;
    }
}
