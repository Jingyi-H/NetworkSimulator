public class PacketStats {
    private double sendTime;
    private double ackTime;
    private boolean rtx; // retransmitted or not

    public PacketStats(double sendTime) {
        this.sendTime = sendTime;
        this.ackTime = -1;
        this.rtx = false;
    }

    public double getSendTime() {
        return sendTime;
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
}
