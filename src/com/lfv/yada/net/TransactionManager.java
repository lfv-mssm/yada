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
package com.lfv.yada.net;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import com.arkatay.yada.base.ItemDispatcher;

/**
 * The TransactionManager inherits from PacketBlockDispatcher and waits for a packet
 * on the queue. When a packet is received it is handled differently depending
 * on whether it is a request packet or a response packet. The transaction manager
 * is also making sure that the UDP control packets reach their destination
 * properly by monitoring transmission, resending packets and timing out
 * transactions.
 */
public class TransactionManager extends PacketBlockDispatcher implements NetworkConstants {

    private List<OutgoingTransaction>   activeOutgoing;
    private List<IncomingTransaction>   activeIncoming;
    private Timer                       timer;
    private ItemDispatcher              packetDispatcher;
    private Map<Integer,RequestHandler> requestHandlerMap;

    public TransactionManager(ItemDispatcher packetDispatcher) {
        super("Ttransactionmanager");

        // store the sender locally
        this.packetDispatcher = packetDispatcher;

        // Create a timer for keeping track of timeouts
        timer = new Timer("Stransactionmanager", true);

        // Create a synchronized list for storing outgoing transactions
        activeOutgoing = new LinkedList<OutgoingTransaction>();

        // Create a synchronized list for storing incoming transactions
        activeIncoming = new LinkedList<IncomingTransaction>();

        // Create a request handler map
        requestHandlerMap = new HashMap<Integer,RequestHandler>(32);
    }

    /**
     * Sets a request handler for a specific type of request packets. The request
     * handler is added to a hash map for fast lookup when an incoming packet
     * is dispatched. This function is unsynchronized and must be called from
     * the same thread.
     * @param  packetType the type of packets this handler should handle
     * @param  handler an object implementing the RequestHandler interface
     */
    public void setRequestHandler(int packetType, RequestHandler handler) {
        requestHandlerMap.put(packetType&Packet.TYPE_FIELD, handler);
    }

    /**
     * Sends a request to the output dispatcher. The packet is encapsulated in
     * an OutgoingTransaction object, added to the activeOutgoing list and
     * sent through the sending packet dispatcher. If the request does not
     * reach its destination or the corresponding response gets lost, the request
     * packet will be resent after the timeout.
     * @param  requestPacket the request packet to send
     * @param  timeoutMillis timeout before resending packet
     * @param  maxRetries maximum number of retries before giving and responding with timeout
     * @param  responseHandler an object implementing the ResponseHandler interface
     */
    public void sendRequest(Packet requestPacket, int timeoutMillis, int maxRetries, ResponseHandler responseHandler) {
        //if(log.isDebugEnabled())
        //    log.debug("OUT: Sending request packet "+requestPacket.toString());

        // Create a new transaction and add it to the activeOutgoing list
        OutgoingTransaction transaction = new OutgoingTransaction(requestPacket, timeoutMillis, maxRetries, responseHandler);
        synchronized(activeOutgoing) {

            activeOutgoing.add(transaction);

            // Intitialize the send counter
            transaction.sendCount = 1;

            // Schedule periodic re-send job for handling timeouts
            timer.schedule(transaction, timeoutMillis, timeoutMillis);

            // Send a copy of the request packet
            Packet packet = PacketPool.getPool().borrowPacket(requestPacket);
            if(!packetDispatcher.postItem(packet))
                PacketPool.getPool().returnPacket(packet);
        }
    }

    /**
     * Sends a request to the output dispatcher without expecting any response.
     * @param  requestPacket the request packet to send
     */
    public void sendRequest(Packet requestPacket) {
        //if(log.isDebugEnabled())
        //    log.debug("OUT: Sending request packet "+requestPacket.toString());

        // Send the request packet (no copy)
        if(!packetDispatcher.postItem(requestPacket))
            PacketPool.getPool().returnPacket(requestPacket);
    }

    // This function is called from the input packet dispatcher when a response
    // has been received.
    private void incomingResponse(Packet responsePacket) {
        //if(log.isDebugEnabled())
        //    log.debug("OUT: Incoming response packet "+responsePacket.toString());

        Packet requestPacket = null;
        ResponseHandler responseHandler = null;
        synchronized(activeOutgoing) {

            // Look for the transaction in the list (match transaction id)
            OutgoingTransaction transaction = null;
            Iterator<OutgoingTransaction> iter = activeOutgoing.iterator();
            while(iter.hasNext()) {
                OutgoingTransaction t = iter.next();
                if(t.getTransactionId() == responsePacket.getTransactionId()) {
                    transaction = t;
                    break;
                }
            }

            // If there is no transaction it it either a strange response without a request packet
            // or the transaction has already timed out - do nothing
            // This occured a couple of times when running one client on a mobile connection (slow connection)
            // The transaction is null means that the requests were sent a couple of times and each one
            // generated a response. It took some time for the responses to get here and when they do,
            // the response has already been handled.
            if(transaction!=null) {

                // Cancel the scheduled timeout task
                transaction.cancel();

                // Remove the active transaction
                activeOutgoing.remove(transaction);

                // Store the response handler and the request packet
                // Call it outside the synchronized block to avoid deadlock
                responseHandler = transaction.responseHandler;
                requestPacket   = transaction.requestPacket;
            }
        }

        // Call the response hander response function back to the upper layer
        if(responseHandler!=null)
            responseHandler.handleResponse(requestPacket, responsePacket);

        // return the packets to the pool
        PacketPool pool = PacketPool.getPool();
        pool.returnPacket(responsePacket);
        pool.returnPacket(requestPacket);
    }

