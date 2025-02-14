package ms.maxwillia.cryptodata.model;

import lombok.Builder;
import lombok.Data;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
public class Transaction {
    String exchange;
    String currency;
    TransactionType orderType;
    TransactionSide side;
    Double requestedPrice;
    Double requestedQuantity;
    @Builder.Default
    Double executedAmount = null;
    @Builder.Default
    Double executedQuantity = null;
    @Builder.Default
    Double fee = null;
    @Builder.Default
    ZonedDateTime requestTime = null;
    @Builder.Default
    ZonedDateTime executedTime = null;
    @Builder.Default
    ZonedDateTime createdTime = ZonedDateTime.now(ZoneOffset.UTC);
    @Builder.Default
    TransactionStatus status = TransactionStatus.CREATED;
    @Builder.Default
    String response = null;

    public void requestTimeNow() {
        this.status = TransactionStatus.REQUESTED;
        this.requestTime = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void executedTimeNow() {
        this.status = TransactionStatus.EXECUTED;
        this.executedTime = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
