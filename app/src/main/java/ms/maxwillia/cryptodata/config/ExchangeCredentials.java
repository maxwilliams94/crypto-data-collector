package ms.maxwillia.cryptodata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Setter
@Getter
public class ExchangeCredentials {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeCredentials.class);

    private String name;
    private String privateKey;

    // Required for Jackson deserialization
    public ExchangeCredentials() {}

    public ExchangeCredentials(String name, String privateKey) {
        this.name = name;
        this.privateKey = privateKey;
    }

    public static ExchangeCredentials loadFromFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(filePath), ExchangeCredentials.class);
    }

    public static ExchangeCredentials loadFromFile(Path path) throws IOException {
        String content = Files.readString(path);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(content, ExchangeCredentials.class);
    }
}