    // This function is called from the scheduler thread when no response has been received for a sent request
    // withing the defined time. The packet can be re-sent depending on the count or the handle method on the
    // resposeHandler can be invoked to indicate that no response was recived. The transaction is then removed from
    // the activeOutgoing list.
    private void outgoingTransactionTimeout(OutgoingTransaction transaction) {

        Packet requestPacket = null;
        ResponseHandler responseHandler = null;
        synchronized(activeOutgoing) {
            // Start by looking for the transaction in the activeOutgoing list
            // if it is not there it has already been handled, just return
            boolean found = false;
            Iterator<OutgoingTransaction> iter = activeOutgoing.iterator();
            while(iter.hasNext()) {
                if(iter.next() == transaction) {
                    found = true;
                    break;
                }
            }

            if(found) {
                if(transaction.sendCount < transaction.maxSendCount) {
                    // Re-send request
                    if(log.isDebugEnabled())
                        log.debug("OUT: outgoing transaction timeout. Re-sending "+transaction.requestPacket.toString());

                    // Re-send a new copy of the request packet
                    Packet packet = PacketPool.getPool().borrowPacket(transaction.requestPacket);
                    if(!packetDispatcher.postItem(packet))
                        PacketPool.getPool().returnPacket(packet);

                    // Increase the send counter
                    transaction.sendCount++;
                }
                else {
                    // Give up re-sending request
                    if(log.isDebugEnabled())
                        log.debug("OUT: outgoing transaction timeout. Giving up "+transaction.requestPacket.toString());

                    // Cancel the scheduled timeout task
                    transaction.cancel();

                    // Remove the active transaction
                    activeOutgoing.remove(transaction);

                    // Store the response handler and the request packet
                    // Call it outside the synchronized block to avoid deadlock
                    responseHandler = transaction.responseHandler;
                    requestPacket   = transaction.requestPacket;
                }
            }
        }

        // Call the response hander response function back to the upper layer
        if(responseHandler!=null)
            responseHandler.handleTimeout(requestPacket);

        // Return the packet
        PacketPool.getPool().returnPacket(requestPacket);
    }

    // This function is called from the input packet dispatcher when a request
    // has been received. The activeIncoming list is examined. If the
    // transaction is not in the list, it is added and the associated handler is
    // called back to the application. The new IncomingTransaction object is also
    // added as a TimerTask to the timer to time out after N seconds. When the
    // transaction times out it is removed from the activeIncoming list. If the transaction is
    // already in the list and the responsePacket is null, just ignore the request, the responding
    // peer has not sent the response yet. If the transaction is in the list and the responsePacket is
    // NOT null then it's a re-send from the other peer indicating that the response did not make it
    // there. Send the response it again through the PacketSender.
    private void incomingRequest(Packet requestPacket) {
        if(log.isDebugEnabled())
            log.debug("IN: Incoming request packet "+requestPacket.toString());

        RequestHandler requestHandler = null;
        synchronized(activeIncoming) {
            int transactionId  = requestPacket.getTransactionId();
            int packetType = requestPacket.getType();

            // Look for the transaction in the list (match transaction id)
            IncomingTransaction transaction = null;
            Iterator<IncomingTransaction> iter = activeIncoming.iterator();
            while(iter.hasNext()) {
                IncomingTransaction t = iter.next();
                if(t.getTransactionId() == transactionId) {
                    transaction = t;
                    break;
                }
            }

            // Not found in list. That means it is the first request in a transaction
            if(transaction==null) {
                log.debug("IN: Adding new transaction");

                // Create a new transaction and add it to the activeIncoming list
                transaction = new IncomingTransaction(requestPacket);
                activeIncoming.add(transaction);

                // Store the request handler for calling outside the synchronized block
                RequestHandler handler = requestHandlerMap.get(packetType&Packet.TYPE_FIELD);
                if(handler!=null)
                    requestHandler = handler;
                else
                    log.warn("IN: No request handler attached! "+requestPacket.toString());

                // Schedule a timeout for removing the transaction
                if ((packetType&Packet.TYPE_FIELD) == Packet.ISA) {
                        timer.schedule(transaction, ISA_TRANSACTION_TIMEOUT);
                } else {
                        timer.schedule(transaction, TRANSACTION_TIMEOUT*TRANSACTION_RETRIES+1000);
                }
            }

            // Found in list
            else {
                // When transaction exists, we dont need this request packet..
                PacketPool.getPool().returnPacket(requestPacket);

                // If the response packet is null the response has not been sent yet, just ignore other peer's resend
                if(transaction.responsePacket!=null) {
                    // This peer has already sent a response packet once but the other peer
                    // is sending the request again, must be packet loss.. Send response again!
                    if(log.isDebugEnabled())
                        log.debug("IN: Re-sending response packet "+transaction.responsePacket);

                    // Send a copy of the request packet
                    Packet packet = PacketPool.getPool().borrowPacket(transaction.responsePacket);
                    if(!packetDispatcher.postItem(packet))
                        PacketPool.getPool().returnPacket(packet);
                }
            }
        }

        // Call the requestHandler to the upper layer
        if(requestHandler!=null)
            requestHandler.handleRequest(requestPacket);
    }

