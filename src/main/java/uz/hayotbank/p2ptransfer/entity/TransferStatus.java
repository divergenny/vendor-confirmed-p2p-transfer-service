package uz.hayotbank.p2ptransfer.entity;

public enum TransferStatus {

    /** Money is reserved on the sender's balance, waiting for the vendor's answer. */
    PENDING,

    /** The vendor approved it, the money went to the recipient. */
    COMPLETED,

    /** The vendor declined it, the reserved money went back to the sender. */
    DECLINED,

    /** The vendor rejected the request itself (400), the reserved money went back to the sender. */
    FAILED
}
