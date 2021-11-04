public class PacketStats {
    private int payload;
    private double sendTime;
    private double ackTime;
    private boolean rtx; // retransmitted or not

    public PacketStats(int payload, double sendTime) {
        this.payload = payload;
        this.sendTime = sendTime;
        this.ackTime = -1;
        this.rtx = false;
    }

    public int getPayload() {
        return payload;
    }

    public double getSendTime() {
        return sendTime;
    }

    public void setSendTime(double sendTime) {
        this.sendTime = sendTime;
    }

    public double getAckTime() {
        return ackTime;
    }

    public void setAckTime(double ackTime) {
        this.ackTime = ackTime;
    }

    public boolean isAcked() {
        if (this.ackTime == -1) {
            return false;
        }
        else {
            return true;
        }
    }

    public boolean isRtx() {
        return rtx;
    }

    public void setRtx(boolean rtx) {
        this.rtx = rtx;
    }

    public double getRTT() {
        return this.ackTime - this.sendTime;
    }
}
