package ebs.generator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Represents a Publication with fixed fields:
 * company (String), value (double), drop (double), variation (double), date (LocalDate)
 */
public class Publication {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final String company;
    private final double value;
    private final double drop;
    private final double variation;
    private final LocalDate date;

    public Publication(String company, double value, double drop, double variation, LocalDate date) {
        this.company   = company;
        this.value     = value;
        this.drop      = drop;
        this.variation = variation;
        this.date      = date;
    }

    @Override
    public String toString() {
        return String.format(
            "{(company,\"%s\");(value,%.2f);(drop,%.2f);(variation,%.4f);(date,%s)}",
            company, value, drop, variation, date.format(DATE_FMT)
        );
    }
}
