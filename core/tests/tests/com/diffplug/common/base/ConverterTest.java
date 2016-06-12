/*
 * Original Guava code is copyright (C) 2015 The Guava Authors.
 * Modifications from Guava are copyright (C) 2016 DiffPlug.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.common.base;

import static com.diffplug.common.base.Functions.toStringFunction;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import junit.framework.TestCase;

import com.diffplug.common.annotations.GwtCompatible;
import com.diffplug.common.collect.ImmutableList;
import com.diffplug.common.collect.Lists;
import com.diffplug.common.primitives.Longs;
import com.diffplug.common.testing.EqualsTester;
import com.diffplug.common.testing.SerializableTester;

/**
 * Unit tests for {@link Converter}.
 */
@GwtCompatible
public class ConverterTest extends TestCase {

	private static final Converter<String, Long> STR_TO_LONG = new Converter<String, Long>() {
		@Override
		public Long doForward(String object) {
			return Long.valueOf(object);
		}

		@Override
		public String doBackward(Long object) {
			return String.valueOf(object);
		}

		@Override
		public String toString() {
			return "string2long";
		}
	};

	private static final Long LONG_VAL = 12345L;
	private static final String STR_VAL = "12345";

	private static final ImmutableList<String> STRINGS = ImmutableList.of("123", "456");
	private static final ImmutableList<Long> LONGS = ImmutableList.of(123L, 456L);

	public void testConverter() {
		// convert
		assertEquals(LONG_VAL, STR_TO_LONG.convert(STR_VAL));
		assertEquals(STR_VAL, STR_TO_LONG.reverse().convert(LONG_VAL));
		// convertNonNull
		assertEquals(LONG_VAL, STR_TO_LONG.convertNonNull(STR_VAL));
		assertEquals(STR_VAL, STR_TO_LONG.reverse().convertNonNull(LONG_VAL));
		// revert
		assertEquals(LONG_VAL, STR_TO_LONG.reverse().revert(STR_VAL));
		assertEquals(STR_VAL, STR_TO_LONG.revert(LONG_VAL));
		// revertNonNull
		assertEquals(LONG_VAL, STR_TO_LONG.reverse().revertNonNull(STR_VAL));
		assertEquals(STR_VAL, STR_TO_LONG.revertNonNull(LONG_VAL));

		Iterable<Long> convertedValues = STR_TO_LONG.convertAll(STRINGS);
		assertEquals(LONGS, ImmutableList.copyOf(convertedValues));
	}

	public void testConvertAllIsView() {
		List<String> mutableList = Lists.newArrayList("789", "123");
		Iterable<Long> convertedValues = STR_TO_LONG.convertAll(mutableList);
		assertEquals(ImmutableList.of(789L, 123L), ImmutableList.copyOf(convertedValues));

		Iterator<Long> iterator = convertedValues.iterator();
		iterator.next();
		iterator.remove();
		assertEquals(ImmutableList.of("123"), mutableList);
	}

	public void testReverse() {
		Converter<Long, String> reverseConverter = STR_TO_LONG.reverse();

		assertEquals(STR_VAL, reverseConverter.convert(LONG_VAL));
		assertEquals(LONG_VAL, reverseConverter.reverse().convert(STR_VAL));

		Iterable<String> convertedValues = reverseConverter.convertAll(LONGS);
		assertEquals(STRINGS, ImmutableList.copyOf(convertedValues));

		assertSame(STR_TO_LONG, reverseConverter.reverse());

		assertEquals("string2long.reverse()", reverseConverter.toString());

		new EqualsTester()
				.addEqualityGroup(STR_TO_LONG, STR_TO_LONG.reverse().reverse())
				.addEqualityGroup(STR_TO_LONG.reverse(), STR_TO_LONG.reverse())
				.testEquals();
	}

	public void testReverseReverse() {
		Converter<String, Long> converter = STR_TO_LONG;
		assertEquals(converter, converter.reverse().reverse());
	}

