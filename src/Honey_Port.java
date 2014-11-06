/*
 * Copyright (C) 2014 Jack L (http://jack-l.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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

/**
 *
 * @author Jack L (http://jack-l.com)
 */

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class Honey_Port {

    //Console Color Code
    public static final String C_RESET = "\u001B[0m";
    public static final String C_BLACK = "\u001B[30m";
    public static final String C_RED = "\u001B[31m";
    public static final String C_GREEN = "\u001B[32m";
    public static final String C_YELLOW = "\u001B[33m";
    public static final String C_BLUE = "\u001B[34m";
    public static final String C_PURPLE = "\u001B[35m";
    public static final String C_CYAN = "\u001B[36m";
    public static final String C_WHITE = "\u001B[37m";

    //Program Settings
    /**
     * Version - Identify current program version
     */
    public static final double Version = 0.1;

    /**
     * UseColorCode - Display message with color or not
     */
    public static boolean UseColorCode = false;

    /**
     * DebugLevel - Controls how much debug message is being output and log 0000
     * (0x0) - OFF 0001 (0x1) - General 0010 (0x2) - Detailed
     */
    public static byte DebugLevel = 0x1;

    /**
     * Variable: LogToFile - Log detections to file
     */
    public static boolean LogToFile = false;

    /**
     * Variable: BanCmd - Command to execute during a detection Use OFF to
     * disable ban feature
     */
    public static String BanCmd = "OFF";

    /**
     * Variable: UnbanCmd - Command to execute during a detection Use OFF to
     * disable unban feature
     */
    public static String UnbanCmd = "OFF";

    /**
     * Variable: BanLength - Time in seconds to keep the IP banned Use 0 to ban
     * permanently
     */
    public static int BanLength = 0;

    public static int PortRangeStart = -1;
    public static int PortRangeEnd = 0;

    public static int SpecifiedPortsCount = 0;
    public static int[] SpecifiedPorts;

    public static int ExcludedPortsCount = 0;
    public static int[] ExcludedPorts;

    public static int IPWhiteListCount = 0;
    public static String[] IPWhiteList;

    // Run time variables
    // Time variable
    public static Date CurrentDate = new Date();
    // Shutdown signal for threads
    private static boolean Shutdown = false;
    // The following three variables should have the same index as primary key.
    private static Thread[] PortsListenerThreads;
    private static ServerSocket[] PortSockets;
    private static int[] ThreadPortNum;
    //
    private static Semaphore BanMutex = new Semaphore(1);
    //
    private static int TotalPortsCount;
    private static int DetectionCount = 0;
    // Log file writer
    private static PrintWriter LogFileWriter;
    // Queue for banned IPs
    private static Queue BannedIP;

    /**
     * ********************************************************************
     * General Functions
     * ********************************************************************
     */
    /**
     * Function: PrintMsg - Display message to user console
     *
     * @param MsgType - Type of Message 0000_0000 (0x0) - Normal Message
     * 0000_0001 (0x1) - Warning Message 0000_0010 (0x2) - Error Message
     * 0000_0100 (0x4) - Detection Message 0001_0000 (0x10) - 0011_0000 (0x30) -
     * Debug Message, corresponding to level
     * @param Msg - Message to display
     */
    public static void PrintMsg(byte MsgType, String Msg) {
        // Get and format current date & time
        CurrentDate = new Date();
        SimpleDateFormat FormatCurrentDate = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String FormattedMsg;

        if ((MsgType >> 4) <= DebugLevel && (MsgType >> 4) != 0x0) {
            // Debug Message
            FormattedMsg = "[" + FormatCurrentDate.format(CurrentDate) + " DEBUG" + (MsgType >> 4) + "    ]: " + Msg;
            if (UseColorCode) {
                FormattedMsg = C_PURPLE + FormattedMsg + C_RESET;
            }
            System.out.println(FormattedMsg);
        } else if (MsgType == 0x0) {
            // Normal Message
            System.out.println("[" + FormatCurrentDate.format(CurrentDate) + " INFO      ]: " + Msg);
        } else if (MsgType == 0x1) {
            // Warning Message
            FormattedMsg = "[" + FormatCurrentDate.format(CurrentDate) + " WARNING   ]: " + Msg;
            if (UseColorCode) {
                FormattedMsg = C_YELLOW + FormattedMsg + C_RESET;
            }
            System.out.println(FormattedMsg);
        } else if (MsgType == 0x2) {
            // Error Message
            FormattedMsg = "[" + FormatCurrentDate.format(CurrentDate) + " ERROR     ]: " + Msg;
            if (UseColorCode) {
                FormattedMsg = C_RED + FormattedMsg + C_RESET;
            }
            System.out.println(FormattedMsg);
        } else if (MsgType == 0x4) {
            // Detection Message
            FormattedMsg = "[" + FormatCurrentDate.format(CurrentDate) + " DETECTION ]: " + Msg;
            if (UseColorCode) {
                FormattedMsg = C_CYAN + FormattedMsg + C_RESET;
            }
            System.out.println(FormattedMsg);
            // Log to file if enabled
            if (LogToFile == true) {
                LogFileWriter.println("[" + FormatCurrentDate.format(CurrentDate) + " DETECTION ]: " + Msg);
            }
        }
    }

    // Check if a port number is valid or not
    public static boolean ValidatePortNum(int PortNum) {
        return PortNum <= 65535 && PortNum >= 1;
    }

    /**
     * ********************************************************************
     * Program Settings
     * ********************************************************************
     */
    // Read settings from Settings.conf file
    private static void ReadSettingsFromConf() {
        final String ConfFile = "Settings.conf"; // Setting file location and name
        PrintMsg((byte) 0x00, "Reading settings from " + ConfFile + "...");
        final Properties ConfPropReader = new Properties();

        // Start reading file
        try {
            InputStream FileReader = new FileInputStream(ConfFile);
            ConfPropReader.load(FileReader);

            // Start reading data from file
            UseColorCode = Boolean.parseBoolean(ConfPropReader.getProperty("Program.UseColorCode"));

            // Debug Level
            DebugLevel = Byte.parseByte(ConfPropReader.getProperty("Program.Debug"), 16);
            PrintMsg((byte) 0x10, "Debug Level is set to: " + DebugLevel);

            //Other settings
            LogToFile = Boolean.parseBoolean(ConfPropReader.getProperty("Program.LogToFile"));
            BanCmd = ConfPropReader.getProperty("General.BanCmd");
            UnbanCmd = ConfPropReader.getProperty("General.UnbanCmd");
            BanLength = Integer.parseInt(ConfPropReader.getProperty("General.BanLength"));
            PortRangeStart = Integer.parseInt(ConfPropReader.getProperty("PortRange.Start"));
            PortRangeEnd = Integer.parseInt(ConfPropReader.getProperty("PortRange.End"));
            SpecifiedPortsCount = Integer.parseInt(ConfPropReader.getProperty("SpecPort.Count"));
            ExcludedPortsCount = Integer.parseInt(ConfPropReader.getProperty("ExclPort.Count"));
            IPWhiteListCount = Integer.parseInt(ConfPropReader.getProperty("IPWhiteList.Count"));

            // Read specificed ports if necessary
            if (SpecifiedPortsCount > 0 && SpecifiedPortsCount < 65536) {
                SpecifiedPorts = new int[SpecifiedPortsCount];
                for (int Count = 0; Count < SpecifiedPortsCount; Count++) {
                    SpecifiedPorts[Count] = Integer.parseInt(ConfPropReader.getProperty("SpecPort." + (Count + 1)));
                }
            }

            // Read excluded ports if necessary
            if (ExcludedPortsCount > 0 && ExcludedPortsCount < 65536) {
                ExcludedPorts = new int[ExcludedPortsCount];
                for (int Count = 0; Count < ExcludedPortsCount; Count++) {
                    ExcludedPorts[Count] = Integer.parseInt(ConfPropReader.getProperty("ExclPort." + (Count + 1)));
                }
            }

            // Read IP Whitelist if necessary
            if (IPWhiteListCount > 0) {
                IPWhiteList = new String[IPWhiteListCount];
                for (int Count = 0; Count < IPWhiteListCount; Count++) {
                    IPWhiteList[Count] = ConfPropReader.getProperty("IPWhiteList." + (Count + 1));
                }
            }

            // Setup queue for banned ip if enabled
            if (UnbanCmd != null && !UnbanCmd.isEmpty() && !UnbanCmd.equalsIgnoreCase("OFF") && BanLength > 0) {
                // Set up new Queue
                BannedIP = new Queue();
                // Start exp checker
                new Thread(new UnbanTimeExpChecker()).start();
            }

            // Set variables for log writer if necessary
            if (LogToFile == true) {
                // Get and format current date & time
                SimpleDateFormat FormatCurrentDate = new SimpleDateFormat("yyyy_MM_dd");

                LogFileWriter = new PrintWriter(new FileOutputStream(new File("Log_" + FormatCurrentDate.format(CurrentDate) + ".log"), true), true);
            }
        } catch (FileNotFoundException e) {
            PrintMsg((byte) 0x02, "Conf file '" + ConfFile + "' not found!");
            System.exit(-1);
        } catch (Exception e) {
            PrintMsg((byte) 0x02, "Failed to read '" + ConfFile + "'!");
            PrintMsg((byte) 0x20, "Exception Detail: " + e);
            System.exit(-1);
        }
    }

    // Check settings and make sure they are valid
    private static void ValidateSettings() {
        PrintMsg((byte) 0x00, "Validating variable settings...");
        if (DebugLevel >= 0x3 || DebugLevel < 0x0) {
            PrintMsg((byte) 0x02, "Invalid 'DebugLevel' input. Valid range is 0x0 - 0x2.");
            System.exit(-1);
        } else if ((!ValidatePortNum(PortRangeStart) || !ValidatePortNum(PortRangeEnd)) && PortRangeStart != -1) {
            PrintMsg((byte) 0x02, "Invalid 'PortRangeStart' or 'PortRangeEnd' input. Valid range is 1-65535. Set 'PortRangeStart' to -1 to disable this feature.");
            System.exit(-1);
        } else if (SpecifiedPortsCount > 65535 || ExcludedPortsCount > 65535) {
            PrintMsg((byte) 0x02, "'SpecifiedPortsCount' and 'ExcludedPortsCount' cannot be greater than 65535.");
            System.exit(-1);
        } else if (SpecifiedPortsCount > 0) {
            // Verify each input for specified ports
            for (int Count = 0; Count < SpecifiedPortsCount; Count++) {
                if (!ValidatePortNum(SpecifiedPorts[Count])) {
                    PrintMsg((byte) 0x02, "Invalid 'SpecifiedPorts' in element" + Count + ". Valid range is 1-65535. Set 'SpecifiedPortsCount' to -1 to disable this feature.");
                    System.exit(-1);
                } else if (PortRangeStart != -1 && (SpecifiedPorts[Count] - PortRangeStart >= 0 && SpecifiedPorts[Count] - PortRangeEnd <= 0)) {
                    // Make sure specified ports not included in the range specification
                    PrintMsg((byte) 0x02, "'SpecifiedPorts' in element" + Count + " is already included in the port range specification settings.");
                    System.exit(-1);
                }
            }
        } else if (ExcludedPortsCount > 0) {
            // Verify each input for excluded ports
            for (int Count = 0; Count < ExcludedPortsCount; Count++) {
                if (!ValidatePortNum(ExcludedPorts[Count])) {
                    PrintMsg((byte) 0x02, "Invalid 'ExcludedPorts' in element" + Count + ". Valid range is 1-65535. Set 'ExcludedPortsCount' to -1 to disable this feature.");
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * ********************************************************************
     * Keyboard input handlers
     * ********************************************************************
     */
    // Keyboard command handler
    private static void KeyboardSignal() {
        Scanner KeyboardIn = new Scanner(System.in);
        String KeyboardInStr;

        do {
            PrintMsg((byte) 0x00, "Enter !q to exit the program.");
            KeyboardInStr = KeyboardIn.nextLine();
            PrintMsg((byte) 0x10, "Key '" + KeyboardInStr + "' detected.");

            if (KeyboardInStr.substring(0, 2).equalsIgnoreCase("!s")) {
                // !s command
                try {
                    ShutdownSinglePort(Integer.parseInt(KeyboardInStr.substring(3)));
                } catch (Exception e) {
                    PrintMsg((byte) 0x02, "Incorrect command format. Example: !s 36478");
                }
            } else if (KeyboardInStr.substring(0, 2).equalsIgnoreCase("!q")) {
                // !q command
                break; // Breaking this loop will means executing shutdown command
            }
        } while (true);
    }

    // This function will shutdown a specificed port
    private static void ShutdownSinglePort(int PortNum) {
        if (!ValidatePortNum(PortNum)) {
            PrintMsg((byte) 0x02, "Incorrect port number. Valid range is 1-65535.");
            return;
        }

        // Search for port
        for (int Count = 0; Count < TotalPortsCount; Count++) {
            if (ThreadPortNum[Count] == PortNum) {
                try {
                    PortSockets[Count].close(); // Send signal
                    PortsListenerThreads[Count].join(); // Wait for thread
                    ThreadPortNum[Count] = 0; // Remove this port from the list
                    PrintMsg((byte) 0x00, "Port " + PortNum + " successfully closed.");
                } catch (Exception e) {
                    PrintMsg((byte) 0x01, "Exception caught while shuttdown port.");
                    PrintMsg((byte) 0x20, "Exception Detail: " + e);
                }
                return;
            }
        }
        PrintMsg((byte) 0x02, "Port " + PortNum + " is not being listened by this application.");
    }

    // Shutdown all threads created by this program
    private static void ShutdownAllThreads() {
        PrintMsg((byte) 0x00, "Trying to shutdown all threads...");
        PrintMsg((byte) 0x00, "Shutting down threads might take a while. Use TaskMgr to end the program if it does not respond for 5 minutes.");
        Shutdown = true;
        // Send socket close signal
        for (int Count = 0; Count < TotalPortsCount; Count++) {
            try {
                PortSockets[Count].close();
            } catch (Exception e) {
                PrintMsg((byte) 0x10, "Exception caught while shuttdown port.");
                PrintMsg((byte) 0x20, "Exception Detail: " + e);
            }
        }
        // Verify all threads are off by using join
        for (int Count = 0; Count < TotalPortsCount; Count++) {
            try {
                if (PortsListenerThreads[Count].isAlive() != false) {
                    PortsListenerThreads[Count].join();
                }
            } catch (Exception e) {
                PrintMsg((byte) 0x10, "Exception caught while shuttdown ports.");
                PrintMsg((byte) 0x20, "Exception Detail: " + e);
            }
        }
        //
        PrintMsg((byte) 0x00, "All listening ports are closed.");
    }

    /**
     * ********************************************************************
     * Port Handler functions
     * ********************************************************************
     */
    // Create listening ports
    private static void CreateListeningPorts() {
        PrintMsg((byte) 0x00, "Initializing listening ports...");

        TotalPortsCount = 0;

        // Check port range specification
        if (PortRangeStart == -1 || PortRangeEnd - PortRangeStart + 1 < 1) {
            PrintMsg((byte) 0x00, "Port range specification is disabled.");
        } else {
            TotalPortsCount += PortRangeEnd - PortRangeStart + 1;
        }

        // Check specified ports
        if (SpecifiedPortsCount <= 0) {
            PrintMsg((byte) 0x00, "Specificed ports are disabled.");
        } else {
            TotalPortsCount += SpecifiedPortsCount;
        }

        // Check for excluded ports
        if (ExcludedPortsCount <= 0) {
            PrintMsg((byte) 0x00, "Exculded ports are disabled.");
        }

        // Check if there is some task we need to do
        if (TotalPortsCount == 0) {
            PrintMsg((byte) 0x02, "All methods are disabled, nothing to do.");
            System.exit(-1);
        }

        //
        PrintMsg((byte) 0x10, "Total ports calculated (exculding ignored ports): " + TotalPortsCount);

        //Init threads
        PortsListenerThreads = new Thread[TotalPortsCount];
        PortSockets = new ServerSocket[TotalPortsCount];
        ThreadPortNum = new int[TotalPortsCount];
        int PortsListenerThreadsPos = 0;

        // Create listening ports for range specification
        if (PortRangeStart != -1 && PortRangeEnd - PortRangeStart + 1 > 0) {
            for (int Count = PortRangeStart; Count <= PortRangeEnd; Count++) {
                boolean Excluded = false;
                for (int Count1 = 0; Count1 < ExcludedPortsCount; Count1++) {
                    if (Count == ExcludedPorts[Count1]) {
                        PrintMsg((byte) 0x10, "Excluding port: " + Count);
                        Excluded = true;
                        TotalPortsCount--;
                        break;
                    }
                }
                // Added to thread if not exlcuded
                if (Excluded == false) {
                    PortsListenerThreads[PortsListenerThreadsPos] = new Thread(new PortListeningThread(Count, PortsListenerThreadsPos));
                    PortsListenerThreadsPos++;
                }
            }
        }

        // Create listening ports for specificed ports
        for (int Count = 0; Count < SpecifiedPortsCount; Count++) {
            boolean Excluded = false;
            for (int Count1 = 0; Count1 < ExcludedPortsCount; Count1++) {
                if (SpecifiedPorts[Count] == ExcludedPorts[Count1]) {
                    PrintMsg((byte) 0x10, "Excluding port: " + SpecifiedPorts[Count]);
                    Excluded = true;
                    TotalPortsCount--;
                    break;
                }
            }
            // Added to thread if not exlcuded
            if (Excluded == false) {
                PortsListenerThreads[PortsListenerThreadsPos] = new Thread(new PortListeningThread(SpecifiedPorts[Count], PortsListenerThreadsPos));
                PortsListenerThreadsPos++;
            }
        }

        // Start listening for all ports
        PrintMsg((byte) 0x00, "Starting listening ports...");
        for (int Count = 0; Count < TotalPortsCount; Count++) {
            PortsListenerThreads[Count].start();
        }

        //Take a nap for 1 sec
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            // Ignore
        }

        // All creating port progress completed
        PrintMsg((byte) 0x00, "All ports creation completed.");
    }

    // Create multiple threads for each port
    static class PortListeningThread implements Runnable {

        // Port number for this thread
        int PortNum = 0;
        int ID = 0;

        // Init Port Number
        public PortListeningThread(int InputPort, int ID) {
            PrintMsg((byte) 0x20, "Initializing port " + InputPort + "...");
            PortNum = InputPort;
            this.ID = ID;
        }

        //Create port listener
        @Override
        public void run() {
            // Validate port number, we do not want for example negative port number
            if (!ValidatePortNum(PortNum)) {
                PrintMsg((byte) 0x02, "Invalid Port Number: " + PortNum + ".");
                System.exit(-1);
            } else {
                // Start listening
                try {
                    ServerSocket Listening = new ServerSocket(PortNum);
                    PortSockets[ID] = Listening;
                    ThreadPortNum[ID] = PortNum;
                    PrintMsg((byte) 0x20, "Listening on port " + PortNum + ".");

                    while (Shutdown != true) {
                        Socket ConnectionS = Listening.accept();
                        new Thread(new ConnectionHandler(ConnectionS)).start();
                    }
                } catch (BindException e) {
                    PrintMsg((byte) 0x01, "Failed to bind on port " + PortNum + ". Thread shutting down.");
                } catch (Exception e) {
                    if (Shutdown != true) {
                        PrintMsg((byte) 0x01, "Exception caught on port " + PortNum + ". Thread shutting down.");
                        PrintMsg((byte) 0x20, "Exception Detail: " + e);
                    } else {
                        PrintMsg((byte) 0x20, "Shutdown command detected. Closing port: " + PortNum);
                    }
                }
            }
        }
    }

    // Connection handler, record incoming request IP
    static class ConnectionHandler implements Runnable {

        private Socket ConnectionS;

        public ConnectionHandler(Socket InputSocket) {
            ConnectionS = InputSocket;
        }

        @Override
        public void run() {
            // Record remote IP
            String RemoteIP = ((InetSocketAddress) ConnectionS.getRemoteSocketAddress()).getAddress().toString();
            int RemotePort = ((InetSocketAddress) ConnectionS.getRemoteSocketAddress()).getPort();
            String LocalIP = ConnectionS.getLocalAddress().toString();
            int LocalPort = ConnectionS.getLocalPort();
            //Close connection immediately after we acquired the IP address of the remote machine
            try {
                ConnectionS.close();
            } catch (Exception e) {
                PrintMsg((byte) 0x01, "Exception caught while closing remote connection for IP: " + RemoteIP);
                PrintMsg((byte) 0x20, "Exception Detail: " + e);
            }

            // Process everything else
            // Remove slash if found in IP address
            if (RemoteIP.substring(0, 1).equalsIgnoreCase("/")) {
                RemoteIP = RemoteIP.substring(1);
            }
            if (LocalIP.substring(0, 1).equalsIgnoreCase("/")) {
                LocalIP = LocalIP.substring(1);
            }
            // Add to counter and display message to log
            DetectionCount++;
            PrintMsg((byte) 0x04, "Connection detected from '" + RemoteIP + ":" + RemotePort + "' to '" + LocalIP + ":" + LocalPort + "'");

            // Add IP to firewall (execute cmd)
            ExecuteBanCmd(RemoteIP);
        }
    }

    private static void ExecuteBanCmd(String RemoteIP) {
        //Check whitelist
        if (BanCmd != null && !BanCmd.isEmpty() && !BanCmd.equalsIgnoreCase("OFF")) {
            for (int Count = 0; Count < IPWhiteListCount; Count++) {
                if (IPWhiteList[Count].equalsIgnoreCase(RemoteIP)) {
                    PrintMsg((byte) 0x04, "IP: '" + RemoteIP + "' is in the whitelist, ignored.");
                    return; // No need this thread anymore. Exit immediately
                }
            }
        }

        // Replacing %ip with actual detected IP address
        String ExeCmd = BanCmd.replaceAll("%ip", RemoteIP);

        // Prepare to execute command, only one execution at a time
        boolean Retry = true;
        while (Retry) {
            Retry = false;

            try {
                //Get Mutex
                BanMutex.acquire();

                // Check banlist, make sure no dulicate bans
                if (BannedIP.Search_Queue(RemoteIP)) {
                    PrintMsg((byte) 0x20, "IP '" + RemoteIP + "' is already in the banned list.");
                    return;
                }

                // Executing cmd here.
                PrintMsg((byte) 0x10, "Executing cmd: " + ExeCmd);
                Runtime.getRuntime().exec(ExeCmd);

                // Put this IP address to queue list if unban is enabled
                if (BannedIP != null) {
                    // Calc expire time
                    CurrentDate = new Date();
                    BannedIPData NewIP = new BannedIPData(RemoteIP, CurrentDate.getTime() + BanLength * 1000);
                    // Put it to ban list
                    BannedIP.En_Queue(NewIP);
                }
            } catch (InterruptedException e) {
                PrintMsg((byte) 0x20, "Mutex locked: " + e + "\nRetry in 1 seconds...");
                try {
                    Thread.sleep(1000);
                } catch (Exception e1) {
                }
                Retry = true;
            } catch (Exception e) {
                PrintMsg((byte) 0x10, "Failed to execute command: " + ExeCmd);
                PrintMsg((byte) 0x20, "Exception Detail: " + e);
            } finally {
                BanMutex.release();
            }
        }
    }

    /**
     * ********************************************************************
     * Unban time expiration checker
     * ********************************************************************
     */
    static class UnbanTimeExpChecker implements Runnable {

        public UnbanTimeExpChecker() {
        }

        @Override
        public void run() {
            while (Shutdown != true) {
                try {
                    if (!BannedIP.isEmpty()) {
                        // Update time
                        CurrentDate = new Date();
                        // Use peak to check if exp
                        BannedIPData IPData = (BannedIPData) BannedIP.Peak_Queue();
                        if (IPData.ExpireTime <= CurrentDate.getTime()) { // Expired
                            PrintMsg((byte) 0x00, "IP '" + IPData.IPAddr + "' ban time has expired.");
                            ExeUnbanCmd((BannedIPData) BannedIP.De_Queue()); //Unban
                        } else {
                            Thread.sleep(1000); // Check only once every 1 seconds
                        }
                    } else {
                        Thread.sleep(1000); // Check only once every 1 seconds
                    }
                } catch (Exception e) {
                    PrintMsg((byte) 0x10, "Exception caight while removing expired IP from Queue.");
                    PrintMsg((byte) 0x20, "Exception Detail: " + e);
                }
            }
        }
    }

    /**
     * ********************************************************************
     * Remove all banned IPs on shutdown
     * ********************************************************************
     * Only works if:
     * 1. UnbanCMD is setup correctly
     * 2. BanLength > 0
     */
    public static void UnbanAllUponShutdown() {
        if (UnbanCmd != null && !UnbanCmd.isEmpty() && !UnbanCmd.equalsIgnoreCase("OFF") && BanLength > 0) {
            PrintMsg((byte) 0x00, "Removing all banned IPs...");
            //Unban all until empty
            while (!BannedIP.isEmpty()) {
                ExeUnbanCmd((BannedIPData) BannedIP.De_Queue());
            }
            PrintMsg((byte) 0x00, "All banned IPs are removed.");
        }
    }
    
    /**
     * ********************************************************************
     * Execute unban command
     * ********************************************************************
     */
    private static void ExeUnbanCmd(BannedIPData IPData) {
        //Convert %ip to actual ip address
        String ExeCmd = UnbanCmd.replaceAll("%ip", IPData.IPAddr);
        try {
            PrintMsg((byte) 0x10, "Executing cmd: " + ExeCmd);
            Runtime.getRuntime().exec(ExeCmd);
        } catch (Exception e) {
            PrintMsg((byte) 0x10, "Failed to execute command: " + ExeCmd);
            PrintMsg((byte) 0x20, "Exception Detail: " + e);
        }
    }
    
    /**
     * ********************************************************************
     * Main function
     * ********************************************************************
     */
    // Main function, entry point of the software
    public static void main(String[] args) {
        // Read settings from setting file
        ReadSettingsFromConf();

        // Program information message
        PrintMsg((byte) 0x00, "-----------------------------------------");
        PrintMsg((byte) 0x00, "Welcome use Honey Port.");
        PrintMsg((byte) 0x00, "Original Author: Jack L (http://jack-l.com)");
        PrintMsg((byte) 0x00, "Version: " + Version);
        PrintMsg((byte) 0x01, "This program is released under GNU General Public License v3");
        PrintMsg((byte) 0x01, "This program is still under Beta testing stage.");
        PrintMsg((byte) 0x00, "-----------------------------------------");

        // Validate current program settings
        ValidateSettings();

        // Creating listening ports
        CreateListeningPorts();

        // Wait for keyboard exit signal
        KeyboardSignal();

        // Issue exit signal
        ShutdownAllThreads();

        //Remove all banned IPs (Only works if UnbanCMD is being setup correctly and BanLength > 0)
        UnbanAllUponShutdown();
        
        // Exiting message
        PrintMsg((byte) 0x00, "Total of " + DetectionCount + " connection caught during program uptime.");
        PrintMsg((byte) 0x00, "Exiting main program...");
        PrintMsg((byte) 0x00, "Bye.");

        // Shutdown log writer
        if (LogFileWriter != null) {
            LogFileWriter.close(); // Close file
        }
    }
}
