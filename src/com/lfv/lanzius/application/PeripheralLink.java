package com.lfv.lanzius.application;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import com.arkatay.yada.base.ItemDispatcher;

/**
 * <p>
 * PeripheralLink
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.1 (Lanzius)
 * 
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
public class PeripheralLink extends ItemDispatcher {

    private static final int MAGIC_NBR  = 0x5923;
    private static final int STX        = 0x02;
    private static final int ETX        = 0x03;

    private AbstractView     view;

    private int              terminalId;

    private boolean          connected;
    private boolean          pressed;
    private int[]            channelIds;

    private ServerSocket     serverSocket;
    private Socket           clientSocket;
    private DataOutputStream stream;

    private final String     statusNotConnected = "Peripheral link disconnected!";

    PeripheralLink(int terminalId, int tcpPort) throws IOException {
        super("Tperipherallink");
        this.terminalId = terminalId;
        serverSocket = new ServerSocket(tcpPort);
    }

    public void setView(AbstractView view) {
        this.view = view;
        if(view!=null) {
            if(connected)
                view.updateStatus(null, false);
            else
                view.updateStatus(statusNotConnected, true);
        }
    }

    @Override
    public void stopModule() {
        if(!isStopping) {
            isStopping = true;

            // Send release if pressed
            if(pressed && connected) {
                try {
                    send(ETX);
                } catch (IOException ex) { }
            }

            // Close everything
            try {
                if(stream!=null)
                    stream.close();
            } catch (IOException ex) { }
            try {
                if(clientSocket!=null)
                    clientSocket.close();
            } catch (IOException ex) { }
            try {
                if(serverSocket!=null)
                    serverSocket.close();
            } catch (IOException ex) { }
            thread.interrupt();
            clear();
        }
    }

    public void PostRadioSendStart(int[] channelIds) {
        if(connected)
            postItem(new Message(true, channelIds));
        else
            clear();
    }

    public void PostRadioSendStop() {
        if(connected)
            postItem(new Message(false, null));
        else
            clear();
    }

    private void send(int type) throws IOException {
        // Byte 0-1: Magic number
        // Byte 2  : StartSend (0x02) or StopSend (0x03)
        // Byte 3  : Sending terminal id
        // Byte 4  : Number of send channels (n)
        // Byte 5- : List of channel id's, one byte each
        if(channelIds==null) {
            log.error("ChannelId array is null when sending peripheral link message!");
        }
        else {
            stream.writeShort(MAGIC_NBR);
            stream.writeByte(type);
            stream.writeByte(terminalId);
            stream.writeByte(channelIds.length);
            for(int i=0;i<channelIds.length;i++) {
                stream.writeByte(channelIds[i]);
            }
        }
    }

    protected boolean threadMain() throws InterruptedException {
        // Stop thread if serversocket has failed
        if(serverSocket==null) return false;

        if(!connected) {
            try {
                log.debug("Waiting on port " + serverSocket.getLocalPort());
                clientSocket = serverSocket.accept();
                log.info("Connected to "+clientSocket.getLocalSocketAddress());
                stream = new DataOutputStream(clientSocket.getOutputStream());
            } catch (IOException ex) {
                throw new InterruptedException(ex.getMessage());
            }
            connected = true;
            pressed = false;
            if(view!=null)
                view.updateStatus(null, false);
        }

        else {
            waitItem();
            Message msg = (Message)getItem();

            try {
                if(pressed) {
                    if(msg.pressed)
                        ;//log.warn("Press message received when foot switch is down, ignoring");
                    else {
                        send(ETX);
                        pressed = false;
                    }
                }
                else {
                    if(msg.pressed) {
                        channelIds = msg.channelIds;
                        send(STX);
                        pressed = true;
                    }
                    //else
                    //    log.warn("Release message received when foot switch is up, ignoring");
                }
            } catch (IOException ex) {
                log.info("Connection broken");
                try {
                    stream.close();
                    clientSocket.close();
                } catch (IOException ex2) { }
                stream=null;
                clientSocket=null;
                clear();
                connected = false;
                if(view!=null)
                    view.updateStatus(statusNotConnected, true);
            }
        }

        return true;
    }

    private class Message {
        private boolean pressed;
        private int[] channelIds;

        Message(boolean pressed, int[] channelIds) {
            this.pressed = pressed;
            this.channelIds = channelIds;
        }

        @Override
        public String toString() {
            return "PeripheralLinkMessage [pressed="+pressed+"]";
        }
    }
}
