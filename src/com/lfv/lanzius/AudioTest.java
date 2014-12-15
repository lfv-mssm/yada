package com.lfv.lanzius;

import com.lfv.yada.net.AttributeData;
import com.lfv.yada.net.client.Distributor;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;
import com.arkatay.yada.codec.*;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import javax.swing.JFrame;

/**
 * <p>
 * The audio test class is used to find proper sound input and output devices
 * supporting the audio formats and capabilities needed by the system.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:info@arkatay.com">Andreas Alptun</a>
 * @version Yada 1.0
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
public class AudioTest {

    private static void dbgListThreads() {

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

    public static final boolean showStackTrace = Config.DEBUG;
    public static int   jitterBufferSize = AudioCodecConstants.jitterBufferSizeDef;
    public static float outputBufferSize = AudioCodecConstants.outputBufferSizeDef;
    public static float inputBufferSize  = AudioCodecConstants.inputBufferSizeDef;
    public static VUMeter vum;
    public static double lastRMS;

    // Formats
    private static AudioFormat format1 = new AudioFormat(16000.0f,16,1,true,false);
    private static AudioFormat format2 = new AudioFormat(16000.0f,16,2,true,false);

    // Output
    private static SourceDataLine.Info sdli1 = (SourceDataLine.Info)new DataLine.Info(SourceDataLine.class,format1);
    private static SourceDataLine.Info sdli2 = (SourceDataLine.Info)new DataLine.Info(SourceDataLine.class,format2);

    // Output Clips
    private static Clip.Info cdli1 = (Clip.Info)new DataLine.Info(Clip.class,format1);
    private static Clip.Info cdli2 = (Clip.Info)new DataLine.Info(Clip.class,format2);

    // Input
    private static TargetDataLine.Info tdli1 = (TargetDataLine.Info)new DataLine.Info(TargetDataLine.class,format1);
    private static TargetDataLine.Info tdli2 = (TargetDataLine.Info)new DataLine.Info(TargetDataLine.class,format2);

    public static void setProvider(String providerId) {
    }
    private static Mixer.Info[] getMixerInfo() {
        return AudioSystem.getMixerInfo();
    }
    private static Mixer getMixer(Mixer.Info info) {
        return AudioSystem.getMixer(info);
    }

    public static void listDevices() {

        // Start by showing off all installed sound mixers in the system
        System.out.println();
        System.out.println("Listing devices: ");

        Mixer.Info[] mixerinfo = getMixerInfo();
        for(int i=0;i<mixerinfo.length;i++) {
            System.out.println(i+". Name: "+mixerinfo[i].getName()+", Version: "+mixerinfo[i].getVersion());
            System.out.println("   Desc: "+mixerinfo[i].getDescription()+", Vendor: "+mixerinfo[i].getVendor());

            // For each mixer, check the available source lines
            Line.Info[] lineinfo = getMixer(mixerinfo[i]).getSourceLineInfo();
            for(int j=0;j<lineinfo.length;j++) {
                Line.Info li = lineinfo[j];
                System.out.println("      Output data line: "+li.toString());
                if(!(li instanceof DataLine.Info))
                    continue;
                DataLine.Info dli = (DataLine.Info)li;

                // For each format that this source line supports
                AudioFormat[] af = dli.getFormats();
                for(int k=0;k<af.length;k++) {
                   System.out.println("         Format: "+af[k].toString());
                }
            }

            // For each mixer, check the available target lines
            lineinfo = getMixer(mixerinfo[i]).getTargetLineInfo();
            for(int j=0;j<lineinfo.length;j++) {
                Line.Info li = lineinfo[j];
                System.out.println("      Input data line: "+li.toString());
                if(!(li instanceof DataLine.Info))
                    continue;
                DataLine.Info dli = (DataLine.Info)li;

                // For each format that this source line supports
                AudioFormat[] af = dli.getFormats();
                for(int k=0;k<af.length;k++) {
                   System.out.println("         Format: "+af[k].toString());
                }
            }
        }

        System.out.println();
        System.out.println("Output devices with required capabilities: ");

        // Output data lines. At least one of them must be supported by the audio system
        for(int i=0;i<mixerinfo.length;i++) {

            Mixer mixer = getMixer(mixerinfo[i]);
            if((mixer.isLineSupported(sdli1)||mixer.isLineSupported(sdli2)) && ((mixer.isLineSupported(cdli1)||mixer.isLineSupported(cdli2))) ) {
                System.out.println(i+". Name: "+mixerinfo[i].getName()+", Version: "+mixerinfo[i].getVersion());
                System.out.println("   Desc: "+mixerinfo[i].getDescription()+", Vendor: "+mixerinfo[i].getVendor());
            }
        }

        System.out.println();
        System.out.println("Input devices with required capabilities: ");

        // Output data lines. At least one of them must be supported by the audio system
        for(int i=0;i<mixerinfo.length;i++) {
            Mixer mixer = getMixer(mixerinfo[i]);
            if(mixer.isLineSupported(tdli1)||mixer.isLineSupported(tdli2)) {
                System.out.println(i+". Name: "+mixerinfo[i].getName()+", Version: "+mixerinfo[i].getVersion());
                System.out.println("   Desc: "+mixerinfo[i].getDescription()+", Vendor: "+mixerinfo[i].getVendor());
            }
        }
    }
    public static void testDevices(int outputDeviceIndex, int inputDeviceIndex, String option, String jitterBufferSizeS, String outputBufferSizeS, String inputBufferSizeS) {
        Mixer.Info[] mixerinfo = getMixerInfo();

        System.out.println();
        System.out.println("--- System sound device test ("+option+") ---");
        System.out.println("Output device: "+mixerinfo[outputDeviceIndex].getName());
        System.out.println("Input device: "+mixerinfo[inputDeviceIndex].getName());
        try {
            jitterBufferSize = Integer.parseInt(jitterBufferSizeS);
        } catch(Exception ex) {
            jitterBufferSize = AudioCodecConstants.jitterBufferSizeDef;
        }
        try {
            outputBufferSize = Float.parseFloat(outputBufferSizeS);
        } catch(Exception ex) {
            outputBufferSize = AudioCodecConstants.outputBufferSizeDef;
        }
        try {
            inputBufferSize = Float.parseFloat(inputBufferSizeS);
        } catch(Exception ex) {
            inputBufferSize = AudioCodecConstants.inputBufferSizeDef;
        }

        jitterBufferSize = AudioDecoder.setJitterBufferSize(jitterBufferSize);
        outputBufferSize = AudioDecoder.setOutputBufferSize(outputBufferSize);
        inputBufferSize  = AudioEncoder.setInputBufferSize(inputBufferSize);

        System.out.print("Configuration: codec ");
        if(option.endsWith("jspeex"))
            System.out.print("jspeex");
        else
            System.out.print("null");
        System.out.print(", jitter "+jitterBufferSize);
        System.out.print(", output "+outputBufferSize);
        System.out.println(", input " +inputBufferSize);

        System.out.println();

        Mixer outputMixer = getMixer(mixerinfo[outputDeviceIndex]);
        if(option.equals("all")||option.equals("clip")) {
            boolean clipUsingStereo=false;
            Clip c1 = null;
            Clip c2 = null;
            System.out.print("Trying to open a mono line for the sound clips...");
            try {
                 c1 = (Clip)outputMixer.getLine(cdli1);
                 System.out.println("[Success]");
            } catch (Exception ex) {
                System.out.println("[Failed]");
                System.out.print("Could not open a mono line, trying with a stereo line...");
                try {
                    c1 = (Clip)outputMixer.getLine(cdli2);
                    clipUsingStereo = true;
                    System.out.println("[Success]");
                } catch(Exception ex2) {
                    System.out.println("[Failed]");
                    System.out.println("Could not open a stereo line, test failed!");
                    if(showStackTrace) ex.printStackTrace();
                    if(showStackTrace) ex2.printStackTrace();
                }
            }

            System.out.print("Trying to open another output line for testing mixing capabilities...");
            try {
                if(clipUsingStereo)
                    c2 = (Clip)outputMixer.getLine(cdli2);
                else
                    c2 = (Clip)outputMixer.getLine(cdli1);
                System.out.println("[Success]");
            } catch (Exception ex) {
                System.out.println("[Failed]");
                System.out.println("Could not open multiple output lines, test failed!");
                if(showStackTrace) ex.printStackTrace();
            }

            System.out.print("Trying to load audio sound clips...");
            AudioInputStream ais1 = null;
            AudioInputStream ais2 = null;
            try {
                if(clipUsingStereo) {
                    ais1 = AudioSystem.getAudioInputStream(new File("data/resources/sounds/phone_ringtone_stereo.wav"));
                    ais2 = AudioSystem.getAudioInputStream(new File("data/resources/sounds/phone_ringback_stereo.wav"));
                }
                else {
                    ais1 = AudioSystem.getAudioInputStream(new File("data/resources/sounds/phone_ringtone_mono.wav"));
                    ais2 = AudioSystem.getAudioInputStream(new File("data/resources/sounds/phone_ringback_mono.wav"));
                }
                System.out.println("[Success]");
            } catch (Exception ex) {
                System.out.println("[Failed]");
                if(showStackTrace) ex.printStackTrace();
            }

            System.out.print("Trying to play a single sound clip...");
            try {
                c1.open(ais1);
                c1.setFramePosition(0);
                c1.start();
                try {
                    Thread.sleep(2250);
                } catch (InterruptedException ex) {
                    if(showStackTrace) ex.printStackTrace();
                }
                c1.stop();
                c1.setFramePosition(0);
                System.out.println("[Success]");
            } catch (Exception ex) {
                System.out.println("[Failed]");
                System.out.println("Could open sound clip, output device test failed!");
                if(showStackTrace) ex.printStackTrace();
            }

            System.out.print("Trying to play two sound clips for testing mixing capabilities...");
            try {
                c2.open(ais2);
                c1.start();
                c2.start();
                try {
                    Thread.sleep(2250);
                } catch (InterruptedException ex) {
                    if(showStackTrace) ex.printStackTrace();
                }
                c1.stop();
                c2.stop();
                System.out.println("[Success]");
            } catch (Exception ex) {
                System.out.println("[Failed]");
                System.out.println("Could not play two sounds - no mixing capabilites, output device test failed!");
                if(showStackTrace) ex.printStackTrace();
            }

            // Close up...
            if(c1!=null)
                c1.close();
            if(c2!=null)
                c2.close();
            try {
                if(ais1!=null)
                    ais1.close();
                if(ais2!=null)
                    ais2.close();
            } catch (IOException ex) {
                if(showStackTrace) ex.printStackTrace();
            }
        }

        Mixer inputMixer = getMixer(mixerinfo[inputDeviceIndex]);
        if(option.equals("all")||option.startsWith("loop")) {
            boolean dbg = (option.indexOf("debug")!=-1);

            AudioDecoder dec = null;
            AudioEncoder enc = null;
            if(option.endsWith("jspeex")) {
                dec = new JSpeexDecoder(outputMixer, 1);
                enc = new JSpeexEncoder(inputMixer, 7);
            }
            else {
                dec = new NullDecoder(outputMixer, 1);
                enc = new NullEncoder(inputMixer);
            }

            enc.setPacketDistributor(new Distributor(0,null,dec) {
                @Override
                public void distributePacket(Packet packet) {
                    packet.complete();
                    if(!((AudioDecoder)dispatcher).postItem(packet))
                        PacketPool.getPool().returnPacket(packet);
                }
            });

            System.out.print("Trying to open a mono input line for testing full duplex capabilities...");
            if(dbg) System.out.println();
            try {
                enc.startModule(0);
                System.out.println("[Success]");
            } catch(Exception ex) {
                System.out.println("[Failed]");
                System.out.print("Could not open a mono line, trying with a stereo line...");
                if(dbg) System.out.println();
                try {
                    enc.startModule(1);
                    System.out.println("[Success]");
                } catch (Exception ex2) {
                    System.out.println("[Failed]");
                    System.out.println("Could not open a stereo line, test failed!");
                    if(showStackTrace) ex.printStackTrace();
                    if(showStackTrace) ex2.printStackTrace();
                    return;
                }
            }

            System.out.print("Trying to open a mono output line...");
            if(dbg) System.out.println();
            try {
                dec.startModule(0);
                System.out.println("[Success]");
            } catch(Exception ex) {
                System.out.println("[Failed]");
                System.out.print("Could not open a mono line, trying with a stereo line...");
                if(dbg) System.out.println();
                try {
                    dec.startModule(1);
                    System.out.println("[Success]");
                } catch (Exception ex2) {
                    System.out.println("[Failed]");
                    System.out.println("Could not open a stereo line, test failed!");
                    if(showStackTrace) ex.printStackTrace();
                    if(showStackTrace) ex2.printStackTrace();
                    return;
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                if(showStackTrace) ex.printStackTrace();
            }

            enc.startProcessing();
            System.out.print("The loopback line is open for 8 seconds, try speaking into the microphone...");
            if(dbg) System.out.println();
            System.out.flush();
            try {
                Thread.sleep(8000);
            } catch (InterruptedException ex) {
                if(showStackTrace) ex.printStackTrace();
            }
            System.out.println("[Done]");
            enc.stopProcessing();
            System.out.print("Pausing encoder to check decoder re-init...");
            if(dbg) System.out.println();
            System.out.flush();
            try {
                Thread.sleep(750);
            } catch (InterruptedException ex) {
                if(showStackTrace) ex.printStackTrace();
            }
            System.out.println("[Done]");
            enc.startProcessing();
            System.out.print("The loopback line is open for another 8 seconds, try speaking into the microphone...");
            if(dbg) System.out.println();
            System.out.flush();
            try {
                Thread.sleep(8000);
            } catch (InterruptedException ex) {
                if(showStackTrace) ex.printStackTrace();
            }
            System.out.println("[Done]");
            enc.stopProcessing();

            enc.stopModule();
            dec.stopModule();
        }
    }
    public static void testDevicesDirect(int outputDeviceIndex, int inputDeviceIndex) {
        Mixer.Info[] mixerinfo = getMixerInfo();

        System.out.println();
        System.out.println("--- System sound device test (loop:direct) ---");
        System.out.println("Output device: "+mixerinfo[outputDeviceIndex].getName());
        System.out.println("Input device: "+mixerinfo[inputDeviceIndex].getName());
        System.out.println();

        Mixer outputMixer = getMixer(mixerinfo[outputDeviceIndex]);
        Mixer inputMixer = getMixer(mixerinfo[inputDeviceIndex]);

        boolean targetUsingStereo=false;
        TargetDataLine tdl = null;
        System.out.print("Trying to open a mono line for voice input...");
        try {
            tdl = (TargetDataLine)inputMixer.getLine(tdli1);
            System.out.println("[Success]");
        } catch (Exception ex) {
            System.out.println("[Failed]");
            System.out.print("Could not open a mono line, trying with a stereo line...");
            try {
                tdl = (TargetDataLine)inputMixer.getLine(tdli2);
                targetUsingStereo=true;
                System.out.println("[Success]");
            } catch (Exception ex2) {
                System.out.println("[Failed]");
                System.out.println("Could not open a stereo line, test failed!");
                if(showStackTrace) ex.printStackTrace();
                if(showStackTrace) ex2.printStackTrace();
            }
        }

        boolean sourceUsingStereo=false;
        SourceDataLine sdl = null;
        System.out.print("Trying to open a mono line for voice samples...");
        try {
             sdl = (SourceDataLine)outputMixer.getLine(sdli1);
             System.out.println("[Success]");
        } catch (Exception ex) {
            System.out.println("[Failed]");
            System.out.print("Could not open a mono line, trying with a stereo line...");
            try {
                sdl = (SourceDataLine)outputMixer.getLine(sdli2);
                sourceUsingStereo = true;
                System.out.println("[Success]");
            } catch (Exception ex2) {
                System.out.println("[Failed]");
                System.out.println("Could not open a stereo line, test failed!");
                if(showStackTrace) ex.printStackTrace();
                if(showStackTrace) ex2.printStackTrace();
            }
        }

        System.out.print("Trying to open input and output lines...");
        try {
            if(sourceUsingStereo)
                sdl.open(format2,2*4*640);
            else
                sdl.open(format1,4*640);

            if(targetUsingStereo)
                tdl.open(format2,2*4*640);
            else
                tdl.open(format1,4*640);
            System.out.println("[Success]");
        } catch(LineUnavailableException ex) {
            System.out.println("[Failed]");
            System.out.println("Could not open lines, test failed!");
            if(showStackTrace) ex.printStackTrace();
        }
        System.out.println("Input line buffer size: "+tdl.getBufferSize());
        System.out.println("Output line buffer size: "+sdl.getBufferSize());

        sdl.start();
        tdl.start();

        byte[] b = new byte[640];
        System.out.print("The loopback line is open for 10 seconds, try speaking into the microphone...");
        System.out.flush();
        long startTime = System.currentTimeMillis();
        while(System.currentTimeMillis()-startTime<10000) {
            int j = tdl.read(b,0,640);
            sdl.write(b,0,j);
        }
        System.out.println("[Done]");

        tdl.stop();
        sdl.stop();

        // Close up...
        if(sdl!=null)
            sdl.close();
        if(tdl!=null)
            tdl.close();
    }
    public static void testMicrophoneLevel(int inputDeviceIndex) {
        Mixer.Info[] mixerinfo = getMixerInfo();

        if(inputDeviceIndex>=mixerinfo.length||inputDeviceIndex<0) {
            System.out.println("Invalid device!");
            return;
        }

        System.out.println();
        System.out.println("Testing microphone level using input device: "+mixerinfo[inputDeviceIndex].getName());
        Mixer inputMixer = getMixer(mixerinfo[inputDeviceIndex]);

        AudioEncoder enc = new NullEncoder(inputMixer);
        enc.setPacketDistributor(new Distributor(0,null,null) {
            @Override
            public void distributePacket(Packet packet) {

                AttributeData data = packet.getAttributeData();

                // Calculate RMS
                if(data!=null) {
                    byte[] buf = data.getBuffer();
                    int length = data.getLength()/2; // in shorts
                    int i      = data.getOffset();
                    double currRMS = 0;
                    for(int j=0;j<length;j++) {
                        // Get the 16 bit mono sample and convert to a double (+-1)
                        double smpd = ((((int)buf[i+1])<<8)|(((int)buf[i+0])&0xff))/32768.0;
                        currRMS += smpd*smpd;
                        i+=2;
                    }
                    currRMS = Math.sqrt(currRMS/length);

                    // Some simple low pass iir filtering
                    if(currRMS>lastRMS)
                        currRMS = lastRMS*0.4 + currRMS*0.6;
                    else
                        currRMS = lastRMS*0.95 + currRMS*0.05;
                    if(vum!=null)
                        vum.setValue((int)(currRMS*100));
                    lastRMS = currRMS;
                }

                PacketPool.getPool().returnPacket(packet);
            }
        });

        System.out.print("Trying to open a mono input line for testing microphone level...");
        try {
            enc.startModule(0);
            System.out.println("[Success]");
        } catch(Exception ex) {
            System.out.println("[Failed]");
            System.out.print("Could not open a mono line, trying with a stereo line...");
            try {
                enc.startModule(1);
                System.out.println("[Success]");
            } catch (Exception ex2) {
                System.out.println("[Failed]");
                System.out.println("Could not open a stereo line, test failed!");
                if(showStackTrace) ex.printStackTrace();
                if(showStackTrace) ex2.printStackTrace();
                return;
            }
        }

        JFrame.setDefaultLookAndFeelDecorated(true);
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                vum = new VUMeter();
                vum.setVisible(true);
            }
        });

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            if(showStackTrace) ex.printStackTrace();
        }

        enc.startProcessing();

        // This function will block until vu-meter frame is closed
        while(vum==null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                if(showStackTrace) ex.printStackTrace();
            }
        }
        vum.waitStopped();

        System.out.print("Stopping microphone level test...");
        enc.stopProcessing();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            if(showStackTrace) ex.printStackTrace();
        }
        enc.stopModule();
        System.out.println("[Done]");
        PacketPool.getPool().printInfoMessage();
    }
}
