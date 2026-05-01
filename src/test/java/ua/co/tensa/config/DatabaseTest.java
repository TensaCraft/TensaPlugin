package ua.co.tensa.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseTest {

    @Test
    void castValuesKeepsNumericStringsAsStrings() throws Exception {
        Database database = new Database();
        Method method = Database.class.getDeclaredMethod("castValuesToLong", Object[].class);
        method.setAccessible(true);

        Object[] values = (Object[]) method.invoke(database, (Object) new Object[]{
                "00123",
                "12.5",
                BigInteger.valueOf(42L)
        });

        assertThat(values).containsExactly("00123", "12.5", 42L);
    }
}