	@SuppressWarnings("deprecation")
	public void testApply() {
		assertEquals(LONG_VAL, STR_TO_LONG.apply(STR_VAL));
	}

	private static class StringWrapper {
		private final String value;

		public StringWrapper(String value) {
			this.value = value;
		}
	}

	public void testAndThen() {
		Converter<StringWrapper, String> first = new Converter<StringWrapper, String>() {
			@Override
			public String doForward(StringWrapper object) {
				return object.value;
			}

			@Override
			public StringWrapper doBackward(String object) {
				return new StringWrapper(object);
			}

			@Override
			public String toString() {
				return "StringWrapper";
			}
		};

		Converter<StringWrapper, Long> converter = first.andThen(STR_TO_LONG);

		assertEquals(LONG_VAL, converter.convert(new StringWrapper(STR_VAL)));
		assertEquals(STR_VAL, converter.reverse().convert(LONG_VAL).value);

		assertEquals("StringWrapper.andThen(string2long)", converter.toString());

		assertEquals(first.andThen(STR_TO_LONG), first.andThen(STR_TO_LONG));
	}

	public void testIdentityConverter() {
		Converter<String, String> stringIdentityConverter = Converter.identity();

		assertSame(stringIdentityConverter, stringIdentityConverter.reverse());
		assertSame(STR_TO_LONG, stringIdentityConverter.andThen(STR_TO_LONG));

		assertSame(STR_VAL, stringIdentityConverter.convert(STR_VAL));
		assertSame(STR_VAL, stringIdentityConverter.reverse().convert(STR_VAL));

		assertEquals("Converter.identity()", stringIdentityConverter.toString());

		assertSame(Converter.identity(), Converter.identity());
	}

	public void testFrom() {
		Function<String, Integer> forward = new Function<String, Integer>() {
			@Override
			public Integer apply(String input) {
				return Integer.parseInt(input);
			}
		};
		Function<Object, String> backward = toStringFunction();

		Converter<String, Number> converter = Converter.<String, Number> from(forward, backward);

		assertNull(converter.convert(null));
		assertNull(converter.reverse().convert(null));

		assertEquals((Integer) 5, converter.convert("5"));
		assertEquals("5", converter.reverse().convert(5));
	}

	public void testNullIsNotPassedThrough() {
		Converter<String, String> nullsAreHandled = sillyConverter();
		assertEquals("forward", nullsAreHandled.convert("foo"));
		assertEquals(null, nullsAreHandled.convert(null));
		assertEquals("backward", nullsAreHandled.reverse().convert("foo"));
		assertEquals(null, nullsAreHandled.reverse().convert(null));
	}

	private static Converter<String, String> sillyConverter() {
		return new Converter<String, String>() {
			@Override
			public String doForward(String string) {
				return "forward";
			}

			@Override
			public String doBackward(String string) {
				return "backward";
			}
		};
	}

	public void testSerialization_identity() {
		Converter<String, String> identityConverter = Converter.identity();
		SerializableTester.reserializeAndAssert(identityConverter);
	}

	public void testSerialization_reverse() {
		Converter<Long, String> reverseConverter = Longs.stringConverter().reverse();
		SerializableTester.reserializeAndAssert(reverseConverter);
	}

	public void testSerialization_andThen() {
		Converter<String, Long> converterA = Longs.stringConverter();
		Converter<Long, String> reverseConverter = Longs.stringConverter().reverse();
		Converter<String, String> composedConverter = converterA.andThen(reverseConverter);
		SerializableTester.reserializeAndAssert(composedConverter);
	}

	public void testSerialization_from() {
		Converter<String, String> dumb = Converter.from(toStringFunction(), toStringFunction());
		SerializableTester.reserializeAndAssert(dumb);
	}

	public void testEquals() throws Exception {
		new EqualsTester()
				.addEqualityGroup(
						Converter.identity(),
						Converter.identity().reverse())
				.addEqualityGroup(
						Converter.from(Function.identity(), Function.identity()),
						Converter.from(Function.identity(), Function.identity(), "nameDoesntAffectEquality"))
				.testEquals();
	}
}
