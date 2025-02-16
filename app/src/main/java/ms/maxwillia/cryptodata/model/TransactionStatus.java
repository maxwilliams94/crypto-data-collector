package ms.maxwillia.cryptodata.model;

public enum TransactionStatus {
    CREATED,
    REQUESTED,
    EXECUTED,
    REQUEST_ERROR,
    EXECUTION_ERROR,
    PREVIEW_ERROR,
    PREVIEW_WARNING,
    PREVIEW_SUCCESS
}
