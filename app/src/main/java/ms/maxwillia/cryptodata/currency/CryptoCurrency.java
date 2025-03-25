package ms.maxwillia.cryptodata.currency;

import org.jetbrains.annotations.NotNull;

import javax.money.CurrencyContext;
import javax.money.CurrencyUnit;

public class CryptoCurrency implements CurrencyUnit {
    private final String currencyCode;
    private final int defaultFractionDigits;

    public CryptoCurrency(String currencyCode, int defaultFractionDigits) {
        this.currencyCode = currencyCode;
        this.defaultFractionDigits = defaultFractionDigits;
    }

    @Override
    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    public int getNumericCode() {
        return 999; // Arbitrary non-ISO value
    }

    @Override
    public int getDefaultFractionDigits() {
        return defaultFractionDigits;
    }

    @Override
    public CurrencyContext getContext() {
        return null;
    }

    @Override
    public int compareTo(@NotNull CurrencyUnit o) {
        return 0;
    }
}
