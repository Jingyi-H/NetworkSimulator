import java.util.*;
import java.io.*;

public class SrNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    private int base_A;
    private int base_B;
    private int nextSeqNo_A;
    private int nextSeqNo_B;
    private int currAck_A;
    private int lastAck_B;
    private Packet[] senderWindow;
    private Packet[] receiverWindow;
    private ArrayList<Integer> isAcked;
    private ArrayList<Packet> buffer_A; // messages from upper layer

    /* statistics variables */
    private int originPktCnt = 0;
    private int rtxCnt = 0;
    private int toLayer5Cnt = 0;
    private int ackCnt = 0;
    private int corruptedCnt = 0;
    private ArrayList<PacketStats> pktStats;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public SrNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }


    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        String msgData = message.getData();
        // TODO: checksum calculation
        int checksum = msgData.length();

        // buffer packet
        Packet pkt = new Packet(nextSeqNo_A, currAck_A, checksum, msgData);
        buffer_A.add(pkt);

        updateSenderWnd();
        sendSenderWnd();
        nextSeqNo_A = (nextSeqNo_A + 1) % LimitSeqNo;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        // if corrupted, drop
        // TODO: checksum calculation
        if (packet.getChecksum() != packet.getPayload().length()) {
            return;
        }
        int unACKed = getFirstUnACKed();
        // A recevied ACK in swnd
        if (isInWindow(packet.getAcknum(), base_A, WindowSize)) {
            stopTimer(A);
            int last = packet.getAcknum();
            slide(receiverWindow, last - base_A + 1); // TODO: + 1?

            /* statistics */
            double currTime = getTime();
            for (int i = 0; i < last - base_A + 1; i++) {
                pktStats.get(i + unACKed).setAckTime(currTime);
            }
            base_A = last;
        }
        else {
            // duplicate ACK -> resend packet
            toLayer3(A, senderWindow[0]);
            pktStats.get(unACKed).setRtx(true);
            rtxCnt++;
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        stopTimer(A);
        startTimer(A, 5 * RxmtInterval);
        toLayer3(A, senderWindow[0]);
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        base_A = nextSeqNo_A = FirstSeqNo;
        currAck_A = 0;
        buffer_A = new ArrayList<>();
        senderWindow = new Packet[WindowSize];
        pktStats = new ArrayList<>();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        String recvData = packet.getPayload();
        // TODO: checksum calculation
        if (packet.getChecksum() != recvData.length() || !isInWindow(packet.getSeqnum(), base_B, WindowSize)) {
            // if packet is corrupted/not in receiver window
            // drop it, send duplicate ACK to notify A
            int checksum = 0;
            Packet reAck = new Packet(nextSeqNo_B, lastAck_B, 0, "");
            toLayer3(B, reAck);
            return;
        }

        // receive packet successfully
        updateReceiverWnd(packet, base_B);

        // if the received packet is base
        if (packet.getSeqnum() == base_B) {
            // send receiver window to upper layer
            int idx = 0;
            while (receiverWindow[idx] != null) {
                toLayer5(receiverWindow[idx].getPayload());
                toLayer5Cnt++;
                idx++;
            }
            // last packet in rwnd
            lastAck_B = receiverWindow[idx - 1].getSeqnum();
            Packet ack = new Packet(nextSeqNo_B, lastAck_B, 0, "");
            toLayer3(B, ack);
            ackCnt += idx;

            // slide RWND
            slide(receiverWindow, idx);
        }
        else {
            // send duplicate ACK
            Packet ack = new Packet(nextSeqNo_B, lastAck_B, 0, "");
            toLayer3(B, ack);
            ackCnt++;
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        base_B = nextSeqNo_B = FirstSeqNo;
        lastAck_B = 0;
        receiverWindow = new Packet[WindowSize];
    }

    protected void updateSenderWnd() {
        if (!buffer_A.isEmpty()) {
            int next = 0;
            while (next < senderWindow.length && senderWindow[next] != null) {
                next++;
            }
            while (buffer_A.size() != 0 && next < senderWindow.length) {
                senderWindow[next] = buffer_A.get(0);
                buffer_A.remove(0);
                next++;
            }
        }
    }

    protected void sendSenderWnd() {
        startTimer(A, 5 * RxmtInterval);
        for (Packet pkt : senderWindow) {
            toLayer3(A, pkt);
            pktStats.add(new PacketStats(getTime()));
        }
    }

    protected void updateReceiverWnd(Packet pkt, int base) {
        int wndIdx = base - pkt.getSeqnum();
        if (wndIdx < 0) {
            wndIdx += LimitSeqNo;
        }
        receiverWindow[wndIdx] = pkt;
    }

    protected boolean isInWindow(int seqnum, int base, int wndsize) {
        // TODO: check
        if (seqnum >= base && seqnum < base + wndsize) {
            return true;
        }
        else {
            if (seqnum + LimitSeqNo >= base && seqnum < (base + wndsize) % LimitSeqNo) {
                return true;
            }
        }
        return false;
    }

    protected void slide(Packet[] window, int k) {
        // slides k packets
        for (int i = 0; i < window.length; i++) {
            if (i + k < window.length) {
                window[i] = window[i + k];
            }
            else {
                window[i] = null;
            }
        }
    }

    /* statistics related methods */

    protected int getFirstUnACKed() {
        int i = 0;
        while (i < pktStats.size()) {
            if (!pktStats.get(i).isAcked()) {
                break;
            }
            i++;
        }
        return i;
    }

    private double getAvgRTT() {
        double rttSum = 0;
        int rttCnt = 0;
        for (PacketStats ps : pktStats) {
            if (!ps.isRtx()) {
                rttCnt++;
                rttSum += ps.getAckTime() - ps.getSendTime();
            }
        }
        return rttSum / rttCnt;
    }

    private double getAvgComms() {
        double commsTime = 0;
        for (PacketStats ps : pktStats) {
            commsTime += ps.getAckTime() - ps.getSendTime();
        }
        return commsTime / pktStats.size();
    }

    // Use to print final statistics
    protected void Simulation_done() {
        double rtt = getAvgRTT();
        double comms = getAvgComms();
        try {
            File myObj = new File("log.txt");
            if (myObj.createNewFile()) {
                FileWriter fw = new FileWriter("log.txt");
                fw.write("\n===============STATISTICS=======================");
                fw.write("\nNumber of original packets transmitted by A:" + originPktCnt);
                fw.write("\nNumber of retransmissions by A:" + rtxCnt);
                fw.write("\nNumber of data packets delivered to layer 5 at B:" + toLayer5Cnt);
                fw.write("\nNumber of ACK packets sent by B:" + ackCnt);
                fw.write("\nNumber of corrupted packets:" + corruptedCnt);
                fw.write("\nRatio of lost packets:" + ((rtxCnt - corruptedCnt) / (originPktCnt + rtxCnt + ackCnt)));
                fw.write("\nRatio of corrupted packets:" + corruptedCnt / (originPktCnt + rtxCnt + ackCnt - (rtxCnt - corruptedCnt)));
                fw.write("\nAverage RTT:" + rtt);
                fw.write("\nAverage communication time:" + comms);
                fw.write("\n==================================================");
            }

        } catch (IOException e) {
            System.out.println("An error occurred when creating log file.");
        }

        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + originPktCnt);
        System.out.println("Number of retransmissions by A:" + rtxCnt);
        System.out.println("Number of data packets delivered to layer 5 at B:" + toLayer5Cnt);
        System.out.println("Number of ACK packets sent by B:" + ackCnt);
        System.out.println("Number of corrupted packets:" + corruptedCnt);
        System.out.println("Ratio of lost packets:" + ((rtxCnt - corruptedCnt) / (originPktCnt + rtxCnt + ackCnt)));
        System.out.println("Ratio of corrupted packets:" + corruptedCnt / (originPktCnt + rtxCnt + ackCnt - (rtxCnt - corruptedCnt)));
        System.out.println("Average RTT:" + rtt);
        System.out.println("Average communication time:" + comms);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}

