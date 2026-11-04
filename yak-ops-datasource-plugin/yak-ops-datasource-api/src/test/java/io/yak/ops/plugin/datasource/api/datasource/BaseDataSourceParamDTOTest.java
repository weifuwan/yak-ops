package io.yak.ops.plugin.datasource.api.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Use MySQLDataSourceParamDTO extends BaseDataSourceParamDTO for test.
 */
public class BaseDataSourceParamDTOTest {

    private MySQLDataSourceParamDTO mockDataSourceParamDTO;

    @BeforeEach
    public void setUp() {
        mockDataSourceParamDTO = new MySQLDataSourceParamDTO();
    }

    @Test
    public void setHostAndPortByAddressTest1() {
        mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://1.2.3.4:3306");
        assertEquals("1.2.3.4", mockDataSourceParamDTO.getHost());
        assertEquals(3306, mockDataSourceParamDTO.getPort());
    }

    @Test
    public void setHostAndPortByAddressTest2() {
        mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://1.2.3.4:3306/database");
        assertEquals("1.2.3.4", mockDataSourceParamDTO.getHost());
        assertEquals(3306, mockDataSourceParamDTO.getPort());
    }

    @Test
    public void setHostAndPortByAddressTest3() {
        mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://h1,h2,h3:3306");
        assertEquals("h1,h2,h3", mockDataSourceParamDTO.getHost());
        assertEquals(3306, mockDataSourceParamDTO.getPort());
    }

    @Test
    public void setHostAndPortByAddressTest4() {
        mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://h1:3306,h2:3306,h3:3306");
        assertEquals("h1,h2,h3", mockDataSourceParamDTO.getHost());
        assertEquals(3306, mockDataSourceParamDTO.getPort());
    }

    @Test
    public void setHostAndPortByAddressTest5() {
        Throwable exception = assertThrows(IllegalArgumentException.class,
                () -> mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://h1"));
        assertEquals("host:port 'h1' illegal.", exception.getMessage());
    }

    @Test
    public void setHostAndPortByAddressTest6() {
        Throwable exception = assertThrows(NumberFormatException.class,
                () -> mockDataSourceParamDTO.setHostAndPortByAddress("jdbc:mysql://h1:port"));
        assertEquals("For input string: \"port\"", exception.getMessage());
    }

}
