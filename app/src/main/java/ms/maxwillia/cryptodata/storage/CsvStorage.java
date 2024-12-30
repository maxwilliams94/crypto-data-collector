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
        writer.println("symbol,price,volume,timestamp");
        writer.flush();
    }

    public void storeTick(CryptoTick tick) {
        writer.printf("%s,%.8f,%.8f,%d%n",
            tick.symbol(),
            tick.price(),
            tick.volume(),
            tick.timestamp()
        );
        writer.flush();
    }

    public void close() {
        writer.close();
    }
}
