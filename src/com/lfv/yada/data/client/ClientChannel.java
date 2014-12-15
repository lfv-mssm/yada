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
package com.lfv.yada.data.client;

import com.arkatay.yada.codec.AudioDecoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.jdom.Element;

public class ClientChannel {

    private int                  channelId;
    private List<ClientTerminal> terminals;
    private Element              element;
    private AudioDecoder         decoder;

    public ClientChannel(int channelId) {
        this.channelId = channelId;
        terminals = new LinkedList<ClientTerminal>();
    }

    public int getId() {
        return channelId;
    }

    public synchronized void setElement(Element element) {
        this.element = element;
    }
    public synchronized Element getElement() {
        return element;
    }

    public synchronized void setDecoder(AudioDecoder decoder) {
        this.decoder = decoder;
    }
    public synchronized AudioDecoder getDecoder() {
        return decoder;
    }

    public synchronized void addTerminal(ClientTerminal terminal) {
        if(!terminals.contains(terminal))
            terminals.add(terminal);
    }
    public synchronized ClientTerminal getTerminal(int terminalId) {
        Iterator<ClientTerminal> iter = terminals.iterator();
        while(iter.hasNext()) {
            ClientTerminal t = iter.next();
            if(t.getId()==terminalId) {
                return t;
            }
        }

        return null;
    }
    public synchronized void removeTerminal(int terminalId) {
        Iterator<ClientTerminal> iter = terminals.iterator();
        while(iter.hasNext()) {
            ClientTerminal t = iter.next();
            if(t.getId()==terminalId) {
                iter.remove();
                return;
            }
        }
    }
    public synchronized void removeTerminal(ClientTerminal terminal) {
        terminals.remove(terminal);
    }
    public Collection<ClientTerminal> getTerminalCollection() {
        return terminals;
    }
    // NOTE! must synchronize on this channel object when using the collection
}
