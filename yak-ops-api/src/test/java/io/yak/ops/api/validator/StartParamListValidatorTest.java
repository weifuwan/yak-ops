package io.yak.ops.api.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartParamListValidatorTest {

    @InjectMocks
    private StartParamListValidator startParamListValidator;

    @Test
    void testValidate_nullList() {
        assertThatCode(() -> startParamListValidator.validate(null))
                .doesNotThrowAnyException();
    }

    @Test
    void testValidate_emptyList() {
        assertThatCode(() -> startParamListValidator.validate(Collections.emptyList()))
                .doesNotThrowAnyException();
    }

    @Test
    void testValidate_validParameters() {
        List<Property> params = Collections.singletonList(
                new Property("workflow_id", Direct.IN, DataType.VARCHAR, "1001"));
        assertThatCode(() -> startParamListValidator.validate(params))
                .doesNotThrowAnyException();
    }

    @Test
    void testValidate_rejectsBlankOrEmptyKeys() {
        assertThrowsIllegalArgument("");

        assertThrowsIllegalArgument("   ");

        assertThrowsIllegalArgument("\t");

        assertThrowsIllegalArgument("\n");

        assertThrowsIllegalArgument("  \t\n  ");
    }

    private void assertThrowsIllegalArgument(String propValue) {
        List<Property> params = Collections.singletonList(
                new Property(propValue, Direct.IN, DataType.VARCHAR, "dummyValue"));

        assertThatThrownBy(() -> startParamListValidator.validate(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameter key cannot be empty");
    }

    @Test
    void testValidate_duplicateKeys() {
        List<Property> params = new ArrayList<>();
        params.add(new Property("app_name", Direct.IN, DataType.VARCHAR, "A"));
        params.add(new Property("app_name", Direct.IN, DataType.VARCHAR, "B"));

        assertThatThrownBy(() -> startParamListValidator.validate(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate parameter key: app_name");
    }

    @Test
    void testValidate_duplicateKeysAfterTrim() {
        List<Property> params = new ArrayList<>();
        params.add(new Property(" app_name ", Direct.IN, DataType.VARCHAR, "A"));
        params.add(new Property("app_name", Direct.IN, DataType.VARCHAR, "B"));

        assertThatThrownBy(() -> startParamListValidator.validate(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate parameter key: app_name");
    }

    @Test
    void testValidate_inTypeEmptyValueAllowed() {
        List<Property> params = Collections.singletonList(
                new Property("input_var", Direct.IN, DataType.VARCHAR, ""));

        assertThatCode(() -> startParamListValidator.validate(params))
                .doesNotThrowAnyException();
    }

    @Test
    void testValidate_outTypeEmptyValueAllowed() {
        List<Property> params = Collections.singletonList(
                new Property("output_var", Direct.OUT, DataType.VARCHAR, ""));

        assertThatCode(() -> startParamListValidator.validate(params))
                .doesNotThrowAnyException();
    }
}
