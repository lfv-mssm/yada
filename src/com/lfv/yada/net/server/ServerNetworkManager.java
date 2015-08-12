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
package com.lfv.yada.net.server;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.lfv.yada.data.server.ServerBundle;
import com.lfv.yada.data.server.ServerChannel;
import com.lfv.yada.data.server.ServerTerminal;
import com.lfv.yada.net.NetworkConstants;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import com.lfv.yada.net.RequestHandler;
import com.lfv.yada.net.ResponseHandler;
import com.lfv.yada.net.SocketAddress;
import com.lfv.yada.net.TransactionManager;

public class ServerNetworkManager implements NetworkConstants {

    private Log                  log;
    private ServerBundle         bundle;
    private ServerTranslator     translator;
    private ServerPacketSender   sender;
    private ServerPacketReceiver receiver;
    private Timer                timer;
    private TransactionManager   transactionManager;
    private ServerNetworkHandler networkHandler;
    private boolean              started;
    private boolean                              possibleConnectionProblem;

    public ServerNetworkManager(int port, ServerBundle bundle, ServerTranslator translator) throws SocketException {
        // Create a logger for this class
        log = LogFactory.getLog(getClass());
        log.debug("Creating server at port " + port);

        this.bundle     = bundle;
        this.translator = translator;

        // The the socket to be used for sending and receiving packets
        DatagramSocket socket = new DatagramSocket(port);

        // Create a receiver and a sender
        receiver = new ServerPacketReceiver(socket, bundle);
        sender   = new ServerPacketSender(socket);

        // Create a transaction mananger
        transactionManager = new TransactionManager(sender);
        receiver.setControlPacketDispatcher(transactionManager);
        receiver.setSendPacketDispatcher(sender);

        // Create a timer for handling terminal timeout
        timer = new Timer("Snetworkmanager", true);

        // Initialize packet pool
        PacketPool.getPool();

        // Setup request handlers
        transactionManager.setRequestHandler(Packet.PING,     new PingRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.UPDATE,   new UpdateRequestPacketHandler());
        transactionManager.setRequestHandler(Packet.INITIATE, new InitiateRequestPacketHandler());
    }

    public void start() {

        // Start up modules
        transactionManager.startModule();
        sender.startModule();
        receiver.startModule();

        // Add terminal timeout task
        timer.schedule(new TimerTask() {
            public void run() {
                long currentTime = System.currentTimeMillis();
                Collection<ServerTerminal> terminals = bundle.getTerminalCollection();
                Iterator<ServerTerminal> iter = terminals.iterator();
                while(iter.hasNext()) {
                    ServerTerminal terminal = iter.next();
                    long pingTime = terminal.getPingTime();
                    // If terminal is logged in..
                    if(pingTime>0) {
                        // If terminal has timed out..
                        if((currentTime-pingTime)>NOPING_TIMEOUT) {
                            // Reset terminal
                            log.warn("Terminal "+terminal.getId()+" has been idle for too long, going offline!");
                            resetTerminal(terminal, true);
                            possibleConnectionProblem = checkAllTerminals();
                        }
                    }
                }
            }
        },TIMEOUT_CHECK_PERIOD,TIMEOUT_CHECK_PERIOD);

        started = true;
        possibleConnectionProblem = false;
    }

