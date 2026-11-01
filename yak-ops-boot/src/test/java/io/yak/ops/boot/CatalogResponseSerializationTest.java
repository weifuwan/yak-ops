package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.vo.datasource.DataSourceTablePageVO;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The final-page sentinel must survive the application's default NON_NULL serialization. */
class CatalogResponseSerializationTest {
    @Test
    void serializesExplicitNullContinuationUnderApplicationInclusionPolicy() throws Exception {
        ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        JsonNode page = mapper.readTree(mapper.writeValueAsString(new DataSourceTablePageVO(List.of(), null)));
        assertTrue(page.has("nextOffset"));
        assertTrue(page.path("nextOffset").isNull());
    }
}
