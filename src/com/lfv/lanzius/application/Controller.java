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
package com.lfv.lanzius.application;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import com.arkatay.yada.base.ItemDispatcher;
import com.arkatay.yada.base.Time;
import com.arkatay.yada.codec.AudioDecoder;
import com.arkatay.yada.codec.AudioDecoderListener;
import com.arkatay.yada.codec.AudioEncoder;
import com.arkatay.yada.codec.AudioRecorder;
import com.arkatay.yada.codec.JSpeexDecoder;
import com.arkatay.yada.codec.JSpeexEncoder;
import com.arkatay.yada.codec.NullDecoder;
import com.arkatay.yada.codec.NullEncoder;
import com.lfv.lanzius.Config;
import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.full.FullView;
import com.lfv.lanzius.application.phoneonly.PhoneOnlyView;
import com.lfv.lanzius.application.slim.SlimView;
import com.lfv.lanzius.audio.MixingDataLine;
import com.lfv.yada.data.client.ClientBundle;
import com.lfv.yada.data.client.ClientChannel;
import com.lfv.yada.data.client.ClientTerminal;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.SocketAddress;
import com.lfv.yada.net.client.ClientNetworkHandler;
import com.lfv.yada.net.client.ClientNetworkManager;
import com.lfv.yada.net.client.ClientPacketReceiver;
import com.lfv.yada.net.client.Distributor;

public class Controller implements ViewEventHandler, ClientNetworkHandler, AudioDecoderListener, Constants {

    private Log log;
    private boolean autoTesterEnabled;
    private AutoTester autoTester;

    private AbstractView view;
    private JDialog settingsDialog;
    private JDialog isaDialog;
    private Document model;
    private ClientBundle bundle;

    private TerminalProperties properties;
    private SocketAddress serverSocketAddr;
    private ClientNetworkManager networkManager;
    private ClientPacketReceiver packetReceiver;

    private Timer timer;
    private Thread hookThread;

    private Mixer outputMixer;
    private Mixer inputMixer;

    // Codec n stuff
    private AudioEncoder encoder;
    private Distributor  distributor;
    private AudioDecoder decoderPhone;
    private AudioDecoder decoderForward;

    // Monitor
    private boolean      monitorActive;
    private int          monitoringTerminalId;
    private int          monitoredTerminalId;
    private int          monitorChannelId;
    private AudioEncoder monitorEncoder;
    private Distributor  monitorDistributor;

    // Recorder
    private AudioRecorder audioRecorder;

    // Sounds
    private SoundClip clipRingTone;
    private SoundClip clipRingBack;
    private SoundClip clipRingBusy;
    private SoundClip clipWarning;
    private SoundClip clipNotify;
    private boolean clipWarningOn;

    // Volume
    private List<VolumeAdjustable> volumeClipList;
    private List<VolumeAdjustable> volumeRadioList;

    // Control
    private int     state;
    //private boolean restarting;
    private int     reconnectPrintCount;

    private boolean semDisconnected;
    private boolean semChannelsLeft;
    private boolean semStartFinished;
    private boolean semStopFinished;

    private boolean phoneActive;
    private boolean radioEncoding;
    private boolean radioDecoding;
    private List<AudioDecoder> radioDecodingList;

    private int phoneState = PHONE_STATE_IDLE;
    private int radioState = RADIO_STATE_IDLE;
    private long radioIdleTime = System.currentTimeMillis();
    private List<ClientChannel> radioAcquiredChannelsList;
    private List<ClientChannel> radioUnacquiredChannelsList;
    private TimerTask     radioReacquireTask;
    private int           radioAcquireCount;
    private int           radioReacquireCount;
    private int           radioReleaseCount;

    private FootSwitchController footSwitch;
    private EventSwitchController eventSwitch;
    private PeripheralLink peripheralLink;

    private long isaReqStartTime;

    private static Controller controller = null;

    public static Controller getInstance() {
        if(controller==null)
            controller = new Controller();
        return controller;
    }

    private Controller() {
        log = LogFactory.getLog(getClass());
        log.info(Config.VERSION+"\n");
        timer = new Timer("Scontroller");
        radioAcquiredChannelsList = new LinkedList<ClientChannel>();
        radioUnacquiredChannelsList = new LinkedList<ClientChannel>();
        radioDecodingList = new LinkedList<AudioDecoder>();
        volumeClipList = new LinkedList<VolumeAdjustable>();
        volumeRadioList = new LinkedList<VolumeAdjustable>();
    }

    public void setAutoTester(boolean autoTesterEnabled) {
        this.autoTesterEnabled = autoTesterEnabled;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {}
    }

    /*
    private synchronized void restart() {
        if(state==CLIENT_STATE_STARTED && !restarting && timer!=null) {
            log.error("Restarting terminal!");
            timer.schedule(new TimerTask() {
                public void run() {
                    stop(true,false);
                }
            }, DELAY_STOP);
            timer.schedule(new TimerTask() {
                public void run() {
                    start();
                    restarting = false;
                }
            }, DELAY_STOP+DELAY_STARTSTOP+DELAY_START);
            restarting = true;
        }
    }
    */

    private synchronized boolean waitChannelAcquiry() {
        try {

            // Wait until all channels are acquired, reacquired or released
            // This function must be called only from synchronized methods
            long t = System.currentTimeMillis();
            while((radioAcquireCount>0||radioReacquireCount>0||radioReleaseCount>0) &&
                  (System.currentTimeMillis()<(t+TIMEOUT_ACQUIRY)))
                wait(100);

            // Failure
            if(radioAcquireCount>0||radioReacquireCount>0||radioReleaseCount>0) {
                log.error("Wait for channel acquiry timed out! " +
                          "(radioAcquireCount="+radioAcquireCount+"," +
                          " radioReacquireCount="+radioReacquireCount+","+
                          " radioReleaseCount="+radioReleaseCount+")");
            }

            // Success
            else {

                // Clean up and prepare for new requests
                radioAcquireCount   = 0;
                radioReacquireCount = 0;
                radioReleaseCount   = 0;

                radioUnacquiredChannelsList.clear();
                if(radioReacquireTask!=null) {
                    radioReacquireTask.cancel();
                    radioReacquireTask = null;
                }

                return true;
            }
        } catch (InterruptedException ex) {
            log.warn("Wait for channel acquiry interrupted!");
        }

        log.error("!!!");
        log.error("!!! The terminal has entered an unstable state and may not function properly!");
        log.error("!!! Relink or restart the terminal if problems arise!");
        log.error("!!!");

        clear();

        return false;
    }

    private synchronized void releaseAcquiredChannels() {

        if(!waitChannelAcquiry())
            return;

        if(!Config.CLIENT_SERVERLESS) {

            // Reset only radio if phone is encoding, channels will
            // automatically be removed from the distributor
            if(phoneActive)
                encoder.resetChannels(distributor.getRadioChannels());

            // Stop encoder otherwise
            else
                encoder.stopProcessing();

            radioEncoding = false;
            updateMonitor();

            // Idle talk button
            model.getRootElement().getChild("TalkButton").setAttribute("state", "idle");

            // Release all acquired channels
            Iterator<ClientChannel> iter = radioAcquiredChannelsList.iterator();
            while(iter.hasNext()) {
                ClientChannel channel = iter.next();

                // Send release to server
                radioReleaseCount++;
                networkManager.radioRelease(channel.getId());

                // Update send flag
                channel.getElement().setAttribute("send", "false");
            }

            // Send message to peripheral link
            if(radioReleaseCount>0 && peripheralLink!=null) {
                peripheralLink.PostRadioSendStop();
            }

            // Clear all fail flags
            iter = bundle.getChannelCollection().iterator();
            while(iter.hasNext()) {
                ClientChannel channel = iter.next();
                if(channel.getId()>CHANNEL_RADIO_START) {
                    channel.getElement().setAttribute("fail", "false");
                }
            }
        }
        else if(peripheralLink!=null) {
            // Serverless debug mode
            peripheralLink.PostRadioSendStop();
        }

        radioAcquiredChannelsList.clear();

        if(view!=null)
            view.updateRadioView();
    }

    private synchronized List<Element> getAllPeerElements(int peerId) {
        List<Element> list = new LinkedList<Element>();
        Iterator iter1 = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
        while(iter1.hasNext()) {
            Element er = (Element)iter1.next();
            Iterator iter2 = er.getChild("PhonePeers").getChildren().iterator();
            while(iter2.hasNext()) {
                Element err = (Element)iter2.next();
                if(DomTools.getAttributeInt(err, "id", 0, false)==peerId)
                    list.add(err);
            }
        }
        return list;
    }

    private synchronized Element getPeerElement(int roleId, int peerId) {
        Iterator iter1 = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
        while(iter1.hasNext()) {
            Element er = (Element)iter1.next();
            if(DomTools.getAttributeInt(er, "id", 0, false)==roleId) {
                Iterator iter2 = er.getChild("PhonePeers").getChildren().iterator();
                while(iter2.hasNext()) {
                    Element err = (Element)iter2.next();
                    if(DomTools.getAttributeInt(err, "id", 0, false)==peerId)
                        return err;
                }
                return null;
            }
        }

        return null;
    }

    private synchronized String getRoleName(int id) {
        String def = "Withheld";
        String ids = String.valueOf(id&ID_MASK_ROLE);
        if(ids.equals("0"))
            log.warn("Get role name called with id 0");
        Iterator iter = model.getRootElement().getChild("RoleDefs").getChildren().iterator();
        while(iter.hasNext()) {
            Element er = (Element)iter.next();
            if(DomTools.getAttributeString(er, "id", "-", false).equals(ids)) {
                return DomTools.getChildText(er, "Name", def, false);
            }
        }
        return def;
    }

    private synchronized void updateLocalRoleActivity(int roleId, int peerId, boolean line, boolean blink) {
        Element ep = blink?getPeerElement(roleId, peerId):null;
        Iterator<Element> iter = getAllPeerElements(peerId).iterator();
        while(iter.hasNext()) {
            Element epi = iter.next();
            epi.setAttribute("line",line?"3":"1");
            epi.setAttribute("blink", String.valueOf(blink&&(ep==epi)));
        }
        if(view!=null)
            view.updatePhoneView();
    }

