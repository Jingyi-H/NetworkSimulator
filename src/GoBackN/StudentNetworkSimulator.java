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
    private int sequenceNoExpected;
    private int[] sack_B = new int[5];
    private int count;
    private HashMap<Integer,String> bufferB;

    //Go back N variables used in the calculation
    private int originalPacketsNumber = 0;
    private double rttStarted;
    private int corruptSeq = 0;
    private int dataTo5AtB = 0;
    private int ACKByB = 0;

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
        if (buffer.size() < buffMaximum + base + WindowSize) {
            String context = message.getData();
            int seqA = buffer.size();
            int ACK = -1;
            int check = calculateCheckSum(context) + seqA + ACK;
            buffer.add(new Packet(seqA, ACK, check, context));
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

    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {

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
        //1.check if this is a corrupted packet
        //2.if the packet is not corrupted, save it in the buffer_B
        //3.send ACK to A
        //4.check the sequence number, if it is the next tolayer5, send to the layer5
        System.out.println("B: Package from A was received through layer 3 ("+packet.getPayload()+").");
        if (checkCorrupted(packet)) {
            System.out.println("\033[31;4m" + "B: Packet corrupted!" + "\033[0m");
            if (checkCorrupted(packet))
                corruptSeq++;
            //return;
        } else {
            System.out.println("B: Packet received from A checks out.");
            String data = packet.getPayload();
            sack_B[count] = packet.getSeqnum();
            count++;
            count = count%5;
            bufferB.put(packet.getSeqnum(),data);

            int seqB = packet.getSeqnum();
            int ACK = seqB;
            String message = "";
            int check = calculateCheckSum(message) + seqB + ACK;
            System.out.println("B: SACK: " + sack_B[0] +", " + sack_B[1] +", " + sack_B[2] +", " + sack_B[3] +", " + sack_B[4]);
            Packet newPacket = new Packet(seqB,ACK,check,message,sack_B);
            toLayer3(B, newPacket);

            while(bufferB.containsKey(sequenceNoExpected)){
                System.out.println("B: toLayer5: " + sequenceNoExpected);
                toLayer5(bufferB.get(sequenceNoExpected));
                sequenceNoExpected++;
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
        sequenceNoExpected = 0;
        for(int i = 0; i < 5; i++){
            sack_B[i] = -1;
        }
        count = 0;
        bufferB = new HashMap<>();
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + "<YourVariableHere>");
    	System.out.println("Number of retransmissions by A:" + "<YourVariableHere>");
    	System.out.println("Number of data packets delivered to layer 5 at B:" + "<YourVariableHere>");
    	System.out.println("Number of ACK packets sent by B:" + "<YourVariableHere>");
    	System.out.println("Number of corrupted packets:" + "<YourVariableHere>");
    	System.out.println("Ratio of lost packets:" + "<YourVariableHere>" );
    	System.out.println("Ratio of corrupted packets:" + "<YourVariableHere>");
    	System.out.println("Average RTT:" + "<YourVariableHere>");
    	System.out.println("Average communication time:" + "<YourVariableHere>");
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

    public int calculateCheckSum (String context) {
        int checkSum = 0;
        for (int i = 0; i < context.length(); i++) {
            checkSum += (int)context.charAt(i);
        }
        return checkSum;
    }

    public boolean checkCorrupted(Packet packet) {
        String context = packet.getPayload();
        int checkSum_compare = 0;
        for(int i = 0; i < context.length(); i++) {
            checkSum_compare += (int)context.charAt(i);
        }
        return (checkSum_compare != packet.getChecksum());
    }
}