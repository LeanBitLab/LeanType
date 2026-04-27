package helium314.keyboard.latin.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class JsonUtilsTest {

    @Test
    public void testJsonStrToListValid() {
        String json = "[ { \"Integer\": 123 }, { \"String\": \"test\" }, { \"InvalidName\": 456 } ]";
        List<Object> result = JsonUtils.jsonStrToList(json);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(123, result.get(0));
        assertEquals("test", result.get(1));
    }

    @Test
    public void testJsonStrToListErrorHandling() {
        // Test with malformed JSON formats that trigger IOException.

        // 1. Truncated object inside array
        List<Object> result1 = JsonUtils.jsonStrToList("[ { \"Integer\": 123 ");
        assertTrue("Expected empty list for truncated JSON", result1.isEmpty());

        // 2. Syntax error in property name
        List<Object> result2 = JsonUtils.jsonStrToList("[ { Integer: 123 } ]");
        assertTrue("Expected empty list for JSON with syntax error", result2.isEmpty());
    }

    @Test
    public void testListToJsonStr() {
        // When processing an object of unsupported type (like 45.6, Double),
        // it still calls writer.beginObject() and writer.endObject(), adding "{}"
        List<Object> list = Arrays.asList(123, "test", 45.6);
        String json = JsonUtils.listToJsonStr(list);
        assertEquals("[{\"Integer\":123},{\"String\":\"test\"},{}]", json);
    }

    @Test
    public void testListToJsonStrEmpty() {
        assertEquals("", JsonUtils.listToJsonStr(null));
        assertEquals("", JsonUtils.listToJsonStr(Arrays.asList()));
    }
}
