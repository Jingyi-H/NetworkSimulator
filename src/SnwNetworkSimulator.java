import java.util.*;
import java.io.*;

public class SnwNetworkSimulator extends NetworkSimulator
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
    private Packet currPkt;
    private ArrayList<Packet> buffer_A;
    private int maxBufferSize;

    /* statistics variables */
    private int rtxCnt = 0;
    private int toLayer5Cnt = 0;
    private int ackCnt = 0;
    private int corruptedCnt = 0;
    private int recvAcksCnt = 0;
    private ArrayList<PacketStats> pktStats;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public SnwNetworkSimulator(int numMessages,
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
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
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
        if (nextSeqNo_A > base_A + WindowSize) {
            return;
        }
        int checksum = calcChecksum(msgData, nextSeqNo_A, currAck_A);
        Packet pkt = new Packet(nextSeqNo_A, currAck_A, checksum, msgData);
        if (buffer_A.size() <= maxBufferSize) {
            buffer_A.add(pkt);
            pktStats.add(new PacketStats(pkt.getPayload().length(), getTime()));
        }
        if (currPkt == null) {
            currPkt = buffer_A.get(0);
            buffer_A.remove(0);
        }
        startTimer(A, 5 * RxmtInterval);
        toLayer3(A, currPkt);
        nextSeqNo_A = (nextSeqNo_A + 1) % LimitSeqNo;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        if (packet.getChecksum() != calcChecksum(packet)) {
            return;
        }

        recvAcksCnt++;

        // A only receives ACKs
        if (packet.getAcknum() == base_A) {
            // stop timer, get next packet from buffer
            stopTimer(A);
            double currTime = getTime();
            if (buffer_A.size() > 0) {
                currPkt = buffer_A.get(0);
            }
            else {
                currPkt = null;
            }
            pktStats.get(getFirstUnACKed()).setAckTime(currTime);
            base_A = (base_A + 1) % LimitSeqNo;
        }
        else {
            // resend current packet
            stopTimer(A);
            startTimer(A, 5 * RxmtInterval);
            toLayer3(A, currPkt);
            pktStats.get(getFirstUnACKed()).setRtx(true);
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
        if (currPkt == null) {
            return;
        }
        toLayer3(A, currPkt);
        rtxCnt++;
        pktStats.get(getFirstUnACKed()).setRtx(true);
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
        currPkt = null;
        maxBufferSize = 50;
        pktStats = new ArrayList<>();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        String recvData = packet.getPayload();
        // if packet is corrupted, drop it, send duplicate ACK to notify A
        // TODO: checksum calculation
        if (packet.getChecksum() != calcChecksum(packet)) {
            corruptedCnt++;
            return;
        }
        // if the packet is not the expected one, drop and resend ACK
        // currAck = nextExpectedSeqNo
        if (packet.getSeqnum() != base_B) {
            int checksum = calcChecksum("", nextSeqNo_B, lastAck_B);
            Packet reAck = new Packet(nextSeqNo_B, lastAck_B, checksum, "");
            toLayer3(B, reAck);
            ackCnt++;
            return;
        }
        // receive packet successfully
        lastAck_B = (lastAck_B + 1) % LimitSeqNo;
        base_B = (base_B + 1) % LimitSeqNo;
        int checksum = calcChecksum("", nextSeqNo_B, lastAck_B);
        Packet ack = new Packet(nextSeqNo_B, lastAck_B, checksum, "");
        toLayer3(B, ack);
        ackCnt++;
        // send data from B to upper layer
        toLayer5(recvData);
        toLayer5Cnt++;
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        base_B = nextSeqNo_B = FirstSeqNo;
        lastAck_B = LimitSeqNo - 1;
    }

    private int calcChecksum (String payload, int seqnum, int ack) {
        int checkSum = 0;
        for (int i = 0; i < payload.length(); i++) {
            checkSum += (int)payload.charAt(i);
        }
        checkSum += seqnum + ack;

        return checkSum;
    }

    private int calcChecksum (Packet packet) {
        String payLoad = packet.getPayload();
        int checkSum = 0;
        for (int i = 0; i < payLoad.length(); i++) {
            checkSum += (int)payLoad.charAt(i);
        }
        checkSum += packet.getSeqnum() + packet.getAcknum();

        return checkSum;
    }

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
            if (!ps.isRtx() && ps.isAcked()) {
                rttCnt++;
                rttSum += ps.getAckTime() - ps.getSendTime();
            }
        }
        return rttSum / rttCnt;
    }

    private double getTtlRTT() {
        double rttSum = 0;
        for (PacketStats ps : pktStats) {
            if (!ps.isRtx() && ps.isAcked()) {
                rttSum += ps.getAckTime() - ps.getSendTime();
            }
        }
        return rttSum;
    }

    private int getRttCnt() {
        int rttCnt = 0;
        for (PacketStats ps : pktStats) {
            if (!ps.isRtx()  && ps.isAcked()) {
                rttCnt++;
            }
        }
        return rttCnt;
    }

    private double getAvgComms() {
        double commsTime = 0;
        int commsCnt = 0;
        for (PacketStats ps : pktStats) {
            if(ps.isAcked()) {
                commsTime += ps.getAckTime() - ps.getSendTime();
                commsCnt++;
            }
        }
        return commsTime / commsCnt;
    }

    private double getTtlComms() {
        double commsTime = 0;
        for (PacketStats ps : pktStats) {
            if(ps.isAcked())
                commsTime += ps.getAckTime() - ps.getSendTime();
        }
        return commsTime;
    }

    private int getCommsCnt() {
        int commsCnt = 0;
        for (PacketStats ps : pktStats) {
            if (!ps.isRtx()  && ps.isAcked()) {
                commsCnt++;
            }
        }
        return commsCnt;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
        double rtt = getAvgRTT();
        double comms = getAvgComms();
        int originPktCnt = pktStats.size();
        double lossRatio = (double)(rtxCnt - corruptedCnt) / (originPktCnt + rtxCnt + ackCnt);
        double corruptedRatio = (double) corruptedCnt / (originPktCnt + rtxCnt + ackCnt - (rtxCnt - corruptedCnt));
        double ttlRtt = getTtlRTT();
        int rttCnt = getRttCnt();
        double ttlComms = getTtlComms();
        int commsCnt = getCommsCnt();

        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + originPktCnt);
        System.out.println("Number of retransmissions by A:" + rtxCnt);
        System.out.println("Number of data packets delivered to layer 5 at B:" + toLayer5Cnt);
        System.out.println("Number of ACK packets sent by B:" + ackCnt);
        System.out.println("Number of corrupted packets:" + corruptedCnt);
        System.out.println("Ratio of lost packets:" + lossRatio);
        System.out.println("Ratio of corrupted packets:" + corruptedRatio);
        System.out.println("Average RTT:" + rtt);
        System.out.println("Average communication time:" + comms);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        System.out.println("number of non-corrupted ACKs received by A: " + recvAcksCnt);
        System.out.println("All RTT: " + ttlRtt);
        System.out.println("Counter RTT: " + rttCnt);
        System.out.println("Total time to communicate: " + ttlComms);
        System.out.println("Counter for time to communicate: " + commsCnt);
    }

}

