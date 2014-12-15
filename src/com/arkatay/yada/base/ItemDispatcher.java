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
package com.arkatay.yada.base;

import java.util.LinkedList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * The ItemDispatcher is one of the building blocks in the system. Basically
 * it consists of a queue and a thread. Items can be posted on the queue and
 * handled by the thread. This class is abstract and the subclass must implement
 * the threadMain method.
 */
public abstract class ItemDispatcher implements Runnable {

    protected Thread thread;
    protected Log log;
    protected List<Object> list;

    protected boolean isStopping;

    /**
     * Creates a new instance of ItemDispatcher. Creates the thread and the
     * queue.
     *
     */
    protected ItemDispatcher(String name) {
        thread = new Thread(this, name+"-itemdisp");

        // Create a logger for this class
        log = LogFactory.getLog(getClass());

        // create the list to act as a item queue
        list = new LinkedList<Object>();
    }

    /**
     * Starts the dispatcher module. This function actually starts the thread.
     *
     */
    public void startModule() {
        if(isStopping)
            throw new IllegalStateException("Trying to re-start the item dispatcher");
        thread.start();
    }

    /**
     * Stops the dispatcher module. This function stops the thread and clears the
     * queue.
     *
     */
    public void stopModule() {
        if(!isStopping) {
            isStopping = true;
            thread.interrupt();
            clear();
        }
    }

    /**
     * The entry point for the internal thread. Calls threadMain in a while loop
     * until the module is stopped.
     *
     */
    public void run() {
        try {
            threadStart();
            while(!thread.isInterrupted()&&!isStopping) {
                if(!threadMain()) {
                    log.error("Item dispatcher was stopped by an error");
                    isStopping=true;
                }
            }
        } catch(InterruptedException ex) {
            if(!isStopping)
                log.warn("Item dispatcher interrupted", ex);
        }
        if(isStopping) {
            // It's ok, the dispatcher was manually interrupted
            log.debug("Item dispatcher successfully stopped");
        }
        try {
            threadStop();
            clear();
        } catch (InterruptedException ex) {
            log.error("Thread interrupted when stopping", ex);
        }
    }

    /**
     * Posts an item on the queue
     *
     * @param  item the object to add to the queue
     * @return true if the item was added
     */
    public synchronized boolean postItem(Object item) {
        // Adding item last in list
        list.add(item);
        notify();
        return true;
    }

    /**
     * Gets an item from the queue and removes it
     *
     * @return the first item in the queue
     */
    protected synchronized Object getItem() {
        if(list.isEmpty())
            return null;
        return list.remove(0);
    }

    /**
     * Gets an item from the queue without removing it
     *
     * @return the first item in the queue
     */
    protected synchronized Object peekItem() {
        if(list.isEmpty())
            return null;
        return list.get(0);
    }

    /**
     * Blocks until there is an item available in the queue
     *
     * @throws InterruptedException if the wait function was interrupted externally
     */
    protected synchronized void waitItem() throws InterruptedException {
        while(list.isEmpty())
            wait();
    }

    /**
     * Blocks until there is an item available in the queue and times out after
     * the specified time.
     *
     * @param  timeoutMillis timeout time i milliseconds
     * @throws InterruptedException if the wait function was interrupted externally
     */
    protected synchronized void waitItem(int timeoutMillis) throws InterruptedException {
        if(list.isEmpty())
            wait(timeoutMillis);
    }

    /**
     * Clears the queue
     *
     */
    public synchronized void clear() {
        list.clear();
    }

    /**
     * Gets the size of the queue
     *
     * @return size of the queue
     */
    public synchronized int getSize() {
        return list.size();
    }

    /**
     * The abstract function where the processing occures. Must be implemented
     * by a subclass. This function is individually synchronized with the other
     * threadXxx functions in same dispatcher.
     *
     * @return false if an error occured and the dispatcher thread should be stopped
     * @throws InterruptedException if the function was interrupted externally
     */
    protected abstract boolean threadMain() throws InterruptedException;

    /**
     * This function is called once before threadMain
     *
     * @throws InterruptedException if the function was interrupted externally
     */
    protected void threadStart() throws InterruptedException {}

    /**
     * This function is called once after the last threadMain just before the
     * thread dies
     *
     * @throws InterruptedException if the function was interrupted externally
     */
    protected void threadStop() throws InterruptedException {}
}
