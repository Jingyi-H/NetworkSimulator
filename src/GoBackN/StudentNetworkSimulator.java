package GoBackN;

import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator
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
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    //Go Back N sender
    private int base;
    private int seqNo;
    private ArrayList<Packet> buffer;
    private int buffMaximum;
    private  int seqPtr;
    private double waitTime;
    private int[] sack_A = new int[5];
    private HashMap<Integer, Double> send;
    private HashMap<Integer, Double> get;
    private double[] RTTstart = new double[1000];
    private double[] RTTEnd = new double[1000];

    //Go back N receiver
    private int sequenceNowExpected;
    private int[] sack_B = new int[5];
    private int sack_B_count;
    private HashMap<Integer,String> bufferB;

    //Go back N Statistics
    private int originalPacketsNumber = 0;
    private double rttStarted;
    private int corruptSeq = 0;
    private int dataTo5AtB = 0;
    private int ACKByB = 0;
    private double totalRtt = 0.0;
    private int totalRttCount = 0;
    private int corruptNum = 0;
    private int retransmissionsNumber = 0;

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
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
        System.out.println("A: get message "+ message.getData());
        //check the buffer size
        if (buffer.size() < buffMaximum + base + WindowSize) {
            //set packet parameters(sequence, ACK, checksum)
            String payLoad = message.getData();
            int seqA = buffer.size();
            int ACK = -1;
            int check = calculateCheckSum(payLoad) + seqA + ACK;

            //add the packet in the buffer_A
            buffer.add(new Packet(seqA, ACK, check, payLoad));

            //tolayer3
            while (seqPtr < base + WindowSize) {
                if (seqPtr < buffer.size()) {
                    System.out.println("A: Sending packet " + seqPtr + " to receiver");
                }
                toLayer3(A, buffer.get(seqPtr));
                RTTstart[buffer.get(seqPtr).getSeqnum()] = getTime();
                if (base == seqPtr) {
                    startTimer(A, waitTime);
                    rttStarted = getTime();
                }
                double time = getTime();
                if(!send.containsKey(seqPtr)) send.put(seqPtr,time);
                seqPtr++;
                originalPacketsNumber++;
            }
        }else {
            System.out.println("The buffer of A is full!");
            System.exit(0);
        }
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        System.out.println("A: Packet " + packet.getSeqnum() +" from layer 3 has been received");
        //check if packet is corrupted
        if (checkCorrupted(packet)) {
            corruptSeq++;
            System.out.println("\033[31;4m" + "A: Packet corrupted!" + "\033[0m");
        } else {
            System.out.println("A: ACK packet " + packet.getSeqnum() + " from layer3 is correct");
            //add the sequence number of this packet in the sack_A
            sack_A = packet.getSackNum();
            int seq = packet.getSeqnum();
            System.out.println("A: SACK: " + sack_A[0] +", " + sack_A[1] +", " + sack_A[2] +", " + sack_A[3] +", "
                    + sack_A[4]);
            System.out.println("A: time: " + getTime() +" - " + RTTstart[seq]);
            // some variable to do STATISTICS
            totalRtt += getTime() - RTTstart[seq];
            totalRttCount++;

            if(!get.containsKey(seq)){
                get.put(seq,getTime());
            }

            //if the correct ACK packet is the base, slide the window
            if(base == packet.getSeqnum()) {
                base++;
            }
            // if base is correct, we should stop the timer
            if (base == seqPtr){
                stopTimer(A);
            }
        }
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        System.out.println("A: The timer was interrupted, resending the message.");
        rttStarted = getTime();
        Set<Integer> q = new HashSet<>();
        System.out.println("A: SACK: " + sack_A[0] +", " + sack_A[1] +", " + sack_A[2] +", " + sack_A[3] +", " + sack_A[4]);
        for(int i = 0; i < 5; i++){
            if(sack_A[i] != -1) q.add(sack_A[i]);
        }
        for (int i = base; i < seqPtr; i++) {
            System.out.println("A: Retransmitting unacknowledged packet " + i + "." + sequenceNowExpected);
            if(q.contains(i)){
                System.out.println("A: in SACK" + i + ".");
                // use continue to use sack
                //continue;
            }
            stopTimer(A);
            startTimer(A, waitTime);
            toLayer3(A,buffer.get(i));
            RTTstart[i] = getTime();
            retransmissionsNumber++;
        }
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        System.out.println("A: init");
        seqNo= 0;
        base = 0;
        buffMaximum = 100;
        buffer = new ArrayList<>();
        seqPtr = 0;
        waitTime = 1 * RxmtInterval;
        for(int i = 0; i < 5; i++){
            sack_A[i] = -1;
        }
        send = new HashMap<>();
        get = new HashMap<>();
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        System.out.println("B: Packet from A was received through layer 3 ("+packet.getPayload()+").");
        //check if this is a corrupted packet
        if (!checkCorrupted(packet)) {
            System.out.println("\033[31;4m" + "B: Packet corrupted!" + "\033[0m");
            if (checkCorrupted(packet))
                corruptSeq++;
            //return;
        } else {
            //save it in the buffer_B
            System.out.println("B: Packet received from A checks out.");
            String payLoad = packet.getPayload();
            sack_B[sack_B_count] = packet.getSeqnum();
            sack_B_count++;
            sack_B_count = sack_B_count %5;
            bufferB.put(packet.getSeqnum(),payLoad);

            //send ACK to A
            int seqB = packet.getSeqnum();
            int ACK = seqB;
            String message = "";
            int check = calculateCheckSum(message) + seqB + ACK;
            System.out.println("B: SACK: " + sack_B[0] +", " + sack_B[1] +", " + sack_B[2] +", " + sack_B[3] +", " + sack_B[4]);
            Packet newPacket = new Packet(seqB,ACK,check,message,sack_B);
            toLayer3(B, newPacket);

            //check the sequence number, if it is the next correct order, send to the layer5
            while(bufferB.containsKey(sequenceNowExpected)){
                System.out.println("B: toLayer5: " + sequenceNowExpected);
                toLayer5(bufferB.get(sequenceNowExpected));
                sequenceNowExpected++;
                dataTo5AtB++;
            }
            ACKByB++;
        }
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        System.out.println("B: init");
        sequenceNowExpected = 0;
        for(int i = 0; i < 5; i++){
            sack_B[i] = -1;
        }
        sack_B_count = 0;
        bufferB = new HashMap<>();
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
        int totalPacket = originalPacketsNumber + retransmissionsNumber + ACKByB;
        double lostRatio = (retransmissionsNumber - corruptSeq) / (double) totalPacket;
        double corruptionRatio = (corruptSeq) / (double) (totalPacket - (retransmissionsNumber - corruptSeq));
        double RTT = totalRtt/(double) totalRttCount;
        double com = 0;
        int num = 0;
        for(int i = 0; i < originalPacketsNumber; i++){
            if(send.containsKey(i) && get.containsKey(i)){
                com += get.get(i) - send.get(i);
                num++;
            }
        }
        com = com/ (double)num;

    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + originalPacketsNumber);
    	System.out.println("Number of retransmissions by A:" + retransmissionsNumber);
    	System.out.println("Number of data packets delivered to layer 5 at B:" + dataTo5AtB);
    	System.out.println("Number of ACK packets sent by B:" + ACKByB);
    	System.out.println("Number of corrupted packets:" + corruptNum);
    	System.out.println("Ratio of lost packets:" + lostRatio );
    	System.out.println("Ratio of corrupted packets:" + corruptionRatio);
    	System.out.println("Average RTT:" + RTT);
    	System.out.println("Average communication time:" + com);
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

    public int calculateCheckSum (String payLoad) {
        int checkSum = 0;
        for (int i = 0; i < payLoad.length(); i++) {
            checkSum += (int)payLoad.charAt(i);
        }
        return checkSum;
    }

    public boolean checkCorrupted(Packet packet) {
        String payload = packet.getPayload();
        int checkSum_compare = 0;
        for(int i = 0; i < payload.length(); i++) {
            checkSum_compare += (int)payload.charAt(i);
        }
        checkSum_compare += packet.getSeqnum() + packet.getAcknum();
        if (checkSum_compare != packet.getChecksum()) {
            return false;
        }else {
            return true;
        }
        //return (checkSum_compare != packet.getChecksum());
    }
}