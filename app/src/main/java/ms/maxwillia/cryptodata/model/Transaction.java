package ms.maxwillia.cryptodata.model;

import lombok.Builder;
import lombok.Data;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
public class Transaction {
    @Builder.Default
    String id = null;
    @Builder.Default
    String exchangeId = null;
    String exchange;
    String currency;
    TransactionType orderType;
    TransactionSide side;
    Double price;
    Double quantity;
    @Builder.Default
    Double fee = null;
    @Builder.Default
    ZonedDateTime createdTime = ZonedDateTime.now(ZoneOffset.UTC);
    @Builder.Default
    TransactionStatus status = TransactionStatus.CREATED;
    @Builder.Default
    String response = null;

}
