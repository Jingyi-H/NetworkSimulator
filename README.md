# PA2

#### Add files:
+ SnwNetworkSimulator
+ SrNetworkSimulator

#### Variables:
(in --NetworkSimulator)
+ base_A: current window base of entity A, slides after base packet is ACKed
+ base_B: current window base of entity B, ditto
+ nextSeqNo_A, nextSeqNo_B: next sequence number to be sent, increase after a packet is sent
+ currAck_A, currAck_B: current ACK, next expected sequence number
+ buffer_A: buffer sent and un-ACKed packets
+ buffer_B: buffer out-of-order packets (i.e. seqNo > base_B)
