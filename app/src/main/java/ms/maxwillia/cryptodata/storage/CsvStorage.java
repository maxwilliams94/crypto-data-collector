package ms.maxwillia.cryptodata.storage;


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