    private synchronized void updateMonitor() {

        // Return if monitor is disabled
        if((monitorEncoder==null) || (monitorDistributor==null))
            return;

        // Is radio or phone active?
        boolean codecActive = (radioEncoding||radioDecoding||phoneActive);

        if(monitorActive) {
            // Stop if codec is not active or monitoring is turned off
            if(!codecActive || (monitoringTerminalId==0) || (monitorChannelId==0)) {
                monitorEncoder.stopProcessing();
                monitorActive = false;
            }
        }
        else {
            if(codecActive && (monitoringTerminalId>0) && (monitorChannelId>0)) {
                ClientTerminal monitoringTerminal = bundle.getTerminal(monitoringTerminalId);

                // Set up distributor and move to started state
                if(monitoringTerminal != null) {
                    monitorDistributor.setConfinedRecipient(monitoringTerminal, monitorChannelId);
                    monitorEncoder.startProcessing();
                    monitorActive = true;
                }
                else if(log.isDebugEnabled()) {
                    log.debug("Monitoring terminal is not available, ignoring");
                }
            }
        }
    }

    private synchronized void playSound(int soundId) {
        if(soundId==SOUND_RINGTONE) clipRingTone.play();
        else                        clipRingTone.stop();
        if(soundId==SOUND_RINGBACK) clipRingBack.play();
        else                        clipRingBack.stop();
        if(soundId==SOUND_RINGBUSY) clipRingBusy.play();
        else                        clipRingBusy.stop();
        if (soundId==SOUND_NOTIFY)      clipNotify.play();
        else                                            clipNotify.stop();
    }

    private synchronized void adjustVolumes() {

        Settings s = Settings.getInstance();

        float masterVolume  = (float)s.getMasterVolume()/(float)Settings.DEF_MASTER_VOLUME;
        float rapassVolume =  (float)s.getRapassVolume()/(float)(Settings.NBR_VOLUME_STEPS-1);
        float chprioVolume =  (float)s.getChprioVolume()/(float)(Settings.NBR_VOLUME_STEPS-1);

        float phoneVolume   = masterVolume;
        float forwardVolume = masterVolume*rapassVolume;
        float radioVolume   = masterVolume;

        // Simultaneous radio and interphone
        if(radioDecoding&&phoneActive) {
            int c = s.getChprioChoice();
            if(c==Settings.CHPRIO_PHONE) {
                radioVolume *= chprioVolume;
                log.debug("Simultaneous phone and radio, priority for phone. Reducing radio volume ("+radioVolume+")");
            }
            else if(c==Settings.CHPRIO_RADIO) {
                phoneVolume   *= chprioVolume;
                forwardVolume *= chprioVolume;
                log.debug("Simultaneous phone and radio, priority for radio. Reducing phone volume ("+phoneVolume+"/"+forwardVolume+")");
            }
        }

        if(log.isDebugEnabled())
            log.debug("Adjusting volumes: "+radioVolume+", "+phoneVolume+", "+forwardVolume);

        if(decoderPhone!=null)
            decoderPhone.setVolume(phoneVolume);
        if(decoderForward!=null)
            decoderForward.setVolume(forwardVolume);

        Iterator<VolumeAdjustable> iter = volumeRadioList.iterator();
        while(iter.hasNext())
            iter.next().setVolume(radioVolume);
    }

    private synchronized void stopPhone() {
        packetReceiver.closeChannel(CHANNEL_PHONE);
        packetReceiver.closeChannel(CHANNEL_FORWARD);
        if(decoderPhone!=null)
            decoderPhone.forceStop();
        if(decoderForward!=null)
            decoderForward.forceStop();
        phoneActive = false;

        if(encoder!=null) {
            // Reset only phone if radio is encoding, recipient will
            // automatically be removed from the distributor
            if(radioEncoding)
                encoder.resetChannel(CHANNEL_PHONE);

            // Stop encoder otherwise
            else
                encoder.stopProcessing();
        }

        adjustVolumes();
        updateMonitor();
    }

    private synchronized void clear() {

        semDisconnected = true;
        semChannelsLeft = true;
        semStartFinished = true;
        semStopFinished = true;

        phoneActive   = false;
        radioEncoding = false;
        radioDecoding = false;
        radioDecodingList.clear();

        monitorActive     = false;
        monitoringTerminalId = 0;
        monitoredTerminalId = 0;
        monitorChannelId  = 0;

        phoneState = PHONE_STATE_IDLE;
        radioState = RADIO_STATE_IDLE;
        radioAcquiredChannelsList.clear();
        radioUnacquiredChannelsList.clear();
        radioAcquireCount   = 0;
        radioReacquireCount = 0;
        radioReleaseCount   = 0;

        footSwitch = null;
        networkManager = null;
        bundle = null;
        serverSocketAddr = null;

    }

    private synchronized boolean validateModel() {

        // Validate model
        if(model==null) return false;

        Element r = model.getRootElement();
        if(r==null) return false;

        if(!r.getName().equals("Terminal")) return false;
        if(r.getChild("Group")==null) return false;
        if(r.getChild("Codec")==null) return false;
        if(r.getChild("ChannelSetup")==null) return false;
        if(r.getChild("RoleSetup")==null) return false;
        if(r.getChild("RoleDefs")==null) return false;

        // Validate ChannelsSetup
        Iterator iter = r.getChild("ChannelSetup").getChildren().iterator();
        while(iter.hasNext()) {
            Element e = (Element)iter.next();
            // Check that child is expected
            if(!e.getName().equals("Channel")) return false;
            // Check mandatory id attribute
            if(e.getAttribute("id")==null) return false;
        }
        iter = r.getChild("RoleSetup").getChildren().iterator();
        while(iter.hasNext()) {
            Element e = (Element)iter.next();
            if(!e.getName().equals("Role")) return false;
            if(e.getChild("Name")==null) return false;
            if(e.getChild("PhonePeers")==null) return false;
            Iterator iter2 = e.getChild("PhonePeers").getChildren().iterator();
            while(iter2.hasNext()) {
                e = (Element)iter2.next();
                if(!e.getName().equals("RoleRef")) return false;
                if(e.getChild("Name")==null) return false;
            }
        }

        return true;
    }

    /*
    private synchronized void dbgPrintModelToFile(String name) {
        if(!Config.DEBUG) return;
        String fname = "data/development/client_model_"+name+".xml";
        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
        try {
            Writer wr = new FileWriter(fname);
            xmlo.output(model, wr);
            wr.close();
        } catch (IOException ex) {
            log.error("IOException when trying to write model to file "+fname+"!", ex);
        }
        log.info("Model successfully written to file "+fname);
    }

    private synchronized void dbgListThreadTree() {
        if(!Config.DEBUG) return;
        System.out.println("--------------------------------------------------");
        System.out.println("Current thread: "+Thread.currentThread());
        System.out.println("Active threads:");
        // Find the root thread group
        ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
        while (root.getParent() != null) {
            root = root.getParent();
        }

        // Visit each thread group
        dbgListThreadTreeRec(root, 0);
        System.out.println("--------------------------------------------------");
    }

    private synchronized void dbgListThreadTreeRec(ThreadGroup group, int level) {
        if(!Config.DEBUG) return;
        System.out.println(group);

        // Get threads in `group'
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads*2];
        numThreads = group.enumerate(threads, false);

        // Enumerate each thread in `group'
        for (int i=0; i<numThreads; i++) {
            // Get thread
            Thread thread = threads[i];
            System.out.println("  "+thread);
        }

        // Get thread subgroups of `group'
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups*2];
        numGroups = group.enumerate(groups, false);

        // Recursively visit each subgroup
        for (int i=0; i<numGroups; i++) {
            dbgListThreadTreeRec(groups[i], level+1);
        }
    }
    */

    private synchronized void dbgListThreads() {
        if(!Config.DEBUG) return;
        System.out.println("--------------------------------------------------");
        System.out.println("Current thread: "+Thread.currentThread());
        System.out.println("Active main threads:");

        // Get threads in `group'
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads*2];
        numThreads = group.enumerate(threads, false);

        // Enumerate each thread in `group'
        for (int i=0; i<numThreads; i++) {
            // Get thread
            Thread thread = threads[i];
            System.out.println("  "+thread);
        }

