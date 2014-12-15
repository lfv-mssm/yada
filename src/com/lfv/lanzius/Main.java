package com.lfv.lanzius;

import com.lfv.yada.net.Packet;
import com.lfv.lanzius.server.LanziusServer;
import com.lfv.lanzius.application.Controller;
import com.lfv.lanzius.application.FootSwitchController;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * <p>
 * The entry point for Yada 2, Lanzius. Start with -help switch for usage
 * information.
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.0 (Lanzius)
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
public class Main {

    public static void main(String[] args) {

        /*
         * debug levels:
         * error - Runtime errors or unexpected conditions. Expect these to be immediately visible on a status console. See also Internationalization.
         * warn  - Use of deprecated APIs, poor use of API, 'almost' errors, other runtime situations that are undesirable or unexpected, but not necessarily "wrong". Expect these to be immediately visible on a status console. See also Internationalization.
         * info  - Interesting runtime events (startup/shutdown). Expect these to be immediately visible on a console, so be conservative and keep to a minimum. See also Internationalization.
         * debug - detailed information on the flow through the system. Expect these to be written to logs only.
         */

        Log log = null;
        DOMConfigurator conf = new DOMConfigurator();
        DOMConfigurator.configure("data/properties/loggingproperties.xml");
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Log4JLogger");

        try {
            if(args.length>=1) {
                // Help
                if(args[0].startsWith("-h")) {
                    printUsage();
                    return;
                }
                // List devices
                else if(args[0].startsWith("-l")) {
                    AudioTest.listDevices();
                    return;
                }
                // Test seleted device
                else if(args[0].startsWith("-d")) {
                    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
                    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "warn");
                    try {
                        String option = "all";
                        if(args.length>=4)
                            option = args[3];

                        if(option.equals("loop:direct"))
                            AudioTest.testDevicesDirect(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                        else {
                            if(option.indexOf("debug")!=-1)
                                System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "debug");

                            AudioTest.testDevices(
                                    Integer.parseInt(args[1]),
                                    Integer.parseInt(args[2]),
                                    option,
                                    (args.length>=5)?args[4]:null,
                                    (args.length>=6)?args[5]:null,
                                    (args.length>=7)?args[6]:null);
                        }
                    } catch(Exception ex) {
                        System.out.println();
                        System.out.println(Config.VERSION);
                        System.out.println();
                        System.out.println("Usage:");
                        System.out.println("  yada.jar -d output_device input_device <option> <jitter_buffer> <output_buffer> <input_buffer>");
                        System.out.println("  option:");
                        System.out.println("    all(default)");
                        System.out.println("    clip");
                        System.out.println("    loop:jspeex");
                        System.out.println("    loop:null");
                        System.out.println("    loop:direct");
                        System.out.println("    loopdebug:jspeex");
                        System.out.println("    loopdebug:null");
                        System.out.println("  jitter_buffer:");
                        System.out.println("    size of jitter buffer in packets (1-20)");
                        System.out.println("  output_buffer:");
                        System.out.println("    size of output buffer in packets (1.0-4.0)");
                        System.out.println("  input_buffer:");
                        System.out.println("    size of input buffer in packets (1.0-4.0)");
                        System.out.println();
                        if(AudioTest.showStackTrace && !(ex instanceof ArrayIndexOutOfBoundsException))
                            ex.printStackTrace();
                    }
                    //System.out.println("Exiting...");
                    return;
                }
                else if(args[0].startsWith("-m")) {
                    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
                    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "warn");
                    try {
                        AudioTest.testMicrophoneLevel(Integer.parseInt(args[1]));
                    } catch(Exception ex) {
                        System.out.println();
                        System.out.println(Config.VERSION);
                        System.out.println();
                        System.out.println("Usage:");
                        System.out.println("  yada.jar -m input_device");
                        System.out.println();
                        if(AudioTest.showStackTrace && !(ex instanceof ArrayIndexOutOfBoundsException))
                            ex.printStackTrace();
                    }
                    return;
                }
                else if(args[0].startsWith("-s")) {
                        Packet.randomSeed = 9182736455L^System.currentTimeMillis();
                        if(args.length > 2 && args[1].startsWith("-configuration")) {
                                String configFilename = args[2];
                                if(args.length > 4 && args[3].startsWith("-exercise")) {
                                String exerciseFilename = args[4];

                                LanziusServer.start(configFilename, exerciseFilename);
                        } else {
                                LanziusServer.start(configFilename);
                        }
                        } else {
                                LanziusServer.start();
                        }
                    return;
                }
                else if(args[0].startsWith("-f")) {
                    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
                    System.setProperty("org.apache.commons.logging.simplelog.log.com.lfv.lanzius.application.FootSwitchController", "debug");
                    try {
                        System.out.println("Starting footswitch controller test using device "+args[1]);
                        try {
                            boolean inverted = false;
                            if(args.length>=3)
                                inverted = args[2].toLowerCase().startsWith("inv");
                            FootSwitchController c = new FootSwitchController(null, args[1], Constants.PERIOD_FTSW_CONNECTED, inverted);
                            c.start();
                            Thread.sleep(30000);
                        } catch(UnsatisfiedLinkError err) {
                            if(args.length>1)
                                System.out.println("UnsatisfiedLinkError: "+err.getMessage());
                            else throw new ArrayIndexOutOfBoundsException();
                            System.out.println("Missing ftsw library (ftsw.dll or libftsw.so)");
                            if(!args[1].equalsIgnoreCase("ftdi"))
                                System.out.println("Missing rxtxSerial library (rxtxSerial.dll or librxtxSerial.so)");
                            if(AudioTest.showStackTrace)
                                err.printStackTrace();
                        }
                    } catch(Exception ex) {
                        if(ex instanceof NoSuchPortException)
                            System.out.println("The serial port "+args[1]+" does not exist!");
                        else if(ex instanceof PortInUseException)
                            System.out.println("The serial port "+args[1]+" is already in use!");
                        System.out.println();
                        System.out.println(Config.VERSION);
                        System.out.println();
                        System.out.println("Usage:");
                        System.out.println("  yada.jar -f interface <invert>");
                        System.out.println("  interface:");
                        System.out.println("    ftdi");
                        System.out.println("    comport (COMx or /dev/ttyx)");
                        System.out.println("  invert:");
                        System.out.println("    true");
                        System.out.println("    false (default)");
                        System.out.println();
                        if(AudioTest.showStackTrace && !(ex instanceof ArrayIndexOutOfBoundsException))
                            ex.printStackTrace();
                    }
                    return;
                }
                else if(args[0].startsWith("-c")) {
                    Packet.randomSeed = 7233103157L^System.currentTimeMillis();
                    if(args.length>=2) {
                        try {
                            int id = Integer.valueOf(args[1]);
                            if(id<=0) throw new NumberFormatException();
                            Controller c = Controller.getInstance();
                            if(args.length>=3) {
                                if(args[2].equalsIgnoreCase("test")) {
                                    c.setAutoTester(true);
                                }
                            }
                            c.init(id);
                            return;
                        } catch(NumberFormatException ex) {
                            printUsage();
                        }
                    }
                    else {
                        Controller.getInstance().init(0);
                    }
                }
                else
                    printUsage();
            }
            else
                printUsage();
        } catch(Throwable t) {
            if(log==null)
                log = LogFactory.getLog(Main.class);
            log.error("Unhandled exception or error",t);
        }
    }

    private static void printUsage() {
        System.out.println();
        System.out.println(Config.VERSION);
        System.out.println();
        System.out.println("Usage:");
        System.out.println();
        System.out.println("  -h     This page");
        System.out.println("  -s     Start the server");
        System.out.println("  -s -configuration <file>");
        System.out.println("         Start the server, open the given configuration file and start server.");
        System.out.println("  -s -configuration <file1> -exercise <file2>");
        System.out.println("         Start the server, open the given configuration and exercise file and start server.");
        System.out.println("  -c     Start a client terminal");
        System.out.println("  -c id  Start a client terminal with a specified id");
        System.out.println("  -l     List sound devices");
        System.out.println("  -d     Test sound devices");
        System.out.println("  -f     Test the foot switch");
        System.out.println("  -m     Test the microphone level");
        System.out.println();
    }
}
