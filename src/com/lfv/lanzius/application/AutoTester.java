package com.lfv.lanzius.application;

import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;

/**
 * <p>
 * Tester
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2 (Lanzius)
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
public class AutoTester implements Runnable {

    private Log log;

    private Thread thread;
    private Document doc;
    private ViewEventHandler handler;
    private boolean running;
    private long startTime;

    public AutoTester(ViewEventHandler handler) {
        log = LogFactory.getLog(getClass());
        this.handler = handler;
    }

    public void startTester(Document doc) {
        if(running)
            stopTester();

        this.doc = doc;
        thread = new Thread(this);
        running = true;
        thread.setDaemon(true);
        thread.start();
    }

    public void stopTester() {
        running = false;
    }

    private String time() {
        long t = System.currentTimeMillis()-startTime+100000000L;
        return "("+t+")";
    }

    public void run() {
        Random rand = new Random();

        try {
            Thread.sleep(3000);

            startTime = System.currentTimeMillis();

            log.info(time()+" Starting auto tester");

            while(running) {
                List channelList = doc.getRootElement().getChild("ChannelSetup").getChildren();

                Element ec = (Element)channelList.get(rand.nextInt(channelList.size()));

                int channelId = DomTools.getAttributeInt(ec, "id", 0, false);

                if(DomTools.getAttributeBoolean(ec, "hidden", false, false))
                    continue;

                double x = rand.nextDouble();

                // Change state
                if(x>0.8) {
                    if(DomTools.getAttributeBoolean(ec, "locked", false, false))
                        continue;

                    boolean rx = rand.nextDouble()>0.5;
                    boolean tx = rand.nextDouble()>0.5;

                    log.info(time()+" rxtxStateUpdated(channelId="+channelId+", rx="+rx+", tx="+tx+")");
                    handler.rxtxStateUpdated(channelId, rx, tx);
                }

                // Push the channel
                else if(x>0.6) {
                    if(DomTools.getAttributeString(ec, "state", "off", false).equals("rxtx")) {
                        log.info(time()+" channelButtonPressed(channelId="+channelId+")");
                        handler.channelButtonPressed(channelId);
                        Thread.sleep((int)(rand.nextDouble()*1500+500));
                        if(running) {
                            log.info(time()+" channelButtonReleased(channelId="+channelId+")");
                            handler.channelButtonReleased(channelId);
                        }
                    }
                }

                // Push talk
                else if(x>0.4) {
                    log.info(time()+" talkButtonPressed(DEVICE_MOUSE)");
                    handler.talkButtonPressed(Constants.DEVICE_MOUSE);
                    Thread.sleep((int)(rand.nextDouble()*1000+300));

                    if(rand.nextDouble()<0.3) {
                        log.info(time()+" talkButtonPressed(DEVICE_FTSW)");
                        handler.talkButtonPressed(Constants.DEVICE_FTSW);
                        Thread.sleep((int)(rand.nextDouble()*1000+500));
                        if(running) {
                            log.info(time()+" talkButtonReleased(DEVICE_FTSW)");
                            handler.talkButtonReleased(Constants.DEVICE_FTSW);
                        }
                    }

                    if(running) {
                        Thread.sleep((int)(rand.nextDouble()*1000+200));
                        log.info(time()+" talkButtonReleased(DEVICE_MOUSE)");
                        handler.talkButtonReleased(Constants.DEVICE_MOUSE);
                    }
                }

                // Call phone
                else if(x>0.2) {
                    List roleList = doc.getRootElement().getChild("RoleSetup").getChildren();
                    if (roleList.size() > 0) {
                            Element er = (Element)roleList.get(rand.nextInt(roleList.size()));

                            List peerList = er.getChild("PhonePeers").getChildren();
                            if (peerList != null) {
                                    Element erp = (Element)peerList.get(rand.nextInt(peerList.size()));

                                    int role = DomTools.getAttributeInt(er, "id", 0, false);
                                    int peer = DomTools.getAttributeInt(erp, "id", 0, false);

                                    log.info(time()+" dialButtonClicked(role="+role+", peer="+peer+")");
                                    handler.dialButtonClicked(role,peer);
                            }
                    }
                }

                // Pick up / Hang up phone
                else {
                    log.info(time()+" hookButtonClicked()");
                    handler.hookButtonClicked();
                }

                if (x>0.8) {
                        handler.isaValueChosen(rand.nextInt(10));
                }

                Thread.sleep((int)(rand.nextDouble()*1000+100));
            }

            log.info(time()+" Stopping auto tester");

        } catch (Exception ex) {
            log.error("An unknown error occured", ex);
        }
    }
}