        System.out.println("--------------------------------------------------");
    }

    public synchronized void init(int id) {

        clear();
        state = CLIENT_STATE_DISCONNECTED;
        reconnectPrintCount = 5;

        // Load terminal properties
        try {
            properties = new TerminalProperties(id);
        } catch (Exception ex) {
            log.error("Invalid or no terminal properties file (data/properties/terminalproperties.xml). Exiting!",ex);
            System.exit(0);
        }

        // Overwrite if specified externally on the command line
        if(id>0)
            properties.setTerminalId(id);

        log.info("Starting terminal "+properties.getTerminalId());

        // Setup codec values
        AudioDecoder.setJitterBufferSize(properties.getJitterBufferSize());
        AudioDecoder.setOutputBufferSize(properties.getOutputBufferSize());
        AudioEncoder.setInputBufferSize(properties.getInputBufferSize());

        // Initialize the timing system
        Time.init();

        // Setup a socket address to the server
        try {
            serverSocketAddr = new SocketAddress(properties.getServerAddress(), properties.getServerUdpPort());
        } catch (UnknownHostException ex) {
            log.error("Invalid server address. Exiting!", ex);
            System.exit(0);
        }

        // Create a bundle
        bundle = new ClientBundle();

        // Create the network manager
        try {
            networkManager = new ClientNetworkManager(properties.getTerminalId(),
                    bundle,
                    serverSocketAddr,
                    new SocketAddress(properties.getLocalBindAddress(),properties.getLocalBindPort()),
                    new SocketAddress(properties.getMulticastAddress(),properties.getMulticastPort()),
                    properties.getMulticastTTL(),
                    properties);
            packetReceiver = networkManager.getReceiver();
        } catch (IOException ex) {
            log.error("The network manager could not be created, possibly due to an invalid multicast socket setting. Exiting!", ex);
            System.exit(0);
        }

        // Create push to talk interfaces
        if (properties.getEventDeviceName().length() > 0) {
            eventSwitch = new EventSwitchController(this, properties.getEventDeviceName());
        }
        if (properties.getFootSwitchInterface().length() > 0) {
            try {
                footSwitch = new FootSwitchController(this,
                        properties.getFootSwitchInterface(),
                        properties.getFootSwitchPollTimeMillis(),
                        properties.isFootSwitchInverted());
            } catch(UnsatisfiedLinkError err) {
                log.error("UnsatisfiedLinkError: "+err.getMessage());
                log.warn("Missing ftsw library (ftsw.dll or libftsw.so)");
                if(!properties.getFootSwitchInterface().equalsIgnoreCase("ftdi"))
                    log.warn("Missing rxtxSerial library (rxtxSerial.dll or librxtxSerial.so)");
                log.warn("The footswitch has been disabled!");
                footSwitch = null;
            } catch(NoClassDefFoundError err) {
                log.warn("Missing ftsw library (ftsw.dll or libftsw.so)");
                if(!properties.getFootSwitchInterface().equalsIgnoreCase("ftdi"))
                    log.warn("Missing rxtxSerial library (rxtxSerial.dll or librxtxSerial.so)");
                log.warn("The footswitch has been disabled!");
                footSwitch = null;
            }
        }

        // Get the selected audio devices
        Mixer.Info[] mixerinfo = AudioSystem.getMixerInfo();

        if((properties.getSoundOutputDevice()>=mixerinfo.length)||(properties.getSoundInputDevice()>=mixerinfo.length)) {
            log.error("Invalid sound device. Exiting!");
            //System.exit(0);
        }

        outputMixer = AudioSystem.getMixer(mixerinfo[properties.getSoundOutputDevice()]);
        inputMixer  = AudioSystem.getMixer(mixerinfo[properties.getSoundInputDevice()]);

        if(log.isDebugEnabled()) {
            log.debug("Using output device "+properties.getSoundOutputDevice()+" ("+mixerinfo[properties.getSoundOutputDevice()]+")");
            log.debug("Using input device "+properties.getSoundInputDevice()+" ("+mixerinfo[properties.getSoundInputDevice()]+")");
        }

        // Creating sound clips
        clipRingTone = new SoundClip(outputMixer,
                "data/resources/sounds/phone_ringtone_mono.wav",
                "data/resources/sounds/phone_ringtone_stereo.wav",
                1100, properties.getSignalVolumeAdjustment());
        volumeClipList.add(clipRingTone);
        clipRingBack = new SoundClip(outputMixer,
                "data/resources/sounds/phone_ringback_mono.wav",
                "data/resources/sounds/phone_ringback_stereo.wav",
                2100, properties.getSignalVolumeAdjustment());
        volumeClipList.add(clipRingBack);
        clipRingBusy = new SoundClip(outputMixer,
                "data/resources/sounds/phone_busy_mono.wav",
                "data/resources/sounds/phone_busy_stereo.wav",
                400, properties.getSignalVolumeAdjustment());
        volumeClipList.add(clipRingBusy);
        clipWarning = new SoundClip(outputMixer,
                "data/resources/sounds/radio_error_mono.wav",
                "data/resources/sounds/radio_error_stereo.wav",
                1000, properties.getSignalVolumeAdjustment());
        volumeClipList.add(clipWarning);
        clipNotify = new SoundClip(outputMixer,
                        "data/resources/sounds/ding_mono.wav",
                "data/resources/sounds/ding_stereo.wav",
                1000, properties.getSignalVolumeAdjustment());
        volumeClipList.add(clipNotify);

        // Create peripheral link
        peripheralLink = null;
        int peripheralLinkPort = properties.getPeripheralLinkPort();
        if(peripheralLinkPort>0) {
            try {
                peripheralLink = new PeripheralLink(properties.getTerminalId(), peripheralLinkPort);
                peripheralLink.startModule();
            } catch (IOException ex) {
                log.warn("Unable to create peripheral link, disabling!");
                peripheralLink = null;
            }
        }

        // Add shutdown hook
        if(hookThread!=null)
            Runtime.getRuntime().removeShutdownHook(hookThread);
        hookThread = new Thread(new Runnable() {
            public void run() {
                stop(true, true);
                shutdown(true);
                sleep(25);
                dbgListThreads();
                log.info("Bye!");
            }
        },"Thook");
        Runtime.getRuntime().addShutdownHook(hookThread);

        if(Config.CLIENT_SERVERLESS) {
            state = CLIENT_STATE_CONNECTED;
            sessionStart();
        }
        else {
            // Setup handler for incoming requests and timeouts
            networkManager.setNetworkHandler(this);

            // Try to connect to server
            log.info("Connecting to server on "+properties.getServerAddress()+"...");
            networkManager.serverConnect();
        }

        if(autoTesterEnabled) {
            autoTester = new AutoTester(this);
        }
    }

    public synchronized void shutdown(boolean finalize) {

        if(state!=CLIENT_STATE_UNINITIALIZED) {
            state = CLIENT_STATE_UNINITIALIZED;

            log.info("Stopping terminal");

            if(networkManager == null || Config.CLIENT_SERVERLESS) {
                serverDisconnected();
            }
            else {
                if(networkManager.serverDisconnect()) {
                    semDisconnected = false;
                    try {
                        long t = System.currentTimeMillis();
                        while(!semDisconnected&&(System.currentTimeMillis()<(t+TIMEOUT_SHUTDOWN)))
                            wait(100);
                    } catch (InterruptedException ex) { }
                }
            }
        }

        if(finalize) {
            if(timer!=null) {
                log.debug("Finalizing");
                timer.cancel();
                timer = null;
            }
        }
    }

    public synchronized void start() {
        try {
            long t = System.currentTimeMillis();
            while(!semStopFinished&&(System.currentTimeMillis()<(t+TIMEOUT_STARTSTOP)))
                wait(100);
        } catch (InterruptedException ex) { }

        if(state==CLIENT_STATE_CONNECTED) {

            view = null;
            audioRecorder = null;

            log.info("Starting session");

            radioState = RADIO_STATE_IDLE;
            phoneState = PHONE_STATE_IDLE;
            phoneActive   = false;
            radioEncoding = false;
            radioDecoding = false;
            radioDecodingList.clear();

            log.debug("Downloading data model for terminal "+properties.getTerminalId()+" from http://"+properties.getServerAddress()+":"+properties.getServerHttpPort());
            // Get configuration document from server
            String url = "http://"+properties.getServerAddress()+":"+properties.getServerHttpPort()+"/xml/info?terminal="+properties.getTerminalId();
            try {
                SAXBuilder builder = new SAXBuilder();
                if(Config.CLIENT_SERVERLESS)
                    model = builder.build(new java.io.File("data/development/client_inforequest.xml"));
                else
                    model = builder.build(new URL(url));
            } catch (Exception ex) {
                log.error("Failed to get or parse the configuration document from "+url.toString(), ex);
                log.debug("Waiting for start session packet from server");
                ex.printStackTrace();
                return;
            }

            if(!validateModel()) {
                log.error("Invalid data model, validation failed!");
                log.debug("Waiting for start session packet from server");
                return;
            }

            // Document is valid
            state = CLIENT_STATE_STARTED;
            log.debug("Data model passed validation test");

            // Add extra nodes
            model.getRootElement().addContent(new Element("TalkButton"));
            model.getRootElement().addContent(new Element("HookButton"));
            Element e1 = new Element("Settings");
            Element e2 = new Element("Name");
            e2.setText("SETTINGS");
            e1.addContent(e2);
            e1.setAttribute("hfac", "0.4");
            model.getRootElement().addContent(e1);

            // Inactivate all phone peers initially
            Iterator iter1 = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter1.hasNext()) {
                Element er = (Element)iter1.next();
                Iterator iter2 = er.getChild("PhonePeers").getChildren().iterator();
                while(iter2.hasNext()) {
                    Element err = (Element)iter2.next();
                    err.setAttribute("active", "false");
                }
            }

            // Clear
            encoder            = null;
            distributor        = null;
            decoderPhone       = null;
            decoderForward     = null;
            monitorEncoder     = null;
            monitorDistributor = null;

            // Get codec type
            String codec = DomTools.getChildText(model.getRootElement(), "Codec", "null", true);

            // Encoder
            if(codec.startsWith("jspeex")) {
                // Parse out the quality from the string "jspeex:<quality 1-10>"
                int quality = 6;
                int idx = codec.indexOf(':');
                if(idx>0) {
                    try {
                        quality = Integer.parseInt(codec.substring(idx+1));
                    } catch(Exception ex) {
                        log.warn("Parse error in string "+codec+", should be \"jspeex:<quality 1-10>\", defaulting to 6!");
                    }
                    if(quality<1||quality>10) {
                        log.warn("Out of range error in string "+codec+", should be \"jspeex:<quality 1-10>\", defaulting to 6!");
                        quality = 6;
                    }
                }
                encoder        = new JSpeexEncoder(inputMixer, quality);
                monitorEncoder = new JSpeexEncoder(inputMixer, quality);
            }
            else {
                encoder        = new NullEncoder(inputMixer);
                monitorEncoder = new NullEncoder(inputMixer);
            }

            // Main distributor
            distributor = new Distributor(properties.getTerminalId(), bundle, networkManager.getSender());
            encoder.setPacketDistributor(distributor);

            // Monitor distributor
            monitorDistributor = new Distributor(properties.getTerminalId(), bundle, networkManager.getSender());
            monitorEncoder.setPacketDistributor(monitorDistributor);

            // Start encoders
            AudioFormat audioFormats[] = encoder.getSupportedAudioFormats();
            int usedAudioFormat = -1;

            // Find which audio format to use
            for(int i=0;i<audioFormats.length;i++) {
                try {
                    encoder.startModule(i);
                    log.debug("Using input audio format "+audioFormats[i]);
                    usedAudioFormat = i;
                    break;
                } catch (Exception ex) {
                    log.warn("Could not open the input device for format "+audioFormats[i]+"!");
                }
            }

            if(usedAudioFormat<0) {
                log.error("Unable to start, the input sound device is unavailable!");
                stop(false, false);
                return;
            }

            MixingDataLine monitorDataLine = null;
            if(usedAudioFormat>0) {
                log.warn("Unable to open the monitor data line, supports mono only!");
                monitorEncoder = null;
                monitorDistributor = null;
            }
            else {

                // Create a mixing data line for the monitor
                monitorDataLine = new MixingDataLine();

                try {
                    // Start monitor encoder with the same format as the main encoder
                    monitorEncoder.startModule(monitorDataLine, 0);

                } catch (Exception ex) {
                    log.warn("Unable to open the monitor data line, monitoring has been disabled!",ex);
                    monitorDataLine = null;
                    monitorEncoder = null;
                    monitorDistributor = null;
                }
            }

            // Pass a monitor channel to the encoder
            if(monitorDataLine!=null) {
                encoder.setMonitorChannel(monitorDataLine.getMixerChannel());
            }

            // Create decoders and packet dispatchers
            if(codec.startsWith("jspeex")) {
                decoderPhone   = new JSpeexDecoder(outputMixer, CHANNEL_PHONE);
                decoderForward = new JSpeexDecoder(outputMixer, CHANNEL_FORWARD);
            }
            else {
                decoderPhone   = new NullDecoder(outputMixer, CHANNEL_PHONE);
                decoderForward = new NullDecoder(outputMixer, CHANNEL_FORWARD);
            }

            // Add decoders as packet receiver handlers
            packetReceiver.addDataPacketDispatcher(CHANNEL_PHONE,   decoderPhone);
            packetReceiver.addDataPacketDispatcher(CHANNEL_FORWARD, decoderForward);

            // Pass monitor channel to decoders
            if(monitorDataLine!=null) {
                decoderPhone.setMonitorChannel(monitorDataLine.getMixerChannel());
                decoderForward.setMonitorChannel(monitorDataLine.getMixerChannel());
            }

            Iterator iter = model.getRootElement().getChild("ChannelSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                int channelId = DomTools.getAttributeInt(ec, "id", 0, true);
                if(channelId>0) {
                    AudioDecoder decoder;
                    if(codec.startsWith("jspeex")) {
                        decoder = new JSpeexDecoder(outputMixer, channelId);
                    }
                    else {
                        decoder = new NullDecoder(outputMixer, channelId);
                    }

                    // Add decoder as packet receiver handler
                    packetReceiver.addDataPacketDispatcher(channelId, decoder);

                    // Pass monitor channel to decoder
                    if(monitorDataLine!=null) {
                        decoder.setMonitorChannel(monitorDataLine.getMixerChannel());
                    }
                }
            }

            // Go through the decoders and create output lines for them
            audioFormats    = null;
            usedAudioFormat = -1;
            iter = packetReceiver.getDataPacketDispatcherCollection().iterator();
            while(iter.hasNext()) {
                AudioDecoder decoder = (AudioDecoder)iter.next();

                // First iteration, find which audio format to use
                if(usedAudioFormat<0) {
                    audioFormats = decoder.getSupportedAudioFormats();
                    for(int i=0;i<audioFormats.length;i++) {
                        try {
                            decoder.startModule(i);
                            log.debug("Using output audio format "+audioFormats[i]);
                            usedAudioFormat = i;
                            break;
                        } catch (Exception ex) {
                            log.warn("Could not open the output device for format "+audioFormats[i]+"!");
                        }
                    }

                    if(usedAudioFormat<0) {
                        log.error("Unable to start, the output sound device is unavailable!");
                        stop(false, false);
                        return;
                    }
                }

                // The format to use is known, just start the module
                else {
                    try {
                        decoder.startModule(usedAudioFormat);
                    } catch (LineUnavailableException ex) {
                        log.error("Could not open the output device for format "+audioFormats[usedAudioFormat]+"!");
                        stop(false, false);
                        return;
                    }
                }

                // Add listeners for all radio
                // Also add to volume list
                if(decoder.getDecoderId()>CHANNEL_RADIO_START) {
                    decoder.addListener(this);
                    volumeRadioList.add(decoder);
                }
            }

            // Create a recorder
            audioRecorder = new AudioRecorder(audioFormats[usedAudioFormat], true);

            // Join channels
            Collection<Integer> c = new LinkedList<Integer>();
            // Common
            bundle.addChannel(new ClientChannel(CHANNEL_COMMON));
            c.add(CHANNEL_COMMON);
            iter = model.getRootElement().getChild("ChannelSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                int channelId = DomTools.getAttributeInt(ec, "id", 0, true);
                if(channelId>0) {
                    ClientChannel channel = new ClientChannel(channelId);
                    bundle.addChannel(channel);
                    c.add(channelId);
                    // Also store the channel element..
                    channel.setElement(ec);
                    // ..and the decoder for fast access
                    AudioDecoder decoder = (AudioDecoder)packetReceiver.getDataPacketDispatcher(channelId);
                    channel.setDecoder(decoder);
                    // finally, add the recorder if the channel has recordable="true"
                    if(DomTools.getAttributeBoolean(ec, "recordable", false, false)) {
                        decoder.setAudioRecorder(audioRecorder);
                        decoder.addListener(audioRecorder); // to get stop events for injecting silence
                    }
                }
            }

            networkManager.sessionConnect(c);

            // Immediately open all channels with state rxtx or rx
            iter = bundle.getChannelCollection().iterator();
            while(iter.hasNext()) {
                ClientChannel channel = (ClientChannel)iter.next();
                if(!DomTools.getAttributeString(channel.getElement(), "state", "off", false).equals("off")) {
                    packetReceiver.openChannel(channel.getId());
                }
                if(DomTools.getAttributeBoolean(channel.getElement(), "autorec", false, false)) {
                	recordStateUpdated(channel.getId(), true);
                    updateRecorder(channel.getId());
                }                
            }

            // Force all settings
            Settings settings = Settings.getInstance();
            settingsValueChanged(Settings.ID_MASTER_VOLUME, settings.getMasterVolume());
            settingsValueChanged(Settings.ID_SIGNAL_VOLUME, settings.getSignalVolume());
            settingsValueChanged(Settings.ID_CHPRIO_VOLUME, settings.getChprioVolume());
            settingsValueChanged(Settings.ID_RAPASS_VOLUME, settings.getRapassVolume());
            settingsValueChanged(Settings.ID_CHPRIO_CHOICE, settings.getChprioChoice());
            settingsValueChanged(Settings.ID_WATONE_CHOICE, settings.getWatoneChoice());

            // Create view
            String style = properties.getUserInterfaceStyle().toLowerCase();
            if(!(style.equals("full")||style.equals("slim-phone")||style.equals("slim-horiz")||style.equals("slim-vert")||style.equals("none"))) {
                log.warn("Invalid user interface style ("+style+") defaulting to full!");
                style = "full";
                properties.setUserInterfaceStyle(style);
            }

            if(!Config.CLIENT_SIZE_FULLSCREEN||!style.equals("full"))
                JFrame.setDefaultLookAndFeelDecorated(true);

            log.debug("Creating view with style: "+style);

            if(properties.getUserInterfaceStyle().equalsIgnoreCase("full")) {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        view = new FullView(Controller.getInstance(), properties);
                        view.setModel(model);
                        view.setVisible(true);
                        view.toFront();
                        if(peripheralLink!=null)
                            peripheralLink.setView(view);
                    }
                });
            }
            else if(properties.getUserInterfaceStyle().equalsIgnoreCase("none")) {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        view = new FullView(Controller.getInstance(), properties);
                        view.setModel(model);
                        view.setVisible(false);
                        view.toFront();
                        if(peripheralLink!=null)
                            peripheralLink.setView(view);
                    }
                });
            }
            else if(properties.getUserInterfaceStyle().equalsIgnoreCase("slim-phone")) {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        view = new PhoneOnlyView(Controller.getInstance(), properties);
                        view.setModel(model);
                        view.setVisible(true);
                        view.toFront();
                        if(peripheralLink!=null)
                            peripheralLink.setView(view);
                    }
                });
            } else {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        view = new SlimView(Controller.getInstance(), properties);
                        view.setModel(model);
                        view.setVisible(true);
                        view.toFront();
                        if(peripheralLink!=null)
                            peripheralLink.setView(view);
                    }
                });
            }

            // Start the push to talk interfaces
            if (properties.getEventDeviceName().length() > 0) {
                eventSwitch.start();
            }
            if (properties.getFootSwitchInterface().length() > 0) {
                if(footSwitch!=null) {
                    try {
                        footSwitch.start();
                    } catch(UnsatisfiedLinkError err) {
                        log.error("UnsatisfiedLinkError: "+err.getMessage());
                        log.warn("Missing ftsw library (ftsw.dll or libftsw.so)");
                        if(!properties.getFootSwitchInterface().equalsIgnoreCase("ftdi"))
                            log.warn("Missing rxtxSerial library (rxtxSerial.dll or librxtxSerial.so)");
                        log.warn("The footswitch has been disabled!");
                        footSwitch = null;
                    } catch(NoClassDefFoundError err) {
                        log.warn("Missing ftsw library (ftsw.dll or libftsw.so)");
                        if(!properties.getFootSwitchInterface().equalsIgnoreCase("ftdi"))
                            log.warn("Missing rxtxSerial library (rxtxSerial.dll or librxtxSerial.so)");
                        log.warn("The footswitch has been disabled!");
                        footSwitch = null;
                    } catch(NoSuchPortException ex) {
                        log.warn("The serial port "+properties.getFootSwitchInterface()+" does not exist, the footswitch has been disabled!");
                        footSwitch = null;
                    } catch(PortInUseException ex) {
                        log.warn("The serial port "+properties.getFootSwitchInterface()+" is already in use, the footswitch has been disabled!");
                        footSwitch = null;
                    }
                }
            }

            sleep(25);

            dbgListThreads();

            // The system is not allowed to be stopped immediately after start
            if(timer!=null) {
                semStartFinished = false;
                timer.schedule(new TimerTask() {
                    public void run() {
                        Controller c = Controller.getInstance();
                        synchronized(c) {
                            semStartFinished = true;
                            c.notifyAll();
                        }
                    }
                }, DELAY_STARTSTOP);
            }

            if(autoTesterEnabled && autoTester!=null) {
                autoTester.startTester(model);
            }
        }
    }

    public synchronized void stop(boolean leaveChannels, boolean waitChannelsLeft) {
        try {
            long t = System.currentTimeMillis();
            while(!semStartFinished&&(System.currentTimeMillis()<(t+TIMEOUT_STARTSTOP)))
                wait(100);
        } catch (InterruptedException ex) { }

        if(autoTesterEnabled && autoTester!=null) {
            autoTester.stopTester();
        }

        semChannelsLeft = true;
        if(state>=CLIENT_STATE_STARTED) {

            log.info("Stopping session");

            state = CLIENT_STATE_CONNECTED;

            // Stop all sounds
            playSound(SOUND_NONE);

            if(footSwitch!=null)
                footSwitch.stop();

            if(peripheralLink!=null)
                peripheralLink.PostRadioSendStop();

            if(view!=null) {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        view.setVisible(false);
                        view.dispose();
                    }
                });
            }

            if(leaveChannels&&bundle!=null&&!Config.CLIENT_SERVERLESS) {
                // Leave all channels
                Collection<Integer> c = new LinkedList<Integer>();
                Iterator<ClientChannel> iterc = bundle.getChannelCollection().iterator();
                while(iterc.hasNext()) {
                    ClientChannel channel = iterc.next();
                    c.add(new Integer(channel.getId()));
                }

                semChannelsLeft = c.isEmpty();
                if(!semChannelsLeft)
                    networkManager.sessionDisconnect();
            }

            // Stop decoder and close channels
            Iterator<ItemDispatcher> iterd = packetReceiver.getDataPacketDispatcherCollection().iterator();
            while(iterd.hasNext()) {
                AudioDecoder decoder = (AudioDecoder)iterd.next();
                int channelId = decoder.getDecoderId();
                packetReceiver.closeChannel(channelId);
                if(audioRecorder!=null) {
                    audioRecorder.stopRecording(channelId);
                }
                decoder.stopModule();
            }

            // Stop encoders
            if(encoder!=null) {
                encoder.stopModule();
            }
            if(monitorEncoder!=null) {
                monitorEncoder.stopModule();
            }

            packetReceiver.removeDataPacketDispatchers();
            volumeRadioList.clear();
            decoderPhone = null;
            decoderForward = null;
            encoder = null;
            monitorEncoder = null;

            model = null;

            sleep(25);

            if(distributor!=null) {
                distributor.removeAll();
                distributor = null;
            }
            if(monitorDistributor!=null) {
                monitorDistributor.removeAll();
                monitorDistributor = null;
            }

            log.debug("Session stopped, Waiting for start session packet from server");

            // The system is not allowed to be started immediately after stop
            if(timer!=null) {
                semStopFinished = false;
                timer.schedule(new TimerTask() {
                    public void run() {
                        Controller c = Controller.getInstance();
                        synchronized(c) {
                            semStopFinished = true;
                            c.notifyAll();
                        }
                    }
                }, DELAY_STARTSTOP);
            }

            if(leaveChannels&&waitChannelsLeft&&!semChannelsLeft&&!Config.CLIENT_SERVERLESS) {
                try {

                    long t = System.currentTimeMillis();
                    while(!semChannelsLeft && (System.currentTimeMillis()<(t+TIMEOUT_LEAVE)))
                        wait(100);

                    if(!semChannelsLeft)
                        log.warn("Wait for channels left timed out!");

                } catch (InterruptedException ex) {
                    log.warn("Wait for channels left interrupted!");
                }
            }
        }
    }
    
    public synchronized void pause() {
        Iterator<ItemDispatcher> iterd = packetReceiver.getDataPacketDispatcherCollection().iterator();
        while(iterd.hasNext()) {
            if(audioRecorder!=null) {
            	AudioDecoder decoder = (AudioDecoder)iterd.next();
            	int channelId = decoder.getDecoderId();
            	ClientChannel channel = bundle.getChannel(channelId);
            	if (channel != null) {
            		if (state == CLIENT_STATE_STARTED) {
            			log.info("Pausing session");
		                recordStateUpdated(channel.getId(), false);
		                updateRecorder(channel.getId());
		                state = CLIENT_STATE_PAUSED;
            		} else if (state == CLIENT_STATE_PAUSED) {
            			log.info("Resuming session");
		                if(DomTools.getAttributeBoolean(channel.getElement(), "autorec", false, false)) {
		                	recordStateUpdated(channel.getId(), true);
		                    updateRecorder(channel.getId());
		                }
		                state = CLIENT_STATE_STARTED;
            		}
            	}
            }
        }    	
    }

    public synchronized void rxtxStateUpdated(int channelId, boolean rx, boolean tx) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;
        ClientChannel channel = bundle.getChannel(channelId);
        if(channel!=null) {
            Element ec = channel.getElement();

            // Decide on new state
            String      newstate = "off";
            if(tx)      newstate = "rxtx";
            else if(rx) newstate = "rx";

            // If channel is a monitor, do not allow rxtx state
            if(newstate.equals("rxtx") && DomTools.getAttributeBoolean(ec, "monitor", false, false)) {
                newstate = "off";
            }

            // Update state in model
            ec.setAttribute("state", newstate);

            // Close sending if state is rx or off
            if(!newstate.equals("rxtx")) {
                ec.setAttribute("send", "false");
                ec.setAttribute("fail", "false");
                encoder.resetChannel(channelId);
            }

            // Close receiving if state is off
            if(newstate.equals("off")) {
                ec.setAttribute("recvp", "false");
                ec.setAttribute("recvs", "false");
                packetReceiver.closeChannel(channelId);
                channel.getDecoder().forceStop();
                recordStateUpdated(channelId, false);
            }

            // Open channel if new state is rx or rxtx
            else {
                packetReceiver.openChannel(channelId);
            }

            // Update view
            if(view!=null)
                view.updateRadioView();
        }
    }

    private synchronized void updateRecorder(int channelId) {
        if(bundle==null || state<CLIENT_STATE_STARTED || audioRecorder==null) return;

        ClientChannel channel = bundle.getChannel(channelId);
        if(channel!=null) {
            Element ec = channel.getElement();

            boolean monitor    = DomTools.getAttributeBoolean(ec, "monitor", false, false);
            boolean recording  = DomTools.getAttributeBoolean(ec, "recording", false, false);
            boolean monitoring = (monitoredTerminalId>0);

            if(monitor) {
                ec.setAttribute("monitoring", String.valueOf(monitoring));

                int curMonitoredTerminalId = audioRecorder.getMonitoredTerminalId(channelId);

                if(monitoring&&recording) {

                    // If monitored terminal has changed, stop recorder first
                    if(curMonitoredTerminalId>0 && curMonitoredTerminalId!=monitoredTerminalId) {
                        audioRecorder.stopRecording(channelId);
                    }

                    audioRecorder.startRecording(channelId, null, monitoredTerminalId);
                }
                else {
                    audioRecorder.stopRecording(channelId);
                }
            }

            else {
                if(recording) {
                    String channelName = DomTools.getChildText(ec, "Name", null, false);
                    audioRecorder.startRecording(channelId, channelName, 0);
                }
                else {
                    audioRecorder.stopRecording(channelId);
                }
            }
        }
    }

    public synchronized void recordStateUpdated(int channelId, boolean recording) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED || audioRecorder==null) return;

        ClientChannel channel = bundle.getChannel(channelId);
        if(channel!=null) {
            Element ec = channel.getElement();

            if (ec != null) { 
	            // Stop recording if state is off
	            if(DomTools.getAttributeString(ec, "state", "off", true).equals("off")) {
	                ec.setAttribute("recording", "false");
	                audioRecorder.stopRecording(channelId);
	            }
	            else {
	                ec.setAttribute("recording", String.valueOf(recording));
	                updateRecorder(channelId);
	            }
	
	            if(view!=null)
	                view.updateRadioView();
            }
        }
    }

    public synchronized void channelButtonPressed(int channelId) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;
        if(radioState!=RADIO_STATE_IDLE) return;

        if(!waitChannelAcquiry())
            return;

        // Acquire clicked channel
        radioAcquiredChannelsList.clear();
        if(!Config.CLIENT_SERVERLESS) {
            ClientChannel channel = bundle.getChannel(channelId);
            if(channel!=null) {
                Element ec = channel.getElement();

                // Check that the state of the channel is tx
                if(DomTools.getAttributeString(ec,"state", "off", false).equals("rxtx")) {
                    // Try to acquire it
                    radioAcquireCount = 1;
                    networkManager.radioAcquire(channelId);
                    radioState = RADIO_STATE_CHANNEL;
                }
            }
        }
    }

    public synchronized void channelButtonReleased(int channelId) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;
        if(radioState!=RADIO_STATE_CHANNEL) return;
        releaseAcquiredChannels();
        radioState = RADIO_STATE_IDLE;
    }

    public synchronized void talkButtonPressed(int device) {
//      if (isaDialog != null) {
//              isaValueChosen(0);
//      }
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;

        // Return if a channel button is pressed
        if(radioState==RADIO_STATE_CHANNEL) return;

        // BN: HACK controller wants to be able to speak, even if incoming transmission
        if (properties.getOverPowerOtherStations() == 1) {
                log.debug("BN HACK: Controller wants to be able to speak");
            // Release the channel so we can send
            Iterator<ClientChannel> iter = bundle.getChannelCollection().iterator();
            while(iter.hasNext()) {
                ClientChannel channel = iter.next();
                networkManager.radioRelease(channel.getId());
            }
            radioState = RADIO_STATE_IDLE;

            // Set the master volume to 0 so we don't hear others
            Settings s = Settings.getInstance();
            s.setMasterVolume(0);
            adjustVolumes();
        }

        // Only acquire channels if state is idle
        boolean acquireChannels = (radioState==RADIO_STATE_IDLE);

        // Go to next state
        if(device==DEVICE_MOUSE)     radioState = RADIO_STATE_TALK_MOUSE;
        else if(device==DEVICE_FTSW) radioState = RADIO_STATE_TALK_FTSW;

        // Leave if double press, same of difference device
        if(!acquireChannels)
            return;

        if(!waitChannelAcquiry())
            return;

        // Try to acquire all tx channels
        radioAcquiredChannelsList.clear();
        if(!Config.CLIENT_SERVERLESS) {
            radioAcquireCount = 0;
            Iterator<ClientChannel> iter = bundle.getChannelCollection().iterator();
            while(iter.hasNext()) {
                ClientChannel channel = iter.next();
                int channelId = channel.getId();

                if(channelId > CHANNEL_RADIO_START) {
                    Element ec = channel.getElement();

                    // Check that the state of the channel is tx
                    if(DomTools.getAttributeString(ec,"state", "off", false).equals("rxtx")) {
                        radioAcquireCount++;
                        networkManager.radioAcquire(channelId);
                    }
                }
            }
        }
        else if(peripheralLink!=null) {
            // Debug: ServerLess mode
            int[] channelIds = {1,2,3};
            peripheralLink.PostRadioSendStart(channelIds);
        }
    }

    public synchronized void talkButtonReleased(int device) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;

        // BN: HACK controller wants to be able to speak, even if incoming transmission
        if (properties.getOverPowerOtherStations() == 1) {
                log.debug("BN HACK: Enable volume again");
                // Set the master volume to 100
            Settings s = Settings.getInstance();
            s.setMasterVolume(100);
            adjustVolumes();
        }

        boolean releaseChannels =
                (radioState==RADIO_STATE_TALK_MOUSE && device==DEVICE_MOUSE) ||
                (radioState==RADIO_STATE_TALK_FTSW  && device==DEVICE_FTSW);

        if(releaseChannels) {
            releaseAcquiredChannels();
            radioState = RADIO_STATE_IDLE;
        }
    }

    public synchronized void dialButtonClicked(int roleId, int peerId) {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;
        if(phoneState==PHONE_STATE_RINGING) {
                Element eh = model.getRootElement().getChild("HookButton");
            int thisId   = DomTools.getAttributeInt(eh, "this",  0, true);
            int otherId  = DomTools.getAttributeInt(eh, "other", 0, true);
            if (otherId != peerId) {
                    eh.setAttribute("state", "idle");
                    networkManager.phoneHangup(thisId, otherId);
                    //phoneState = PHONE_STATE_HANGUP_REQ;
                    phoneState = PHONE_STATE_IDLE;
            }
        }
        if(phoneState==PHONE_STATE_IDLE) {

            if(roleId==0||peerId==0)
                return;

            // Setup information on hook button
            Element eh = model.getRootElement().getChild("HookButton");
            eh.setAttribute("src", getRoleName(roleId));
            eh.setAttribute("dest", getRoleName(peerId));
            eh.setAttribute("this", String.valueOf(roleId));
            eh.setAttribute("other", String.valueOf(peerId));
            eh.setAttribute("state", "dialing");
            if(view!=null)
                view.updatePhoneView();
            networkManager.phoneRing(roleId, peerId);
            phoneState = PHONE_STATE_DIALING_REQ;
        }
    }

    public synchronized void hookButtonClicked() {
        if(bundle==null) return;
        if(state<CLIENT_STATE_STARTED) return;
        Element eh = model.getRootElement().getChild("HookButton");
        if(phoneState==PHONE_STATE_RINGING) {
            eh.setAttribute("state", "in_call");
            int thisId   = DomTools.getAttributeInt(eh, "this",  0, true);
            int otherId  = DomTools.getAttributeInt(eh, "other", 0, true);
            networkManager.phoneAnswer(thisId, otherId);
            phoneState = PHONE_STATE_ANSWER_REQ;
        }
        else if(phoneState==PHONE_STATE_DIALING||phoneState==PHONE_STATE_IN_CALL) {
            eh.setAttribute("state", "idle");
            int thisId   = DomTools.getAttributeInt(eh, "this",  0, true);
            int otherId  = DomTools.getAttributeInt(eh, "other", 0, true);
            networkManager.phoneHangup(thisId, otherId);
            phoneState = PHONE_STATE_HANGUP_REQ;
        }
        else if(phoneState==PHONE_STATE_BUSY) {
            eh.setAttribute("state", "idle");
            playSound(SOUND_NONE);
            phoneState = PHONE_STATE_IDLE;
        }
        if(view!=null)
            view.updatePhoneView();
    }

    public synchronized void settingsDialogOpen() {
        if(state<CLIENT_STATE_STARTED) return;
        // This button is also used to stop the auto tester
        if(autoTesterEnabled) {
            autoTester.stopTester();
            JOptionPane.showMessageDialog(view,
                    "The auto tester has been stopped! The settings dialog\n" +
                    "is unaccessible when the auto tester is enabled!",
                    "Auto tester",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if(settingsDialog==null) {
            boolean full = properties.getUserInterfaceStyle().equalsIgnoreCase("full");
            if(!full)
                JDialog.setDefaultLookAndFeelDecorated(true);

            settingsDialog = new JDialog(view, "Settings", false);
            settingsDialog.setContentPane(new SettingsPane(this));

            if(full) {
                settingsDialog.setResizable(false);
                settingsDialog.setUndecorated(true);
                Rectangle rect = view.getBounds();
                rect.x += rect.width*0.1;
                rect.y += rect.height*0.1;
                rect.width *= 0.8;
                rect.height *= 0.8;
                settingsDialog.setBounds(rect);

                // Hide mouse cursor if stated in the properties file
                if(properties.isMouseCursorHidden())
                    settingsDialog.setCursor(InvisibleCursor.getCursor());
            }
            else {
                GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle rect = graphicsEnvironment.getMaximumWindowBounds();
                rect.x += rect.width*0.25;
                rect.y += rect.height*0.25;
                rect.width *= 0.5;
                rect.height *= 0.5;
                settingsDialog.setBounds(rect);
                settingsDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                settingsDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        settingsDialogClose();
                    }
                });
            }

            settingsDialog.setVisible(true);
        }
    }

    public synchronized void settingsDialogClose() {
        if(settingsDialog!=null) {
            settingsDialog.setVisible(false);
            settingsDialog = null;
        }
    }

    public synchronized void settingsValueChanged(int id, int value) {
        if(state<CLIENT_STATE_STARTED) return;
        Settings s = Settings.getInstance();
        switch(id) {
            case(Settings.ID_MASTER_VOLUME): {
                s.setMasterVolume(value);
                adjustVolumes();
                break;
            }
            case(Settings.ID_SIGNAL_VOLUME): {
                float signalVolume = (float)value/(float)Settings.DEF_SIGNAL_VOLUME;
                Iterator<VolumeAdjustable> iter = volumeClipList.iterator();
                while(iter.hasNext())
                    iter.next().setVolume(signalVolume);
                s.setSignalVolume(value);
                break;
            }
            case(Settings.ID_CHPRIO_VOLUME): {
                s.setChprioVolume(value);
                adjustVolumes();
                break;
            }
            case(Settings.ID_RAPASS_VOLUME): {
                s.setRapassVolume(value);
                adjustVolumes();
                break;
            }
            case(Settings.ID_CHPRIO_CHOICE): {
                s.setChprioChoice(value);
                adjustVolumes();
                break;
            }
            case(Settings.ID_WATONE_CHOICE): {
                clipWarningOn = (value==Settings.WATONE_ON);
                s.setWatoneChoice(value);
                break;
            }
            default:
                log.error("Settings id is invalid!");
        }
    }

    /**
     * Open the ISA dialog (if the radio has been silent for at least 2 seconds)
     * @return true if the dialog was opened
     */
    public boolean isaDialogOpen(int isaNumChoices, boolean isaExtendedMode, String isakeytext[]) {
        final int radioSilentLimit = 500; // Do not show ISA dialog until at least half a second of silence
        if (view == null) return true; // This might happen if ISA request when terminal is starting up, don't open ISA dialog
                if(isaDialog==null) {
                // Is radio or phone active?
                boolean codecActive = (radioEncoding||radioDecoding||phoneActive);
                        // Wait for 2 seconds of radio silence
                        if (codecActive ||
                                        (System.currentTimeMillis()-radioIdleTime) < radioSilentLimit) {
                                // Ignore ISA request if too busy
                                return false;
                        }

                    boolean full = properties.getUserInterfaceStyle().equalsIgnoreCase("full");
                    if(!full)
                        JDialog.setDefaultLookAndFeelDecorated(true);

                    isaDialog = new JDialog(view, "Workload", false);
                    isaDialog.setContentPane(new ISAPane(Controller.getInstance(), isaNumChoices, isaExtendedMode, isakeytext));

                    if(full) {
                        isaDialog.setResizable(false);
                        isaDialog.setUndecorated(true);
                        Rectangle rect = view.getBounds();
                        if (isaExtendedMode) {
                                rect.x += rect.width*0.3;
                                rect.y += rect.height*0.3;
                                rect.width *= 0.4;
                                rect.height *= 0.5;
                        } else {
                                rect.x += rect.width*0.2;
                                rect.y += rect.height*0.4;
                                rect.width *= 0.6;
                                rect.height *= 0.15;
                        }
                        isaDialog.setBounds(rect);

                        // Hide mouse cursor if stated in the properties file
                        if(properties.isMouseCursorHidden())
                                isaDialog.setCursor(InvisibleCursor.getCursor());
                    }
                    else {
                        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        Rectangle rect = graphicsEnvironment.getMaximumWindowBounds();
                        if (isaExtendedMode) {
                                rect.x = (int)(rect.width - (rect.width*0.2));
                                rect.y = (int)(rect.height - (rect.height*0.25));
                                rect.width *= 0.2;
                                rect.height *= 0.25;
                        } else {
                                rect.x = (int)(rect.width - (rect.width*0.3));
                                rect.y = (int)(rect.height - (rect.height*0.1));
                                rect.width *= 0.3;
                                rect.height *= 0.1;
                        }
                        isaDialog.setBounds(rect);
                        isaDialog.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                isaValueChosen(0);
                            }
                        });
                    }
                    isaDialog.setVisible(true);
                    isaReqStartTime = System.currentTimeMillis();
                    clipNotify.playOnce();
                }
                return true;
        }

    /**
     * Close the ISA dialog
     */
    public void isaDialogClose() {
        if(isaDialog != null) {
            isaDialog.setVisible(false);
                isaDialog = null;
        }
    }

    /**
     * User has chosen an ISA value, send response to server
     */
    public void isaValueChosen(int value) {
        if (isaDialog != null && isaDialog.isShowing()) {
                log.debug("ISA value chosen: " + value);
                isaDialogClose();
                networkManager.sendIsaResponse(value, (System.currentTimeMillis()-isaReqStartTime));
        } else {
                log.debug("ISA dialog not open - ignore");
        }

    }

    public synchronized void closeButtonClicked() {
        if(state<CLIENT_STATE_STARTED) return;
        if(Config.CLIENT_EXIT_DIALOG) {
            int res = JOptionPane.showConfirmDialog(view,
                    "Are you sure you want to exit the terminal application?",
                    "Exit?",
                    JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
            if(res == JOptionPane.YES_OPTION) {
                stop(true, true);
                shutdown(true);
            }
        }
        else {
            stop(true, true);
            shutdown(true);
        }
    }


    public synchronized void serverConnected(boolean accepted) {
        if(accepted) {
            log.info("Connected to server "+serverSocketAddr);
            state = CLIENT_STATE_CONNECTED;
            dbgListThreads();
            // Wait for control start to create visuals
            log.debug("Waiting for start session packet from server");
        }
        else {
            if(reconnectPrintCount>0) {
                log.info("Unable to connect to server");
            }
            if(reconnectPrintCount>=0) {
                reconnectPrintCount--;
            }
            if(timer!=null) {
                if(reconnectPrintCount>0) log.info("Reconnecting in "+(DELAY_RECONNECT/1000)+" seconds...");
                timer.schedule(new TimerTask() {
                    public void run() {
                        if(networkManager!=null) {
                            if(reconnectPrintCount>0) log.info("Connecting to server...");
                            networkManager.serverConnect();
                        }
                    }
                }, DELAY_RECONNECT);
                if(reconnectPrintCount==0) {
                    log.info("Entering silent mode, still trying to connect to server...");
                    reconnectPrintCount=-1;
                }
            }
            else {
                log.error("Unexpected shutdown, timer is null!");
                shutdown(true);
            }
        }
    }

    public synchronized void serverDisconnected() {
        networkManager.setNetworkHandler(null);

        // Close peripheral link
        if(peripheralLink!=null)
            peripheralLink.stopModule();

        // Close sound clips
        clipRingTone.close();
        clipRingBack.close();
        clipRingBusy.close();
        clipWarning.close();
        clipNotify.close();
        SoundClip.closeCommonTimer();
        outputMixer.close();
        inputMixer.close();
        volumeClipList.clear();

        state = CLIENT_STATE_UNINITIALIZED;
        clear();

        // Notify shutdown hook
        notifyAll();
    }

    public synchronized void sessionStart() {
        if(state<CLIENT_STATE_STARTED && timer!=null) {
            log.debug("Start session packet received");
            timer.schedule(new TimerTask() {
                public void run() {
                    start();
                }
            }, DELAY_START + (int)(2*Math.random()*DELAY_START_VARIANCE)-DELAY_START_VARIANCE);
        }
        else if (state==CLIENT_STATE_PAUSED) {
        	log.debug("Start session packet received (resume from pause)");
        	pause();        	
        }
    }

    public synchronized void sessionStop() {
        if(state>=CLIENT_STATE_STARTED) {
            log.debug("Stop session packet received");
            stop(true, false);
        }
   }

    public synchronized void sessionClose() {
        log.debug("Close session packet received");
        // Ok to call stop and shutdown directly after each other here
        // since the server is stopping anyway.. No synchronization
        // problems then..
                stop(false, false);
                shutdown(timer==null);
        if(timer!=null) {
            log.info("Reconnecting in "+(DELAY_RECONNECT/1000)+" seconds...");
            timer.schedule(new TimerTask() {
                public void run() {
                    int id = properties.getTerminalId();
                    init(id);
                }
            }, DELAY_RECONNECT);
        }
    }
    
    public synchronized void sessionPause() {
        if(state==CLIENT_STATE_STARTED) {
            log.debug("Pause session packet received");
            pause();
        }
   }    

    public synchronized void sessionConnected() {
    }

    public synchronized void sessionDisconnected() {
        bundle.clear();
        semChannelsLeft = true;
        notifyAll();
    }

    public synchronized void infoPacket(Packet packet) {

        // Update role activity
        int[] roleActivityArray = packet.getAttributeList(Packet.ATTR_ROLE_ACTIVITY);
        if(state>=CLIENT_STATE_STARTED && model!=null && roleActivityArray!=null) {
            log.debug("Received info packet ROLE_ACTIVITY");
            // Update all peers (online and in call info)
            Iterator iter1 = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter1.hasNext()) {
                Element er = (Element)iter1.next();
                Iterator iter2 = er.getChild("PhonePeers").getChildren().iterator();
                while(iter2.hasNext()) {
                    Element err = (Element)iter2.next();
                    int id = DomTools.getAttributeInt(err,"id",0,false);
                    for(int i : roleActivityArray) {
                        if((i&Packet.ATTR_ROLE_ACTIVITY_MASK_ID)==id) {
                            err.setAttribute("active", String.valueOf((i&Packet.ATTR_ROLE_ACTIVITY_FLAG_AVAILABLE)!=0));
                            err.setAttribute("line",   ((i&Packet.ATTR_ROLE_ACTIVITY_FLAG_INCALL)!=0)?"3":"1");
                            err.setAttribute("blink", "false");
                        }
                    }
                }
            }

            if(view!=null)
                view.updatePhoneView();
        }

        // Monitoring terminal
        else if(packet.getAttributeBool(Packet.ATTR_MONITOR_SINK_START)) {
            log.debug("Received info packet MONITOR_SINK_START");
            monitoredTerminalId = packet.getSourceId();
            updateRecorder(packet.getAttributeInt(Packet.ATTR_CHANNEL));
            if(view!=null)
                view.updateRadioView();
        }
        else if(packet.getAttributeBool(Packet.ATTR_MONITOR_SINK_STOP)) {
            log.debug("Received info packet MONITOR_SINK_STOP");
            monitoredTerminalId = 0;
            updateRecorder(packet.getAttributeInt(Packet.ATTR_CHANNEL));
            if(view!=null)
                view.updateRadioView();
        }

        // Monitoring terminal
        else if(packet.getAttributeBool(Packet.ATTR_MONITOR_SOURCE_START)) {
            log.debug("Received info packet MONITOR_SOURCE_START");
            monitoringTerminalId = packet.getSourceId();
            monitorChannelId     = packet.getAttributeInt(Packet.ATTR_CHANNEL);
            updateMonitor();
        }
        else if(packet.getAttributeBool(Packet.ATTR_MONITOR_SOURCE_STOP)) {
            log.debug("Received info packet MONITOR_SOURCE_STOP");
            monitoringTerminalId = 0;
            monitorChannelId     = 0;
            updateMonitor();
        }
    }

    public synchronized void radioAcquired(ClientChannel channel, boolean accepted) {
        if(model==null) return;

        boolean startProcessing = false;

        // Re-acquire
        if(radioReacquireCount>0) {
            radioReacquireCount--;

            if(accepted) {

                // Add to acquired list and remove from unacquired list
                radioAcquiredChannelsList.add(channel);
                radioUnacquiredChannelsList.remove(channel);

                // Set busy indicator and clear error indicator on channel
                Element ec = channel.getElement();
                ec.setAttribute("send", "true");
                ec.setAttribute("fail", "false");

                // If all channels are acquired
                if(radioUnacquiredChannelsList.isEmpty()) {
                    startProcessing = true;
                    if(radioReacquireTask!=null) {
                        radioReacquireTask.cancel();
                    }
                }
            }
        }

        // Normal acquire
        else if(radioAcquireCount>0) {
            radioAcquireCount--;

            if(accepted) {
                radioAcquiredChannelsList.add(channel);

                // Set busy indicator on channel
                channel.getElement().setAttribute("send", "true");
            }
            else {
                radioUnacquiredChannelsList.add(channel);

                // Set error indicator
                channel.getElement().setAttribute("fail", "true");
            }

            // All channels have been acquired
            if(radioAcquireCount==0) {
                if(radioUnacquiredChannelsList.isEmpty())
                    startProcessing = true;

                else {
                    // Talk button is pressed with error, show fail
                    if(radioState==RADIO_STATE_TALK_MOUSE||radioState==RADIO_STATE_TALK_FTSW) {
                        model.getRootElement().getChild("TalkButton").setAttribute("state", "fail");
                    }

                    if(!radioUnacquiredChannelsList.isEmpty()) {

                        // Create a new task to periodically try to acquire channels
                        radioReacquireTask = new TimerTask() {
                            public void run() {
                                Controller c = Controller.getInstance();
                                synchronized(c) {
                                    // Dont reacquire if channels are being released
                                    // or if still trying to reacquire from last tick
                                    if(radioReleaseCount>0 || radioReacquireCount>0)
                                        return;

                                    radioReacquireCount = 0;
                                    Iterator<ClientChannel> iter = radioUnacquiredChannelsList.iterator();
                                    while(iter.hasNext()) {
                                        ClientChannel channel = iter.next();
                                        radioReacquireCount++;
                                        networkManager.radioAcquire(channel.getId());
                                    }
                                }
                            }
                        };

                        // Schedule the task
                        if(timer!=null)
                            timer.schedule(radioReacquireTask, PERIOD_REACQUIRE, PERIOD_REACQUIRE);
                        else
                            log.error("Unable to schedule reacquire task, timer is not available!");
                    }
                    else
                        log.error("Radio acquirement failed but there are no unacquired channels in list!");

                    // Audio error signal
                    if(clipWarningOn)
                        clipWarning.playOnce();

                    radioEncoding = false;

                    updateMonitor();
                }
            }
        }

        // Error
        else {
            log.error("radioAcquired was called without (re)acquire count");
        }

        if(startProcessing) {

            if(radioEncoding)
                log.error("Trying to start encoder when radioEncoding is already true!");

            if(radioAcquiredChannelsList.isEmpty())
                log.error("Trying to start encoder without any acquired channels!");

            if(!radioUnacquiredChannelsList.isEmpty())
                log.error("Trying to start encoder with remaining unacquired channels!");

            // Talk button is pressed successfully, show busy
            if(radioState==RADIO_STATE_TALK_MOUSE||radioState==RADIO_STATE_TALK_FTSW) {
                model.getRootElement().getChild("TalkButton").setAttribute("state", "send");
            }

            // Prepare channels list for peripheral link messages
            int i=0;
            int[] channelIds = new int[radioAcquiredChannelsList.size()];

            // Add all acquired channels to distributor
            Iterator<ClientChannel> iter = radioAcquiredChannelsList.iterator();
            while(iter.hasNext()) {
                int channelId = iter.next().getId();
                channelIds[i] = channelId;
                distributor.addRadioChannel(channelId);
                i++;
            }

            // Start sending voice packets, start encoder if phone has not already started it
            if(!phoneActive)
                encoder.startProcessing();

            // Send message to peripheral
            if(peripheralLink!=null)
                peripheralLink.PostRadioSendStart(channelIds);

            radioEncoding = true;
            updateMonitor();
        }

        if(view!=null)
            view.updateRadioView();

        notifyAll();
    }

    public synchronized void radioReleased(ClientChannel channel) {
        radioReleaseCount--;
        notifyAll();
        radioIdleTime = System.currentTimeMillis();
    }

    public synchronized void phoneRingOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId, boolean accepted) {
        if(model==null) return;

        if(phoneState==PHONE_STATE_DIALING_REQ) {
            if(accepted) {
                // Destination phone is ringing
                updateLocalRoleActivity(sourceRoleId, destRoleId, true, true);
                playSound(SOUND_RINGBACK);
                phoneState = PHONE_STATE_DIALING;
            }
            else {
                // Phone line was busy
                Element eh = model.getRootElement().getChild("HookButton");
                eh.setAttribute("state", "busy");
                if(view!=null)
                    view.updatePhoneView();
                playSound(SOUND_RINGBUSY);
                phoneState = PHONE_STATE_BUSY;
            }
        }
        else {
            log.error("phoneRingOutgoing called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void phoneRingIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId) {
        if(model==null) return;

        if(state<CLIENT_STATE_STARTED) return;
        if(phoneState==PHONE_STATE_IDLE||phoneState==PHONE_STATE_BUSY) {
            Element eh = model.getRootElement().getChild("HookButton");
            eh.setAttribute("src", getRoleName(sourceRoleId));
            eh.setAttribute("dest", getRoleName(destRoleId));
            eh.setAttribute("this", String.valueOf(destRoleId));
            eh.setAttribute("other", String.valueOf(sourceRoleId));
            eh.setAttribute("state", "ringing");
            updateLocalRoleActivity(destRoleId, sourceRoleId, true, true);
            playSound(SOUND_RINGTONE);
            phoneState = PHONE_STATE_RINGING;
        }
        else {
            log.error("phoneRingIncoming called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void phoneAnswerOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId, boolean accepted) {
        if(model==null) return;

        if(phoneState==PHONE_STATE_ANSWER_REQ) {
            Element eh = model.getRootElement().getChild("HookButton");
            eh.setAttribute("state", "in_call");
            updateLocalRoleActivity(sourceRoleId, destRoleId, true, false);
            playSound(SOUND_NONE);
            phoneState = PHONE_STATE_IN_CALL;
            if(accepted) {
                if(destTerminal!=null) {
                    packetReceiver.openChannel(CHANNEL_PHONE);
                    packetReceiver.openChannelForward(destTerminal);
                    phoneActive = true;

                    distributor.setConfinedRecipient(destTerminal, CHANNEL_PHONE);
                    if(!radioEncoding)
                        encoder.startProcessing();

                    adjustVolumes();
                    updateMonitor();
                }
                else log.warn("destTerminal is null in phoneAnswerOutgoing!");
            }
            else {
                // Hang up phone
                hookButtonClicked();
            }
        }
        else {
            log.error("phoneAnswerOutgoing called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void phoneAnswerIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId) {
        if(model==null) return;

        if(state<CLIENT_STATE_STARTED) return;
        if(phoneState==PHONE_STATE_DIALING) {
            Element eh = model.getRootElement().getChild("HookButton");
            eh.setAttribute("state", "in_call");
            updateLocalRoleActivity(destRoleId, sourceRoleId, true, false);
            playSound(SOUND_NONE);
            if(sourceTerminal!=null) {
                packetReceiver.openChannel(CHANNEL_PHONE);
                packetReceiver.openChannelForward(sourceTerminal);
                phoneActive = true;

                distributor.setConfinedRecipient(sourceTerminal, CHANNEL_PHONE);
                if(!radioEncoding)
                    encoder.startProcessing();

                adjustVolumes();
                updateMonitor();
            }
            else log.error("sourceTerminal is null in phoneAnswerIncoming");

            phoneState = PHONE_STATE_IN_CALL;
        }
        else if(phoneState==PHONE_STATE_HANGUP_REQ) {
            // Other peer answered the phone when this peer requested to hangup
        }
        else {
            log.error("phoneAnswerIncoming called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void phoneHangupOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId) {
        if(model==null) return;

        if(phoneState==PHONE_STATE_HANGUP_REQ ||
                        phoneState==PHONE_STATE_DIALING_REQ) {  // It is possible for receiver to dial another phone instead of answer a ring.
                                                                                                        // If so - send hangup to "dissed" dialer and keep own phone state PHONE_STATE_DIALING_REQ
            updateLocalRoleActivity(sourceRoleId, destRoleId, false, false);
            playSound(SOUND_NONE);
            stopPhone();
            if (phoneState != PHONE_STATE_DIALING_REQ) {
                phoneState = PHONE_STATE_IDLE;
            }
        }
        else {
            log.error("phoneHangupOutgoing called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void phoneHangupIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId) {
        if(model==null) return;

        if(state<CLIENT_STATE_STARTED) return;
        if(phoneState==PHONE_STATE_IN_CALL||phoneState==PHONE_STATE_RINGING||phoneState==PHONE_STATE_DIALING) {
            Element eh = model.getRootElement().getChild("HookButton");
            eh.setAttribute("state", "idle");
            updateLocalRoleActivity(destRoleId, sourceRoleId, false, false);
            playSound(SOUND_NONE);
            stopPhone();
            phoneState = PHONE_STATE_IDLE;
        }
        else if(phoneState==PHONE_STATE_IDLE) {
            // Both peers hung up at the same time, already back in idle state
        }
        else {
            log.error("phoneHangupIncoming called in wrong state ["+phoneState+"]");
        }
    }

    public synchronized void timeout() {
        log.error("Unexpected timeout has occured, disconnected from server!");
        stop(false, false);
        shutdown(timer==null);
        if(timer!=null) {
            log.info("Reconnecting in "+(DELAY_RECONNECT/1000)+" seconds...");
            timer.schedule(new TimerTask() {
                public void run() {
                    int id = properties.getTerminalId();
                    init(id);
                }
            }, DELAY_RECONNECT);
        }
    }

    public synchronized void startDecoding(AudioDecoder source, int[] channelIdArray) {

        int decoderId = source.getDecoderId();

        // prefer channel, this channel will always be used before other open channels
        packetReceiver.preferChannel(decoderId);

        // update visuals
        for(int i : channelIdArray) {
            ClientChannel channel = bundle.getChannel(i);
            if(channel!=null) {
                if(channel.getId()==decoderId) {
                    channel.getElement().setAttribute("recvp", "true");
                }
                else {
                    channel.getElement().setAttribute("recvs", "true");
                }
            }
        }

        radioDecodingList.add(source);
        radioDecoding = !radioDecodingList.isEmpty();
        adjustVolumes();
        updateMonitor();

        if(view!=null)
            view.updateRadioView();

    }

    public synchronized void stopDecoding(AudioDecoder source, int[] channelIdArray) {

        int decoderId = source.getDecoderId();

        // unprefer channel
        packetReceiver.unpreferChannel(decoderId);

        // update visuals
        for(int i : channelIdArray) {
            ClientChannel channel = bundle.getChannel(i);
            if(channel!=null) {
                if(channel.getId()==decoderId) {
                    channel.getElement().setAttribute("recvp", "false");
                }
                else {
                    channel.getElement().setAttribute("recvs", "false");
                }
            }
        }

        radioDecodingList.remove(source);
        radioDecoding = !radioDecodingList.isEmpty();
        adjustVolumes();
        updateMonitor();

        if(view!=null)
            view.updateRadioView();
        radioIdleTime = System.currentTimeMillis();
    }

}
