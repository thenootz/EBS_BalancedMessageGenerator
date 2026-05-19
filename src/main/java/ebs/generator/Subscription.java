package ebs.generator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a Subscription.
 * Each present field has the form (fieldName, operator, value).
 * Fields may be absent; only fields explicitly added appear in the output.
 */
public class Subscription {

    // ordered map: fieldName -> "operator,value" pair kept as a display string
    private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();

    public void addField(String fieldName, String operator, String value) {
        fields.put(fieldName, "(" + fieldName + "," + operator + "," + value + ")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (i++ > 0) sb.append(";");
            sb.append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }
}
