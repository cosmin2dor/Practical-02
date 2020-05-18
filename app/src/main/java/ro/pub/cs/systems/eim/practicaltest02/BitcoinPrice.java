package ro.pub.cs.systems.eim.practicaltest02;

import androidx.annotation.NonNull;

public class BitcoinPrice {
    private String eurValue;
    private String usdValue;
    private String updated;

    public BitcoinPrice(String eurValue, String usdValue, String updated) {
        this.eurValue = eurValue;
        this.usdValue = usdValue;
        this.updated = updated;
    }

    @NonNull
    @Override
    public String toString() {
        return "EUR: " + getEurValue() + " USD: " + getUsdValue() + " Updated: " + getUpdated();
    }

    public String getEurValue() {
        return eurValue;
    }

    public String getUsdValue() {
        return usdValue;
    }

    public String getUpdated() {
        return updated.split(" ")[3];
    }
}
