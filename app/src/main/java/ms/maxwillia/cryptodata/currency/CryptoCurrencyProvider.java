package ms.maxwillia.cryptodata.currency;

import javax.money.CurrencyQuery;
import javax.money.CurrencyUnit;
import javax.money.spi.CurrencyProviderSpi;
import java.util.HashSet;
import java.util.Set;

public class CryptoCurrencyProvider implements CurrencyProviderSpi {
    private static final Set<CurrencyUnit> CURRENCIES = new HashSet<>();

    static {
        CURRENCIES.add(new CryptoCurrency("BTC", 8));
        CURRENCIES.add(new CryptoCurrency("ETH", 8));
        CURRENCIES.add(new CryptoCurrency("USDC", 2));
    }

    @Override
    public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {
        Set<CurrencyUnit> result = new HashSet<>();
        for (CurrencyUnit currency : CURRENCIES) {
            if (query.getCurrencyCodes().contains(currency.getCurrencyCode())) {
                result.add(currency);
            }
        }
        return result;
    }
}