    public void stop() {
        timer.cancel();
        transactionManager.stopModule();
        sender.stopModule();
        receiver.stopModule();
        PacketPool.getPool().printInfoMessage();

        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    public void setNetworkHandler(ServerNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public boolean connectionProblem() {
        return possibleConnectionProblem;
    }

    private boolean checkAllTerminals() {
        ServerTerminal terminal;
        Iterator<ServerTerminal> iter = bundle.getTerminalCollection().iterator();
        boolean lostAllTerminals = true;
        while (iter.hasNext()) {
                terminal = iter.next();
                if (terminal.isOnline()) {
                        lostAllTerminals = false;
                }
        }
        return lostAllTerminals;
    }

    //
    // PING
    //
    private class PingRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();
            int terminalId = requestPacket.getSourceId();

            // Setup and send response packet
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.PING | Packet.RESPONSE);
            responsePacket.setDestId(terminalId);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            if(log.isDebugEnabled()) {
                log.debug("Ping: Terminal "+terminalId+" @ "+ socketAddress);
            }

            ServerTerminal terminal = bundle.getTerminal(terminalId);

            if(terminal!=null) {

                // Reset terminal if stopping
                if(requestPacket.getFlag(Packet.FLAG_STOP)) {
                    resetTerminal(terminal, true);
                }
                else {

                    // Reset terminal if starting, warn if already online
                    if(requestPacket.getFlag(Packet.FLAG_START)) {
                        if(terminal.isOnline()) {
                            log.warn("!!!");
                            log.warn("!!! Terminal " + terminal.getId() + " is already active when requesting to login!");
                            log.warn("!!! Two terminals are not allowed to have the same id,");
                            log.warn("!!! make sure this is not the case!");
                            log.warn("!!!");
                            resetTerminal(terminal, true);
                        }
                        else {
                            resetTerminal(terminal, false);
                        }

                        // Store ip address and port from packet
                        int ip   = requestPacket.getAttributeInt(Packet.ATTR_IPADDRESS);
                        int port = requestPacket.getAttributeInt(Packet.ATTR_IPPORT);
                        terminal.setSocketAddress(ip, port);

                        // Call handler
                        if(networkHandler!=null) {
                            networkHandler.terminalOnline(terminal);
                        }
                    }

                    // Update ping time
                    terminal.setPingTime(System.currentTimeMillis());
                }
            }
            else {
                log.error("Ping: Terminal "+terminalId+" does not exist!");
            }
        }
    }

    //
    // SESSION
    //
    private void sendSessionRequest(int terminalId, int flag) {
        ServerTerminal terminal = bundle.getTerminal(terminalId);

        if(terminal!=null) {
            SocketAddress socketAddr = terminal.getSocketAddress();
            if(socketAddr!=null) {
                if(log.isDebugEnabled())
                    log.debug("Session: Sending request to "+terminalId+" @ "+ socketAddr);

                // Set up a new change request packet
                Packet packet = PacketPool.getPool().borrowPacket(Packet.SESSION | Packet.REQUEST);
                // Set destination address
                packet.setDestId(terminalId);
                packet.getSocketAddress().setAddress(socketAddr);
                // Add session flag
                packet.setFlag(flag);

                // Send packet
                transactionManager.sendRequest(packet,
                        TRANSACTION_TIMEOUT,
                        TRANSACTION_RETRIES,
                        new EmptyResponsePacketHandler());
            }
            else {
                log.warn("Unable to send session request, terminal "+terminal.getId()+" has no socket address!");
            }
        }
        else {
            log.error("Session: Terminal "+terminalId+" does not exist!");
        }
    }
    public void sendSessionRequestStart(int terminalId) {
        sendSessionRequest(terminalId, Packet.FLAG_START);
    }
    public void sendSessionRequestStop(int terminalId) {
        sendSessionRequest(terminalId, Packet.FLAG_STOP);
    }
    public void sendSessionRequestClose(int terminalId) {
        sendSessionRequest(terminalId, Packet.FLAG_CLOSE);
    }
    public void sendSessionRequestPause(int terminalId) {
        sendSessionRequest(terminalId, Packet.FLAG_PAUSE);
    }    

    public void sendIsaStartRequest(int terminalId, int period, int isaNumChoices, boolean isaExtendedMode, String isakeytext[]) {
        ServerTerminal terminal = bundle.getTerminal(terminalId);

        if(terminal!=null) {
            SocketAddress socketAddr = terminal.getSocketAddress();
            if(socketAddr!=null) {
                if(log.isDebugEnabled())
                    log.debug("Session: Sending request to "+terminalId);

                // Set up a new change request packet
                Packet packet = PacketPool.getPool().borrowPacket(Packet.ISA | Packet.REQUEST);
                // Set destination address
                packet.setDestId(terminalId);
                packet.addAttributeInt(Packet.ATTR_ISA_PERIOD, period);
                packet.addAttributeInt(Packet.ATTR_ISA_NUM_CHOICES, isaNumChoices);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT0, isakeytext[0]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT1, isakeytext[1]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT2, isakeytext[2]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT3, isakeytext[3]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT4, isakeytext[4]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT5, isakeytext[5]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT6, isakeytext[6]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT7, isakeytext[7]);
                packet.addAttributeString(Packet.ATTR_ISA_KEYTEXT8, isakeytext[8]);
                if (isaExtendedMode) {
                        packet.addAttributeBool(Packet.ATTR_ISA_EXTMODE);
                }
                packet.getSocketAddress().setAddress(socketAddr);

                // Send packet
                log.debug("Sending ISA request with ID: " + packet.getTransactionId());
                transactionManager.sendRequest(packet,
                        TRANSACTION_TIMEOUT,
                        TRANSACTION_RETRIES,
                        new EmptyResponsePacketHandler());
            }
            else {
                log.warn("Unable to send ISA request, terminal "+terminal.getId()+" has no socket address!");
            }
        }
        else {
            log.error("Session: Terminal "+terminalId+" does not exist!");
        }
    }

    //
    // UPDATE
    //
    private void sendUpdateRequest(ServerTerminal destTerminal, ServerTerminal sourceTerminal, boolean start) {

        boolean valid =   (destTerminal != null) && (sourceTerminal != null) && // not null
                          (destTerminal!=sourceTerminal) &&                     // not same
                          (destTerminal.isStarted()) &&                         // dest started
                          (destTerminal.isConnected());
        if(start) valid =  valid &&
                          (sourceTerminal.isStarted()) &&                       // source started
                          (sourceTerminal.isConnected());

        if(valid) {
            // Create packet
            Packet packet = PacketPool.getPool().borrowPacket(Packet.UPDATE | Packet.REQUEST);

            // Set address
            packet.setSourceId(sourceTerminal.getId());
            packet.setDestId(destTerminal.getId());
            packet.setSessionId(destTerminal.getSessionId());
            packet.getSocketAddress().setAddress(destTerminal.getSocketAddress());

            // Set flag and attributes
            if(start) {
                packet.setFlag(Packet.FLAG_START);
                SocketAddress sourceSocketAddress = sourceTerminal.getSocketAddress();
                packet.addAttributeInt(Packet.ATTR_IPADDRESS, sourceSocketAddress.getAddress());
                packet.addAttributeInt(Packet.ATTR_IPPORT, sourceSocketAddress.getPort());
                packet.addAttributeList(Packet.ATTR_CHANNEL_LIST, sourceTerminal.getChannels());
            }
            else {
                packet.setFlag(Packet.FLAG_STOP);
            }

            // Send packet
            transactionManager.sendRequest(packet,
                    TRANSACTION_TIMEOUT,
                    TRANSACTION_RETRIES,
                    new EmptyResponsePacketHandler());
        }
    }
    private class UpdateRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddress = requestPacket.getSocketAddress();
            int terminalId = requestPacket.getSourceId();

            // Setup and send response packet
            Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.UPDATE | Packet.RESPONSE);
            responsePacket.setDestId(terminalId);
            responsePacket.getSocketAddress().setAddress(socketAddress);
            transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());

            ServerTerminal terminal = bundle.getTerminal(terminalId);
            if(terminal!=null) {

                if(requestPacket.getFlag(Packet.FLAG_START)) {

                    // Set terminal started
                    terminal.setStarted(true);

                    // Set session id
                    terminal.setSessionId(requestPacket.getSessionId());

                    // Store the channel list
                    terminal.setChannels(requestPacket.getAttributeList(Packet.ATTR_CHANNEL_LIST));

                    // Send update to all terminals
                    Collection<ServerTerminal> terminals = bundle.getTerminalCollection();
                    Iterator<ServerTerminal> iter = terminals.iterator();
                    while(iter.hasNext()) {
                        ServerTerminal t = iter.next();
                        // All terminals must receive update requests from the joining terminal...
                        sendUpdateRequest(t, terminal, true);
                        // ...and the joining terminal must receive update requests from all terminals
                        sendUpdateRequest(terminal, t, true);
                    }

                    // Call handler
                    if(networkHandler!=null) {
                        networkHandler.terminalStarted(terminal);
                    }
                }

                else if(requestPacket.getFlag(Packet.FLAG_STOP)) {

                    // Set terminal stopped
                    terminal.setStarted(false);

                    // Remove channels
                    terminal.setChannels(null);

                    // Reset phone, radio and send updates
                    resetTerminal(terminal, false);

                    // Call handler
                    if(networkHandler!=null) {
                        networkHandler.terminalStopped(terminal);
                    }
                }
                else {
                    log.error("Update: Invalid packet, missing start/stop flags!");
                }
            }
            else {
                log.error("Update: Terminal "+terminalId+" does not exist!");
            }
        }
    }

    //
    // INFO
    //
    public Packet prepareInfoPacket(ServerTerminal destTerminal, ServerTerminal sourceTerminal) {
        SocketAddress socketAddr = destTerminal.getSocketAddress();
        if(socketAddr!=null) {
            // Set up a new update request packet
            Packet packet = PacketPool.getPool().borrowPacket(Packet.INFO | Packet.REQUEST);
            packet.setSourceId(sourceTerminal.getId());
            packet.setDestId(destTerminal.getId());
            packet.getSocketAddress().setAddress(destTerminal.getSocketAddress());

            return packet;
        }

        return null;
    }
    public void sendInfoPacket(Packet packet) {
        transactionManager.sendRequest(packet,
                TRANSACTION_TIMEOUT,
                TRANSACTION_RETRIES,
                new EmptyResponsePacketHandler());
    }

    //
    // INITIATE
    //
    private class InitiateRequestPacketHandler implements RequestHandler {
        public void handleRequest(Packet requestPacket) {
            SocketAddress socketAddr = requestPacket.getSocketAddress();

            // Get source id's
            int sourceTerminalId = requestPacket.getSourceId();
            int sourceRoleId = requestPacket.getAttributeInt(Packet.ATTR_SOURCE_ROLE);

            if(log.isDebugEnabled())
                log.debug("Initiate: Terminal "+sourceTerminalId+", Role "+sourceRoleId);

            // Get source terminal from the bundle
            ServerTerminal sourceTerminal = bundle.getTerminal(sourceTerminalId);

            if(sourceTerminal==null) {
                log.error("Initiate: Source terminal "+sourceTerminalId+" was not found!");
                return;
            }

            // Type of initiation
            boolean radioAcquire  = requestPacket.getAttributeBool(Packet.ATTR_RADIO_ACQUIRE);
            boolean radioRelease = requestPacket.getAttributeBool(Packet.ATTR_RADIO_RELEASE);
            boolean radio = radioAcquire||radioRelease;

            // RADIO
            if(radio) {
                // Get channel id
                int channelId = requestPacket.getAttributeInt(Packet.ATTR_CHANNEL);

                // Get the channel from the bundle
                ServerChannel channel = bundle.getChannel(channelId);

                // Setup response packet
                Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.RESPONSE);
                responsePacket.setDestId(sourceTerminalId);
                responsePacket.getSocketAddress().setAddress(socketAddr);
                responsePacket.addAttributeInt(Packet.ATTR_CHANNEL, channelId);

                // For knowing if the packet should be sent or recycled
                boolean sendResponse = false;

                // ACQUIRE
                if(radioAcquire) {
                    if(!channel.isAcquired()) {
                        // The channel is free! Allocate it!
                        log.debug("Initiate: ACQUIRE, ok! From "+sourceTerminalId +" on channel "+channelId);
                        channel.acquire(sourceTerminalId);
                        responsePacket.addAttributeBool(Packet.ATTR_RADIO_ACQUIRE_ACCEPT);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.radioAcquired(sourceTerminal, channel, true);
                        }
                    }
                    else {
                        // Some one else is already using the channel
                        log.debug("Initiate: ACQUIRE, channel was busy! From "+sourceTerminalId +" on channel "+channelId);
                        responsePacket.addAttributeBool(Packet.ATTR_RADIO_ACQUIRE_BUSY);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.radioAcquired(sourceTerminal, channel, false);
                        }
                    }
                }
                else if(radioRelease) {
                    if(channel.getAcquiredTerminalId()==sourceTerminalId) {
                        // Ok, terminal is sending on that channel, release it
                        log.debug("Initiate: RELEASE, ok! From "+sourceTerminalId +" on channel "+channelId);
                        channel.release();
                        responsePacket.addAttributeBool(Packet.ATTR_RADIO_RELEASE_ACCEPT);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.radioReleased(sourceTerminal, channel);
                        }
                    }
                    else {
                        // Some terminal is trying to release but has not allocated the channel
                        log.warn("Initiate: RELEASE, releasing terminal has not allocated the channel! From "+sourceTerminalId +" on channel "+channelId);
                        channel.release();
                        responsePacket.addAttributeBool(Packet.ATTR_RADIO_RELEASE_ACCEPT);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.radioReleased(sourceTerminal, channel);
                        }
                    }
                }

                // Send request and response or return the packets
                if(sendResponse)
                    transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());
                else
                    PacketPool.getPool().returnPacket(responsePacket);
            }

            // PHONE
            else {
                // Get destination id's
                int destTerminalId = requestPacket.getDestId();
                int destRoleId = requestPacket.getAttributeInt(Packet.ATTR_DEST_ROLE);

                // Translate if necessary
                if(destTerminalId==0&&translator!=null) {
                    destTerminalId = translator.translateRoleIdToTerminalId(destRoleId);
                    if(log.isDebugEnabled())
                        log.debug("Initiate: Translating destination from "+destRoleId+" to "+destTerminalId);
                }

                // Get destination terminal from the bundle
                ServerTerminal destTerminal = bundle.getTerminal(destTerminalId);
                if(destTerminal==null)
                    log.warn("Initiate: Destination phone terminal "+destTerminalId+" was not found!");

                // For knowing if the packets should be sent or recycled
                boolean sendRequest = false;
                boolean sendResponse = false;

                // Setup response packet
                Packet responsePacket = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.RESPONSE);
                responsePacket.setSourceId(destTerminalId);
                responsePacket.setDestId(sourceTerminalId);
                responsePacket.getSocketAddress().setAddress(socketAddr);

                // Is the destination terminal valid?
                boolean valid =
                        sourceTerminal!=null && destTerminal!=null &&
                        sourceTerminalId>0 && destTerminalId>0 &&
                        sourceRoleId>0 && destRoleId>0 &&
                        destRoleId != sourceRoleId && destTerminalId != sourceTerminalId &&
                        destTerminal.isStarted() && destTerminal.isConnected();

                // Setup outgoing request packet
                Packet requestPacketOut = null;
                if(valid) {
                    requestPacketOut = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.REQUEST);
                    requestPacketOut.setSourceId(sourceTerminalId);
                    requestPacketOut.setDestId(destTerminalId);
                    requestPacketOut.setSessionId(destTerminal.getSessionId());
                    requestPacketOut.getSocketAddress().setAddress(destTerminal.getSocketAddress());
                    requestPacketOut.addAttributeInt(Packet.ATTR_SOURCE_ROLE, sourceRoleId);
                    requestPacketOut.addAttributeInt(Packet.ATTR_DEST_ROLE, destRoleId);
                }

                // RING
                if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_RING)) {
                    if(valid && !destTerminal.isInCall()) {
                        // Send ring request to other peer
                        destTerminal.setCallPeer(sourceTerminalId);
                        sourceTerminal.setCallPeer(destTerminalId);
                        log.debug("Initiate: RING, ok! From "+sourceTerminalId +" to "+destTerminalId);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_RING_ACCEPT);
                        requestPacketOut.addAttributeBool(Packet.ATTR_PHONE_RING);
                        sendRequest = true;
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.phoneRing(sourceTerminal, destTerminal, sourceRoleId, destRoleId, true);
                        }
                    }
                    else {
                        // Terminal is busy
                        log.debug("Initiate: RING, terminal was busy! From "+sourceTerminalId +" to "+destTerminalId);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_RING_BUSY);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.phoneRing(sourceTerminal, destTerminal, sourceRoleId, destRoleId, false);
                        }
                    }
                }

                // ANSWER
                else if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_ANSWER)) {
                    if(valid && destTerminal.getCallPeer()==sourceTerminalId) {
                        // Answer previous ring request
                        log.debug("Initiate: ANSWER, ok! From "+sourceTerminalId +" to "+destTerminalId);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_ANSWER_ACCEPT);
                        requestPacketOut.addAttributeBool(Packet.ATTR_PHONE_ANSWER);
                        sendRequest = true;
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.phoneAnswer(sourceTerminal, destTerminal, sourceRoleId, destRoleId, true);
                        }
                    }
                    else {
                        // No one there when call was answered
                        log.debug("Initiate: ANSWER, no one there! From "+sourceTerminalId +" to "+destTerminalId);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_ANSWER_REJECT);
                        sendResponse = true;

                        if(networkHandler!=null) {
                            networkHandler.phoneAnswer(sourceTerminal, destTerminal, sourceRoleId, destRoleId, false);
                        }
                    }
                }

                // HANGUP
                else if(requestPacket.getAttributeBool(Packet.ATTR_PHONE_HANGUP)) {
                    if(valid && destTerminal.getCallPeer()==sourceTerminalId) {
                        log.debug("Initiate: HANGUP, ok! From "+sourceTerminalId +" to "+destTerminalId);
                        destTerminal.setCallPeer(0);
                        sourceTerminal.setCallPeer(0);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_HANGUP_ACCEPT);
                        requestPacketOut.addAttributeBool(Packet.ATTR_PHONE_HANGUP);
                        sendRequest = true;
                        sendResponse = true;
                    }
                    else {
                        // If dest terminal is not valid, hangup source terminal anyway
                        log.debug("Initiate: HANGUP, no other terminal! From "+sourceTerminalId +" to "+destTerminalId);
                        sourceTerminal.setCallPeer(0);
                        responsePacket.addAttributeBool(Packet.ATTR_PHONE_HANGUP_ACCEPT);
                        sendResponse = true;
                    }

                    if(networkHandler!=null) {
                        networkHandler.phoneHangup(sourceTerminal, destTerminal, sourceRoleId, destRoleId);
                    }
                }

                // Send request and response or return the packets
                if(sendResponse) {
                    transactionManager.sendResponse(responsePacket, requestPacket.getTransactionId());
                }
                else {
                    PacketPool.getPool().returnPacket(responsePacket);
                }

                if(sendRequest) {
                    transactionManager.sendRequest(requestPacketOut,
                            TRANSACTION_TIMEOUT,
                            TRANSACTION_RETRIES,
                            new EmptyResponsePacketHandler());
                }
                else {
                    PacketPool.getPool().returnPacket(requestPacketOut);
                }
            }
        }
    }

    //
    // HELPERS
    //
    private class EmptyResponsePacketHandler implements ResponseHandler {
        public void handleResponse(Packet requestPacket, Packet responsePacket) {
        }
        public void handleTimeout(Packet requestPacket) {
            // Terminal has went offline, reset connection
            int terminalId = requestPacket.getDestId();
            log.warn("Terminal "+terminalId+" is not responding, going offline!");
            ServerTerminal terminal = bundle.getTerminal(terminalId);
            if(terminal!=null) {
                resetTerminal(terminal, true);
            }

            possibleConnectionProblem = checkAllTerminals();
        }
    }
    private void resetTerminal(ServerTerminal terminal, boolean offline) {
        if(terminal==null) return;
        int terminalId = terminal.getId();
        if(log.isDebugEnabled()) {
            log.debug("Resetting terminal "+terminalId+ ", offline="+offline);
        }

        // Release radio..
        Collection<ServerChannel> channels = bundle.getChannelCollection();
        Iterator<ServerChannel> iter = channels.iterator();
        while(iter.hasNext()) {
            ServerChannel c = iter.next();
            if(c.getAcquiredTerminalId()==terminalId) {
                c.release();
            }
        }

        // ..and hangup phone
        if(terminal.isInCall()) {
            ServerTerminal callPeerTerminal = bundle.getTerminal(terminal.getCallPeer());
            if(callPeerTerminal!=null) {
                // Check if the other terminal is really talking to me
                if(callPeerTerminal.getCallPeer() == terminalId) {
                    // Send a hangup request packet to the other terminal
                    Packet packet = PacketPool.getPool().borrowPacket(Packet.INITIATE | Packet.REQUEST);
                    packet.setSourceId(terminalId);
                    packet.setDestId(callPeerTerminal.getId());
                    packet.setSessionId(callPeerTerminal.getSessionId());
                    packet.addAttributeBool(Packet.ATTR_PHONE_HANGUP);
                    packet.getSocketAddress().setAddress(callPeerTerminal.getSocketAddress());
                    transactionManager.sendRequest(packet,
                            TRANSACTION_TIMEOUT,
                            TRANSACTION_RETRIES,
                            new EmptyResponsePacketHandler());
                    // Clear peer
                    callPeerTerminal.setCallPeer(0);
                }
            }

            // Clear
            terminal.setCallPeer(0);

            // Call handler
            if(networkHandler!=null) {
                networkHandler.phoneHangup(terminal, callPeerTerminal, 0, 0);
            }
        }

        // Send update to all terminals
        Collection<ServerTerminal> terminals = bundle.getTerminalCollection();
        Iterator<ServerTerminal> itert = terminals.iterator();
        while(itert.hasNext()) {
            ServerTerminal t = itert.next();
            // All terminals must receive update requests from the leaving terminal
            sendUpdateRequest(t, terminal, false);
        }

        if(offline) {

            // Call handler
            if(networkHandler!=null) {
                networkHandler.terminalOffline(terminal);
            }

            // Clear
            terminal.setPingTime(0);
            terminal.setSocketAddress(null);
        }

        // Clear variables
        terminal.setCallPeer(0);
        terminal.setStarted(false);
    }
}
