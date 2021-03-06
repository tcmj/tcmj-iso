package com.tcmj.pug.enums.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/** Holds all data of a single enum constant values. The enum must consist of a constant name and may have sub fields. */
public class NameTypeValue implements Comparable<NameTypeValue>, Serializable {

  private static final long serialVersionUID = 1L;
  private static final Function<Object, String> QUOTE_OBJECT = (v) -> "\"".concat(String.valueOf(v)).concat("\"");

  public final String constantName;
  public final Object[] values;

  /** Constant name. */
  public String getConstantName() {
    return constantName;
  }

  private NameTypeValue(final String constantName, final Object[] value) {
    this.constantName = constantName;
    this.values = value == null ? null : Arrays.copyOf(value, value.length);
  }

  /** Value of sub element. */
  public Object[] getValue() {
    return this.values == null ? null : Arrays.copyOf(this.values, this.values.length);
  }

  /** Create a immutable instance of NameTypeValue used to hold field values of enums. */
  public static NameTypeValue of(final String constantName, final Object[] value) {
    Objects.requireNonNull(constantName, "Constant name may not be null!");
    Objects.requireNonNull(value, "Object[] value may not be null!");
    return new NameTypeValue(constantName, value);
  }

  /** Create a immutable instance of NameTypeValue used to hold a enum constant values without having subfields. */
  public static NameTypeValue of(final String constantName) {
    Objects.requireNonNull(constantName, "Constant name may not be null!");
    return new NameTypeValue(constantName, null);
  }

  public int getSubFieldsAmount() {
    return this.values == null ? 0 : this.values.length;
  }

  @Override
  public int compareTo(NameTypeValue other) {
    return other == null ? -1 : this.constantName.compareTo(other.constantName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NameTypeValue)) {
      return false;
    }
    NameTypeValue that = (NameTypeValue) o;
    return Objects.equals(getConstantName(), that.getConstantName()) &&
      Arrays.equals(this.values, that.values);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(getConstantName());
    result = 31 * result + Arrays.hashCode(this.values);
    return result;
  }

  @Override
  public String toString() {
    final String[] value = Stream.of(this.values).map(QUOTE_OBJECT).toArray(String[]::new);
    return String.format("{\"name\":%s,\"values\":%s}", this.constantName, Arrays.toString(value));
  }
}