    /**
     * Sends a response corresponding to an incoming request from the handleRequest
     * function.
     * @param  responsePacket the response packet to send
     * @param  transactionId the transaction id of the corresponding request packet
     */
    public void sendResponse(Packet responsePacket, int transactionId) {
        if(log.isDebugEnabled())
            log.debug("IN: Sending response packet "+responsePacket.toString());

        synchronized(activeIncoming) {
            // Override the transaction id in the response packet with this one
            responsePacket.setTransactionId(transactionId);

            // Look for the transaction in the list (match transaction id)
            IncomingTransaction transaction = null;
            Iterator<IncomingTransaction> iter = activeIncoming.iterator();
            while(iter.hasNext()) {
                IncomingTransaction t = iter.next();
                if(t.getTransactionId()==transactionId) {
                    transaction = t;
                    break;
                }
            }

            // If not in list, the transaction has timed out, or application is trying to send a response
            // for a non-existent request
            if(transaction==null) {
                log.warn("IN: Response packet is sent too late, the transaction has already timed out or non-existent transaction [0x"+Integer.toHexString(transactionId)+"]");
                PacketPool.getPool().returnPacket(responsePacket);
                return;
            }
            else {
                // Create a copy of the response packet to send
                Packet responsePacketCopy = PacketPool.getPool().borrowPacket(responsePacket);

                // Send a copy of the request packet
                if(!packetDispatcher.postItem(responsePacketCopy))
                    PacketPool.getPool().returnPacket(responsePacketCopy);

                // Add the response to the transaction to indicate that the response has been sent
                transaction.responsePacket = responsePacket;
            }
        }
    }

    // When the transaction times out it is removed from the activeIncoming list.
    private void incomingTransactionTimeout(IncomingTransaction transaction) {
        if(log.isDebugEnabled())
            log.debug("IN: incoming transaction timeout, removing transaction from list.");

        synchronized(activeIncoming) {
            // Remove the transaction
            if(!activeIncoming.remove(transaction))
                log.warn("IN: Transaction not found in activeIncoming when removing it");

            // Return the packets to the pool
            PacketPool.getPool().returnPacket(transaction.requestPacket);
            PacketPool.getPool().returnPacket(transaction.responsePacket);
        }
    }

    @Override
    protected void threadStop() {
        timer.cancel();
        activeOutgoing.clear();
        activeIncoming.clear();
    }

    protected void dispatchPacket(Packet packet) {
        // Get the REQUEST or RESPONSE field
        int packetType = packet.getType()&Packet.DIRECTION_FIELD;
        if(packetType==Packet.REQUEST)
            incomingRequest(packet);
        else if(packetType==Packet.RESPONSE)
            incomingResponse(packet);
        else {
            log.error("Wrong packet type: "+packet.toString());
            PacketPool.getPool().returnPacket(packet);
        }
    }

    private class OutgoingTransaction extends TimerTask {
        private Packet requestPacket;
        private int timeoutMillis;
        private int maxSendCount;
        private int sendCount;
        private ResponseHandler responseHandler;

        private OutgoingTransaction(Packet requestPacket, int timeoutMillis, int maxRetries, ResponseHandler responseHandler) {
            this.requestPacket = requestPacket;
            this.timeoutMillis = timeoutMillis;
            this.maxSendCount = maxRetries;
            this.responseHandler = responseHandler;
            this.sendCount = 0;
        }

        public void run() {
            outgoingTransactionTimeout(this);
        }

        public int getTransactionId() {
            return requestPacket.getTransactionId();
        }

        @Override
        public String toString() {
            return "OutgoingTransaction: "+Integer.toHexString(requestPacket.getTransactionId());
        }
    }
    private class IncomingTransaction extends TimerTask {
        private Packet requestPacket;
        private Packet responsePacket;

        private IncomingTransaction(Packet requestPacket) {
            this.requestPacket = requestPacket;
            this.responsePacket = null;
        }

        public void run() {
            incomingTransactionTimeout(this);
        }

        public int getTransactionId() {
            return requestPacket.getTransactionId();
        }

        @Override
        public String toString() {
            return "IncomingTransaction: "+Integer.toHexString(requestPacket.getTransactionId());
        }
    }
}
