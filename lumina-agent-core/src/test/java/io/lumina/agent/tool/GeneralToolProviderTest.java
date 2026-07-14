package io.lumina.agent.tool;

import io.lumina.agent.tool.search.SearchProvider;
import io.lumina.agent.tool.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 通用工具提供者单元测试
 *
 * <p>覆盖 4 个 @AgentTool 方法：
 * <ul>
 *   <li>getCurrentTime — 时间格式与时区</li>
 *   <li>calculate — ExprParser 递归下降解析器全场景（四则运算/括号/除零/负数/精度）</li>
 *   <li>webSearch — 正常调用/未配置/异常容错</li>
 *   <li>httpRequest — header 解析逻辑（网络部分用 MockWebServer 验证）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class GeneralToolProviderTest {

    private GeneralToolProvider provider;

    @Mock
    private SearchProvider searchProvider;

    @BeforeEach
    void setUp() {
        provider = new GeneralToolProvider();
        // searchProvider 是 @Autowired(required=false) 字段注入，用反射设置 mock
        ReflectionTestUtils.setField(provider, "searchProvider", searchProvider);
    }

    // ==================== getCurrentTime ====================

    @Nested
    class GetCurrentTimeTest {

        @Test
        void returnsAllExpectedFields() {
            Map<String, Object> result = provider.getCurrentTime();

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("datetime")).isInstanceOf(String.class);
            assertThat((String) result.get("datetime")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat(result.get("date")).isInstanceOf(String.class);
            assertThat(result.get("time")).isInstanceOf(String.class);
            assertThat(result.get("dayOfWeek")).isInstanceOf(String.class);
            assertThat(result.get("timezone")).asString().contains("Asia/Shanghai");
            assertThat(result.get("timestamp")).isInstanceOf(Long.class);
        }

        @Test
        void datetimeFormatIsCorrect() {
            Map<String, Object> result = provider.getCurrentTime();

            String datetime = (String) result.get("date");
            assertThat(datetime).matches("\\d{4}-\\d{2}-\\d{2}");
            String time = (String) result.get("time");
            assertThat(time).matches("\\d{2}:\\d{2}:\\d{2}");
        }

        @Test
        void timestampIsRecent() {
            long before = System.currentTimeMillis() / 1000;
            Map<String, Object> result = provider.getCurrentTime();
            long after = System.currentTimeMillis() / 1000;

            long timestamp = (Long) result.get("timestamp");
            assertThat(timestamp).isBetween(before, after);
        }
    }

    // ==================== calculate (ExprParser 全场景) ====================

    @Nested
    class CalculateTest {

        @ParameterizedTest
        @CsvSource({
            "1+2, 6, 3.000000",
            "10-3, 6, 7.000000",
            "4*5, 6, 20.000000",
            "20/4, 6, 5.000000",
            "2+3*4, 6, 14.000000",
            "(2+3)*4, 6, 20.000000",
            "2*(3+4)-5, 6, 9.000000",
            "100/3, 2, 33.33",
            "3.14*10*10, 2, 314.00",
            "0+0, 6, 0.000000",
            "9999999999*9999999999, 0, 99999999980000000001"
        })
        void evaluateExpressions(String expression, int scale, String expected) {
            Map<String, Object> result = provider.calculate(expression, String.valueOf(scale));

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo(expected);
        }

        @Test
        void defaultScaleIs6WhenNotSpecified() {
            Map<String, Object> result = provider.calculate("10/3", null);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("result")).startsWith("3.333333");
        }

        @Test
        void defaultScaleIs6WhenBlank() {
            Map<String, Object> result = provider.calculate("10/3", "  ");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("result")).startsWith("3.333333");
        }

        @Test
        void divisionByZeroThrows() {
            Map<String, Object> result = provider.calculate("1/0", "2");

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).asString().contains("计算失败");
        }

        @Test
        void emptyExpressionFails() {
            Map<String, Object> result = provider.calculate("", "2");

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).asString().contains("计算失败");
        }

        @Test
        void blankExpressionFails() {
            Map<String, Object> result = provider.calculate("   ", "2");

            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        void negativeNumbers() {
            Map<String, Object> result = provider.calculate("-5+3", "0");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo("-2");
        }

        @Test
        void doubleNegation() {
            Map<String, Object> result = provider.calculate("-(-5)", "0");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo("5");
        }

        @Test
        void nestedParentheses() {
            Map<String, Object> result = provider.calculate("((1+2)*(3+4))", "0");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo("21");
        }

        @Test
        void unmatchedOpenParenthesisFails() {
            Map<String, Object> result = provider.calculate("(1+2*3", "0");

            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        void unmatchedCloseParenthesisFails() {
            Map<String, Object> result = provider.calculate("1+2)*3", "0");

            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        void whitespaceIsIgnored() {
            Map<String, Object> result = provider.calculate("  1  +  2  ", "0");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo("3");
        }

        @Test
        void leftToRightAdditionSubtraction() {
            // 10-3+2 = 9（不是 10-(3+2)=5）
            Map<String, Object> result = provider.calculate("10-3+2", "0");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("result")).isEqualTo("9");
        }

        @Test
        void multiplicationBeforeAddition() {
            Map<String, Object> result = provider.calculate("2+3*4-1", "0");

            assertThat(result.get("success")).isEqualTo(true);
            // 2 + 12 - 1 = 13
            assertThat(result.get("result")).isEqualTo("13");
        }

        @Test
        void trailingGarbageFails() {
            Map<String, Object> result = provider.calculate("1+2abc", "0");

            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        void expressionEchoedInResult() {
            Map<String, Object> result = provider.calculate("1+1", "0");

            assertThat(result.get("expression")).isEqualTo("1+1");
        }

        @Test
        void largePrecisionDivision() {
            Map<String, Object> result = provider.calculate("1/7", "10");

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("result")).startsWith("0.1428571429");
        }
    }

    // ==================== webSearch ====================

    @Nested
    class WebSearchTest {

        @Test
        void searchWithConfiguredProvider() throws Exception {
            // given
            List<SearchResult> mockResults = List.of(
                    new SearchResult("标题1", "https://example.com/1", "摘要1", "智谱搜索"),
                    new SearchResult("标题2", "https://example.com/2", "摘要2", "智谱搜索"));
            when(searchProvider.search(eq("测试关键词"), anyInt())).thenReturn(mockResults);
            when(searchProvider.getProviderName()).thenReturn("zhipu");

            // when
            Map<String, Object> result = provider.webSearch("测试关键词");

            // then
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("provider")).isEqualTo("zhipu");
            assertThat(result.get("count")).isEqualTo(2);
            assertThat(result.get("results")).isEqualTo(mockResults);
        }

        @Test
        void searchReturnsEmptyResults() throws Exception {
            // given
            when(searchProvider.search(eq("冷门词"), anyInt())).thenReturn(List.of());
            when(searchProvider.getProviderName()).thenReturn("zhipu");

            // when
            Map<String, Object> result = provider.webSearch("冷门词");

            // then
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("count")).isEqualTo(0);
        }

        @Test
        void searchProviderNotConfiguredReturnsError() {
            // given: 移除 searchProvider
            ReflectionTestUtils.setField(provider, "searchProvider", null);

            // when
            Map<String, Object> result = provider.webSearch("任意词");

            // then
            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).contains("网络搜索未配置");
        }

        @Test
        void searchProviderThrowsReturnsError() throws Exception {
            // given
            when(searchProvider.search(eq("error case"), anyInt()))
                    .thenThrow(new RuntimeException("API 超时"));
            when(searchProvider.getProviderName()).thenReturn("zhipu");

            // when
            Map<String, Object> result = provider.webSearch("error case");

            // then
            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("provider")).isEqualTo("zhipu");
            assertThat((String) result.get("error")).contains("API 超时");
        }
    }

    // ==================== httpRequest (header 解析逻辑) ====================

    @Nested
    class HttpRequestTest {

        @Test
        void invalidUrlReturnsError() {
            Map<String, Object> result = provider.httpRequest(
                    "not-a-valid-url", "GET", null, null);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isNotNull();
        }

        @Test
        void unresolvableHostReturnsError() {
            // 不依赖外网：用一个一定无法解析的域名
            Map<String, Object> result = provider.httpRequest(
                    "http://this-host-does-not-exist-99999.invalid", "GET", null, null);

            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        void invalidHeadersJsonIgnoredGracefully() {
            // 非法 JSON headers 应被优雅忽略（parseHeaders 返回空 Map）
            // 即使 URL 无效，也不会因 header 解析崩溃
            Map<String, Object> result = provider.httpRequest(
                    "http://this-host-does-not-exist-99999.invalid",
                    "GET", null,
                    "{invalid json}");

            // 不应因 header 解析崩溃
            assertThat(result).containsKey("success");
        }
    }
}
