package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.dto.request.EmployeeProfileInput;
import com.fnbx.hrm.entity.EmployeeProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/** An import never overwrites what the profile already holds: it fills blanks and reports the fields that disagree. */
@Component
public class ProfileFieldMerger {

    public record Merge(EmployeeProfileInput blanksOnly, List<String> differingFields) {}

    public Merge merge(EmployeeProfile existing, EmployeeProfileInput incoming) {
        RecordComponent[] components = EmployeeProfileInput.class.getRecordComponents();
        Object[] values = new Object[components.length];
        List<String> differing = new ArrayList<>();
        BeanWrapper stored = new BeanWrapperImpl(existing);
        for (int index = 0; index < components.length; index++) {
            Object value = read(components[index], incoming);
            if (value == null) {
                continue;
            }
            Object current = stored.isReadableProperty(components[index].getName())
                    ? stored.getPropertyValue(components[index].getName()) : null;
            if (isBlank(current)) {
                values[index] = value;
            } else if (!current.equals(value)) {
                differing.add(components[index].getName());
            }
        }
        return new Merge(create(values), differing);
    }

    private Object read(RecordComponent component, EmployeeProfileInput input) {
        try {
            return component.getAccessor().invoke(input);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Cannot read " + component.getName(), ex);
        }
    }

    private EmployeeProfileInput create(Object[] values) {
        try {
            Constructor<?> canonical = EmployeeProfileInput.class.getDeclaredConstructors()[0];
            return (EmployeeProfileInput) canonical.newInstance(values);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot build the profile input", ex);
        }
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
