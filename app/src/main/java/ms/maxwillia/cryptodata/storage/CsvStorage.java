package ms.maxwillia.cryptodata.storage;


import ms.maxwillia.cryptodata.model.CryptoTick;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;


public class CsvStorage {
    private final String filename;
    private final PrintWriter writer;

    public CsvStorage(String filename) throws IOException {
        this.filename = filename;
        this.writer = new PrintWriter(new FileWriter(filename, true));
        writeHeader();
    }



    private void writeHeader() {
        writer.println(CryptoTick.header());
        writer.flush();
    }

    public void storeTick(CryptoTick tick) {
        writer.printf(CryptoTick.headerFormat(),
            tick.symbol(),
            tick.price(),
            tick.volume_24_h(),
            tick.best_bid(),
            tick.best_bid_quantity(),
            tick.best_ask(),
            tick.best_ask_quantity(),
            tick.timestamp(),
            tick.nokPrice(),
            tick.usdcRate()
        );
        writer.flush();
    }

    public void close() {
        writer.close();
    }
}
