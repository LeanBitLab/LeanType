package helium314.keyboard.latin.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class JsonUtilsTest {

    @Test
    public void testListToJsonStr() {
        // null list
        assertEquals("", JsonUtils.listToJsonStr(null));

        // empty list
        assertEquals("", JsonUtils.listToJsonStr(Collections.emptyList()));

        // list with integers and strings
        List<Object> list = Arrays.asList(1, "hello", 2, "world");
        String expected = "[{\"Integer\":1},{\"String\":\"hello\"},{\"Integer\":2},{\"String\":\"world\"}]";
        assertEquals(expected, JsonUtils.listToJsonStr(list));

        // list with unsupported types (they should be serialized as empty objects)
        List<Object> listWithUnsupported = Arrays.asList(1, 3.14, "test");
        String expectedWithUnsupported = "[{\"Integer\":1},{},{\"String\":\"test\"}]";
        assertEquals(expectedWithUnsupported, JsonUtils.listToJsonStr(listWithUnsupported));
    }

    @Test
    public void testJsonStrToList() {
        // empty or invalid
        assertTrue(JsonUtils.jsonStrToList("").isEmpty());

        // Test parsing the generated string back
        List<Object> originalList = Arrays.asList(1, "hello", 2, "world");
        String json = JsonUtils.listToJsonStr(originalList);
        List<Object> parsedList = JsonUtils.jsonStrToList(json);
        assertEquals(originalList, parsedList);
    }

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
}
